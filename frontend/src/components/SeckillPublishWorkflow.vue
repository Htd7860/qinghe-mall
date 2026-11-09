<script setup lang="ts">
// Selected upstream seckill/activity/index.vue confirmation -> API -> refresh duty; staged recovery is project-specific.
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { allowed } from "../api/management";
import { ApiError } from "../api/http";
import { credentials, identities } from "../api/session";
import {
  publishActions,
  seckillPermission,
  seckillStates,
  seckillIdentity,
  seckillGet,
  seckillPublishResult,
  seckillPublishCommand,
  readPublishPending,
  forgetPublish,
  publishPendingFor,
  publishOutcome,
  samePublishIntent,
  samePublishPending,
  PublishRejected,
  type Activity,
  type PublishIntent,
  type PublishResult,
  type PublishPending,
  type PublishAction,
} from "../api/merchant-seckill";

const props = defineProps<{
  activity: Activity;
  intent: PublishIntent | null;
  disabled: boolean;
  blocked: boolean;
}>();
const emit = defineEmits<{
  busy: [value: boolean];
  pending: [value: PublishPending | null];
  selected: [value: PublishIntent];
  updated: [];
}>();
const intent = ref<PublishIntent | null>(null),
  result = ref<PublishResult | null>(null);
const pending = ref<PublishPending | null>(null),
  busy = ref(false),
  error = ref(""),
  notice = ref("");
const storageError = ref(""),
  queried = ref(false),
  reason = ref("");
const resumeQueued = ref(false);
const rejected = ref<PublishPending | null>(null);
type Confirmation = {
  action: PublishAction;
  intent: PublishIntent;
  skuId: number | null;
  retry: boolean;
  original: PublishPending | null;
  configuration: Activity | null;
};
const confirmation = ref<Confirmation | null>(null);
const canPublish = computed(
  () =>
    allowed(seckillPermission + "query") &&
    allowed(seckillPermission + "publish"),
);
const locked = computed(
  () =>
    busy.value || props.disabled || !!storageError.value || !canPublish.value,
);
let sequence = 0;
const intentKey = (x: PublishIntent | null) =>
  x ? `${x.id}:${x.expectedVersion}:${x.requestId}` : "";
const message = (e: unknown) =>
  e instanceof Error ? e.message : "处理结果待确认，请查询原发布请求";
const time = (n: number) =>
  new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    dateStyle: "short",
    timeStyle: "medium",
    hour12: false,
  }).format(n);
const money = (n: number) =>
  `${Math.floor(n / 100)}.${String(n % 100).padStart(2, "0")}`;
const confirmationQuantity = computed(
  () =>
    result.value?.skus.find((s) => s.skuId === confirmation.value?.skuId)
      ?.quantity ?? null,
);
function setBusy(value: boolean) {
  busy.value = value;
  emit("busy", value);
}
function fail(e: unknown) {
  error.value = message(e);
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
function readPending() {
  try {
    pending.value = readPublishPending();
    storageError.value = "";
    emit("pending", pending.value);
  } catch (e) {
    storageError.value = message(e);
  }
}
function choose(value: PublishIntent) {
  intent.value = {
    id: value.id,
    expectedVersion: value.expectedVersion,
    requestId: value.requestId,
  };
  emit("selected", { ...intent.value });
}
async function query() {
  if (locked.value || !intent.value) return;
  const original = { ...intent.value },
    serial = ++sequence,
    actor = seckillIdentity(),
    token = credentials().accessToken;
  resumeQueued.value = false;
  setBusy(true);
  error.value = "";
  result.value = null;
  queried.value = false;
  let changed = false;
  try {
    // Ordered reads matter: after a committed version change, an absent old intent cannot later pass prepare CAS.
    const current =
      pending.value?.action === "prepare" &&
      samePublishIntent(pending.value, original)
        ? await seckillGet(original.id)
        : null;
    const response = await seckillPublishResult(original);
    if (
      serial !== sequence ||
      actor !== seckillIdentity() ||
      token !== credentials().accessToken
    )
      return;
    result.value = response;
    queried.value = true;
    if (
      response?.currentGeneration &&
      (response.generation !== props.activity.generation ||
        response.version !== props.activity.version)
    )
      changed = true;
    const marker = pending.value;
    if (marker && samePublishIntent(marker, original)) {
      const outcome = publishOutcome(marker, response);
      const invalidated =
        marker.action === "prepare" &&
        response === null &&
        current !== null &&
        current.version > marker.expectedVersion;
      const refusalReadBack =
        marker.action !== "prepare" &&
        response !== null &&
        rejected.value !== null &&
        samePublishPending(marker, rejected.value);
      if (outcome !== "unknown" || invalidated || refusalReadBack) {
        forgetPublish(marker);
        pending.value = null;
        emit("pending", null);
        changed = true;
        rejected.value = null;
        notice.value =
          refusalReadBack && outcome === "unknown"
            ? "原操作返回拒绝，已读取当前原请求状态。后续仍按本次发布记录处理。"
            : invalidated
              ? "原草稿版本已变化，旧准备请求已失效。"
              : outcome === "closed"
                ? "原批次已结清，请核对原处理记录。"
                : "已取得原请求处理结果。";
      } else
        notice.value =
          "原操作仍待确认。可继续查询，或明确确认后按同一原请求重试。";
    } else if (response === null)
      notice.value = "暂未查到已提交记录，请保留原请求继续查询。";
  } catch (e) {
    if (
      serial === sequence &&
      actor === seckillIdentity() &&
      token === credentials().accessToken
    )
      fail(e);
  } finally {
    if (serial === sequence) {
      setBusy(false);
      if (changed) emit("updated");
    }
  }
}
const active = computed(
  () =>
    !!result.value?.currentGeneration &&
    !!intent.value &&
    result.value.id === props.activity.id &&
    result.value.generation === props.activity.generation &&
    samePublishIntent(intent.value, {
      id: result.value.id,
      expectedVersion: result.value.fromVersion,
      requestId: result.value.requestId,
    }),
);
function permitted(action: PublishAction, skuId: number | null = null) {
  if (locked.value || props.blocked) return false;
  const stopping =
    pending.value &&
    intent.value &&
    queried.value &&
    action === "cancel" &&
    ["advance", "confirm"].includes(pending.value.action) &&
    samePublishIntent(pending.value, intent.value);
  if (pending.value && !stopping) return false;
  if (action === "prepare")
    return props.activity.status === 0 && !result.value?.currentGeneration;
  const r = result.value;
  if (!active.value || !r) return false;
  const sku = r.skus.find((s) => s.skuId === skuId);
  switch (action) {
    case "advance":
      return (
        r.status === 10 &&
        r.version === r.preparedVersion &&
        !!sku &&
        sku.productStatus === null
      );
    case "cancel":
      return r.status === 10;
    case "confirm":
      return (
        r.status === 10 &&
        r.version === r.preparedVersion &&
        r.skus.every(
          (s) =>
            s.productStatus === 10 &&
            s.allocatedQuantity === s.quantity &&
            s.returnedQuantity === 0,
        )
      );
    case "withdraw":
      return (
        r.status === 20 &&
        r.published &&
        !r.withdrawn &&
        r.serverTime < Date.parse(props.activity.startTime)
      );
    case "compensate":
      return r.status === 60 && !!sku && sku.productStatus !== 20;
    case "settle":
      return (
        r.status === 60 &&
        r.skus.every(
          (s) =>
            s.productStatus === 20 &&
            s.allocatedQuantity === s.returnedQuantity,
        )
      );
    case "reopen":
      return r.status === 50 && r.settled && r.reopenedVersion === null;
    default:
      return false;
  }
}
async function ask(action: PublishAction, skuId: number | null = null) {
  if (!permitted(action, skuId)) return;
  reason.value = "";
  error.value = "";
  if (action === "prepare") {
    const serial = ++sequence,
      actor = seckillIdentity(),
      token = credentials().accessToken;
    setBusy(true);
    try {
      const fresh = await seckillGet(props.activity.id);
      if (
        serial !== sequence ||
        actor !== seckillIdentity() ||
        token !== credentials().accessToken
      )
        return;
      if (
        fresh.status !== 0 ||
        fresh.skus.some((s) =>
          [
            s.allocatedQuantity,
            s.availableQuantity,
            s.heldQuantity,
            s.soldQuantity,
            s.returnedQuantity,
          ].some((n) => n !== 0),
        )
      )
        throw new ApiError("原活动已变化，请刷新详情", 409);
      if (!globalThis.crypto?.randomUUID)
        throw new ApiError("当前浏览器无法生成发布请求号", -2);
      confirmation.value = {
        action,
        intent: {
          id: fresh.id,
          expectedVersion: fresh.version,
          requestId: crypto.randomUUID(),
        },
        skuId: null,
        retry: false,
        original: null,
        configuration: fresh,
      };
    } catch (e) {
      if (
        serial === sequence &&
        actor === seckillIdentity() &&
        token === credentials().accessToken
      )
        fail(e);
    } finally {
      if (serial === sequence) setBusy(false);
    }
    return;
  }
  const querySequence = sequence,
    queryActor = seckillIdentity(),
    queryToken = credentials().accessToken,
    originalIntent = intentKey(intent.value);
  await query(); // Re-read the original result before offering another explicit mutation.
  if (
    sequence !== querySequence + 1 ||
    queryActor !== seckillIdentity() ||
    queryToken !== credentials().accessToken ||
    originalIntent !== intentKey(intent.value)
  )
    return;
  if (!permitted(action, skuId) || !queried.value || !intent.value) return;
  confirmation.value = {
    action,
    intent: { ...intent.value },
    skuId,
    retry: false,
    original: null,
    configuration: null,
  };
}
function askRetry() {
  if (
    locked.value ||
    props.blocked ||
    !pending.value ||
    pending.value.id !== props.activity.id ||
    !queried.value
  )
    return;
  const original = { ...pending.value };
  confirmation.value = {
    action: original.action,
    intent: original,
    skuId: original.skuId,
    retry: true,
    original,
    configuration: null,
  };
  reason.value = "";
  error.value = "";
}
async function submit() {
  const choice = confirmation.value;
  if (!choice || locked.value || props.blocked) return;
  if (
    choice.retry
      ? !pending.value ||
        !queried.value ||
        !samePublishIntent(pending.value, choice.intent)
      : !permitted(choice.action, choice.skuId)
  )
    return;
  const serial = ++sequence,
    actor = seckillIdentity(),
    token = credentials().accessToken;
  setBusy(true);
  error.value = "";
  notice.value = "";
  let changed = false;
  try {
    const marker =
      choice.retry && choice.original
        ? choice.original
        : await publishPendingFor(
            choice.action,
            choice.intent,
            choice.skuId,
            reason.value,
          );
    if (
      serial !== sequence ||
      actor !== seckillIdentity() ||
      token !== credentials().accessToken
    )
      return;
    choose(marker);
    queried.value = false;
    rejected.value = null;
    pending.value = marker;
    emit("pending", marker);
    const response = await seckillPublishCommand(marker, reason.value);
    if (
      serial !== sequence ||
      actor !== seckillIdentity() ||
      token !== credentials().accessToken
    )
      return;
    result.value = response;
    queried.value = true;
    forgetPublish(marker);
    pending.value = null;
    emit("pending", null);
    confirmation.value = null;
    reason.value = "";
    changed = true;
    notice.value =
      publishOutcome(marker, response) === "closed"
        ? "原批次已结清，请核对原记录。"
        : "已取得原请求处理结果。";
  } catch (e) {
    if (
      serial !== sequence ||
      actor !== seckillIdentity() ||
      token !== credentials().accessToken
    )
      return;
    readPending();
    if (
      e instanceof PublishRejected &&
      pending.value &&
      pending.value.action !== "prepare"
    )
      rejected.value = { ...pending.value };
    confirmation.value = null;
    reason.value = "";
    fail(e);
    if (pending.value)
      notice.value =
        "操作结果待确认，请先查询原请求，不要换号。仍在准备中时，可明确停止本次准备。";
  } finally {
    if (serial === sequence) {
      setBusy(false);
      if (changed) emit("updated");
    }
  }
}
function reset() {
  sequence++;
  setBusy(false);
  intent.value = null;
  pending.value = null;
  result.value = null;
  queried.value = false;
  resumeQueued.value = false;
  rejected.value = null;
  confirmation.value = null;
  reason.value = "";
  error.value = "";
  notice.value = "";
  storageError.value = "";
}
watch(
  [
    seckillIdentity,
    () => credentials().accessToken,
    () => identities.merchant?.permissions.join("|"),
    () => props.activity.id,
  ],
  () => {
    reset();
    readPending();
    const original =
      pending.value?.id === props.activity.id
        ? pending.value
        : props.intent?.id === props.activity.id
          ? props.intent
          : null;
    if (original) {
      choose(original);
      resumeQueued.value = true;
      if (canPublish.value && !storageError.value) void query();
    }
  },
  { immediate: true },
);
watch(
  () => intentKey(props.intent),
  () => {
    if (
      !props.intent ||
      props.intent.id !== props.activity.id ||
      intentKey(props.intent) === intentKey(intent.value)
    )
      return;
    if (
      busy.value ||
      (pending.value && !samePublishIntent(pending.value, props.intent))
    ) {
      error.value = "请先确认当前原请求，再选择其他发布记录。";
      return;
    }
    sequence++;
    confirmation.value = null;
    reason.value = "";
    result.value = null;
    queried.value = false;
    choose(props.intent);
    resumeQueued.value = true;
    void query();
  },
);
watch(
  () => props.disabled,
  (disabled) => {
    if (
      !disabled &&
      resumeQueued.value &&
      !busy.value &&
      canPublish.value &&
      !storageError.value
    )
      void query();
  },
);
onBeforeUnmount(() => {
  reset();
  pending.value = null;
});
const explanations: Record<PublishAction, string> = {
  prepare: "冻结这次活动配置。准备完成后，需要逐规格划拨库存，再确认发布。",
  advance: "按本次计划划拨该规格的普通库存。",
  cancel: "停止本次发布准备。所有规格仍需逐一确认归还和关闭，之后才能结清。",
  compensate: "归还该规格实际已划拨库存，并关闭本次划拨。",
  confirm: "确认全部规格库存后发布活动。商城是否开放参与，以实际开放状态为准。",
  withdraw: "只可撤回尚未开始且未开放参与的活动。撤回后仍须逐规格归还并结清。",
  settle: "核对所有规格均已关闭并归还后，结清本次发布。",
  reopen: "保留本次历史记录，将已结清的活动恢复为草稿。",
};
</script>

<template>
  <section class="publish-workflow" aria-label="活动发布操作">
    <h3>发布操作</h3>
    <p v-if="!canPublish" class="muted">
      当前没有发布权限，不能查询本人处理结果或执行发布操作。
    </p>
    <p v-if="error || storageError" class="form-error" role="alert">
      {{ error || storageError }}
    </p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="busy" role="status">正在处理，请稍候…</p>
    <div v-if="pending" class="pending-publish">
      <strong>有待确认的{{ publishActions[pending.action].label }}</strong>
      <p v-if="pending.id !== activity.id">
        请先打开活动 {{ pending.id }} 查询原请求。
      </p>
      <template v-else>
        <p>
          请求号 {{ pending.requestId
          }}{{ pending.skuId === null ? "" : ` · SKU ${pending.skuId}` }}
        </p>
        <button class="button" :disabled="locked || !intent" @click="query">
          查询原请求
        </button>
        <button
          class="button"
          :disabled="locked || blocked || !queried"
          @click="askRetry"
        >
          按原请求重试
        </button>
        <p v-if="pending.action === 'prepare'" class="muted">
          若活动仍为草稿，可明确修改草稿使旧版本失效，再查询原请求；请勿直接换号发布。
        </p>
      </template>
    </div>
    <div v-if="canPublish && !confirmation" class="publish-buttons">
      <button
        v-if="activity.status === 0"
        class="button primary"
        :disabled="!permitted('prepare')"
        @click="ask('prepare')"
      >
        准备发布
      </button>
      <button v-if="intent" class="button" :disabled="locked" @click="query">
        查询本次发布
      </button>
      <button
        v-if="active && result?.status === 10"
        class="button primary"
        :disabled="!permitted('confirm')"
        @click="ask('confirm')"
      >
        确认发布
      </button>
      <button
        v-if="active && result?.status === 10"
        class="button"
        :disabled="!permitted('cancel')"
        @click="ask('cancel')"
      >
        停止本次准备
      </button>
      <button
        v-if="active && result?.status === 20"
        class="button"
        :disabled="!permitted('withdraw')"
        @click="ask('withdraw')"
      >
        撤回未开始活动
      </button>
      <button
        v-if="active && result?.status === 60"
        class="button"
        :disabled="!permitted('settle')"
        @click="ask('settle')"
      >
        确认结清
      </button>
      <button
        v-if="active && result?.status === 50"
        class="button"
        :disabled="!permitted('reopen')"
        @click="ask('reopen')"
      >
        恢复草稿
      </button>
    </div>
    <p v-if="!intent && activity.status !== 0" class="muted">
      请选择您发起的原发布记录，查询后继续操作。
    </p>
    <form
      v-if="confirmation"
      class="publish-confirmation"
      @submit.prevent="submit"
    >
      <h4>
        {{ confirmation.retry ? "按原请求重试：" : ""
        }}{{ publishActions[confirmation.action].label }}
      </h4>
      <p>
        {{ confirmation.configuration?.name ?? activity.name
        }}{{
          confirmation.skuId === null ? "" : ` · SKU ${confirmation.skuId}`
        }}
      </p>
      <p>{{ explanations[confirmation.action] }}</p>
      <p v-if="confirmationQuantity !== null">
        本规格原计划数量：{{ confirmationQuantity }}
      </p>
      <template v-if="confirmation.configuration">
        <p>
          {{ confirmation.configuration.productName }} · 北京时间
          {{ time(Date.parse(confirmation.configuration.startTime)) }} 至
          {{ time(Date.parse(confirmation.configuration.endTime)) }}
        </p>
        <p>
          单次限购 {{ confirmation.configuration.singleLimit }}，每人累计
          {{ confirmation.configuration.memberLimit }}。
        </p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>冻结规格</th>
                <th>活动价</th>
                <th>计划数量</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in confirmation.configuration.skus" :key="s.skuId">
                <td>SKU {{ s.skuId }}</td>
                <td>¥{{ money(s.priceCent) }}</td>
                <td>{{ s.plannedQuantity }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
      <label v-if="['cancel', 'withdraw'].includes(confirmation.action)"
        >{{ confirmation.retry ? "重新填写原原因" : "原因" }}
        <input v-model="reason" required maxlength="500" :disabled="locked" />
      </label>
      <div class="publish-buttons">
        <button
          type="button"
          class="button"
          :disabled="busy"
          @click="
            confirmation = null;
            reason = '';
          "
        >
          取消
        </button>
        <button class="button primary" :disabled="locked || blocked">
          确认{{ publishActions[confirmation.action].label }}
        </button>
      </div>
    </form>
    <template v-if="result">
      <p>
        第 {{ result.generation }} 次发布 ·
        {{
          result.currentGeneration
            ? `当前状态：${seckillStates[result.status ?? -1] || "待确认"}`
            : "历史发布记录"
        }}
      </p>
      <p>
        {{ result.published ? "已有发布记录" : "尚无发布记录"
        }}{{ result.withdrawn ? " · 已申请撤回" : ""
        }}{{ result.settled ? " · 已结清" : ""
        }}{{ result.reopenedVersion !== null ? " · 曾恢复为草稿" : "" }}
      </p>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>规格</th>
              <th>原计划</th>
              <th>库存确认</th>
              <th>已划拨 / 已归还</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in result.skus" :key="s.skuId">
              <td>SKU {{ s.skuId }}</td>
              <td>{{ s.quantity }}</td>
              <td>
                {{
                  s.productStatus === null
                    ? "尚无确认记录"
                    : s.productStatus === 10
                      ? "已划拨"
                      : s.productStatus === 50
                        ? "部分归还"
                        : s.productStatus === 30
                          ? "全部归还"
                          : "已关闭"
                }}
              </td>
              <td>
                {{ s.allocatedQuantity ?? "—" }} /
                {{ s.returnedQuantity ?? "—" }}
              </td>
              <td>
                <button
                  v-if="active && result.status === 10"
                  class="text-button"
                  :disabled="!permitted('advance', s.skuId)"
                  @click="ask('advance', s.skuId)"
                >
                  划拨本规格
                </button>
                <button
                  v-if="active && result.status === 60"
                  class="text-button"
                  :disabled="!permitted('compensate', s.skuId)"
                  @click="ask('compensate', s.skuId)"
                >
                  归还并关闭本规格
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="muted">
        查询截至 {{ time(result.serverTime) }}。尚无确认记录不代表库存没有变化。
      </p>
    </template>
  </section>
</template>

<style scoped>
.publish-workflow {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}
.publish-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 14px 0;
}
.pending-publish,
.publish-confirmation {
  padding: 16px;
  margin: 12px 0;
  border: 1px solid var(--border);
  border-radius: 8px;
}
.publish-confirmation label {
  display: grid;
  gap: 8px;
}
.pending-publish .button {
  margin-right: 10px;
}
.pending-publish p {
  overflow-wrap: anywhere;
}
</style>
