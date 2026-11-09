<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import SeckillPublishWorkflow from "../components/SeckillPublishWorkflow.vue";
import SeckillClearingProgress from "../components/SeckillClearingProgress.vue";
import SeckillRuntimeWorkflow from "../components/SeckillRuntimeWorkflow.vue";
import { ApiError } from "../api/http";
import { allowed } from "../api/management";
import { credentials, identities } from "../api/session";
import {
  productPage,
  productDetail,
  type ProductRow,
  type ProductDetail,
} from "../api/product";
import {
  seckillIdentity,
  seckillPermission,
  seckillStates,
  seckillPage,
  seckillGet,
  seckillSave,
  seckillHistory,
  readDraftPending,
  readPublishPending,
  readRuntimePending,
  publishActions,
  rememberDraft,
  forgetDraft,
  type Activity,
  type ActivityRow,
  type DraftInput,
  type DraftPending,
  type PublishHistory,
  type PublishIntent,
  type PublishPending,
} from "../api/merchant-seckill";

const route = useRoute();
const can = (action: string) =>
  allowed(seckillPermission + "query") && allowed(seckillPermission + action);
const productAllowed = computed(() => allowed("product:spu:query"));
const query = reactive({ name: "", status: "" });
const rows = ref<ActivityRow[]>([]),
  total = ref(0),
  page = ref(1);
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref("");
const listReadComplete = ref(false);
const publishBusy = ref(false),
  publishPending = ref<PublishPending | null>(null),
  publishRecoveryError = ref("");
const runtimeBusy = ref(false),
  runtimeBlocked = ref(false);
const locked = computed(
  () => loading.value || busy.value || publishBusy.value || runtimeBusy.value,
);
const detail = ref<Activity | null>(null),
  editing = ref(false),
  modalError = ref("");
const pending = ref<DraftPending | null>(null),
  recoveryError = ref("");
const saveBlocked = computed(
  () =>
    !!pending.value ||
    !!recoveryError.value ||
    !!publishRecoveryError.value ||
    runtimeBlocked.value ||
    (!!publishPending.value && publishPending.value.action !== "prepare"),
);
const history = ref<PublishHistory[]>([]),
  historyTotal = ref(0),
  historyPage = ref(1),
  historyError = ref("");
const selectedIntent = ref<PublishIntent | null>(null);
const clearingGeneration = ref<number | null>(null);
const picker = ref(false),
  productName = ref(""),
  productNo = ref(1),
  productTotal = ref(0);
const productOptions = ref<ProductRow[]>([]),
  chosenProduct = ref<ProductDetail | null>(null);
const form = reactive({
  name: "",
  startTime: "",
  endTime: "",
  singleLimit: "1",
  memberLimit: "1",
});
type SkuEditor = {
  skuId: number;
  label: string;
  selected: boolean;
  price: string;
  planned: string;
  stock: number | null;
  unavailable: boolean;
};
const skus = ref<SkuEditor[]>([]);
let epoch = 0,
  listSequence = 0,
  modalSequence = 0;
const stateName = (status: number | null) =>
  status === null ? "状态待确认" : seckillStates[status] || "状态待确认";
const money = (n: number) =>
  `${Math.floor(n / 100)}.${String(n % 100).padStart(2, "0")}`;
const spec = (properties: Activity["skus"][number]["properties"]) =>
  properties.map((p) => `${p.propertyName}：${p.valueName}`).join(" / ");
const time = (s: string) =>
  new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    dateStyle: "short",
    timeStyle: "medium",
    hour12: false,
  }).format(Date.parse(s));
const inputTime = (s: string) =>
  new Date(Date.parse(s) + 8 * 3600000).toISOString().slice(0, 23);
const message = (e: unknown) =>
  e instanceof Error ? e.message : "操作未完成，请重新查询原活动";
function fail(e: unknown) {
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
async function readProduct<T>(read: () => Promise<T>) {
  const actor = seckillIdentity(),
    token = credentials().accessToken;
  const response = await read();
  if (actor !== seckillIdentity() || token !== credentials().accessToken)
    throw new ApiError("身份已变化，请重新选择商品", 409);
  return response;
}
function restorePending() {
  try {
    runtimeBlocked.value = !!readRuntimePending();
  } catch {
    runtimeBlocked.value = true;
  }
  pending.value = null;
  recoveryError.value = "";
  try {
    pending.value = readDraftPending();
  } catch (e) {
    recoveryError.value = message(e);
  }
  publishPending.value = null;
  publishRecoveryError.value = "";
  try {
    publishPending.value = readPublishPending();
  } catch (e) {
    publishRecoveryError.value = message(e);
  }
}
async function load() {
  if (!can("query")) return;
  const ticket = epoch,
    serial = ++listSequence;
  loading.value = true;
  listReadComplete.value = false;
  rows.value = [];
  total.value = 0;
  error.value = "";
  try {
    const response = await seckillPage(
      page.value,
      query.name.trim(),
      query.status,
    );
    if (ticket !== epoch || serial !== listSequence) return;
    rows.value = response.list;
    total.value = response.total;
    listReadComplete.value = true;
  } catch (e) {
    if (ticket === epoch && serial === listSequence) {
      error.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === listSequence) loading.value = false;
  }
}
function search() {
  page.value = 1;
  void load();
}
function changePage(delta: number) {
  page.value += delta;
  void load();
}
async function loadHistory() {
  if (!detail.value || locked.value) return;
  const ticket = epoch,
    serial = modalSequence,
    id = detail.value.id;
  busy.value = true;
  history.value = [];
  historyTotal.value = 0;
  historyError.value = "";
  selectedIntent.value = null;
  try {
    const response = await seckillHistory(id, historyPage.value);
    if (ticket !== epoch || serial !== modalSequence) return;
    history.value = response.list;
    historyTotal.value = response.total;
    if (clearingGeneration.value === null) {
      const current = response.list.find(
        (h) =>
          h.generation === detail.value?.generation &&
          h.published &&
          !h.withdrawn,
      );
      if (current) clearingGeneration.value = current.generation;
    }
  } catch (e) {
    if (ticket === epoch && serial === modalSequence) {
      historyError.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === modalSequence) busy.value = false;
  }
}
async function openDetail(id: number) {
  if (locked.value) return;
  const ticket = epoch,
    serial = ++modalSequence;
  busy.value = true;
  error.value = "";
  clearModal();
  try {
    const response = await seckillGet(id);
    if (ticket !== epoch || serial !== modalSequence) return;
    detail.value = response;
    historyPage.value = 1;
  } catch (e) {
    if (ticket === epoch && serial === modalSequence) {
      error.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === modalSequence) busy.value = false;
  }
  if (ticket === epoch && serial === modalSequence && detail.value)
    await loadHistory();
}
function readResult(row: PublishHistory) {
  if (
    !detail.value ||
    locked.value ||
    !can("publish") ||
    row.actorId !== identities.merchant?.userId
  )
    return;
  selectedIntent.value = {
    id: detail.value.id,
    expectedVersion: row.fromVersion,
    requestId: row.requestId,
  };
}
function readClearing(row: PublishHistory) {
  if (
    !detail.value ||
    locked.value ||
    !can("query") ||
    !row.published ||
    row.withdrawn
  )
    return;
  clearingGeneration.value = row.generation;
}
async function refreshPublishDetails() {
  if (!detail.value || busy.value) return;
  const ticket = epoch,
    serial = modalSequence,
    id = detail.value.id;
  busy.value = true;
  modalError.value = "";
  try {
    const current = await seckillGet(id);
    const page = await seckillHistory(id, historyPage.value);
    if (ticket !== epoch || serial !== modalSequence) return;
    detail.value = current;
    history.value = page.list;
    historyTotal.value = page.total;
  } catch (e) {
    if (ticket === epoch && serial === modalSequence) {
      modalError.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === modalSequence) {
      busy.value = false;
      await load();
    }
  }
}
function editable(a: Activity) {
  return (
    a.status === 0 &&
    a.skus.every((s) =>
      [
        s.allocatedQuantity,
        s.availableQuantity,
        s.heldQuantity,
        s.soldQuantity,
        s.returnedQuantity,
      ].every((n) => n === 0),
    )
  );
}
function setupProduct(product: ProductDetail, original?: Activity) {
  if (product.status !== 1) throw new ApiError("请选择已上架商品", 400);
  chosenProduct.value = product;
  skus.value = product.skus.map((s) => {
    const old =
      original?.productSpuId === product.id
        ? original.skus.find((x) => x.skuId === s.id)
        : undefined;
    return {
      skuId: s.id,
      label: spec(s.properties),
      selected: !!old,
      price: money(old?.priceCent ?? s.price),
      planned: String(old?.plannedQuantity ?? 1),
      stock: s.stock,
      unavailable: s.status !== 0,
    };
  });
  // Removed SKUs remain visible and selected until the operator explicitly excludes them.
  for (const old of original?.productSpuId === product.id
    ? original.skus
    : []) {
    if (!skus.value.some((s) => s.skuId === old.skuId))
      skus.value.push({
        skuId: old.skuId,
        label: spec(old.properties),
        selected: true,
        price: money(old.priceCent),
        planned: String(old.plannedQuantity),
        stock: null,
        unavailable: true,
      });
  }
}
async function openEditor(id?: number) {
  if (
    publishPending.value &&
    (publishPending.value.action !== "prepare" ||
      id !== publishPending.value.id)
  )
    return;
  if (
    locked.value ||
    saveBlocked.value ||
    !productAllowed.value ||
    !can(id ? "update" : "create")
  )
    return;
  const ticket = epoch,
    serial = ++modalSequence;
  busy.value = true;
  error.value = "";
  clearModal();
  try {
    const original = id ? await seckillGet(id) : null;
    if (ticket !== epoch || serial !== modalSequence) return;
    if (original && !editable(original))
      throw new ApiError("当前活动不能编辑，请先查看库存和发布进度", 409);
    const product = original
      ? await readProduct(() => productDetail(original.productSpuId))
      : null;
    if (ticket !== epoch || serial !== modalSequence) return;
    detail.value = original;
    editing.value = true;
    Object.assign(form, {
      name: original?.name ?? "",
      startTime: inputTime(
        original?.startTime ?? new Date(Date.now() + 3600000).toISOString(),
      ),
      endTime: inputTime(
        original?.endTime ?? new Date(Date.now() + 7200000).toISOString(),
      ),
      singleLimit: String(original?.singleLimit ?? 1),
      memberLimit: String(original?.memberLimit ?? 1),
    });
    if (product) setupProduct(product, original ?? undefined);
  } catch (e) {
    if (ticket === epoch && serial === modalSequence) {
      clearModal();
      error.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === modalSequence) busy.value = false;
  }
}
async function loadProducts() {
  if (locked.value || !editing.value || !productAllowed.value) return;
  const ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  modalError.value = "";
  productOptions.value = [];
  productTotal.value = 0;
  try {
    const response = await readProduct(() =>
      productPage(productNo.value, 0, productName.value.trim()),
    );
    if (ticket !== epoch || serial !== modalSequence) return;
    productOptions.value = response.list.filter((p) => p.status === 1);
    productTotal.value = response.total;
  } catch (e) {
    if (ticket === epoch && serial === modalSequence) {
      modalError.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === modalSequence) busy.value = false;
  }
}
async function pickProduct(id: number) {
  if (locked.value || !editing.value || !productAllowed.value) return;
  const ticket = epoch,
    serial = modalSequence;
  busy.value = true;
  modalError.value = "";
  try {
    const product = await readProduct(() => productDetail(id));
    if (ticket !== epoch || serial !== modalSequence) return;
    setupProduct(product, detail.value ?? undefined);
    picker.value = false;
    productOptions.value = [];
  } catch (e) {
    if (ticket === epoch && serial === modalSequence) {
      modalError.value = message(e);
      fail(e);
    }
  } finally {
    if (ticket === epoch && serial === modalSequence) busy.value = false;
  }
}
function positive(s: string, max = 2147483647) {
  if (
    !/^[1-9]\d{0,9}$/.test(s) ||
    !Number.isSafeInteger(Number(s)) ||
    Number(s) > max
  )
    throw new ApiError("限购和计划数量须为范围内的正整数", 400);
  return Number(s);
}
function cents(s: string) {
  if (!/^\d{1,8}(?:\.\d{1,2})?$/.test(s))
    throw new ApiError("价格最多保留两位小数", 400);
  const [yuan = "", fen = ""] = s.split("."),
    n = Number(yuan) * 100 + Number(fen.padEnd(2, "0"));
  if (!Number.isSafeInteger(n) || n < 1 || n > 2000000000)
    throw new ApiError("请填写有效的活动价格", 400);
  return n;
}
function offsetInput(s: string) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(s))
    throw new ApiError("请填写完整的北京时间", 400);
  const full =
    s.length === 16
      ? `${s}:00.000`
      : s.includes(".")
        ? s.padEnd(23, "0")
        : `${s}.000`;
  const value = `${full}+08:00`;
  if (!Number.isFinite(Date.parse(value)) || inputTime(value) !== full)
    throw new ApiError("活动时间不正确", 400);
  return value;
}
async function save() {
  if (
    publishPending.value &&
    (publishPending.value.action !== "prepare" ||
      detail.value?.id !== publishPending.value.id)
  )
    return;
  if (
    locked.value ||
    saveBlocked.value ||
    !editing.value ||
    !chosenProduct.value ||
    !productAllowed.value ||
    !can(detail.value ? "update" : "create")
  )
    return;
  const ticket = epoch,
    serial = modalSequence;
  let reminder: DraftPending | null = null,
    submitted = false;
  busy.value = true;
  modalError.value = "";
  notice.value = "";
  try {
    const selected = skus.value.filter((s) => s.selected);
    if (!selected.length || selected.some((s) => s.unavailable))
      throw new ApiError("请选择有效规格，排除已停用或移除的规格", 400);
    const body: DraftInput = {
      id: detail.value?.id ?? null,
      expectedVersion: detail.value?.version ?? null,
      name: form.name.trim(),
      productSpuId: chosenProduct.value.id,
      startTime: offsetInput(form.startTime),
      endTime: offsetInput(form.endTime),
      singleLimit: positive(form.singleLimit, 9999),
      memberLimit: positive(form.memberLimit),
      skus: selected.map((s) => ({
        skuId: s.skuId,
        priceCent: cents(s.price),
        plannedQuantity: positive(s.planned),
      })),
    };
    reminder = {
      id: body.id,
      expectedVersion: body.expectedVersion,
      productSpuId: body.productSpuId,
    };
    rememberDraft(reminder);
    pending.value = reminder;
    submitted = true;
    const saved = await seckillSave(body);
    if (ticket !== epoch || serial !== modalSequence) return;
    forgetDraft(reminder);
    pending.value = null;
    editing.value = false;
    detail.value = saved;
    chosenProduct.value = null;
    skus.value = [];
    notice.value = "活动草稿已保存。";
  } catch (e) {
    if (ticket !== epoch || serial !== modalSequence) return;
    if (reminder && !submitted) restorePending();
    if (reminder && submitted && e instanceof ApiError && !e.outcomeUnknown) {
      try {
        forgetDraft(reminder);
        pending.value = null;
      } catch (storageError) {
        recoveryError.value = message(storageError);
      }
    }
    modalError.value = message(e);
    fail(e);
    if (pending.value)
      notice.value =
        "保存结果待确认，请先核对活动列表和详情，避免重复创建或覆盖。";
  } finally {
    if (ticket === epoch && serial === modalSequence) {
      busy.value = false;
      if (!editing.value) {
        await load();
        if (ticket === epoch && serial === modalSequence) await loadHistory();
      }
    }
  }
}
const canAcknowledge = computed(
  () =>
    !!pending.value &&
    !!detail.value &&
    !editing.value &&
    detail.value.productSpuId === pending.value.productSpuId &&
    (pending.value.id === null || pending.value.id === detail.value.id),
);
function acknowledge() {
  if (
    !pending.value ||
    locked.value ||
    !(
      canAcknowledge.value ||
      (pending.value.id === null && listReadComplete.value && !editing.value)
    )
  )
    return;
  try {
    forgetDraft(pending.value);
    pending.value = null;
    recoveryError.value = "";
    notice.value = "已结束本次人工核对；后续编辑将重新读取最新版本。";
  } catch (e) {
    recoveryError.value = message(e);
  }
}
function clearModal() {
  detail.value = null;
  editing.value = false;
  picker.value = false;
  chosenProduct.value = null;
  skus.value = [];
  productOptions.value = [];
  productName.value = "";
  productNo.value = 1;
  productTotal.value = 0;
  history.value = [];
  historyTotal.value = 0;
  historyError.value = "";
  selectedIntent.value = null;
  modalError.value = "";
  clearingGeneration.value = null;
  form.name = "";
  form.startTime = "";
  form.endTime = "";
}
function close() {
  if (!busy.value && !publishBusy.value && !runtimeBusy.value) {
    modalSequence++;
    clearModal();
  }
}
function clear() {
  runtimeBusy.value = false;
  runtimeBlocked.value = false;
  publishBusy.value = false;
  publishPending.value = null;
  publishRecoveryError.value = "";
  epoch++;
  listSequence++;
  modalSequence++;
  clearModal();
  rows.value = [];
  total.value = 0;
  loading.value = false;
  listReadComplete.value = false;
  busy.value = false;
  pending.value = null;
  recoveryError.value = "";
  error.value = "";
  notice.value = "";
}
watch(
  [
    seckillIdentity,
    () => credentials().accessToken,
    () => identities.merchant?.permissions.join("|"),
    () => route.fullPath,
  ],
  () => {
    clear();
    page.value = 1;
    query.name = "";
    query.status = "";
    if (route.path === "/admin/seckill" && can("query")) {
      restorePending();
      void load();
    } else if (!can("query")) error.value = "当前没有活动查询权限";
  },
  { immediate: true },
);
watch(
  () => [query.name, query.status],
  () => {
    listSequence++;
    rows.value = [];
    total.value = 0;
    loading.value = false;
    listReadComplete.value = false;
    error.value = "";
  },
  { flush: "sync" },
);
onBeforeUnmount(clear);
</script>

<template>
  <PageHeader title="限量秒杀" eyebrow="客户与营销">
    <button class="button" :disabled="locked" @click="load">刷新活动</button>
    <button
      v-if="can('create')"
      class="button primary"
      :disabled="locked || saveBlocked || !productAllowed || !!publishPending"
      :title="productAllowed ? '' : '需商品查询权限以选择活动规格'"
      @click="openEditor()"
    >
      创建活动
    </button>
  </PageHeader>
  <p v-if="notice" role="status">{{ notice }}</p>
  <p
    v-if="error || recoveryError || publishRecoveryError"
    class="form-error"
    role="alert"
  >
    {{ error || recoveryError || publishRecoveryError }}
  </p>
  <section
    v-if="pending"
    class="panel recovery-panel"
    aria-label="待核对的草稿保存"
  >
    <strong>有一笔草稿保存结果待核对</strong>
    <p>
      请查看{{
        pending.id
          ? `活动 ${pending.id}`
          : `商品 ${pending.productSpuId} 的活动列表`
      }}，核对配置后再继续保存。查询不会重复提交。
    </p>
    <button
      v-if="pending.id"
      class="button"
      :disabled="locked"
      @click="openDetail(pending.id)"
    >
      查询原活动
    </button>
    <button v-else class="button" :disabled="locked" @click="load">
      查询活动列表
    </button>
    <template v-if="pending.id === null && listReadComplete">
      <p>
        原保存仍可能稍后完成。结束核对只解除保存限制，不代表保存成功或失败，请避免立即重复创建。
      </p>
      <button class="button" :disabled="locked" @click="acknowledge">
        已核对列表，结束核对
      </button>
    </template>
  </section>
  <section
    v-if="publishPending"
    class="panel recovery-panel"
    aria-label="待确认发布操作"
  >
    <strong
      >活动 {{ publishPending.id }} 的{{
        publishActions[publishPending.action].label
      }}待确认</strong
    >
    <p>
      原请求号
      {{ publishPending.requestId }}。查询后再继续，请勿换号发布或补偿。
    </p>
    <p v-if="publishPending.action === 'prepare'">
      如果仍为草稿，可明确编辑该活动使旧版本失效，再查询原请求。
    </p>
    <button
      class="button"
      :disabled="locked"
      @click="openDetail(publishPending.id)"
    >
      查询原活动
    </button>
  </section>
  <section class="panel">
    <form class="management-filters" @submit.prevent="search">
      <label
        >活动名称<input v-model="query.name" maxlength="60" :disabled="busy"
      /></label>
      <label
        >活动状态<select v-model="query.status" :disabled="busy">
          <option value="">全部</option>
          <option
            v-for="(label, key) in seckillStates"
            :key="key"
            :value="String(key)"
          >
            {{ label }}
          </option>
        </select></label
      >
      <button class="button primary" :disabled="locked">查询</button>
    </form>
    <p v-if="loading" role="status">正在查询活动…</p>
    <div v-else-if="rows.length" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>活动 / 商品</th>
            <th>活动时间（北京）</th>
            <th>状态</th>
            <th>计划 / 已划拨</th>
            <th>可用 / 占用 / 售出 / 归还</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in rows" :key="a.id">
            <td>
              <strong>{{ a.name }}</strong
              ><small>{{ a.productName }}</small
              ><small>活动 {{ a.id }} · {{ a.skuCount }} 个规格</small>
            </td>
            <td>
              {{ time(a.startTime) }}<small>至 {{ time(a.endTime) }}</small>
            </td>
            <td>
              <span class="badge">{{ stateName(a.status) }}</span>
            </td>
            <td>{{ a.plannedQuantity }} / {{ a.allocatedQuantity }}</td>
            <td>
              {{ a.availableQuantity }} / {{ a.heldQuantity }} /
              {{ a.soldQuantity }} / {{ a.returnedQuantity }}
            </td>
            <td>
              <div class="row-actions">
                <button
                  class="text-button"
                  :disabled="locked"
                  @click="openDetail(a.id)"
                >
                  详情与发布记录
                </button>
                <button
                  v-if="a.status === 0 && can('update')"
                  class="text-button"
                  :disabled="
                    locked ||
                    saveBlocked ||
                    !productAllowed ||
                    (!!publishPending && publishPending.id !== a.id)
                  "
                  @click="openEditor(a.id)"
                >
                  编辑草稿
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Empty v-else-if="!error" title="暂无符合条件的活动" icon="campaign" />
    <div class="seckill-pagination">
      <span>共 {{ total }} 个活动 · 第 {{ page }} 页</span>
      <button
        class="button"
        :disabled="locked || page <= 1"
        @click="changePage(-1)"
      >
        上一页
      </button>
      <button
        class="button"
        :disabled="locked || page >= 10000 || page * 20 >= total"
        @click="changePage(1)"
      >
        下一页
      </button>
    </div>
  </section>
  <Modal
    v-if="editing || detail"
    :title="
      editing ? (detail ? '编辑活动草稿' : '创建活动') : '活动详情与发布记录'
    "
    wide
    @close="close"
  >
    <template v-if="editing">
      <form id="seckill-draft" @submit.prevent="save">
        <div class="form-grid">
          <label class="full"
            >活动名称<input
              v-model="form.name"
              required
              maxlength="60"
              :disabled="locked || saveBlocked"
          /></label>
          <label
            >开始时间（北京）<input
              v-model="form.startTime"
              type="datetime-local"
              step="0.001"
              required
              :disabled="locked || saveBlocked"
          /></label>
          <label
            >结束时间（北京，不含）<input
              v-model="form.endTime"
              type="datetime-local"
              step="0.001"
              required
              :disabled="locked || saveBlocked"
          /></label>
          <label
            >单次限购<input
              v-model="form.singleLimit"
              inputmode="numeric"
              required
              :disabled="locked || saveBlocked"
          /></label>
          <label
            >每人累计限购<input
              v-model="form.memberLimit"
              inputmode="numeric"
              required
              :disabled="locked || saveBlocked"
          /></label>
        </div>
        <div class="seckill-product">
          <strong>{{ chosenProduct?.name || "尚未选择商品" }}</strong>
          <button
            type="button"
            class="button"
            :disabled="locked || saveBlocked || !productAllowed"
            @click="picker = !picker"
          >
            {{ picker ? "收起商品选择" : "选择商品" }}
          </button>
        </div>
        <div v-if="picker" class="product-picker">
          <div class="management-filters">
            <label
              >商品名称<input
                v-model="productName"
                maxlength="100"
                :disabled="locked"
            /></label>
            <button
              type="button"
              class="button"
              :disabled="locked"
              @click="
                productNo = 1;
                loadProducts();
              "
            >
              查询上架商品
            </button>
          </div>
          <ul>
            <li v-for="p in productOptions" :key="p.id">
              <span>{{ p.name }} · 商品 {{ p.id }}</span
              ><button
                type="button"
                class="text-button"
                :disabled="locked"
                @click="pickProduct(p.id)"
              >
                选择
              </button>
            </li>
          </ul>
          <div class="seckill-pagination">
            <span>第 {{ productNo }} 页</span>
            <button
              type="button"
              class="button"
              :disabled="locked || productNo <= 1"
              @click="
                productNo--;
                loadProducts();
              "
            >
              上一页
            </button>
            <button
              type="button"
              class="button"
              :disabled="
                locked || productNo >= 10000 || productNo * 20 >= productTotal
              "
              @click="
                productNo++;
                loadProducts();
              "
            >
              下一页
            </button>
          </div>
        </div>
        <div v-if="skus.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>参与</th>
                <th>规格</th>
                <th>选品时普通库存</th>
                <th>活动单价（元）</th>
                <th>计划数量</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in skus" :key="s.skuId">
                <td>
                  <input
                    v-model="s.selected"
                    type="checkbox"
                    :aria-label="`选择规格 ${s.skuId}`"
                    :disabled="locked || saveBlocked"
                  />
                </td>
                <td>
                  {{ s.label
                  }}<small
                    >SKU {{ s.skuId
                    }}{{
                      s.unavailable ? " · 已停用或移除，请取消参与" : ""
                    }}</small
                  >
                </td>
                <td>{{ s.stock ?? "—" }}</td>
                <td>
                  <input
                    v-model="s.price"
                    inputmode="decimal"
                    :aria-label="`规格 ${s.skuId} 单价`"
                    :disabled="
                      locked || saveBlocked || !s.selected || s.unavailable
                    "
                  />
                </td>
                <td>
                  <input
                    v-model="s.planned"
                    inputmode="numeric"
                    :aria-label="`规格 ${s.skuId} 计划数量`"
                    :disabled="
                      locked || saveBlocked || !s.selected || s.unavailable
                    "
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="muted">
          保存草稿不会划拨库存。发布时将按最新商品状态和库存重新校验。
        </p>
      </form>
    </template>
    <template v-else-if="detail">
      <div class="seckill-detail">
        <h3>{{ detail.name }}</h3>
        <p>{{ detail.productName }} · 活动 {{ detail.id }}</p>
        <p>
          <span class="badge">{{ stateName(detail.status) }}</span> ·
          {{ time(detail.startTime) }} 至 {{ time(detail.endTime) }}（北京）
        </p>
        <p>
          单次限购 {{ detail.singleLimit }}，每人累计 {{ detail.memberLimit }}。
        </p>
        <p v-if="detail.status === 20" class="muted">
          已完成发布裁定。是否可以参与，以商城实际开放状态为准。
        </p>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>规格</th>
              <th>活动价</th>
              <th>计划 / 已划拨</th>
              <th>可用 / 占用 / 售出 / 归还</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in detail.skus" :key="s.skuId">
              <td>
                {{ spec(s.properties) }}<small>SKU {{ s.skuId }}</small>
              </td>
              <td>¥{{ money(s.priceCent) }}</td>
              <td>{{ s.plannedQuantity }} / {{ s.allocatedQuantity }}</td>
              <td>
                {{ s.availableQuantity }} / {{ s.heldQuantity }} /
                {{ s.soldQuantity }} / {{ s.returnedQuantity }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <section
        v-if="canAcknowledge"
        class="recovery-panel"
        aria-label="核对保存结果"
      >
        <p>
          请确认这条活动的名称、时间和规格是否与刚才的保存一致。此操作只结束人工核对，不会再次保存。
        </p>
        <button class="button" :disabled="locked" @click="acknowledge">
          我已核对，继续管理
        </button>
      </section>
      <h3>发布记录</h3>
      <p v-if="historyError" class="form-error" role="alert">
        {{ historyError }}
      </p>
      <p v-if="busy" role="status">正在读取…</p>
      <div v-if="history.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>准备时间（北京）</th>
              <th>批次</th>
              <th>原处理记录</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="h in history" :key="h.requestId">
              <td>{{ time(h.preparedAt) }}</td>
              <td>第 {{ h.generation }} 次</td>
              <td>
                {{ h.published ? "曾发布" : "尚无发布记录"
                }}{{ h.withdrawn ? " · 已申请撤回" : ""
                }}{{ h.settled ? " · 已结清" : "" }}
              </td>
              <td>
                <button
                  v-if="
                    h.actorId === identities.merchant?.userId && can('publish')
                  "
                  class="text-button"
                  :disabled="locked"
                  @click="readResult(h)"
                >
                  查看本次发布</button
                ><span v-else class="muted">由原发布人员查询结果</span>
                <button
                  v-if="h.published && !h.withdrawn && can('query')"
                  class="text-button"
                  :disabled="locked"
                  :aria-pressed="clearingGeneration === h.generation"
                  @click="readClearing(h)"
                >
                  查看第 {{ h.generation }} 轮清算
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else-if="!busy && !historyError" class="muted">暂无发布记录。</p>
      <div class="seckill-pagination">
        <span>共 {{ historyTotal }} 条 · 第 {{ historyPage }} 页</span>
        <button
          class="button"
          :disabled="locked || historyPage <= 1"
          @click="
            historyPage--;
            loadHistory();
          "
        >
          上一页
        </button>
        <button
          class="button"
          :disabled="
            locked || historyPage >= 10000 || historyPage * 20 >= historyTotal
          "
          @click="
            historyPage++;
            loadHistory();
          "
        >
          下一页
        </button>
        <button class="button" :disabled="locked" @click="loadHistory">
          刷新记录
        </button>
      </div>
      <SeckillClearingProgress
        v-if="clearingGeneration !== null"
        :activity-id="detail.id"
        :generation="clearingGeneration"
        :refresh-key="detail.version"
        :disabled="busy || loading || publishBusy || runtimeBusy"
      />
      <SeckillPublishWorkflow
        :activity="detail"
        :intent="selectedIntent"
        :disabled="busy || loading || runtimeBusy"
        :blocked="!!pending || !!recoveryError || runtimeBlocked"
        @busy="publishBusy = $event"
        @pending="publishPending = $event"
        @selected="selectedIntent = $event"
        @updated="refreshPublishDetails"
      />
      <SeckillRuntimeWorkflow
        :activity="detail"
        :intent="selectedIntent"
        :disabled="busy || loading || publishBusy"
        :blocked="
          !!pending ||
          !!recoveryError ||
          !!publishPending ||
          !!publishRecoveryError
        "
        @busy="runtimeBusy = $event"
        @pending="runtimeBlocked = $event"
        @recover="openDetail"
        @updated="refreshPublishDetails"
      />
    </template>
    <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
    <template #footer>
      <button
        class="button"
        :disabled="busy || publishBusy || runtimeBusy"
        @click="close"
      >
        关闭
      </button>
      <button
        v-if="detail && !editing"
        class="button"
        :disabled="locked"
        @click="openDetail(detail.id)"
      >
        刷新详情
      </button>
      <button
        v-if="editing"
        form="seckill-draft"
        class="button primary"
        :disabled="locked || saveBlocked || !chosenProduct"
      >
        保存草稿
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.seckill-pagination,
.seckill-product,
.row-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.seckill-pagination {
  justify-content: flex-end;
  padding: 16px 0;
}
.seckill-pagination > span {
  margin-right: auto;
  color: var(--muted);
}
.seckill-product {
  justify-content: space-between;
  margin: 18px 0;
}
.seckill-detail {
  margin-bottom: 16px;
}
.text-button[aria-pressed="true"] {
  color: var(--primary);
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 4px;
}
.recovery-panel {
  padding: 16px;
  margin-bottom: 18px;
  border: 1px solid var(--border);
  border-radius: 8px;
}
.recovery-panel p {
  overflow-wrap: anywhere;
}
.publish-result,
.product-picker {
  padding: 16px;
  margin-top: 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
}
.product-picker ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.product-picker li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
td small {
  display: block;
  color: var(--muted);
  margin-top: 4px;
}
td input:not([type="checkbox"]) {
  min-width: 100px;
  max-width: 140px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.form-grid label {
  display: grid;
  gap: 8px;
}
.form-grid .full {
  grid-column: 1 / -1;
}
@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
