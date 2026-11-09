<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import Empty from "./Empty.vue";
import { ApiError } from "../api/http";
import { context, credentials, identities } from "../api/session";
import { memberIdentity } from "../api/member-profile";
import { resolveStorefront, type Storefront } from "../api/storefront";
import {
  readMemberCart,
  addMemberCart,
  type MemberCart,
} from "../api/member-cart";
import {
  catalogPage,
  catalogSearchPage,
  catalogDetail,
  catalogOptions,
  catalogImage,
  pendingCatalogAdd,
  saveCatalogAdd,
  clearCatalogAdd,
  type CatalogProduct,
  type CatalogDetail,
  type CatalogQuery,
  type CatalogOptions,
  type CatalogCartAdd,
} from "../api/member-catalog";

const emit = defineEmits<{ count: [number | null] }>();
const route = useRoute(),
  router = useRouter();
const base = computed(() => `/shop/${context.tenant}`);
const isHome = computed(() => String(route.params.page || "home") === "home");
const isDetail = computed(() => String(route.params.page) === "product");
const signedIn = computed(
  () =>
    context.realm === "member" &&
    !!identities.member &&
    identities.member.tenantCode === context.tenant,
);
const login = computed(() => ({
  path: `${base.value}/login`,
  query: { redirect: route.fullPath },
}));
const store = ref<Storefront | null>(null),
  rows = ref<CatalogProduct[]>([]),
  total = ref(0);
const detail = ref<CatalogDetail | null>(null),
  options = ref<CatalogOptions | null>(null);
const loading = ref(false),
  busy = ref(false),
  readError = ref(""),
  optionError = ref(""),
  writeError = ref(""),
  notice = ref("");
const storageBlocked = ref(false),
  pending = ref<CatalogCartAdd | null>(null),
  observedCart = ref<MemberCart | null>(null),
  rejected = ref(false);
const skuId = ref<number | null>(null),
  quantity = ref(1),
  picture = ref(0),
  badImages = ref(new Set<string>());
const draft = reactive({
  keyword: "",
  category: "",
  brand: "",
  min: "",
  max: "",
  sort: "default",
});
const initial = (): CatalogQuery => ({
  pageNo: 1,
  keyword: "",
  categoryId: null,
  brandId: null,
  minPrice: null,
  maxPrice: null,
  sort: "default",
});
const query = ref<CatalogQuery>(initial());
const searching = computed(
  () => !isHome.value && !isDetail.value && !!query.value.keyword.trim(),
);
const maxPage = computed(() => (searching.value ? 500 : 10000));
const sku = computed(() =>
  detail.value?.skus.find((s) => s.id === skuId.value),
);
const shownRows = computed(() =>
  isHome.value ? rows.value.slice(0, 8) : rows.value,
);
const pictureUrl = computed(
  () =>
    detail.value?.sliderPicUrls[picture.value] || detail.value?.picUrl || "",
);
const canAdd = computed(
  () =>
    signedIn.value &&
    !busy.value &&
    !loading.value &&
    !storageBlocked.value &&
    !pending.value &&
    !!detail.value?.canPurchase &&
    !!sku.value &&
    Number.isSafeInteger(quantity.value) &&
    quantity.value > 0 &&
    quantity.value <= Math.min(sku.value.stock, 9999),
);
const observedLine = computed(() =>
  observedCart.value?.lines.find((l) => l.skuId === pending.value?.skuId),
);
const canAcknowledge = computed(
  () =>
    !!pending.value &&
    !busy.value &&
    (rejected.value ||
      (!!observedCart.value &&
        observedCart.value.version > pending.value.expectedVersion)),
);
let epoch = 0,
  reads = 0;
const money = (n: number) =>
  `${Math.floor(n / 100)}.${String(n % 100).padStart(2, "0")}`;
function image(value: string) {
  const src = catalogImage(value);
  return badImages.value.has(src) ? "" : src;
}
function fail(e: unknown, write = false) {
  const msg = e instanceof Error ? e.message : "暂时无法读取，请稍后重试";
  if (write) writeError.value = msg;
  else readError.value = msg;
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
function scalar(value: unknown) {
  if (value === undefined || value === null) return "";
  if (typeof value !== "string") throw new ApiError("筛选参数不正确", 400);
  return value;
}
function positive(value: string) {
  if (!value) return null;
  if (!/^[1-9][0-9]*$/.test(value) || !Number.isSafeInteger(Number(value)))
    throw new ApiError("筛选编号不正确", 400);
  return Number(value);
}
function cents(value: string) {
  if (!value) return null;
  if (!/^\d{1,8}(?:\.\d{1,2})?$/.test(value))
    throw new ApiError("价格请填写最多两位小数", 400);
  const [whole, decimal = ""] = value.split(".");
  const n = Number(whole) * 100 + Number(decimal.padEnd(2, "0"));
  if (n > 2000000000) throw new ApiError("筛选价格超出范围", 400);
  return n;
}
function filters(raw: Record<string, unknown>): CatalogQuery {
  const page = positive(scalar(raw.page)) || 1,
    sort = scalar(raw.sort) || "default",
    keyword = scalar(raw.q).trim();
  if (
    page > 10000 ||
    !["default", "priceAsc", "priceDesc", "salesDesc", "newest"].includes(
      sort,
    ) ||
    keyword.length > 64 ||
    /[\x00-\x1f\x7f]/.test(keyword)
  )
    throw new ApiError("商品筛选条件不正确", 400);
  const q: CatalogQuery = {
    pageNo: page,
    keyword,
    categoryId: positive(scalar(raw.category)),
    brandId: positive(scalar(raw.brand)),
    minPrice: cents(scalar(raw.min)),
    maxPrice: cents(scalar(raw.max)),
    sort: sort as CatalogQuery["sort"],
  };
  if (q.minPrice !== null && q.maxPrice !== null && q.minPrice > q.maxPrice)
    throw new ApiError("最低价不能大于最高价", 400);
  if (keyword && page > 500)
    throw new ApiError(
      "搜索最多浏览前 500 页，请缩小关键词、分类或价格范围。",
      400,
    );
  return q;
}
async function applyFilters() {
  if (loading.value || busy.value) return;
  try {
    filters({
      q: draft.keyword,
      category: draft.category,
      brand: draft.brand,
      min: draft.min,
      max: draft.max,
      sort: draft.sort,
    });
    const target = {
      path: `${base.value}/products`,
      query: {
        q: draft.keyword.trim() || undefined,
        category: draft.category || undefined,
        brand: draft.brand || undefined,
        min: draft.min || undefined,
        max: draft.max || undefined,
        sort: draft.sort === "default" ? undefined : draft.sort,
      },
    };
    if (router.resolve(target).fullPath === route.fullPath) await reload();
    else await router.push(target);
  } catch (e) {
    fail(e);
  }
}
async function page(delta: number) {
  if (loading.value || busy.value) return;
  const n = query.value.pageNo + delta;
  if (n < 1 || n > maxPage.value || (delta > 0 && (n - 1) * 20 >= total.value))
    return;
  await router.push({
    path: `${base.value}/products`,
    query: { ...route.query, page: String(n) },
  });
}
async function clearSearch() {
  if (loading.value || busy.value) return;
  await router.push({
    path: `${base.value}/products`,
    query: { ...route.query, q: undefined, page: undefined },
  });
}
function categoryName(id: number) {
  const all = new Map(options.value?.categories.map((c) => [c.id, c]) || []),
    path: string[] = [];
  for (
    let n: number | undefined = id;
    n && path.length < 3;
    n = all.get(n)?.parentId
  ) {
    const c = all.get(n);
    if (!c) break;
    path.unshift(c.name);
  }
  return path.join(" / ");
}
async function reload() {
  if (busy.value) return;
  const n = epoch,
    serial = ++reads;
  loading.value = true;
  readError.value = "";
  optionError.value = "";
  rows.value = [];
  detail.value = null;
  options.value = null;
  total.value = 0;
  skuId.value = null;
  quantity.value = 1;
  picture.value = 0;
  try {
    query.value = isHome.value ? initial() : filters(route.query);
    Object.assign(draft, {
      keyword: query.value.keyword,
      category: query.value.categoryId ? String(query.value.categoryId) : "",
      brand: query.value.brandId ? String(query.value.brandId) : "",
      min: query.value.minPrice === null ? "" : money(query.value.minPrice),
      max: query.value.maxPrice === null ? "" : money(query.value.maxPrice),
      sort: query.value.sort,
    });
    const id = isDetail.value ? positive(String(route.params.id || "")) : null;
    if (isDetail.value && !id) throw new ApiError("商品编号不正确", 400);
    const result = await Promise.allSettled([
      resolveStorefront(context.tenant),
      isDetail.value
        ? catalogDetail(id!)
        : searching.value
          ? catalogSearchPage(query.value)
          : catalogPage(query.value),
      !isHome.value && !isDetail.value
        ? catalogOptions()
        : Promise.resolve(null),
    ]);
    if (n !== epoch || serial !== reads) return;
    const [entry, products, choices] = result;
    if (entry.status === "fulfilled") store.value = entry.value;
    else {
      store.value = null;
      optionError.value = "店铺名称暂未能读取";
    }
    if (products.status === "fulfilled") {
      if (isDetail.value) {
        detail.value = products.value as CatalogDetail;
        if (detail.value.skus.length === 1)
          skuId.value = detail.value.skus[0]!.id;
      } else {
        const p = products.value as { list: CatalogProduct[]; total: number };
        rows.value = p.list;
        total.value = p.total;
      }
    } else fail(products.reason);
    if (choices.status === "fulfilled") options.value = choices.value;
    else
      optionError.value =
        choices.reason instanceof Error
          ? choices.reason.message
          : "筛选项暂不可用";
  } catch (e) {
    if (n === epoch && serial === reads) fail(e);
  } finally {
    if (n === epoch && serial === reads) loading.value = false;
  }
}
async function cartState(n: number) {
  const value = await readMemberCart();
  if (n !== epoch) return null;
  observedCart.value = value;
  emit(
    "count",
    value.lines.reduce((sum, l) => sum + l.count, 0),
  );
  return value;
}
async function modifying(action: (n: number) => Promise<void>) {
  if (busy.value || loading.value || storageBlocked.value || !signedIn.value)
    return;
  const n = epoch;
  busy.value = true;
  writeError.value = "";
  try {
    await action(n);
  } catch (e) {
    if (n === epoch) fail(e, true);
  } finally {
    if (n === epoch) busy.value = false;
  }
}
async function sendOriginal(p: CatalogCartAdd, n: number, firstSend = false) {
  if (JSON.stringify(pendingCatalogAdd()) !== JSON.stringify(p))
    throw new ApiError("上次添加记录已变化，请重新读取", 409);
  rejected.value = false;
  try {
    await addMemberCart(p.skuId, p.count, p.expectedVersion);
  } catch (e) {
    if (
      firstSend &&
      n === epoch &&
      e instanceof ApiError &&
      !e.outcomeUnknown &&
      [400, 403, 404, 409, 429].includes(e.code)
    )
      rejected.value = true;
    throw e;
  }
  if (n !== epoch) return;
  notice.value = "商品已加入购物车。";
  clearCatalogAdd(p);
  pending.value = null;
  try {
    await cartState(n);
  } catch (e) {
    if (n === epoch) {
      fail(e, true);
      notice.value = "添加已提交，购物车数量暂未刷新，请进入购物车核对。";
    }
  }
}
async function add() {
  if (!canAdd.value) return;
  const selected = sku.value!,
    product = detail.value!,
    count = quantity.value;
  await modifying(async (n) => {
    const cart = await cartState(n);
    if (!cart || n !== epoch) return;
    const old = cart.lines.find((l) => l.skuId === selected.id),
      sum = (old?.count || 0) + count;
    if (
      !cart.canPurchase ||
      sum > cart.maxItemQuantity ||
      sum > selected.stock ||
      (!old && cart.lines.length >= cart.maxCartLines)
    )
      throw new ApiError(
        "当前数量、库存或购物车状态不允许添加，请重新核对",
        409,
      );
    const p = {
      spuId: product.id,
      skuId: selected.id,
      count,
      expectedVersion: cart.version,
    };
    saveCatalogAdd(p);
    pending.value = p;
    observedCart.value = null;
    await sendOriginal(p, n, true);
  });
}
async function queryAdd(retry = false) {
  if (!pending.value) return;
  const p = pending.value;
  await modifying(async (n) => {
    const cart = await cartState(n);
    if (!cart || n !== epoch) return;
    if (cart.version < p.expectedVersion)
      throw new ApiError("购物车版本与原添加记录不一致，请保留记录并核查", 409);
    notice.value =
      cart.version > p.expectedVersion
        ? "购物车已有变化，请核对当前数量。不能仅凭版本变化判断这次添加成功。"
        : "原添加结果尚未确认，可继续同一添加请求。";
    if (retry && cart.version === p.expectedVersion) await sendOriginal(p, n);
  });
}
function acknowledge() {
  if (!canAcknowledge.value || !pending.value) return;
  try {
    clearCatalogAdd(pending.value);
    pending.value = null;
    rejected.value = false;
    writeError.value = "";
    notice.value = "本次核对已结束，可继续选购。";
  } catch (e) {
    fail(e, true);
  }
}
watch(
  [() => route.fullPath, memberIdentity, () => credentials().accessToken],
  () => {
    epoch++;
    reads++;
    loading.value = false;
    busy.value = false;
    store.value = null;
    rows.value = [];
    detail.value = null;
    options.value = null;
    badImages.value = new Set();
    pending.value = null;
    observedCart.value = null;
    rejected.value = false;
    storageBlocked.value = false;
    writeError.value = "";
    notice.value = "";
    emit("count", null);
    if (signedIn.value)
      try {
        pending.value = pendingCatalogAdd();
      } catch (e) {
        storageBlocked.value = true;
        fail(e, true);
      }
    void reload();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  epoch++;
  reads++;
  emit("count", null);
});
</script>

<template>
  <main class="member-products" :aria-busy="loading || busy">
    <header class="catalog-heading">
      <div>
        <RouterLink v-if="isDetail" class="text-link" :to="`${base}/products`"
          >返回全部商品</RouterLink
        >
        <h1>
          {{
            isHome
              ? store?.name || context.tenant
              : isDetail
                ? "商品详情"
                : searching
                  ? "搜索结果"
                  : "全部商品"
          }}
        </h1>
        <p v-if="!isDetail && !loading && !readError">
          {{
            isHome
              ? "在售商品"
              : searching
                ? `“${query.keyword}” · 共 ${total} 件商品`
                : `共 ${total} 件商品`
          }}
        </p>
      </div>
      <div class="catalog-actions">
        <RouterLink v-if="isHome" class="text-link" :to="`${base}/products`"
          >查看全部商品</RouterLink
        ><RouterLink class="text-link" :to="`${base}/bag`">购物车</RouterLink
        ><button class="button" :disabled="busy || loading" @click="reload">
          {{ loading ? "读取中…" : searching ? "重新搜索" : "刷新商品" }}
        </button>
        <button
          v-if="searching"
          class="button"
          :disabled="busy || loading"
          @click="clearSearch"
        >
          清除关键词
        </button>
      </div>
    </header>
    <p v-if="readError" class="form-error" role="alert">{{ readError }}</p>
    <p v-if="optionError" class="catalog-help" role="status">
      {{ optionError }}
    </p>
    <p v-if="writeError" class="form-error" role="alert">{{ writeError }}</p>
    <p v-if="notice" class="catalog-notice" role="status">{{ notice }}</p>
    <section
      v-if="pending"
      class="cart-pending"
      aria-labelledby="cart-pending-title"
    >
      <h2 id="cart-pending-title">核对上次添加</h2>
      <p>
        商品 {{ pending.spuId }} · 规格 {{ pending.skuId }} · 添加
        {{ pending.count }} 件
      </p>
      <p v-if="observedCart">
        当前购物车中此规格有
        {{ observedLine?.count || 0 }} 件。请核对后再继续选购。
      </p>
      <div class="catalog-actions">
        <button class="button" :disabled="busy || loading" @click="queryAdd()">
          读取购物车核对</button
        ><button
          v-if="
            !observedCart || observedCart.version === pending.expectedVersion
          "
          class="button"
          :disabled="busy || loading"
          @click="queryAdd(true)"
        >
          继续原添加请求</button
        ><RouterLink class="text-link" :to="`${base}/bag`"
          >查看购物车</RouterLink
        ><RouterLink
          v-if="String(route.params.id) !== String(pending.spuId)"
          class="text-link"
          :to="`${base}/product/${pending.spuId}`"
          >查看原商品</RouterLink
        ><button v-if="canAcknowledge" class="button" @click="acknowledge">
          已核对，继续选购
        </button>
      </div>
    </section>

    <form
      v-if="!isHome && !isDetail"
      class="catalog-filters"
      @submit.prevent="applyFilters"
    >
      <fieldset :disabled="loading || busy">
        <label
          >关键词<input
            v-model="draft.keyword"
            maxlength="64"
            type="search"
            placeholder="搜索商品、品牌或分类" /></label
        ><label
          >分类<select v-model="draft.category" :disabled="!options">
            <option value="">全部分类</option>
            <option
              v-for="c in options?.categories || []"
              :key="c.id"
              :value="String(c.id)"
            >
              {{ categoryName(c.id) }}
            </option>
          </select></label
        ><label
          >品牌<select v-model="draft.brand" :disabled="!options">
            <option value="">全部品牌</option>
            <option
              v-for="b in options?.brands || []"
              :key="b.id"
              :value="String(b.id)"
            >
              {{ b.name }}
            </option>
          </select></label
        ><label
          >最低起价 / 元<input
            v-model="draft.min"
            inputmode="decimal"
            placeholder="不限" /></label
        ><label
          >最高起价 / 元<input
            v-model="draft.max"
            inputmode="decimal"
            placeholder="不限" /></label
        ><label
          >排序<select v-model="draft.sort">
            <option value="default">
              {{ draft.keyword.trim() ? "相关度排序" : "默认排序" }}
            </option>
            <option value="priceAsc">起价从低到高</option>
            <option value="priceDesc">起价从高到低</option>
            <option value="salesDesc">销量从高到低</option>
            <option value="newest">最新商品</option>
          </select></label
        ><button class="shop-button" type="submit">
          {{ draft.keyword.trim() ? "搜索商品" : "筛选商品" }}
        </button>
      </fieldset>
    </form>
    <p v-if="loading" role="status">正在读取商品…</p>
    <template v-else-if="!isDetail">
      <div v-if="shownRows.length" class="catalog-grid">
        <article v-for="p in shownRows" :key="p.id" class="catalog-item">
          <RouterLink class="catalog-picture" :to="`${base}/product/${p.id}`"
            ><img
              v-if="image(p.picUrl)"
              :src="image(p.picUrl)"
              :alt="p.name"
              loading="lazy"
              referrerpolicy="no-referrer"
              @error="badImages.add(image(p.picUrl))"
            /><span v-else class="picture-missing"
              >暂无商品图片</span
            ></RouterLink
          >
          <p class="catalog-category">
            {{ p.brandName }} · {{ p.categoryName }}
          </p>
          <h2>
            <RouterLink :to="`${base}/product/${p.id}`">{{
              p.name
            }}</RouterLink>
          </h2>
          <p class="catalog-introduction">{{ p.introduction }}</p>
          <p class="catalog-price">
            ¥{{ money(p.price) }}<small v-if="p.specType"> 起</small
            ><span v-if="p.stock === 0" class="catalog-help">暂时售罄</span>
          </p>
        </article>
      </div>
      <Empty v-else-if="!readError" title="没有找到相关商品" icon="search"
        ><p>可以调整筛选条件，或稍后再来看看。</p></Empty
      >
      <nav
        v-if="!isHome && total > 20"
        class="catalog-actions catalog-pages"
        aria-label="商品分页"
      >
        <button
          class="button"
          :disabled="busy || query.pageNo <= 1"
          @click="page(-1)"
        >
          上一页</button
        ><span>第 {{ query.pageNo }} 页 · 共 {{ total }} 项</span
        ><button
          class="button"
          :disabled="
            busy || query.pageNo * 20 >= total || query.pageNo >= maxPage
          "
          @click="page(1)"
        >
          下一页
        </button>
      </nav>
      <p
        v-if="searching && !readError && total > 10000"
        class="catalog-help"
        role="status"
      >
        搜索最多展示前 10000 件商品，请增加关键词或缩小分类、品牌和价格范围。
      </p>
    </template>
    <template v-else-if="detail">
      <section class="catalog-detail">
        <div class="product-gallery">
          <div class="detail-picture">
            <img
              v-if="image(pictureUrl)"
              :src="image(pictureUrl)"
              :alt="detail.name"
              referrerpolicy="no-referrer"
              @error="badImages.add(image(pictureUrl))"
            /><span v-else class="picture-missing">暂无商品图片</span>
          </div>
          <div v-if="detail.sliderPicUrls.length > 1" class="picture-options">
            <button
              v-for="(url, i) in detail.sliderPicUrls"
              :key="i"
              :class="{ selected: picture === i }"
              :aria-label="`查看第 ${i + 1} 张商品图片`"
              :aria-pressed="picture === i"
              @click="picture = i"
            >
              <img
                v-if="image(url)"
                :src="image(url)"
                alt=""
                loading="lazy"
                referrerpolicy="no-referrer"
                @error="badImages.add(image(url))"
              /><span v-else>{{ i + 1 }}</span>
            </button>
          </div>
        </div>
        <div class="product-purchase">
          <p class="catalog-category">
            {{ detail.brandName }} · {{ detail.categoryName }}
          </p>
          <h2>{{ detail.name }}</h2>
          <p>{{ detail.introduction }}</p>
          <p class="detail-price">
            ¥{{ money(sku?.price || detail.price)
            }}<small v-if="!sku && detail.specType"> 起</small>
          </p>
          <p v-if="sku && sku.marketPrice > sku.price" class="catalog-help">
            市场价 <s>¥{{ money(sku.marketPrice) }}</s>
          </p>
          <form @submit.prevent="add">
            <fieldset :disabled="busy || !!pending || storageBlocked">
              <label
                >商品规格<select v-model="skuId" required>
                  <option :value="null" disabled>请选择规格</option>
                  <option v-for="s in detail.skus" :key="s.id" :value="s.id">
                    {{
                      detail.specType
                        ? s.properties
                            .map((p) => `${p.propertyName}：${p.valueName}`)
                            .join(" / ")
                        : "默认规格"
                    }}
                    · ¥{{ money(s.price)
                    }}{{ s.stock === 0 ? " · 暂时售罄" : "" }}
                  </option>
                </select></label
              ><label
                >数量<input
                  v-model.number="quantity"
                  type="number"
                  min="1"
                  :max="Math.min(sku?.stock || 0, 9999)"
                  step="1"
                  required
              /></label>
              <p v-if="sku" class="catalog-help">当前库存 {{ sku.stock }} 件</p>
              <p v-if="!detail.canPurchase" class="catalog-help">
                当前商品暂不可加入购物车。
              </p>
              <button
                v-if="signedIn"
                class="shop-button"
                type="submit"
                :disabled="!canAdd"
              >
                {{ busy ? "处理中…" : "加入购物车" }}</button
              ><RouterLink v-else :to="login" class="shop-button"
                >登录后加入购物车</RouterLink
              >
            </fieldset>
          </form>
          <p class="catalog-help">运费及可用优惠将在结算时计算。</p>
        </div>
      </section>
      <section class="product-description">
        <h2>商品详情</h2>
        <dl v-if="detail.displayProperties.length" class="display-properties">
          <div v-for="(p, i) in detail.displayProperties" :key="i">
            <dt>{{ p.propertyName }}</dt>
            <dd>{{ p.valueName }}</dd>
          </div>
        </dl>
        <div class="description-text">{{ detail.description }}</div>
      </section>
    </template>
  </main>
</template>

<style scoped>
.member-products {
  max-width: 1280px;
  margin: 0 auto;
  padding: 36px 32px 72px;
  color: var(--text);
}
.catalog-heading,
.catalog-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.catalog-heading {
  justify-content: space-between;
  margin-bottom: 28px;
}
h1 {
  font-size: clamp(26px, 4vw, 36px);
  margin: 8px 0;
}
h2 {
  font-size: 20px;
  margin: 12px 0;
}
p {
  line-height: 1.7;
  margin: 10px 0;
  overflow-wrap: anywhere;
}
.catalog-heading p,
.catalog-help,
.catalog-category,
.catalog-introduction {
  color: var(--muted);
}
.catalog-help {
  font-size: 14px;
}
.catalog-notice,
.cart-pending {
  background: var(--surface);
  border: 1px solid var(--border);
  padding: 18px 22px;
  border-radius: 12px;
  margin-bottom: 24px;
}
.cart-pending h2 {
  margin-top: 0;
}
.catalog-filters {
  padding: 24px 0;
  margin-bottom: 24px;
  border-block: 1px solid var(--border);
}
fieldset {
  border: 0;
  padding: 0;
  margin: 0;
}
.catalog-filters fieldset {
  display: grid;
  grid-template-columns: 2fr repeat(2, minmax(120px, 1fr));
  gap: 18px;
  align-items: end;
}
label {
  display: block;
  font-size: 14px;
}
input,
select {
  display: block;
  width: 100%;
  margin-top: 8px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text);
  font: inherit;
}
.catalog-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 36px 24px;
}
.catalog-item {
  min-width: 0;
}
.catalog-item h2 {
  font-size: 17px;
  line-height: 1.5;
  margin: 8px 0;
}
.catalog-item a {
  color: inherit;
  text-decoration: none;
}
.catalog-picture {
  display: flex;
  aspect-ratio: 4/5;
  align-items: center;
  justify-content: center;
  background: var(--surface-soft, #f4f6f5);
  overflow: hidden;
}
.catalog-picture img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.picture-missing {
  color: var(--muted);
  font-size: 14px;
}
.catalog-category {
  font-size: 13px;
  margin-top: 16px;
}
.catalog-introduction {
  font-size: 14px;
}
.catalog-price,
.detail-price {
  color: var(--primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.catalog-price {
  font-size: 20px;
}
.catalog-price small,
.detail-price small {
  font-size: 13px;
  font-weight: 400;
}
.catalog-price .catalog-help {
  margin-left: 12px;
}
.catalog-pages {
  justify-content: center;
  margin-top: 36px;
}
.catalog-detail {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: 56px;
}
.detail-picture {
  display: flex;
  aspect-ratio: 4/5;
  background: var(--surface-soft, #f4f6f5);
  align-items: center;
  justify-content: center;
}
.detail-picture img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.picture-options {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 12px 2px;
}
.picture-options button {
  width: 64px;
  height: 80px;
  flex-shrink: 0;
  padding: 2px;
  border: 1px solid var(--border);
  background: var(--surface);
}
.picture-options button.selected {
  border: 2px solid var(--primary);
}
.picture-options img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.product-purchase h2 {
  font-size: clamp(24px, 3vw, 32px);
}
.detail-price {
  font-size: 32px;
  margin: 24px 0 12px;
}
.product-purchase form {
  margin-top: 28px;
}
.product-purchase label {
  margin: 20px 0;
}
.product-purchase input[type="number"] {
  max-width: 140px;
}
.product-purchase .shop-button {
  margin-top: 12px;
}
.product-description {
  border-top: 1px solid var(--border);
  margin-top: 48px;
  padding-top: 28px;
}
.display-properties {
  max-width: 640px;
}
.display-properties div {
  display: grid;
  grid-template-columns: minmax(80px, 1fr) 3fr;
  gap: 20px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
}
.display-properties dd {
  margin: 0;
}
.description-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
  max-width: 75ch;
  margin-top: 28px;
}
:is(button, a, input, select):focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}
button:disabled,
fieldset:disabled {
  opacity: 0.6;
}
button:disabled {
  cursor: not-allowed;
}
@media (max-width: 960px) {
  .catalog-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .catalog-detail {
    gap: 28px;
  }
  .catalog-filters fieldset {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .member-products {
    padding: 24px 16px 48px;
  }
  .catalog-heading {
    align-items: flex-start;
  }
  .catalog-detail {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  .catalog-grid {
    gap: 28px 14px;
  }
  .catalog-filters fieldset {
    grid-template-columns: 1fr;
  }
  .catalog-actions > :is(button, a) {
    min-height: 44px;
    display: inline-flex;
    align-items: center;
  }
}
</style>
