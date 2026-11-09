<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import Empty from "./Empty.vue";
import Modal from "./Modal.vue";
import Icon from "./Icon.vue";
import { ApiError } from "../api/http";
import { context } from "../api/session";
import {
  readMemberCart,
  cartIdentity,
  cartProductOptions,
  addMemberCart,
  setCartCount,
  setCartSelected,
  resetMemberCart,
  deleteMemberCart,
  type MemberCart,
  type CartLine,
  type CartProduct,
} from "../api/member-cart";
const emit = defineEmits<{ count: [number | null] }>();
const cart = ref<MemberCart | null>(null),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  uncertain = ref(false);
const draft = reactive<Record<number, number | string>>({}),
  deleting = ref<CartLine | null>(null);
const chooser = ref(false),
  changing = ref<CartLine | null>(null),
  keyword = ref(""),
  optionPage = ref(1),
  optionTotal = ref(0),
  options = ref<CartProduct[]>([]);
const optionBusy = ref(false),
  optionError = ref(""),
  chosen = ref<CartProduct | null>(null),
  quantity = ref<number | string>(1);
const locked = computed(
  () => !cart.value || loading.value || busy.value || uncertain.value,
);
const selected = computed(
  () => cart.value?.lines.filter((x) => x.selected) || [],
);
const selectedValid = computed(() => selected.value.filter((x) => x.valid));
const subtotal = computed(() =>
  selectedValid.value.reduce((n, x) => n + x.product!.price * x.count, 0),
);
const base = computed(() => `/shop/${context.tenant}`);
const canCheckout = computed(
  () =>
    !locked.value &&
    !!cart.value?.canPurchase &&
    selected.value.length > 0 &&
    selected.value.length <= (cart.value?.maxOrderLines || 0) &&
    selected.value.every((x) => x.valid),
);
const allSelected = computed(
  () => !!cart.value?.lines.length && cart.value.lines.every((x) => x.selected),
);
let epoch = 0,
  reads = 0,
  pickerReads = 0;
function fail(e: unknown) {
  error.value = e instanceof Error ? e.message : "操作失败，请重试";
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
async function reload() {
  const ticket = epoch,
    serial = ++reads;
  loading.value = true;
  error.value = "";
  cart.value = null;
  emit("count", null);
  try {
    const value = await readMemberCart();
    if (ticket !== epoch || serial !== reads) return false;
    cart.value = value;
    for (const key of Object.keys(draft)) delete draft[Number(key)];
    for (const row of value.lines) draft[row.id] = row.count;
    uncertain.value = false;
    emit(
      "count",
      value.lines.reduce((n, x) => n + x.count, 0),
    );
    return true;
  } catch (e) {
    if (ticket === epoch && serial === reads) fail(e);
    return false;
  } finally {
    if (ticket === epoch && serial === reads) loading.value = false;
  }
}
watch(
  cartIdentity,
  () => {
    epoch++;
    reads++;
    pickerReads++;
    cart.value = null;
    busy.value = false;
    uncertain.value = false;
    chooser.value = false;
    changing.value = null;
    deleting.value = null;
    options.value = [];
    chosen.value = null;
    optionBusy.value = false;
    optionError.value = "";
    notice.value = "";
    void reload();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  epoch++;
  reads++;
  pickerReads++;
  cart.value = null;
  options.value = [];
  chosen.value = null;
  emit("count", null);
});
async function modify(action: () => Promise<unknown>, message: string) {
  if (locked.value) return;
  const ticket = epoch;
  busy.value = true;
  error.value = "";
  notice.value = "";
  let committed = false;
  try {
    await action();
    committed = true;
    if (ticket !== epoch) return;
    chooser.value = false;
    changing.value = null;
    deleting.value = null;
    pickerReads++;
    notice.value = message;
    uncertain.value = true;
    const ok = await reload();
    if (ticket === epoch && !ok)
      notice.value = "修改已提交，列表暂未能刷新，请重新读取后核对。";
  } catch (e) {
    if (ticket !== epoch) return;
    uncertain.value = true;
    chooser.value = false;
    changing.value = null;
    deleting.value = null;
    pickerReads++;
    options.value = [];
    fail(e);
    notice.value = "请先重新读取购物车，核对数量和勾选后再操作。";
  } finally {
    if (ticket === epoch) {
      busy.value = false;
      if (committed) options.value = [];
    }
  }
}
function money(cents: number) {
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, "0")}`;
}
function spec(row: CartProduct) {
  return row.properties.length
    ? row.properties.map((x) => `${x.propertyName}：${x.valueName}`).join(" / ")
    : "默认规格";
}
function updateCount(row: CartLine) {
  const n = Number(draft[row.id]);
  if (
    !Number.isSafeInteger(n) ||
    n < 1 ||
    n > (cart.value?.maxItemQuantity || 0)
  ) {
    error.value = "请填写当前允许范围内的整数数量";
    return;
  }
  if (n === row.count) return;
  void modify(
    () => setCartCount(row.id, n, cart.value!.version),
    "商品数量已更新",
  );
}
function selectOne(row: CartLine, value: boolean) {
  void modify(
    () => setCartSelected([row.id], value, cart.value!.version),
    "商品勾选已更新",
  );
}
function toggleAll() {
  if (!cart.value) return;
  const value = !allSelected.value;
  const ids = cart.value.lines
    .filter((x) => !value || x.valid)
    .map((x) => x.id);
  if (ids.length)
    void modify(
      () => setCartSelected(ids, value, cart.value!.version),
      "商品勾选已更新",
    );
}
async function loadOptions() {
  const ticket = epoch,
    serial = ++pickerReads;
  optionBusy.value = true;
  optionError.value = "";
  options.value = [];
  chosen.value = null;
  try {
    const data = await cartProductOptions(
      keyword.value.trim(),
      optionPage.value,
      changing.value?.spuId,
    );
    if (ticket !== epoch || serial !== pickerReads) return;
    options.value = data.list;
    optionTotal.value = data.total;
  } catch (e) {
    if (ticket === epoch && serial === pickerReads)
      optionError.value = e instanceof Error ? e.message : "商品读取失败";
  } finally {
    if (ticket === epoch && serial === pickerReads) optionBusy.value = false;
  }
}
function openPicker(row: CartLine | null = null) {
  if (locked.value || !cart.value?.canPurchase) return;
  changing.value = row;
  keyword.value = "";
  optionPage.value = 1;
  quantity.value = row?.count || 1;
  chosen.value = null;
  chooser.value = true;
  void loadOptions();
}
function closePicker() {
  if (busy.value) return;
  chooser.value = false;
  changing.value = null;
  chosen.value = null;
  options.value = [];
  pickerReads++;
}
function savePicker() {
  if (!chosen.value || !cart.value) return;
  const n = Number(quantity.value);
  if (!Number.isSafeInteger(n) || n < 1 || n > cart.value.maxItemQuantity) {
    optionError.value = "请填写允许范围内的整数数量";
    return;
  }
  const sku = chosen.value.skuId,
    old = changing.value;
  void modify(
    () =>
      old
        ? resetMemberCart(old.id, sku, n, cart.value!.version)
        : addMemberCart(sku, n, cart.value!.version),
    old ? "商品规格已更新，相同规格已合并数量" : "商品已加入购物袋",
  );
}
function searchOptions() {
  optionPage.value = 1;
  void loadOptions();
}
function nextOption(delta: number) {
  if (optionBusy.value || busy.value) return;
  optionPage.value += delta;
  void loadOptions();
}
</script>
<template>
  <main class="shop-container member-cart">
    <div class="shop-page-heading inline">
      <h1>购物袋</h1>
      <button
        class="shop-button"
        :disabled="locked || !cart?.canPurchase"
        @click="openPicker()"
      >
        <Icon name="plus" :size="17" />添加商品
      </button>
    </div>
    <p v-if="notice" class="cart-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <div class="cart-toolbar">
      <button class="button" :disabled="busy || loading" @click="reload">
        重新读取</button
      ><span v-if="cart"
        >共 {{ cart.lines.length }} 种商品 · 最多
        {{ cart.maxCartLines }} 种</span
      >
    </div>
    <Empty v-if="loading" title="正在读取购物袋…" icon="refresh" />
    <template v-else-if="cart">
      <p v-if="!cart.canPurchase" class="cart-notice">
        店铺暂不支持新增购买，仍可移除商品或取消勾选。
      </p>
      <Empty v-if="!cart.lines.length" title="购物袋还是空的" icon="bag"
        ><button
          class="button primary"
          :disabled="locked || !cart.canPurchase"
          @click="openPicker()"
        >
          选购商品
        </button></Empty
      >
      <div v-else class="shopping-layout">
        <section class="bag-items">
          <label class="cart-select-all"
            ><input
              type="checkbox"
              :checked="allSelected"
              :disabled="locked || (!allSelected && !cart.canPurchase)"
              @change="toggleAll"
            />全选有效商品 / 取消全部勾选</label
          >
          <article v-for="row in cart.lines" :key="row.id" class="bag-item">
            <input
              type="checkbox"
              :checked="row.selected"
              :disabled="
                locked || (!row.selected && (!row.valid || !cart.canPurchase))
              "
              :aria-label="`选择${row.product?.name || '失效商品'}`"
              @change="
                selectOne(row, ($event.target as HTMLInputElement).checked)
              "
            />
            <div class="cart-product-mark" aria-hidden="true">
              <Icon name="bag" :size="28" />
            </div>
            <div class="bag-description">
              <h3>{{ row.product?.name || "商品或规格已失效" }}</h3>
              <p>{{ row.product ? spec(row.product) : "此商品暂不可购买" }}</p>
              <strong v-if="row.product"
                >¥{{ money(row.product.price) }}</strong
              >
              <p v-if="!row.valid" class="form-error">
                {{ row.invalidReason }}
              </p>
              <div class="cart-quantity">
                <label
                  >数量
                  <input
                    v-model="draft[row.id]"
                    type="number"
                    min="1"
                    :max="cart.maxItemQuantity"
                    step="1"
                    :disabled="
                      locked ||
                      !cart.canPurchase ||
                      !row.product ||
                      row.product.stock < 1
                    "
                    :aria-label="`${row.product?.name || '商品'}数量`"
                /></label>
                <button
                  class="text-link"
                  :disabled="
                    locked ||
                    !cart.canPurchase ||
                    !row.product ||
                    row.product.stock < 1 ||
                    Number(draft[row.id]) === row.count
                  "
                  @click="updateCount(row)"
                >
                  更新数量
                </button>
              </div>
              <button
                class="text-link"
                :disabled="locked || !cart.canPurchase"
                @click="openPicker(row)"
              >
                更换规格
              </button>
            </div>
            <button
              class="icon-button bag-remove"
              :disabled="locked"
              :aria-label="`移除${row.product?.name || '失效商品'}`"
              @click="deleting = row"
            >
              <Icon name="close" :size="17" />
            </button>
            <strong v-if="row.product" class="bag-line-total"
              >¥{{ money(row.product.price * row.count) }}</strong
            >
          </article>
        </section>
        <aside class="order-summary">
          <h2>已选商品</h2>
          <div>
            <span>有效商品金额</span><strong>¥{{ money(subtotal) }}</strong>
          </div>
          <p>{{ selectedValid.length }} 种有效商品</p>
          <p v-if="selected.some((x) => !x.valid)" class="form-error">
            已勾选的商品中存在失效或库存不足项，请先调整。
          </p>
          <p class="muted small">运费及优惠以结算确认为准。</p>
          <RouterLink
            v-if="canCheckout"
            class="shop-button full-width"
            :to="`${base}/checkout`"
            >去结算</RouterLink
          >
          <button v-else class="shop-button full-width" disabled>去结算</button>
          <p
            v-if="selected.length > (cart?.maxOrderLines || 0)"
            class="form-error"
          >
            单次最多结算 {{ cart?.maxOrderLines }} 种商品。
          </p>
        </aside>
      </div>
    </template>
    <Modal
      v-if="chooser"
      :title="changing ? '更换商品规格' : '添加商品'"
      wide
      @close="closePicker"
    >
      <form class="cart-picker-search" @submit.prevent="searchOptions">
        <input
          v-model="keyword"
          maxlength="64"
          placeholder="搜索商品名称"
          aria-label="商品名称"
          :disabled="busy || optionBusy"
        /><button class="button" :disabled="busy || optionBusy">搜索</button>
      </form>
      <p v-if="optionError" class="form-error" role="alert">
        {{ optionError }}
      </p>
      <Empty v-if="optionBusy" title="正在读取商品…" icon="refresh" />
      <div v-else class="cart-options">
        <label
          v-for="row in options"
          :key="row.skuId"
          class="cart-option"
          :class="{ 'is-selected': chosen?.skuId === row.skuId }"
        >
          <input
            v-model="chosen"
            type="radio"
            :value="row"
            name="cart-product"
            :disabled="busy || row.stock < 1"
          /><span
            ><strong>{{ row.name }}</strong
            ><small>{{ spec(row) }}</small
            ><small
              >¥{{ money(row.price) }} ·
              {{
                row.stock
                  ? `可购 ${Math.min(row.stock, cart?.maxItemQuantity || 999)} 件`
                  : "暂时缺货"
              }}</small
            ></span
          > </label
        ><Empty
          v-if="!options.length && !optionError"
          title="没有可选择的商品规格"
          icon="bag"
        />
      </div>
      <div v-if="optionTotal > 20" class="cart-picker-pages">
        <button
          class="button"
          :disabled="optionPage <= 1 || busy || optionBusy"
          @click="nextOption(-1)"
        >
          上一页</button
        ><span>第 {{ optionPage }} 页</span
        ><button
          class="button"
          :disabled="optionPage * 20 >= optionTotal || busy || optionBusy"
          @click="nextOption(1)"
        >
          下一页
        </button>
      </div>
      <label v-if="chosen" class="cart-picker-quantity"
        >数量
        <input
          v-model="quantity"
          type="number"
          min="1"
          :max="cart?.maxItemQuantity || 999"
          step="1"
          :disabled="busy"
      /></label>
      <p v-if="changing" class="muted small">
        选择购物袋中已有的规格时，两项数量会合并。
      </p>
      <template #footer
        ><button class="button" :disabled="busy" @click="closePicker">
          取消</button
        ><button
          class="button primary"
          :disabled="locked || !chosen || optionBusy"
          @click="savePicker"
        >
          {{ busy ? "正在保存…" : changing ? "确认更换" : "加入购物袋" }}
        </button></template
      >
    </Modal>
    <Modal v-if="deleting" title="移除商品" @close="!busy && (deleting = null)"
      ><p>确认从购物袋移除 {{ deleting.product?.name || "此失效商品" }}？</p>
      <template #footer
        ><button class="button" :disabled="busy" @click="deleting = null">
          取消</button
        ><button
          class="button danger"
          :disabled="locked"
          @click="
            modify(
              () => deleteMemberCart([deleting!.id], cart!.version),
              '商品已移除',
            )
          "
        >
          确认移除
        </button></template
      >
    </Modal>
  </main>
</template>
<style scoped>
.member-cart {
  padding-top: 32px;
  padding-bottom: 48px;
}
.cart-toolbar,
.cart-quantity,
.cart-picker-pages {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin: 16px 0;
}
.cart-toolbar {
  justify-content: space-between;
  color: var(--muted);
}
.cart-notice {
  padding: 12px 16px;
  background: var(--primary-soft);
  border-radius: 6px;
}
.cart-product-mark {
  display: grid;
  place-items: center;
  align-self: start;
  width: 72px;
  height: 88px;
  background: var(--workspace);
  color: var(--muted);
  border-radius: 6px;
}
.bag-description h3 {
  font-size: 15px;
  margin: 0 0 8px;
}
.cart-quantity input,
.cart-picker-quantity input {
  width: 88px;
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  margin-left: 8px;
}
.cart-select-all {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.cart-picker-search {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.cart-picker-search input {
  flex: 1;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
}
.cart-options {
  display: grid;
  gap: 8px;
  max-height: 360px;
  overflow: auto;
}
.cart-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
}
.cart-option.is-selected {
  border-color: var(--primary);
  background: var(--primary-soft);
}
.cart-option small {
  display: block;
  margin-top: 6px;
  color: var(--muted);
  overflow-wrap: anywhere;
}
.cart-picker-pages {
  justify-content: center;
}
.cart-picker-quantity {
  display: block;
  margin-top: 16px;
}
.text-link:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@media (max-width: 600px) {
  .cart-product-mark {
    display: none;
  }
  .cart-quantity {
    align-items: flex-start;
    flex-direction: column;
  }
  .cart-picker-search {
    gap: 8px;
  }
}
</style>
