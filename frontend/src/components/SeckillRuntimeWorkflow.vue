<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { allowed } from "../api/management";
import { ApiError } from "../api/http";
import { credentials, identities } from "../api/session";
import {
  seckillPermission,
  seckillIdentity,
  runtimeActions,
  readRuntimePending,
  rememberRuntimePending,
  forgetRuntimePending,
  seckillRuntimeResult,
  seckillRuntimeCommand,
  type RuntimeReceipt,
  type RuntimePending,
  type RuntimeAction,
  type PublishIntent,
  type Activity,
} from "../api/merchant-seckill";
const props = defineProps<{
  activity: Activity;
  intent: PublishIntent | null;
  disabled: boolean;
  blocked: boolean;
}>();
const emit = defineEmits<{
  busy: [value: boolean];
  pending: [value: boolean];
  updated: [];
  recover: [id: number];
}>();
const pending = ref<RuntimePending | null>(null),
  receipt = ref<RuntimeReceipt | null>(null);
const busy = ref(false),
  queried = ref(false),
  error = ref(""),
  storageError = ref(""),
  notice = ref("");
const confirmation = ref<RuntimeAction | null>(null),
  reason = ref(""),
  rejected = ref(false);
let sequence = 0;
const can = computed(
  () =>
    allowed(seckillPermission + "query") &&
    allowed(seckillPermission + "publish"),
);
const intent = computed(() =>
  pending.value?.intent.id === props.activity.id
    ? pending.value.intent
    : props.intent,
);
const foreign = computed(
  () => !!pending.value && pending.value.intent.id !== props.activity.id,
);
const locked = computed(
  () =>
    busy.value ||
    props.disabled ||
    props.blocked ||
    !can.value ||
    !!storageError.value ||
    foreign.value,
);
const stages: Record<number, string> = {
  0: "定义待确认",
  10: "运行定义已确认",
  15: "运行库存初始化阶段",
  20: "数据库已确认就绪",
  30: "已停止新接单",
  40: "未初始化运行库存即关闭",
};
const time = (s: string) =>
  new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    dateStyle: "short",
    timeStyle: "medium",
    hour12: false,
  }).format(Date.parse(s));
function setBusy(value: boolean) {
  busy.value = value;
  emit("busy", value);
}
function restore() {
  storageError.value = "";
  pending.value = null;
  try {
    pending.value = readRuntimePending();
  } catch (e) {
    storageError.value = e instanceof Error ? e.message : "原记录读取失败";
  }
  emit("pending", !!pending.value || !!storageError.value);
}
function fail(e: unknown) {
  error.value = e instanceof Error ? e.message : "运行结果待确认，请查询原记录";
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
function completed(p: RuntimePending, r: RuntimeReceipt | null) {
  if (!r) return false;
  return p.action === "seed"
    ? [10, 15, 20, 30].includes(r.stage)
    : p.action === "initialize"
      ? [15, 20, 30].includes(r.stage)
      : p.action === "ready"
        ? r.stage === 20
        : r.stage === 30;
}
async function load() {
  const original = intent.value;
  if (!original || locked.value) return;
  const ticket = ++sequence;
  setBusy(true);
  error.value = "";
  queried.value = false;
  receipt.value = null;
  try {
    const r = await seckillRuntimeResult(original);
    if (ticket !== sequence) return;
    receipt.value = r;
    queried.value = true;
    // 查询可证明阶段推进，但停止原因/操作者仍须原POST幂等回执核对，不凭其他人的停止清记录。
    if (
      pending.value &&
      pending.value.action !== "stop" &&
      completed(pending.value, r)
    ) {
      forgetRuntimePending(pending.value);
      restore();
      notice.value = "已从原运行记录确认本次阶段。";
    }
  } catch (e) {
    if (ticket === sequence) fail(e);
  } finally {
    if (ticket === sequence) setBusy(false);
  }
}
function permitted(action: RuntimeAction) {
  if (
    locked.value ||
    pending.value ||
    !intent.value ||
    !queried.value ||
    props.activity.status !== 20
  )
    return false;
  if (action === "seed")
    return receipt.value === null || receipt.value.stage === 0;
  if (action === "initialize") return receipt.value?.stage === 10;
  if (action === "ready" || action === "stop")
    return (
      receipt.value?.stage === 15 ||
      (receipt.value?.stage === 20 && action === "stop")
    );
  return false;
}
function ask(action: RuntimeAction) {
  if (!permitted(action)) return;
  reason.value = "";
  confirmation.value = action;
}
async function submit(value: RuntimePending) {
  if (locked.value) return;
  try {
    rememberRuntimePending(value);
    restore();
  } catch (e) {
    fail(e);
    return;
  }
  confirmation.value = null;
  const ticket = ++sequence;
  setBusy(true);
  error.value = "";
  notice.value = "";
  rejected.value = false;
  try {
    const r = await seckillRuntimeCommand(value);
    if (ticket !== sequence) return;
    receipt.value = r;
    queried.value = true;
    if (!completed(value, r))
      throw new ApiError("原操作结果尚未确认，请保留原记录", -2, true);
    forgetRuntimePending(value);
    restore();
    notice.value =
      value.action === "stop"
        ? "数据库已停止接单，请继续核对缓存关闭进度。"
        : "本次运行阶段已确认。";
    emit("updated");
  } catch (e) {
    if (ticket === sequence) {
      fail(e);
      rejected.value =
        e instanceof ApiError &&
        !e.outcomeUnknown &&
        [400, 403, 404, 409].includes(e.code);
    }
  } finally {
    if (ticket === sequence) setBusy(false);
  }
}
function confirm() {
  if (!confirmation.value || !intent.value || !permitted(confirmation.value))
    return;
  void submit({
    intent: { ...intent.value },
    action: confirmation.value,
    reason: confirmation.value === "stop" ? reason.value.trim() : "",
  });
}
function acknowledge() {
  if (!rejected.value || !pending.value || locked.value) return;
  try {
    forgetRuntimePending(pending.value);
    restore();
    rejected.value = false;
    queried.value = false;
    notice.value = "已结束被拒绝操作，请重新查询原阶段。";
  } catch (e) {
    fail(e);
  }
}
watch(
  [
    () => props.activity.id,
    () => props.activity.version,
    () => props.intent?.requestId,
    () => props.intent?.expectedVersion,
    () => props.disabled,
    seckillIdentity,
    () => credentials().accessToken,
    () => identities.merchant?.permissions.join("|"),
  ],
  () => {
    sequence++;
    setBusy(false);
    queried.value = false;
    receipt.value = null;
    error.value = "";
    notice.value = "";
    confirmation.value = null;
    rejected.value = false;
    restore();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  sequence++;
  emit("busy", false);
});
</script>

<template>
  <section class="runtime-panel" aria-label="运行阶段管理" :aria-busy="busy">
    <h3>运行阶段</h3>
    <p v-if="!can" class="muted">运行操作需要活动查询与发布权限。</p>
    <p v-else-if="!intent && !pending" class="muted">
      请先在发布记录中选择本人本次发布，查询运行阶段。
    </p>
    <p v-if="storageError" class="form-error" role="alert">
      {{ storageError }}
    </p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="busy" role="status">正在核对原运行记录…</p>
    <div v-if="pending" class="runtime-recovery">
      <p>
        待核对：活动 {{ pending.intent.id }} ·
        {{ runtimeActions[pending.action].label }}
      </p>
      <button
        v-if="foreign"
        class="button"
        :disabled="busy || disabled"
        @click="emit('recover', pending.intent.id)"
      >
        打开原活动核对
      </button>
      <button v-else class="button" :disabled="locked" @click="submit(pending)">
        按原操作重试
      </button>
      <button
        v-if="rejected"
        class="button"
        :disabled="locked"
        @click="acknowledge"
      >
        结束已拒绝操作的核对
      </button>
    </div>
    <template v-if="intent && !foreign && can">
      <button class="button" :disabled="locked" @click="load">
        查询原运行阶段
      </button>
      <template v-if="queried">
        <p class="runtime-state">
          {{ receipt ? stages[receipt.stage] : "尚未准备运行定义" }}
        </p>
        <p v-if="receipt?.initDeadline" class="muted">
          固定初始化截止：{{
            time(receipt.initDeadline)
          }}（北京）。确认就绪须等待服务端窗口结束，不会自动续期。
        </p>
        <p v-if="receipt?.stage === 30" class="muted">
          缓存关闭：{{
            receipt.cacheCloseState === "SYNCED" ? "已确认" : "仍待后台确认"
          }}。原订单和售后继续处理。
        </p>
        <p class="muted">
          这里显示数据库阶段，实际能否购买仍由服务端校验。缺失的运行库存不会自动重建。
        </p>
      </template>
      <div class="runtime-actions">
        <button
          v-for="(action, key) in runtimeActions"
          :key="key"
          class="button"
          :disabled="!permitted(key)"
          @click="ask(key)"
        >
          {{ action.label }}
        </button>
      </div>
    </template>
    <form v-if="confirmation" class="runtime-confirm" @submit.prevent="confirm">
      <p>
        确认对活动 {{ activity.id }} 执行“{{
          runtimeActions[confirmation].label
        }}”？
      </p>
      <label v-if="confirmation === 'stop'"
        >停止原因<input
          v-model="reason"
          required
          maxlength="500"
          :disabled="busy"
      /></label>
      <p v-if="confirmation === 'stop'" class="muted">
        停止后不再受理新购买，不会取消已有订单或立即归还全部库存。
      </p>
      <button
        class="button"
        type="button"
        :disabled="busy"
        @click="confirmation = null"
      >
        取消
      </button>
      <button
        class="button primary"
        :disabled="locked || (confirmation === 'stop' && !reason.trim())"
      >
        确认执行
      </button>
    </form>
  </section>
</template>

<style scoped>
.runtime-panel {
  border-top: 1px solid var(--border);
  padding-top: 20px;
  margin-top: 24px;
}
.runtime-panel p {
  margin: 12px 0;
  overflow-wrap: anywhere;
}
.runtime-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}
.runtime-state {
  font-weight: 600;
}
.runtime-confirm,
.runtime-recovery {
  padding: 14px 0;
}
.runtime-confirm label {
  display: grid;
  gap: 8px;
  margin: 12px 0;
}
</style>
