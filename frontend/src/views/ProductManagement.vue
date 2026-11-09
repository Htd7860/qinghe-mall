<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import PageHeader from "../components/PageHeader.vue";
import Modal from "../components/Modal.vue";
import ProductEditor from "../components/ProductEditor.vue";
import ProductOwnerTransfer from "../components/ProductOwnerTransfer.vue";
import Empty from "../components/Empty.vue";
import { allowed } from "../api/management";
import { ApiError } from "../api/http";
import {
  productPage,
  productCounts,
  productDetail,
  productStatus,
  productDelete,
  productIdentity,
  productExport,
  stockAdjust,
  stockOperation,
  type ProductRow,
  type ProductDetail,
  type StockRequest,
  type StockResult,
} from "../api/product";
const tabs = [
  { id: undefined, label: "全部" },
  { id: 0, label: "出售中" },
  { id: 1, label: "下架" },
  { id: 2, label: "售罄" },
  { id: 3, label: "库存预警" },
  { id: 4, label: "回收站" },
];
const tab = ref<number>(),
  page = ref(1),
  search = ref(""),
  rows = ref<ProductRow[]>([]),
  total = ref(0),
  counts = ref<Record<string, number>>({});
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  detail = ref<ProductDetail>();
const transfer = ref<ProductRow>();
function transferred() {
  transfer.value = undefined;
  notice.value = "商品归属已转移。";
  void load();
}
function closeTransfer() {
  transfer.value = undefined;
  void load();
}
const editor = ref<{ id?: number }>();
function edited() {
  editor.value = undefined;
  notice.value = "商品已保存，请在下架或原状态列表核对资料。";
  page.value = 1;
  void load();
}
const action = ref<{ row: ProductRow; status?: number; remove?: boolean }>(),
  modalError = ref(""),
  uncertain = ref(false);
const stock = ref<ProductDetail>(),
  changes = ref<Record<number, string>>({}),
  reason = ref("");
type Pending = { identity: string; body: StockRequest; unknown: boolean };
const pending = ref<Pending>(),
  result = ref<StockResult>(),
  pendingUnreadable = ref(false);
const can = (permission: string) => allowed(`product:spu:${permission}`);
const statusName = (status: number) =>
  status === 1 ? "上架" : status === 0 ? "下架" : "回收站";
const money = (amount: number) => (amount / 100).toFixed(2);
let exportController: AbortController | undefined;
let epoch = 0,
  sequence = 0;
const storageKey = () => `qinghe:stock-pending:${productIdentity()}`;
function persist() {
  if (pending.value)
    sessionStorage.setItem(storageKey(), JSON.stringify(pending.value));
  else sessionStorage.removeItem(storageKey());
}
function restorePending() {
  pending.value = undefined;
  pendingUnreadable.value = false;
  try {
    const raw = sessionStorage.getItem(storageKey());
    const value = JSON.parse(raw || "null") as Pending | null;
    if (
      value &&
      value.identity === productIdentity() &&
      value.body &&
      Number.isSafeInteger(value.body.spuId) &&
      value.body.spuId > 0 &&
      /^[A-Za-z0-9][A-Za-z0-9:_-]{7,79}$/.test(value.body.operationKey) &&
      typeof value.body.reason === "string" &&
      value.body.reason.length <= 512 &&
      Array.isArray(value.body.items) &&
      value.body.items.length > 0 &&
      value.body.items.length <= 2000 &&
      value.body.items.every(
        (item) =>
          Number.isSafeInteger(item.skuId) &&
          item.skuId > 0 &&
          Number.isSafeInteger(item.delta) &&
          item.delta !== 0 &&
          Math.abs(item.delta) <= 2147483647,
      )
    )
      pending.value = { ...value, unknown: true };
    else if (raw) pendingUnreadable.value = true;
  } catch {
    pendingUnreadable.value = true;
  }
}
const message = (cause: unknown) =>
  cause instanceof Error ? cause.message : "操作未完成，请稍后重试";
async function exportProducts() {
  if (busy.value || loading.value || !can("export")) return;
  const generation = epoch;
  busy.value = true;
  error.value = "";
  notice.value = "";
  exportController = new AbortController();
  try {
    const value = await productExport(
      tab.value,
      search.value.trim(),
      exportController.signal,
    );
    if (generation !== epoch) return;
    const url = URL.createObjectURL(value.blob),
      link = document.createElement("a");
    try {
      link.href = url;
      link.download = `products-${value.id}.xlsx`;
      document.body.appendChild(link);
      link.click();
    } finally {
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
    notice.value = `已生成${value.rows}条商品并发起文件保存，记录编号：${value.id}`;
  } catch (cause) {
    if (generation === epoch) error.value = message(cause);
  } finally {
    if (generation === epoch) {
      busy.value = false;
      exportController = undefined;
    }
  }
}
async function load() {
  const generation = epoch,
    current = ++sequence;
  loading.value = true;
  error.value = "";
  rows.value = [];
  total.value = 0;
  try {
    const [list, stats] = await Promise.all([
      productPage(page.value, tab.value, search.value.trim()),
      productCounts(search.value.trim()),
    ]);
    if (generation !== epoch || current !== sequence) return;
    rows.value = list.list;
    total.value = list.total;
    counts.value = stats;
  } catch (cause) {
    if (generation === epoch && current === sequence)
      error.value = message(cause);
  } finally {
    if (generation === epoch && current === sequence) loading.value = false;
  }
}
watch(
  productIdentity,
  () => {
    epoch++;
    exportController?.abort();
    page.value = 1;
    tab.value = undefined;
    transfer.value = undefined;
    editor.value = undefined;
    detail.value = undefined;
    stock.value = undefined;
    action.value = undefined;
    result.value = undefined;
    busy.value = false;
    notice.value = "";
    reason.value = "";
    changes.value = {};
    restorePending();
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  epoch++;
  exportController?.abort();
});
async function view(row: ProductRow, inventory = false) {
  if (busy.value) return;
  const generation = epoch;
  busy.value = true;
  error.value = "";
  modalError.value = "";
  try {
    const value = await productDetail(row.id);
    if (generation !== epoch) return;
    if (inventory) {
      stock.value = value;
      changes.value = {};
      reason.value = "";
      result.value = undefined;
    } else detail.value = value;
  } catch (cause) {
    if (generation === epoch) error.value = message(cause);
  } finally {
    if (generation === epoch) busy.value = false;
  }
}
function confirm(row: ProductRow, status?: number, remove = false) {
  action.value = { row, status, remove };
  modalError.value = "";
  uncertain.value = false;
}
async function perform() {
  if (!action.value || busy.value || uncertain.value) return;
  const generation = epoch;
  busy.value = true;
  modalError.value = "";
  try {
    if (action.value.remove) await productDelete(action.value.row);
    else await productStatus(action.value.row, action.value.status!);
    if (generation !== epoch) return;
    action.value = undefined;
    notice.value = "商品状态已更新";
    await load();
  } catch (cause) {
    if (generation !== epoch) return;
    uncertain.value =
      cause instanceof ApiError &&
      (cause.outcomeUnknown || [404, 409].includes(cause.code));
    modalError.value = uncertain.value
      ? "商品或操作结果需要确认，请关闭弹窗并重新读取后再操作。"
      : message(cause);
  } finally {
    if (generation === epoch) busy.value = false;
  }
}
async function adjust() {
  if (!stock.value || busy.value || pending.value || pendingUnreadable.value)
    return;
  modalError.value = "";
  try {
    const items = stock.value.skus.flatMap((sku) => {
      const text = (changes.value[sku.id] || "").trim();
      if (!text) return [];
      if (
        !/^-?\d{1,10}$/.test(text) ||
        !Number.isSafeInteger(Number(text)) ||
        Number(text) === 0 ||
        Math.abs(Number(text)) > 2147483647
      )
        throw new Error("增减量须为非零整数；增加填正数，减少填负数");
      return [{ skuId: sku.id, delta: Number(text) }];
    });
    if (!items.length || !reason.value.trim())
      throw new Error("请填写库存增减量和调整原因");
    pending.value = {
      identity: productIdentity(),
      unknown: false,
      body: {
        spuId: stock.value.id,
        operationKey: crypto.randomUUID(),
        reason: reason.value.trim(),
        items,
      },
    };
    persist();
    await recover(true);
  } catch (cause) {
    modalError.value = message(cause);
  }
}
async function recover(retry = false) {
  if (!pending.value || busy.value) return;
  const generation = epoch,
    entry = pending.value,
    wasUnknown = entry.unknown;
  busy.value = true;
  modalError.value = "";
  try {
    if (retry) {
      entry.unknown = true;
      persist();
    }
    const value = retry
      ? await stockAdjust(entry.body)
      : await stockOperation(entry.body);
    if (generation !== epoch) return;
    result.value = value;
    pending.value = undefined;
    persist();
    stock.value = undefined;
    notice.value = "库存操作已确认，可查看本次流水。";
    await load();
  } catch (cause) {
    if (generation !== epoch) return;
    if (
      retry &&
      !wasUnknown &&
      cause instanceof ApiError &&
      !cause.outcomeUnknown &&
      cause.code >= 400 &&
      cause.code < 500 &&
      cause.code !== 429
    ) {
      entry.unknown = false;
      persist();
    }
    modalError.value = message(cause);
  } finally {
    if (generation === epoch) busy.value = false;
  }
}
function discardFailed() {
  if (!pending.value || pending.value.unknown || busy.value) return;
  pending.value = undefined;
  persist();
  modalError.value = "";
}
</script>
<template>
  <div class="management-page">
    <PageHeader title="商品管理" eyebrow="店铺经营"
      ><button
        v-if="can('create')"
        class="button primary"
        :disabled="busy || loading"
        @click="editor = {}"
      >
        新增商品</button
      ><button
        v-if="can('export')"
        class="button"
        :disabled="busy || loading"
        @click="exportProducts"
      >
        导出筛选结果</button
      ><button class="button" :disabled="busy || loading" @click="load">
        重新读取
      </button></PageHeader
    >
    <div v-if="pending" class="management-result" role="status">
      <strong>{{
        pending.unknown ? "库存操作结果待确认" : "库存请求已明确拒绝"
      }}</strong
      ><span
        >商品 {{ pending.body.spuId }} · {{ pending.body.operationKey }}</span
      >
      <button class="button" :disabled="busy" @click="recover()">
        查询原结果</button
      ><button class="button" :disabled="busy" @click="recover(true)">
        按原请求重试</button
      ><button
        v-if="!pending.unknown"
        class="button"
        :disabled="busy"
        @click="discardFailed"
      >
        关闭失败请求
      </button>
    </div>
    <p v-if="pendingUnreadable" class="management-alert" role="alert">
      本机有无法读取的待确认库存记录，请先保留原记录并核对操作结果，暂不可发起新调整。
    </p>
    <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>
    <p
      v-if="modalError && !stock && !action"
      class="management-alert"
      role="alert"
    >
      {{ modalError }}
    </p>
    <nav class="management-tabs" aria-label="商品状态">
      <button
        v-for="item in tabs"
        :key="item.label"
        class="text-link"
        :aria-current="tab === item.id ? 'page' : undefined"
        :disabled="busy || loading"
        @click="
          tab = item.id;
          page = 1;
          load();
        "
      >
        {{ item.label
        }}{{
          item.id !== undefined && counts[String(item.id)] !== undefined
            ? ` (${counts[String(item.id)]})`
            : ""
        }}
      </button>
    </nav>
    <div class="panel">
      <form
        class="filter-bar"
        @submit.prevent="
          page = 1;
          load();
        "
      >
        <label class="management-filter"
          >商品名称<input v-model="search" maxlength="128" /></label
        ><button class="button" :disabled="busy || loading">查询</button>
      </form>
      <div v-if="error" class="management-alert" role="alert">{{ error }}</div>
      <p v-if="loading" class="management-empty" role="status">正在读取…</p>
      <div v-else-if="rows.length" class="management-table-scroll">
        <table class="management-table">
          <thead>
            <tr>
              <th>商品</th>
              <th>售价</th>
              <th>可售库存</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>
                {{ row.name }}<small>编号 {{ row.id }}</small>
              </td>
              <td>¥{{ money(row.price) }}</td>
              <td>{{ row.stock }}</td>
              <td>{{ statusName(row.status) }}</td>
              <td>
                <div class="management-row-actions">
                  <button class="text-link" :disabled="busy" @click="view(row)">
                    详情</button
                  ><button
                    v-if="can('update') && row.status !== -1"
                    class="text-link"
                    :disabled="busy"
                    @click="editor = { id: row.id }"
                  >
                    编辑</button
                  ><button
                    v-if="can('transfer')"
                    class="text-link"
                    :disabled="busy"
                    @click="transfer = row"
                  >
                    转移归属</button
                  ><button
                    v-if="can('update') && row.status === 0"
                    class="text-link"
                    :disabled="busy"
                    @click="confirm(row, 1)"
                  >
                    上架</button
                  ><button
                    v-if="can('update') && row.status === 1"
                    class="text-link"
                    :disabled="busy"
                    @click="confirm(row, 0)"
                  >
                    下架</button
                  ><button
                    v-if="can('update') && row.status === 0"
                    class="text-link"
                    :disabled="busy"
                    @click="confirm(row, -1)"
                  >
                    移入回收站</button
                  ><button
                    v-if="can('update') && row.status === -1"
                    class="text-link"
                    :disabled="busy"
                    @click="confirm(row, 0)"
                  >
                    恢复为下架</button
                  ><button
                    v-if="can('update-stock')"
                    class="text-link"
                    :disabled="busy || !!pending || pendingUnreadable"
                    @click="view(row, true)"
                  >
                    调整库存</button
                  ><button
                    v-if="can('delete') && row.status === -1"
                    class="text-link management-danger"
                    :disabled="busy || !!pending || pendingUnreadable"
                    @click="confirm(row, undefined, true)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Empty v-else-if="!error" title="暂无符合条件的商品" />
      <div class="pagination">
        <span>共 {{ total }} 条</span>
        <div class="pagination-right">
          <button
            class="button"
            :disabled="busy || loading || page <= 1"
            @click="
              page--;
              load();
            "
          >
            上一页</button
          ><span>第 {{ page }} 页</span
          ><button
            class="button"
            :disabled="busy || loading || page * 20 >= total"
            @click="
              page++;
              load();
            "
          >
            下一页
          </button>
        </div>
      </div>
    </div>
    <Modal v-if="detail" :title="detail.name" wide @close="detail = undefined"
      ><dl class="product-facts">
        <dt>状态</dt>
        <dd>{{ statusName(detail.status) }}</dd>
        <dt>分类 / 品牌编号</dt>
        <dd>{{ detail.categoryId }} / {{ detail.brandId }}</dd>
        <dt>配送模板编号</dt>
        <dd>{{ detail.deliveryTemplateId }}</dd>
        <dt>归属员工 / 部门</dt>
        <dd>
          {{ detail.ownerUserId }} / {{ detail.ownerDeptId ?? "未分配部门" }}
        </dd>
        <dt>简介</dt>
        <dd>{{ detail.introduction }}</dd>
        <dt>详情</dt>
        <dd class="product-description">{{ detail.description }}</dd>
      </dl>
      <div class="management-table-scroll">
        <table class="management-table">
          <thead>
            <tr>
              <th>SKU / 规格</th>
              <th>售价</th>
              <th>成本</th>
              <th>库存</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="sku in detail.skus" :key="sku.id">
              <td>
                {{ sku.id
                }}<small>{{
                  sku.properties
                    .map((value) => `${value.propertyName}：${value.valueName}`)
                    .join(" / ")
                }}</small>
              </td>
              <td>¥{{ money(sku.price) }}</td>
              <td>¥{{ money(sku.costPrice) }}</td>
              <td>{{ sku.stock }}</td>
              <td>{{ sku.status === 0 ? "启用" : "停用" }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </Modal>
    <Modal
      v-if="action"
      :title="
        action.remove ? '确认删除商品' : `确认${statusName(action.status!)}`
      "
      @close="!busy && (action = undefined)"
      ><p>商品：{{ action.row.name }}</p>
      <p v-if="action.remove" class="muted">
        删除后不再出现在管理列表，稳定 SKU、库存流水与历史引用仍保留。
      </p>
      <p v-else-if="action.status === 1" class="muted">
        上架前将重新校验分类、品牌、启用规格、图片和配送模板。零库存商品仍不可购买。
      </p>
      <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
      <template #footer
        ><button class="button" :disabled="busy" @click="action = undefined">
          关闭</button
        ><button
          class="button primary"
          :disabled="busy || uncertain"
          @click="perform"
        >
          确认操作
        </button></template
      ></Modal
    >
    <Modal
      v-if="stock"
      :title="`调整库存 · ${stock.name}`"
      wide
      @close="!busy && (stock = undefined)"
      ><p class="muted">
        增加填正数，减少填负数；未填写的 SKU 保持原样。停用 SKU
        的库存保留，但不计入可售总库存。
      </p>
      <form id="stock-adjust" @submit.prevent="adjust">
        <fieldset
          class="management-fieldset"
          :disabled="busy || !!pending || pendingUnreadable"
        >
          <div class="management-table-scroll">
            <table class="management-table">
              <thead>
                <tr>
                  <th>SKU / 规格</th>
                  <th>当前库存</th>
                  <th>增减量</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="sku in stock.skus" :key="sku.id">
                  <td>
                    {{ sku.id
                    }}<small
                      >{{
                        sku.properties
                          .map((value) => value.valueName)
                          .join(" / ")
                      }}{{ sku.status === 1 ? " · 已停用" : "" }}</small
                    >
                  </td>
                  <td>{{ sku.stock }}</td>
                  <td>
                    <input
                      v-model="changes[sku.id]"
                      inputmode="numeric"
                      maxlength="11"
                      :aria-label="`SKU ${sku.id} 库存增减量`"
                      placeholder="例如 10 或 -2"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <label
            >调整原因<textarea v-model="reason" required maxlength="512" />
          </label>
        </fieldset>
        <p v-if="modalError" class="form-error" role="alert">
          {{ modalError }}
        </p>
      </form>
      <template #footer
        ><button class="button" :disabled="busy" @click="stock = undefined">
          关闭</button
        ><button
          class="button primary"
          form="stock-adjust"
          :disabled="busy || !!pending || pendingUnreadable"
        >
          确认调整
        </button></template
      ></Modal
    >
    <Modal v-if="result" title="库存操作流水" @close="result = undefined"
      ><p>业务键：{{ result.operationKey }}</p>
      <table class="management-table">
        <thead>
          <tr>
            <th>SKU</th>
            <th>调整前</th>
            <th>增减量</th>
            <th>调整后</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in result.items" :key="item.skuId">
            <td>{{ item.skuId }}</td>
            <td>{{ item.beforeStock }}</td>
            <td>{{ item.delta }}</td>
            <td>{{ item.afterStock }}</td>
          </tr>
        </tbody>
      </table></Modal
    >
    <ProductOwnerTransfer
      v-if="transfer"
      :key="`${productIdentity()}:${transfer.id}`"
      :row="transfer"
      @close="closeTransfer"
      @saved="transferred"
    />
    <ProductEditor
      v-if="editor"
      :key="`${productIdentity()}:${editor.id ?? 'new'}`"
      :id="editor.id"
      @close="editor = undefined"
      @saved="edited"
    />
  </div>
</template>
<style scoped>
.management-tabs button {
  padding: 12px 0;
  white-space: nowrap;
  border-bottom: 2px solid transparent;
}
.management-tabs button[aria-current="page"] {
  color: var(--primary);
  border-color: var(--primary);
  font-weight: 600;
}
.product-facts {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
  gap: 14px;
  font-size: 13px;
}
.product-facts dt {
  color: var(--muted);
}
.product-facts dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.product-description {
  white-space: pre-wrap;
}
</style>
