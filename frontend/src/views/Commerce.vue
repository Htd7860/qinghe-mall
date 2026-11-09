<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import Icon from "../components/Icon.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import {
  db,
  ui,
  tenantProducts,
  tenantOrders,
  attempt,
  saveProduct,
  toggleProduct,
  takeDownProducts,
  adjustStock,
  shipOrder,
  reviewAfterSale,
  inspectReturn,
  executeRefund,
  can,
  notify,
  previewUnavailable,
} from "../data/store";
import { money, type Product, type Order, type AfterSale } from "../domain";
import { exportCsv, maskPhone } from "../utils";
const route = useRoute(),
  mode = computed(() => route.path.split("/").at(-1)),
  query = ref(String(route.query.q || "")),
  status = ref(String(route.query.status || "")),
  category = ref(""),
  page = ref(1),
  selected = ref<number[]>([]);
const editing = ref(false),
  editingId = ref<number>(),
  stockProduct = ref<Product>(),
  orderDetail = ref<Order>(),
  shippingOrder = ref<Order>(),
  afterDetail = ref<AfterSale>(),
  confirmProduct = ref<Product>(),
  confirmBulk = ref(false);
const stockDelta = ref(0),
  stockReason = ref(""),
  carrier = ref("顺丰速运"),
  tracking = ref(""),
  reviewNote = ref(""),
  sellable = ref(true),
  formError = ref("");
const stockSpec = ref(""),
  skuDraft = ref<{ spec: string; price: number; stock: number }[]>([]);
const form = reactive({
  name: "",
  subtitle: "",
  category: "T 恤",
  price: "99.00",
  stock: 0,
  colors: "云白,炭黑",
  sizes: "M,L,XL",
  image: "/images/tee.jpg",
  status: "已下架" as Product["status"],
});
const products = computed(() =>
  tenantProducts.value.filter(
    (p) =>
      (!query.value ||
        p.name.includes(query.value) ||
        String(p.id).includes(query.value)) &&
      (!status.value || p.status === status.value) &&
      (!category.value || p.category === category.value),
  ),
);
const orders = computed(() =>
  tenantOrders.value.filter(
    (o) =>
      (!query.value ||
        [o.id, o.buyer, ...o.items.map((i) => i.name)].some((v) =>
          v.includes(query.value),
        )) &&
      (!status.value || o.status === status.value),
  ),
);
const afters = computed(() =>
  db.afterSales.filter(
    (a) =>
      a.tenant === ui.tenant &&
      (!query.value ||
        a.id.includes(query.value) ||
        a.orderId.includes(query.value)) &&
      (!status.value || a.status === status.value),
  ),
);
const total = computed(() =>
  mode.value === "orders"
    ? orders.value.length
    : mode.value === "aftersales"
      ? afters.value.length
      : products.value.length,
);
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / 6)));
const slice = <T,>(rows: T[]) =>
  rows.slice((page.value - 1) * 6, page.value * 6);
watch([query, status, category], () => (page.value = 1));
watch(total, () => {
  if (page.value > pageCount.value) page.value = pageCount.value;
});
watch(
  () => route.query.q,
  (v) => (query.value = String(v || "")),
);
function openEditor(p?: Product) {
  editingId.value = p?.id;
  Object.assign(
    form,
    p
      ? {
          ...p,
          price: (p.price / 100).toFixed(2),
          colors: p.colors.join(","),
          sizes: p.sizes.join(","),
        }
      : {
          name: "",
          subtitle: "",
          category: "T 恤",
          price: "99.00",
          stock: 0,
          colors: "云白,炭黑",
          sizes: "M,L,XL",
          image: "/images/tee.jpg",
          status: "已下架",
        },
  );
  formError.value = "";
  const specs = form.colors
    .split(/[,，]/)
    .map((x) => x.trim())
    .filter(Boolean)
    .flatMap((c) =>
      form.sizes
        .split(/[,，]/)
        .map((x) => x.trim())
        .filter(Boolean)
        .map((z) => `${c} / ${z}`),
    );
  skuDraft.value = specs.map((spec, i) => {
    const old = p?.skus?.find((x) => x.spec === spec);
    return {
      spec,
      price: (old?.price || p?.price || 9900) / 100,
      stock: old?.stock || 0,
    };
  });
  editing.value = true;
}
async function submitProduct() {
  formError.value = "";
  try {
    if (!/^\d+(\.\d{1,2})?$/.test(form.price))
      throw new Error("请输入有效售价，最多保留两位小数");
    await saveProduct(
      {
        ...form,
        skus: skuDraft.value.map((s) => ({
          ...s,
          price: Math.round(s.price * 100),
        })),
        price: Math.round(Number(form.price) * 100),
        colors: [
          ...new Set(
            form.colors
              .split(/[,，]/)
              .map((s) => s.trim())
              .filter(Boolean),
          ),
        ],
        sizes: [
          ...new Set(
            form.sizes
              .split(/[,，]/)
              .map((s) => s.trim())
              .filter(Boolean),
          ),
        ],
      },
      editingId.value,
    );
    editing.value = false;
  } catch (e) {
    formError.value = (e as Error).message;
  }
}
function download() {
  if (mode.value === "orders")
    exportCsv(
      "订单明细",
      ["订单号", "买家", "实付金额", "状态", "创建时间"],
      orders.value.map((o) => [
        o.id,
        o.buyer,
        money(o.total),
        o.status,
        o.created,
      ]),
    );
  else
    exportCsv(
      "商品明细",
      ["商品ID", "商品名称", "售价", "库存", "状态"],
      products.value.map((p) => [
        p.id,
        p.name,
        money(p.price),
        p.stock,
        p.status,
      ]),
    );
}
function orderFor(a: AfterSale) {
  return db.orders.find((o) => o.id === a.orderId && o.tenant === ui.tenant);
}
watch([() => form.colors, () => form.sizes], () => {
  if (!editing.value) return;
  const specs = [
    ...new Set(
      form.colors
        .split(/[,，]/)
        .map((x) => x.trim())
        .filter(Boolean)
        .flatMap((c) =>
          form.sizes
            .split(/[,，]/)
            .map((x) => x.trim())
            .filter(Boolean)
            .map((z) => `${c} / ${z}`),
        ),
    ),
  ];
  skuDraft.value = specs.map(
    (spec) =>
      skuDraft.value.find((s) => s.spec === spec) || {
        spec,
        price: Number(form.price) || 99,
        stock: 0,
      },
  );
});
watch(
  () => form.stock,
  () => {
    if (editingId.value || !skuDraft.value.length) return;
    skuDraft.value.forEach(
      (s, i) =>
        (s.stock =
          Math.floor(form.stock / skuDraft.value.length) +
          (i < form.stock % skuDraft.value.length ? 1 : 0)),
    );
  },
);
if (route.query.create === "1") openEditor();
const session = reactive(ui);
</script>
<template>
  <PageHeader
    :title="String(route.meta.title)"
    :eyebrow="mode === 'products' || mode === 'inventory' ? '商品' : '交易'"
    ><template v-if="mode === 'products'"
      ><button class="button" @click="download">
        <Icon name="download" :size="16" />导出</button
      ><button class="button primary" @click="openEditor()">
        <Icon name="plus" :size="16" />添加商品
      </button></template
    ><button v-else-if="mode === 'orders'" class="button" @click="download">
      <Icon name="download" :size="16" />导出订单
    </button>
    <div v-else-if="mode === 'inventory'" class="metric-inline">
      库存预警
      <strong>{{ tenantProducts.filter((p) => p.stock < 20).length }}</strong>
    </div></PageHeader
  >
  <div v-if="previewUnavailable" class="request-error">
    <Icon name="alert" />暂时无法加载，请重试。<button
      class="button"
      @click="previewUnavailable = false"
    >
      重新加载
    </button>
  </div>
  <section v-else class="panel">
    <div v-if="mode === 'products' || mode === 'orders'" class="tab-row">
      <button :class="{ active: status === '' }" @click="status = ''">
        全部{{ mode === "products" ? "商品" : "订单"
        }}<span>{{
          mode === "products" ? tenantProducts.length : tenantOrders.length
        }}</span></button
      ><button
        v-for="s in mode === 'products'
          ? ['上架中', '已下架']
          : ['待付款', '待发货', '已发货', '已完成', '已取消']"
        :key="s"
        :class="{ active: status === s }"
        @click="status = s"
      >
        {{ s
        }}<span>{{
          mode === "products"
            ? tenantProducts.filter((p) => p.status === s).length
            : tenantOrders.filter((o) => o.status === s).length
        }}</span>
      </button>
    </div>
    <div class="filter-bar">
      <label class="search-field"
        ><Icon name="search" :size="17" /><input
          v-model.trim="query"
          :placeholder="
            mode === 'orders'
              ? '搜索订单号、买家或商品'
              : mode === 'aftersales'
                ? '搜索售后单号或订单号'
                : '搜索商品名称或编号'
          "
          :aria-label="
            mode === 'orders'
              ? '搜索订单'
              : mode === 'aftersales'
                ? '搜索售后'
                : '搜索商品'
          " /><button
          v-if="query"
          class="icon-button"
          aria-label="清空搜索"
          @click="query = ''"
        >
          <Icon name="close" :size="14" /></button></label
      ><select
        v-if="mode === 'products' || mode === 'inventory'"
        v-model="category"
        aria-label="商品分类"
      >
        <option value="">全部分类</option>
        <option
          v-for="c in [...new Set(tenantProducts.map((p) => p.category))]"
          :key="c"
        >
          {{ c }}
        </option></select
      ><select
        v-if="mode === 'aftersales'"
        v-model="status"
        aria-label="售后状态"
      >
        <option value="">全部状态</option>
        <option
          v-for="s in [
            '待审核',
            '待买家退货',
            '待商家收货',
            '待退款',
            '退款成功',
            '已拒绝',
          ]"
          :key="s"
        >
          {{ s }}
        </option></select
      ><button
        class="button text-button"
        @click="
          query = '';
          status = '';
          category = '';
        "
      >
        重置</button
      ><span class="filter-count"
        >共 {{ total }}
        {{
          mode === "products" || mode === "inventory" ? "件商品" : "条记录"
        }}</span
      >
    </div>
    <div v-if="selected.length && mode === 'products'" class="selection-bar">
      已选择 {{ selected.length }} 件商品<button
        class="text-link"
        @click="confirmBulk = true"
      >
        批量下架</button
      ><button class="text-link" @click="selected = []">取消选择</button>
    </div>
    <Empty
      v-if="!total"
      :title="query || status || category ? '没有符合条件的记录' : '暂无记录'"
      :icon="mode === 'orders' ? 'order' : 'product'"
      ><button
        v-if="query || status || category"
        class="button"
        @click="
          query = '';
          status = '';
          category = '';
        "
      >
        清空筛选</button
      ><button
        v-else-if="mode === 'products'"
        class="button primary"
        @click="openEditor()"
      >
        添加商品
      </button></Empty
    >
    <div v-else class="table-scroll">
      <table v-if="mode === 'products'" class="product-table">
        <thead>
          <tr>
            <th class="checkbox-col">
              <input
                type="checkbox"
                aria-label="选择本页商品"
                :checked="slice(products).every((p) => selected.includes(p.id))"
                @change="
                  selected = ($event.target as HTMLInputElement).checked
                    ? slice(products).map((p) => p.id)
                    : []
                "
              />
            </th>
            <th>商品信息</th>
            <th>分类</th>
            <th class="numeric">售价</th>
            <th class="numeric">库存</th>
            <th class="numeric">销量</th>
            <th>状态</th>
            <th>更新时间</th>
            <th class="actions-col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in slice(products)" :key="p.id">
            <td>
              <input
                v-model="selected"
                type="checkbox"
                :value="p.id"
                :aria-label="`选择${p.name}`"
              />
            </td>
            <td>
              <button class="product-cell" @click="openEditor(p)">
                <img :src="p.image" :alt="p.name" />
                <div>
                  <strong>{{ p.name }}</strong
                  ><small
                    >ID {{ p.id }} ·
                    {{ p.colors.length * p.sizes.length }} 个规格</small
                  >
                </div>
              </button>
            </td>
            <td>{{ p.category }}</td>
            <td class="numeric semibold">¥{{ money(p.price) }}</td>
            <td class="numeric" :class="{ 'warning-text': p.stock < 20 }">
              {{ p.stock }}<Icon v-if="p.stock < 20" name="alert" :size="12" />
            </td>
            <td class="numeric">{{ p.sales }}</td>
            <td>
              <span
                class="status"
                :class="p.status === '上架中' ? 'green' : 'gray'"
                ><i />{{ p.status }}</span
              >
            </td>
            <td class="muted small nowrap">{{ p.updated }}</td>
            <td>
              <div class="row-actions">
                <button @click="openEditor(p)">编辑</button
                ><button @click="confirmProduct = p">
                  {{ p.status === "上架中" ? "下架" : "上架" }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="mode === 'inventory'">
        <thead>
          <tr>
            <th>商品</th>
            <th class="numeric">可售库存</th>
            <th>库存状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in slice(products)" :key="p.id">
            <td>
              <div class="product-cell">
                <img :src="p.image" :alt="p.name" />
                <div>
                  <strong>{{ p.name }}</strong
                  ><small>{{ p.category }}</small>
                </div>
              </div>
            </td>
            <td class="numeric semibold">{{ p.stock }}</td>
            <td>
              <span class="status" :class="p.stock < 20 ? 'amber' : 'green'">{{
                p.stock < 20 ? "库存偏低" : "正常"
              }}</span>
            </td>
            <td class="muted">{{ p.updated }}</td>
            <td>
              <button
                class="text-link"
                @click="
                  stockProduct = p;
                  stockSpec = p.skus?.[0]?.spec || '';
                  stockDelta = 0;
                  stockReason = '';
                "
              >
                调整库存
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="mode === 'orders'">
        <thead>
          <tr>
            <th>订单 / 商品</th>
            <th>买家</th>
            <th class="numeric">实付金额</th>
            <th>状态</th>
            <th>下单时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in slice(orders)" :key="o.id">
            <td>
              <div class="order-number">{{ o.id }}</div>
              <div class="product-cell">
                <img :src="o.items[0]!.image" :alt="o.items[0]!.name" />
                <div>
                  <strong
                    >{{ o.items[0]!.name
                    }}<span v-if="o.items.length > 1">
                      等{{ o.items.length }}件</span
                    ></strong
                  ><small
                    >{{ o.items[0]!.spec }} × {{ o.items[0]!.quantity }}</small
                  >
                </div>
              </div>
            </td>
            <td>
              {{ o.buyer
              }}<small class="cell-sub">{{ maskPhone(o.phone) }}</small>
            </td>
            <td class="numeric semibold">
              ¥{{ money(o.total)
              }}<small class="cell-sub">含运费 ¥{{ money(o.shipping) }}</small>
            </td>
            <td>
              <span
                class="status"
                :class="
                  o.status === '待付款'
                    ? 'amber'
                    : o.status === '待发货'
                      ? 'blue'
                      : o.status === '已取消'
                        ? 'gray'
                        : 'green'
                "
                >{{ o.status }}</span
              >
            </td>
            <td class="muted small nowrap">{{ o.created }}</td>
            <td>
              <div class="row-actions">
                <button @click="orderDetail = o">详情</button
                ><button
                  v-if="o.status === '待发货'"
                  @click="
                    shippingOrder = o;
                    tracking = '';
                  "
                >
                  发货
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else>
        <thead>
          <tr>
            <th>售后 / 订单</th>
            <th>商品</th>
            <th>类型</th>
            <th class="numeric">申请金额</th>
            <th>状态</th>
            <th>申请时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in slice(afters)" :key="a.id">
            <td>
              <strong class="small">{{ a.id }}</strong
              ><small class="cell-sub">{{ a.orderId }}</small>
            </td>
            <td>
              {{ orderFor(a)?.items[a.itemIndex]?.name
              }}<small class="cell-sub">申请数量 {{ a.quantity }}</small>
            </td>
            <td>{{ a.type }}</td>
            <td class="numeric semibold">¥{{ money(a.amount) }}</td>
            <td>
              <span
                class="status"
                :class="
                  a.status === '退款成功'
                    ? 'green'
                    : a.status === '已拒绝'
                      ? 'gray'
                      : 'amber'
                "
                >{{ a.status }}</span
              >
            </td>
            <td class="muted small">{{ a.created }}</td>
            <td>
              <button
                class="text-link"
                @click="
                  afterDetail = a;
                  reviewNote = '';
                "
              >
                {{
                  ["退款成功", "已拒绝"].includes(a.status) ? "查看" : "处理"
                }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="total" class="pagination">
      <span>共 {{ total }} 条</span
      ><span class="pagination-right"
        ><span>6 条 / 页</span
        ><button
          class="page-button"
          :disabled="page === 1"
          aria-label="上一页"
          @click="page--"
        >
          <Icon name="right" class="rotate-180" :size="14" /></button
        ><button
          v-for="n in pageCount"
          :key="n"
          class="page-button"
          :class="{ active: page === n }"
          @click="page = n"
        >
          {{ n }}</button
        ><button
          class="page-button"
          :disabled="page === pageCount"
          aria-label="下一页"
          @click="page++"
        >
          <Icon name="right" :size="14" /></button
      ></span>
    </div>
  </section>
  <section v-if="mode === 'inventory'" class="panel section-gap">
    <div class="panel-heading"><h2>最近调整</h2></div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>商品</th>
            <th>变动</th>
            <th>调整后</th>
            <th>原因</th>
            <th>操作人</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="l in db.inventory
              .filter((l) => l.tenant === session.tenant)
              .slice(0, 10)"
            :key="l.id"
          >
            <td>{{ l.product }}</td>
            <td :class="l.delta > 0 ? 'green-text' : 'danger-text'">
              {{ l.delta > 0 ? "+" : "" }}{{ l.delta }}
            </td>
            <td>{{ l.after }}</td>
            <td>{{ l.reason }}</td>
            <td>{{ l.actor }}</td>
            <td class="muted">{{ l.at }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <Modal
    v-if="editing"
    :title="editingId ? '编辑商品' : '添加商品'"
    wide
    @close="editing = false"
    ><form id="product-form" class="form-grid" @submit.prevent="submitProduct">
      <label class="span-2"
        >商品名称<input
          v-model.trim="form.name"
          required
          maxlength="80"
          placeholder="输入商品名称" /></label
      ><label class="span-2"
        >商品短描述<input
          v-model.trim="form.subtitle"
          maxlength="100"
          placeholder="例如：180g 精梳棉 · 舒适日常" /></label
      ><label
        >分类<select v-model="form.category">
          <option
            v-for="c in [
              'T 恤',
              '针织',
              '卫衣',
              '裤装',
              '外套',
              '衬衫',
              '坚果',
            ]"
            :key="c"
          >
            {{ c }}
          </option>
        </select></label
      ><label
        >售价（元）<input
          v-model="form.price"
          @change="skuDraft.forEach((s) => (s.price = Number(form.price)))"
          inputmode="decimal"
          required /></label
      ><label v-if="!editingId"
        >初始库存<input
          v-model.number="form.stock"
          type="number"
          min="0"
          step="1"
          required /></label
      ><label
        >颜色<input
          v-model="form.colors"
          required
          placeholder="云白,炭黑"
        /><small>多个颜色用逗号分隔</small></label
      ><label
        >尺码<input v-model="form.sizes" required placeholder="M,L,XL" /></label
      ><label
        >商品图片<select v-model="form.image">
          <option value="/images/tee.jpg">圆领 T 恤</option>
          <option value="/images/knit.jpg">针织衫</option>
          <option value="/images/hoodie.jpg">连帽卫衣</option>
          <option value="/images/denim.jpg">牛仔裤</option>
          <option value="/images/jacket.jpg">夹克</option>
          <option value="/images/shirt.jpg">衬衫</option>
        </select></label
      >
      <div class="form-product-preview">
        <img :src="form.image" alt="商品图片预览" /><span>{{
          form.name || "商品图片"
        }}</span>
      </div>
      <div class="span-2 sku-editor">
        <h3>规格明细</h3>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>规格</th>
                <th>售价（元）</th>
                <th>库存</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in skuDraft" :key="s.spec">
                <td>{{ s.spec }}</td>
                <td>
                  <input
                    v-model.number="s.price"
                    type="number"
                    min="0.01"
                    step="0.01"
                    :aria-label="s.spec + '售价'"
                    required
                  />
                </td>
                <td>
                  <input
                    v-model.number="s.stock"
                    type="number"
                    min="0"
                    step="1"
                    :disabled="!!editingId"
                    :aria-label="s.spec + '库存'"
                    required
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <p v-if="formError" class="form-error span-2" role="alert">
        {{ formError }}
      </p>
    </form>
    <template #footer
      ><button class="button" @click="editing = false">取消</button
      ><button type="submit" form="product-form" class="button primary">
        保存商品
      </button></template
    ></Modal
  >
  <Modal
    v-if="confirmProduct"
    :title="confirmProduct.status === '上架中' ? '下架商品' : '上架商品'"
    @close="confirmProduct = undefined"
    ><p>
      确认{{ confirmProduct.status === "上架中" ? "下架" : "上架" }}「{{
        confirmProduct.name
      }}」？
    </p>
    <template #footer
      ><button class="button" @click="confirmProduct = undefined">取消</button
      ><button
        class="button primary"
        @click="
          attempt(async () => {
            await toggleProduct(confirmProduct!);
            confirmProduct = undefined;
          })
        "
      >
        确认{{ confirmProduct.status === "上架中" ? "下架" : "上架" }}
      </button></template
    ></Modal
  >
  <Modal v-if="confirmBulk" title="批量下架" @close="confirmBulk = false"
    ><p>确认下架选中的 {{ selected.length }} 件商品？</p>
    <template #footer
      ><button class="button" @click="confirmBulk = false">取消</button
      ><button
        class="button primary"
        @click="
          attempt(async () => {
            await takeDownProducts([...selected]);
            selected = [];
            confirmBulk = false;
            notify('所选商品已下架');
          })
        "
      >
        确认下架
      </button></template
    ></Modal
  >
  <Modal v-if="stockProduct" title="调整库存" @close="stockProduct = undefined"
    ><form
      id="stock-form"
      @submit.prevent="
        attempt(async () => {
          await adjustStock(stockProduct!, stockDelta, stockReason, stockSpec);
          stockProduct = undefined;
        })
      "
    >
      <div class="context-block">
        <strong>{{ stockProduct.name }}</strong
        ><span>当前可售库存 {{ stockProduct.stock }}</span>
      </div>
      <label
        >商品规格<select v-model="stockSpec">
          <option v-for="s in stockProduct.skus" :key="s.spec" :value="s.spec">
            {{ s.spec }} · 可售 {{ s.stock }}
          </option>
        </select></label
      ><label
        >调整数量<input
          v-model.number="stockDelta"
          type="number"
          step="1"
          required
        /><small>增加填正数，减少填负数</small></label
      ><label
        >调整原因<textarea
          v-model.trim="stockReason"
          required
          maxlength="200"
        />
      </label>
    </form>
    <template #footer
      ><button class="button" @click="stockProduct = undefined">取消</button
      ><button class="button primary" form="stock-form" type="submit">
        确认调整
      </button></template
    ></Modal
  >
  <Modal
    v-if="shippingOrder"
    title="确认发货"
    @close="shippingOrder = undefined"
    ><form
      id="shipping-form"
      @submit.prevent="
        attempt(async () => {
          await shipOrder(shippingOrder!.id, carrier, tracking);
          shippingOrder = undefined;
        })
      "
    >
      <div class="context-block">
        <strong>{{ shippingOrder.id }}</strong
        ><span>{{ shippingOrder.buyer }} · {{ shippingOrder.address }}</span>
      </div>
      <label
        >快递公司<select v-model="carrier">
          <option>顺丰速运</option>
          <option>中通快递</option>
          <option>圆通速递</option>
          <option>邮政 EMS</option>
        </select></label
      ><label
        >运单号<input
          v-model.trim="tracking"
          required
          placeholder="填写快递运单号"
          minlength="6"
          maxlength="40"
      /></label>
    </form>
    <template #footer
      ><button class="button" @click="shippingOrder = undefined">取消</button
      ><button class="button primary" form="shipping-form" type="submit">
        确认发货
      </button></template
    ></Modal
  >
  <Modal
    v-if="orderDetail"
    title="订单详情"
    wide
    @close="orderDetail = undefined"
    ><div class="detail-summary">
      <div>
        <small>订单编号</small><strong>{{ orderDetail.id }}</strong>
      </div>
      <span class="status green">{{ orderDetail.status }}</span>
    </div>
    <div v-for="(item, i) in orderDetail.items" :key="i" class="detail-item">
      <div class="product-cell">
        <img :src="item.image" :alt="item.name" />
        <div>
          <strong>{{ item.name }}</strong
          ><small>{{ item.spec }} × {{ item.quantity }}</small>
        </div>
      </div>
      <span>¥{{ money(item.price * item.quantity - item.discount) }}</span>
    </div>
    <dl class="detail-grid">
      <div>
        <dt>买家</dt>
        <dd>{{ orderDetail.buyer }} / {{ maskPhone(orderDetail.phone) }}</dd>
      </div>
      <div>
        <dt>收货地址</dt>
        <dd>{{ orderDetail.address }}</dd>
      </div>
      <div>
        <dt>下单时间</dt>
        <dd>{{ orderDetail.created }}</dd>
      </div>
      <div>
        <dt>运费</dt>
        <dd>¥{{ money(orderDetail.shipping) }}</dd>
      </div>
      <div v-if="orderDetail.tracking">
        <dt>物流信息</dt>
        <dd>{{ orderDetail.carrier }} · {{ orderDetail.tracking }}</dd>
      </div>
      <div>
        <dt>实付金额</dt>
        <dd class="amount">¥{{ money(orderDetail.total) }}</dd>
      </div>
    </dl>
    <h3 class="section-title">订单记录</h3>
    <ol class="timeline">
      <li v-for="(h, i) in orderDetail.history" :key="i">{{ h }}</li>
    </ol>
    <template #footer
      ><button class="button" @click="orderDetail = undefined">关闭</button
      ><button
        v-if="orderDetail.status === '待发货'"
        class="button primary"
        @click="
          shippingOrder = orderDetail;
          tracking = '';
          orderDetail = undefined;
        "
      >
        去发货
      </button></template
    ></Modal
  >
  <Modal
    v-if="afterDetail"
    title="售后详情"
    wide
    @close="afterDetail = undefined"
    ><div class="detail-summary">
      <div>
        <small>售后单号</small><strong>{{ afterDetail.id }}</strong>
      </div>
      <span class="status amber">{{ afterDetail.status }}</span>
    </div>
    <dl class="detail-grid">
      <div>
        <dt>订单编号</dt>
        <dd>{{ afterDetail.orderId }}</dd>
      </div>
      <div>
        <dt>商品</dt>
        <dd>{{ orderFor(afterDetail)?.items[afterDetail.itemIndex]?.name }}</dd>
      </div>
      <div>
        <dt>类型 / 数量</dt>
        <dd>{{ afterDetail.type }} / {{ afterDetail.quantity }} 件</dd>
      </div>
      <div>
        <dt>申请原因</dt>
        <dd>{{ afterDetail.reason }}</dd>
      </div>
      <div>
        <dt>申请金额</dt>
        <dd class="amount">¥{{ money(afterDetail.amount) }}</dd>
      </div>
      <div v-if="afterDetail.tracking">
        <dt>退货物流</dt>
        <dd>{{ afterDetail.tracking }}</dd>
      </div>
    </dl>
    <label v-if="afterDetail.status === '待审核'"
      >审核意见<textarea
        v-model.trim="reviewNote"
        placeholder="拒绝申请时需填写原因"
      /></label
    ><label v-if="afterDetail.status === '待商家收货'" class="checkbox-label"
      ><input v-model="sellable" type="checkbox" />验货后可再次销售</label
    ><template #footer
      ><button class="button" @click="afterDetail = undefined">关闭</button
      ><template v-if="afterDetail.status === '待审核'"
        ><button
          class="button danger-outline"
          @click="
            attempt(() => reviewAfterSale(afterDetail!, false, reviewNote))
          "
        >
          拒绝申请</button
        ><button
          class="button primary"
          @click="attempt(() => reviewAfterSale(afterDetail!, true))"
        >
          {{ afterDetail.type === "退货退款" ? "同意退货" : "同意退款申请" }}
        </button></template
      ><button
        v-if="afterDetail.status === '待商家收货'"
        class="button primary"
        @click="attempt(() => inspectReturn(afterDetail!, sellable))"
      >
        确认收货验收</button
      ><button
        v-if="afterDetail.status === '待退款' && can('refund')"
        class="button primary"
        @click="attempt(() => executeRefund(afterDetail!))"
      >
        执行退款 ¥{{ money(afterDetail.amount) }}
      </button></template
    ></Modal
  >
</template>
