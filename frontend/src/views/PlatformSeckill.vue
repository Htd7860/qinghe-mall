<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import Modal from "../components/Modal.vue";
import { ApiError } from "../api/http";
import { allowed } from "../api/management";
import { credentials, identities } from "../api/session";
import { seckillStates } from "../api/merchant-seckill";
import {
  platformSeckillPermission,
  platformSeckillIdentity,
  platformSeckillPage,
  platformSeckillAudits,
  platformSeckillStop,
  readPlatformStop,
  rememberPlatformStop,
  forgetPlatformStop,
  type PlatformActivity,
  type PlatformAudit,
  type StopCommand,
} from "../api/platform-seckill";

const route = useRoute();
const canRead = computed(() => allowed(platformSeckillPermission + "query"));
const canStop = computed(
  () => canRead.value && allowed(platformSeckillPermission + "stop"),
);
const query = reactive({ tenant: "", name: "", status: "" });
const rows = ref<PlatformActivity[]>([]),
  total = ref(0),
  page = ref(1);
const busy = ref(false),
  error = ref(""),
  notice = ref("");
const pending = ref<StopCommand | null>(null),
  storageError = ref("");
const stopError = ref(""),
  rejected = ref(false);
const confirmation = ref<PlatformActivity | null>(null),
  reason = ref("");
const selected = ref<PlatformActivity | null>(null),
  audits = ref<PlatformAudit[]>([]);
const auditTotal = ref(0),
  auditPage = ref(1),
  auditError = ref("");
let epoch = 0,
  listSequence = 0,
  auditSequence = 0;
const time = (x: string) =>
  new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    dateStyle: "short",
    timeStyle: "medium",
    hour12: false,
  }).format(Date.parse(x));
const realmName = { platform: "平台", merchant: "商家", system: "系统" };
const actionNames: Record<string, string> = {
  CREATE: "创建",
  UPDATE: "修改",
  PREPARE: "发布准备",
  FAIL: "发布失败",
  PUBLISH: "发布",
  SETTLE: "失败结清",
  WITHDRAW: "撤回",
  REOPEN: "恢复草稿",
  EPOCH_INIT: "准备运行",
  EPOCH_SEED: "初始化确认",
  EPOCH_RUNTIME: "运行许可",
  EPOCH_READY: "运行就绪",
  EPOCH_STOP: "停止接单",
  EPOCH_EXPIRE: "到期停止",
  CLEARING: "进入清算",
  CLEARING_DONE: "清算完成",
};
function message(e: unknown) {
  return e instanceof Error ? e.message : "读取失败，请重试";
}
function fail(e: unknown) {
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
function restorePending() {
  pending.value = null;
  storageError.value = "";
  try {
    pending.value = readPlatformStop();
  } catch (e) {
    storageError.value = message(e);
  }
}
async function load() {
  if (!canRead.value || busy.value) return;
  const ticket = epoch,
    serial = ++listSequence;
  busy.value = true;
  error.value = "";
  rows.value = [];
  total.value = 0;
  try {
    const data = await platformSeckillPage(
      page.value,
      query.tenant.trim(),
      query.name.trim(),
      query.status,
    );
    if (ticket !== epoch || serial !== listSequence) return;
    rows.value = data.list;
    total.value = data.total;
  } catch (e) {
    if (ticket === epoch && serial === listSequence) {
      error.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === listSequence) busy.value = false;
  }
}
async function loadAudits(row = selected.value) {
  if (!row || !canRead.value || busy.value) return;
  selected.value = row;
  audits.value = [];
  auditError.value = "";
  auditTotal.value = 0;
  const ticket = epoch,
    serial = ++auditSequence;
  busy.value = true;
  try {
    const data = await platformSeckillAudits(row, auditPage.value);
    if (ticket !== epoch || serial !== auditSequence) return;
    audits.value = data.list;
    auditTotal.value = data.total;
  } catch (e) {
    if (ticket === epoch && serial === auditSequence) {
      auditError.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === auditSequence) busy.value = false;
  }
}
function ask(row: PlatformActivity) {
  if (
    !canStop.value ||
    busy.value ||
    pending.value ||
    storageError.value ||
    row.status !== 20
  )
    return;
  confirmation.value = { ...row };
  reason.value = "";
  error.value = "";
}
async function stop(command: StopCommand) {
  if (!canStop.value || busy.value || storageError.value) return;
  const ticket = epoch;
  stopError.value = "";
  rejected.value = false;
  notice.value = "";
  try {
    rememberPlatformStop(command);
    pending.value = { ...command };
  } catch (e) {
    storageError.value = message(e);
    return;
  }
  confirmation.value = null;
  busy.value = true;
  try {
    const receipt = await platformSeckillStop(command);
    if (ticket !== epoch) return;
    // 只在完整原命令回执后清理。网络未知、409或服务关闭均保留原号待核对。
    forgetPlatformStop(command);
    pending.value = null;
    notice.value =
      receipt.cacheCloseState === "PENDING"
        ? "数据库已停止接单，缓存关闭仍待后台确认。"
        : "本次停止已确认，原订单与售后继续处理。";
  } catch (e) {
    if (ticket === epoch) {
      stopError.value = message(e);
      rejected.value =
        e instanceof ApiError &&
        !e.outcomeUnknown &&
        [400, 403, 404, 409].includes(e.code);
      fail(e);
    }
  } finally {
    if (ticket === epoch) {
      busy.value = false;
      void load();
    }
  }
}
function acknowledgeRejected() {
  if (!rejected.value || !pending.value || busy.value) return;
  try {
    forgetPlatformStop(pending.value);
    pending.value = null;
    rejected.value = false;
    stopError.value = "";
    notice.value = "已结束被拒绝操作的核对，请读取最新活动后重新决定。";
  } catch (e) {
    storageError.value = message(e);
  }
}
function confirm() {
  const row = confirmation.value;
  if (!row) return;
  void stop({
    tenantId: row.tenantId,
    id: row.id,
    generation: row.generation,
    expectedVersion: row.version,
    reason: reason.value.trim(),
  });
}
function closeAudit() {
  if (busy.value) return;
  auditSequence++;
  selected.value = null;
  audits.value = [];
  auditError.value = "";
}
function clear() {
  stopError.value = "";
  rejected.value = false;
  epoch++;
  listSequence++;
  auditSequence++;
  busy.value = false;
  rows.value = [];
  total.value = 0;
  selected.value = null;
  audits.value = [];
  confirmation.value = null;
  reason.value = "";
  pending.value = null;
  storageError.value = "";
  error.value = "";
  notice.value = "";
  auditError.value = "";
}
watch(
  [
    platformSeckillIdentity,
    () => credentials().accessToken,
    () => identities.platform?.permissions.join("|"),
    () => route.fullPath,
  ],
  () => {
    clear();
    page.value = 1;
    query.tenant = "";
    query.name = "";
    query.status = "";
    if (route.path === "/platform/seckill" && canRead.value) {
      restorePending();
      void load();
    }
  },
  { immediate: true },
);
onBeforeUnmount(clear);
</script>

<template>
  <PageHeader title="活动监管" eyebrow="平台运营">
    <button class="button" :disabled="busy || !canRead" @click="load">
      刷新活动
    </button>
  </PageHeader>
  <p v-if="!canRead" class="form-error" role="alert">
    当前没有活动监管查询权限。
  </p>
  <p v-if="error" class="form-error" role="alert">{{ error }}</p>
  <p v-if="stopError" class="form-error" role="alert">{{ stopError }}</p>
  <p v-if="notice" role="status">{{ notice }}</p>
  <section
    v-if="pending || storageError"
    class="stop-recovery"
    aria-label="原停止操作核对"
  >
    <h3>有一条停止操作待核对</h3>
    <p v-if="storageError" class="form-error" role="alert">
      {{ storageError }}
    </p>
    <template v-if="pending">
      <p>
        商家 {{ pending.tenantId }} · 活动 {{ pending.id }} · 第
        {{ pending.generation }} 轮
      </p>
      <p>原因：{{ pending.reason }}</p>
      <p class="muted">
        将按原轮次和版本重查停止结果，不会改为停止新一轮活动。
      </p>
      <button
        class="button"
        :disabled="busy || !canStop || !!storageError"
        @click="stop(pending)"
      >
        按原操作重查
      </button>
      <button
        v-if="rejected"
        class="button"
        :disabled="busy"
        @click="acknowledgeRejected"
      >
        结束已拒绝操作的核对
      </button>
    </template>
  </section>
  <form
    class="platform-seckill-filter"
    @submit.prevent="
      page = 1;
      load();
    "
  >
    <label
      >商家编号<input
        v-model="query.tenant"
        inputmode="numeric"
        :disabled="busy"
        placeholder="全部商家"
    /></label>
    <label
      >活动名称<input v-model="query.name" maxlength="60" :disabled="busy"
    /></label>
    <label
      >状态<select v-model="query.status" :disabled="busy">
        <option value="">全部状态</option>
        <option
          v-for="(name, code) in seckillStates"
          :key="code"
          :value="String(code)"
        >
          {{ name }}
        </option>
      </select></label
    >
    <button class="button primary" :disabled="busy || !canRead">查询</button>
  </form>
  <p v-if="busy && !selected && !confirmation" role="status">正在处理…</p>
  <div v-if="rows.length" class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>商家 / 活动</th>
          <th>活动 / 商品</th>
          <th>时间（北京）</th>
          <th>轮次 / 状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.id">
          <td>{{ r.tenantId }} / {{ r.id }}</td>
          <td>
            {{ r.name }}<small>{{ r.productName }}</small>
          </td>
          <td>
            {{ time(r.startTime) }}<small>至 {{ time(r.endTime) }}</small>
          </td>
          <td>
            第 {{ r.generation }} 轮<small>{{ seckillStates[r.status] }}</small>
          </td>
          <td>
            <button
              class="text-button"
              :disabled="busy"
              @click="
                auditPage = 1;
                loadAudits(r);
              "
            >
              查看记录
            </button>
            <button
              v-if="canStop && r.status === 20"
              class="text-button"
              :disabled="busy || !!pending || !!storageError"
              @click="ask(r)"
            >
              停止接单
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <p v-else-if="!busy && !error && canRead" class="muted">
    没有符合条件的活动。
  </p>
  <div class="platform-seckill-pages">
    <span>共 {{ total }} 条 · 第 {{ page }} 页</span>
    <button
      class="button"
      :disabled="busy || page <= 1"
      @click="
        page--;
        load();
      "
    >
      上一页
    </button>
    <button
      class="button"
      :disabled="busy || page >= 10000 || page * 20 >= total"
      @click="
        page++;
        load();
      "
    >
      下一页
    </button>
  </div>
  <Modal
    v-if="confirmation"
    title="停止活动接单"
    @close="!busy && (confirmation = null)"
  >
    <p>
      商家 {{ confirmation.tenantId }} · {{ confirmation.name }} · 第
      {{ confirmation.generation }} 轮
    </p>
    <p>
      停止后不再受理新购买，已有订单、付款和售后继续处理，不会立即退回全部库存。
    </p>
    <form id="platform-seckill-stop" @submit.prevent="confirm">
      <label class="stop-reason"
        >停止原因<textarea
          v-model="reason"
          required
          maxlength="500"
          rows="4"
          :disabled="busy"
        />
      </label>
    </form>
    <template #footer
      ><button class="button" :disabled="busy" @click="confirmation = null">
        取消</button
      ><button
        class="button primary"
        form="platform-seckill-stop"
        :disabled="busy || !reason.trim()"
      >
        确认停止接单
      </button></template
    >
  </Modal>
  <Modal v-if="selected" title="活动操作记录" wide @close="closeAudit">
    <p>
      商家 {{ selected.tenantId }} · 活动 {{ selected.id }} ·
      {{ selected.name }}
    </p>
    <p v-if="busy" role="status">正在读取记录…</p>
    <p v-if="auditError" class="form-error" role="alert">{{ auditError }}</p>
    <div v-if="audits.length" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>时间（北京）</th>
            <th>动作</th>
            <th>操作者来源 / 编号</th>
            <th>版本变化</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in audits" :key="a.id">
            <td>{{ time(a.createTime) }}</td>
            <td>{{ actionNames[a.action] || a.action }}</td>
            <td>{{ realmName[a.actorRealm] }} / {{ a.actorId }}</td>
            <td>{{ a.fromVersion }} → {{ a.toVersion }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else-if="!busy && !auditError" class="muted">暂无操作记录。</p>
    <template #footer
      ><span>共 {{ auditTotal }} 条 · 第 {{ auditPage }} 页</span>
      <button
        class="button"
        :disabled="busy || auditPage <= 1"
        @click="
          auditPage--;
          loadAudits();
        "
      >
        上一页
      </button>
      <button
        class="button"
        :disabled="busy || auditPage >= 10000 || auditPage * 20 >= auditTotal"
        @click="
          auditPage++;
          loadAudits();
        "
      >
        下一页
      </button>
      <button class="button" :disabled="busy" @click="loadAudits()">
        刷新记录</button
      ><button class="button" :disabled="busy" @click="closeAudit">
        关闭
      </button></template
    >
  </Modal>
</template>

<style scoped>
.platform-seckill-filter,
.platform-seckill-pages {
  display: flex;
  align-items: end;
  flex-wrap: wrap;
  gap: 12px;
  margin: 20px 0;
}
.platform-seckill-filter label,
.stop-reason {
  display: grid;
  gap: 8px;
}
.platform-seckill-pages {
  align-items: center;
  justify-content: flex-end;
}
.platform-seckill-pages span {
  margin-right: auto;
  color: var(--muted);
}
.stop-recovery {
  border: 1px solid var(--border);
  padding: 16px;
  margin: 20px 0;
}
.stop-recovery p {
  margin: 10px 0;
  overflow-wrap: anywhere;
}
.stop-reason {
  margin-top: 20px;
}
td small {
  display: block;
  color: var(--muted);
  margin-top: 6px;
}
@media (max-width: 640px) {
  .platform-seckill-filter label {
    width: 100%;
  }
}
</style>
