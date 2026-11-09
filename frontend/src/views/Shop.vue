<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import SeckillShop from "../components/SeckillShop.vue";
import MemberSeckillPanel from "../components/MemberSeckillPanel.vue";
import MemberProductPanel from "../components/MemberProductPanel.vue";
import MemberAccountPanel from "../components/MemberAccountPanel.vue";
import MemberCouponPanel from "../components/MemberCouponPanel.vue";
import MemberCartPanel from "../components/MemberCartPanel.vue";
import MemberCheckoutPanel from "../components/MemberCheckoutPanel.vue";
import MemberOrdersPanel from "../components/MemberOrdersPanel.vue";
import MemberAfterSalesPanel from "../components/MemberAfterSalesPanel.vue";
import MemberPaymentPanel from "../components/MemberPaymentPanel.vue";
import Icon from "../components/Icon.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import {
  db,
  command,
  refreshQuote,
  serverQuote,
  loadPage,
  ui,
  brand,
  activeTenant,
  currentMember,
  tenantProducts,
  attempt,
  addToCart,
  quote,
  checkout,
  payOrder,
  cancelOrder,
  receiveOrder,
  claimCoupon,
  applyAfterSale,
  saveAddress,
  returnShipment,
  notify,
  log,
} from "../data/store";
import {
  cancelSignIn,
  demo,
  signIn,
  signOut,
  identities,
} from "../api/session";
import { sendMemberCode, submitMemberAccount } from "../api/member-account";
import { safeReturn } from "../api/policy";
import { ApiError, request } from "../api/http";
import {
  money,
  refundable,
  unitRefund,
  timestamp,
  type Product,
  type Order,
  type AfterSale,
  type Address,
} from "../domain";
const route = useRoute(),
  router = useRouter(),
  page = computed(() => String(route.params.page || "home")),
  base = computed(() => `/shop/${ui.tenant}`),
  search = ref(String(route.query.q || "")),
  category = ref(String(route.query.category || "")),
  sort = ref("推荐"),
  color = ref(""),
  size = ref(""),
  quantity = ref(1),
  addressEditor = ref(false),
  addressId = ref<number>(),
  couponId = ref<number | null>(null),
  applyOrder = ref<Order>(),
  applyItem = ref(0),
  refundQuantity = ref(1),
  refundAmount = ref(0),
  refundType = ref<AfterSale["type"]>("仅退款"),
  refundReason = ref("尺码不合适"),
  returnCase = ref<AfterSale>(),
  returnTracking = ref(""),
  confirmCancel = ref<Order>(),
  confirmReceive = ref<Order>(),
  addressEditingId = ref<number>(),
  addressError = ref(""),
  loginError = ref(""),
  loginNotice = ref(""),
  loginMode = ref<"登录" | "注册" | "找回密码">("登录"),
  loginPhone = ref(""),
  loginPassword = ref(""),
  orderTab = ref("全部"),
  loginBusy = ref(false),
  verificationCode = ref(""),
  codeBusy = ref(false),
  codeWait = ref(0),
  quoteError = ref("");
let authRevision = 0;
let codeTimer: ReturnType<typeof setInterval> | undefined;
function stopCodeTimer() {
  if (codeTimer !== undefined) clearInterval(codeTimer);
  codeTimer = undefined;
}
onBeforeUnmount(() => {
  authRevision++;
  cancelSignIn("member");
  stopCodeTimer();
  loginPassword.value = "";
  verificationCode.value = "";
});
const addressForm = reactive({
  name: "",
  phone: "",
  region: "浙江省 杭州市 西湖区",
  detail: "",
  isDefault: false,
});
const selectedProduct = computed(() =>
  db.products.find(
    (p) =>
      p.id === Number(route.params.id) &&
      p.tenant === ui.tenant &&
      p.status === "上架中",
  ),
);
const selectedSku = computed(() =>
  selectedProduct.value?.skus?.find(
    (s) => s.spec === `${color.value} / ${size.value}`,
  ),
);
const selectedOrder = computed(() =>
  db.orders.find(
    (o) =>
      o.id === route.params.id &&
      o.tenant === ui.tenant &&
      o.memberId === ui.memberId,
  ),
);
const products = computed(() => {
  let rows = tenantProducts.value.filter(
    (p) =>
      p.status === "上架中" &&
      (!search.value ||
        p.name.includes(search.value) ||
        p.category.includes(search.value)) &&
      (!category.value || p.category === category.value),
  );
  if (sort.value === "价格从低到高")
    rows = [...rows].sort((a, b) => a.price - b.price);
  if (sort.value === "价格从高到低")
    rows = [...rows].sort((a, b) => b.price - a.price);
  if (sort.value === "热销") rows = [...rows].sort((a, b) => b.sales - a.sales);
  return rows;
});
const cart = computed(() =>
  db.cart
    .filter((l) => l.tenant === ui.tenant && l.memberId === ui.memberId)
    .map((l) => ({
      ...l,
      product: db.products.find(
        (p) => p.id === l.productId && p.tenant === ui.tenant,
      )!,
      unitPrice:
        db.products
          .find((p) => p.id === l.productId && p.tenant === ui.tenant)
          ?.skus?.find((s) => s.spec === l.spec)?.price || 0,
    }))
    .filter((l) => l.product),
);
const realCartCount = ref<number | null>(null);
const cartCount = computed(() =>
  demo ? cart.value.reduce((n, l) => n + l.quantity, 0) : realCartCount.value,
);
const addresses = computed(() =>
  db.addresses.filter(
    (a) => a.tenant === ui.tenant && a.memberId === ui.memberId,
  ),
);
const orders = computed(() =>
  db.orders.filter(
    (o) =>
      o.tenant === ui.tenant &&
      o.memberId === ui.memberId &&
      (orderTab.value === "全部" || o.status === orderTab.value),
  ),
);
const afters = computed(() =>
  db.afterSales.filter(
    (a) =>
      a.tenant === ui.tenant &&
      db.orders.some((o) => o.id === a.orderId && o.memberId === ui.memberId),
  ),
);
const couponTemplates = computed(() =>
  db.coupons.filter((c) => c.tenant === ui.tenant),
);
const ownedCoupons = computed(() =>
  db.ownedCoupons.filter(
    (c) => c.tenant === ui.tenant && c.memberId === ui.memberId,
  ),
);
const quoteResult = computed(() => {
  try {
    if (!demo && page.value === "checkout") {
      if (!serverQuote.value)
        return { data: null, error: quoteError.value || "正在确认订单金额…" };
      return {
        data: { ...serverQuote.value, items: quote(couponId.value).items },
        error: "",
      };
    }
    return { data: quote(couponId.value), error: "" };
  } catch (e) {
    return { data: null, error: (e as Error).message };
  }
});
const availableCoupons = computed(() =>
  ownedCoupons.value
    .filter((o) => !o.orderId)
    .map((o) =>
      db.coupons.find((c) => c.id === o.couponId && c.tenant === ui.tenant)!,
    )
    .filter(Boolean),
);
const categories = computed(() => [
  ...new Set(
    tenantProducts.value
      .filter((p) => p.status === "上架中")
      .map((p) => p.category),
  ),
]);
watch(
  () => route.fullPath,
  () => {
    authRevision++;
    cancelSignIn("member");
    loginPassword.value = "";
    verificationCode.value = "";
    loginError.value = "";
    loginNotice.value = "";
    stopCodeTimer();
    codeWait.value = 0;
    color.value = "";
    size.value = "";
    quantity.value = 1;
    category.value = String(route.query.category || "");
    search.value = String(route.query.q || "");
    addressId.value =
      addresses.value.find((a) => a.isDefault)?.id || addresses.value[0]?.id;
    couponId.value = null;
  },
  { immediate: true },
);
function goSearch() {
  router.push({ path: `${base.value}/products`, query: { q: search.value } });
}
function buy(p: Product, now = false) {
  attempt(async () => {
    if (!identities.member) {
      await router.push({
        path: `${base.value}/login`,
        query: { redirect: route.fullPath },
      });
      return;
    }
    if (!color.value || !size.value) throw new Error("请选择颜色和尺码");
    await addToCart(p, `${color.value} / ${size.value}`, quantity.value);
    if (now) router.push(`${base.value}/checkout`);
  });
}
function updateCart(productId: number, spec: string, value: number) {
  const line = db.cart.find(
    (l) =>
      l.tenant === ui.tenant &&
      l.memberId === ui.memberId &&
      l.productId === productId &&
      l.spec === spec,
  );
  const p = db.products.find(
    (p) => p.id === productId && p.tenant === ui.tenant,
  );
  if (!line || !p) return;
  if (
    !Number.isInteger(value) ||
    value < 1 ||
    value > (p.skus?.find((s) => s.spec === spec)?.stock || 0)
  ) {
    notify("数量超出可购买范围", true);
    return;
  }
  void attempt(() =>
    command("cart.quantity", { productId, spec, quantity: value }, () => {
      line.quantity = value;
    }),
  );
}
function removeCart(productId: number, spec: string) {
  void attempt(() =>
    command("cart.remove", { productId, spec }, () => {
      db.cart = db.cart.filter(
        (l) =>
          !(
            l.tenant === ui.tenant &&
            l.memberId === ui.memberId &&
            l.productId === productId &&
            l.spec === spec
          ),
      );
    }),
  );
}
function checkCart(productId: number, spec: string, checked: boolean) {
  void attempt(() =>
    command("cart.select", { productId, spec, checked }, () => {
      const line = db.cart.find(
        (l) =>
          l.tenant === ui.tenant &&
          l.memberId === ui.memberId &&
          l.productId === productId &&
          l.spec === spec,
      );
      if (line) line.checked = checked;
    }),
  );
}
function openAddress(a?: Address) {
  addressEditingId.value = a?.id;
  Object.assign(
    addressForm,
    a || {
      name: currentMember.value?.name || "",
      phone: currentMember.value?.phone || "",
      region: "浙江省 杭州市 西湖区",
      detail: "",
      isDefault: !addresses.value.length,
    },
  );
  addressError.value = "";
  addressEditor.value = true;
}
async function submitAddress() {
  try {
    await saveAddress(addressForm, addressEditingId.value);
    addressEditor.value = false;
    addressId.value = addresses.value.at(-1)?.id;
  } catch (e) {
    addressError.value = (e as Error).message;
  }
}
function placeOrder() {
  attempt(async () => {
    const id = await checkout(addressId.value || 0, couponId.value);
    router.push(`${base.value}/payment/${id}`);
  });
}
function openRefund(o: Order, index = 0) {
  applyOrder.value = o;
  applyItem.value = index;
  refundQuantity.value = 1;
  refundAmount.value = unitRefund(o.items[index]!, 1) / 100;
  refundType.value = "仅退款";
  refundReason.value = "尺码不合适";
}
function updateRefund() {
  if (applyOrder.value)
    refundAmount.value =
      unitRefund(
        applyOrder.value.items[applyItem.value]!,
        refundQuantity.value,
      ) / 100;
}
async function login() {
  if (loginBusy.value || codeBusy.value) return;
  const revision = authRevision;
  const mode = loginMode.value;
  const tenant = ui.tenant;
  loginBusy.value = true;
  loginError.value = "";
  loginNotice.value = "";
  try {
    if (import.meta.env.MODE === "prototype") {
      if (loginMode.value !== "登录")
        throw new Error("演示环境使用预置账号，不创建真实账号");
      const { demoSignIn } = await import("../data/demo-auth");
      demoSignIn("member", ui.tenant, loginPhone.value, loginPassword.value);
    } else if (mode !== "登录") {
      await submitMemberAccount(
        mode === "注册" ? "register" : "reset-password",
        {
          tenant,
          mobile: loginPhone.value,
          password: loginPassword.value,
          code: verificationCode.value,
        },
      );
      if (revision !== authRevision || tenant !== ui.tenant) return;
      loginMode.value = "登录";
      loginNotice.value =
        mode === "注册"
          ? "注册成功，请使用手机号和密码登录"
          : "密码已重置，请重新登录";
      return;
    } else {
      await signIn("member", {
        tenant,
        username: loginPhone.value,
        password: loginPassword.value,
      });
    }
    if (revision !== authRevision || tenant !== ui.tenant) return;
    loginPassword.value = "";
    await router.replace(safeReturn(route.query.redirect, "member", ui.tenant));
  } catch (e) {
    if (revision !== authRevision || tenant !== ui.tenant) return;
    if (e instanceof ApiError && mode !== "登录" && e.outcomeUnknown)
      loginMode.value = "登录";
    loginError.value =
      e instanceof ApiError && mode !== "登录" && e.outcomeUnknown
        ? "操作结果待确认，请先使用设置的密码登录，不要重复提交"
        : e instanceof ApiError && mode !== "登录" && e.code === 401
          ? "验证码无效、过期或已使用，请重新申请"
          : e instanceof ApiError && mode !== "登录" && e.code === 409
            ? "该账号暂时无法操作，请尝试登录或联系管理员"
            : e instanceof Error
              ? e.message
              : "操作未完成";
  } finally {
    loginPassword.value = "";
    verificationCode.value = "";
    loginBusy.value = false;
  }
}
function changeLoginMode(mode: "登录" | "注册" | "找回密码") {
  if (loginBusy.value || codeBusy.value) return;
  loginMode.value = mode;
  loginPassword.value = "";
  verificationCode.value = "";
  loginError.value = "";
  loginNotice.value = "";
}
function waitForCode() {
  stopCodeTimer();
  const deadline = Date.now() + 60_000;
  codeWait.value = 60;
  codeTimer = setInterval(() => {
    codeWait.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
    if (!codeWait.value) stopCodeTimer();
  }, 1000);
}
async function sendCode() {
  if (
    codeBusy.value ||
    codeWait.value ||
    loginBusy.value ||
    loginMode.value === "登录"
  )
    return;
  const revision = authRevision;
  const tenant = ui.tenant;
  const purpose = loginMode.value === "注册" ? "register" : "reset-password";
  codeBusy.value = true;
  loginError.value = "";
  loginNotice.value = "";
  try {
    await sendMemberCode(tenant, loginPhone.value, purpose);
    if (revision !== authRevision || tenant !== ui.tenant) return;
    waitForCode();
    loginNotice.value = "验证码发送请求已受理，请查看短信";
  } catch (e) {
    if (revision !== authRevision || tenant !== ui.tenant) return;
    if (e instanceof ApiError && (e.outcomeUnknown || e.code === 429))
      waitForCode();
    loginError.value =
      e instanceof ApiError && e.outcomeUnknown
        ? "短信发送结果待确认，请稍后查看，勿立即重发"
        : e instanceof Error
          ? e.message
          : "验证码发送未完成";
  } finally {
    codeBusy.value = false;
  }
}
async function logout() {
  await attempt(async () => {
    await signOut("member");
    await router.replace(`${base.value}/login`);
  });
}
watch(
  [addressId, couponId, () => JSON.stringify(db.cart)],
  async () => {
    if (!demo || page.value !== "checkout") return;
    quoteError.value = "";
    try {
      await refreshQuote(addressId.value, couponId.value);
    } catch (e) {
      quoteError.value = (e as Error).message;
    }
  },
  { immediate: true },
);
const session = reactive(ui);
</script>
<template>
  <div class="storefront">
    <div v-if="demo" class="shop-announcement">
      {{ brand.announcement }}<span>QINGHE COLLECTION</span>
    </div>
    <header class="shop-header">
      <RouterLink class="shop-logo" :to="base"
        >{{ demo ? brand.logo : ui.tenant
        }}<span v-if="demo">{{
          session.tenant === "qinghe" ? "QINGHE" : "SHANYE"
        }}</span></RouterLink
      >
      <nav class="shop-nav" aria-label="商城导航">
        <RouterLink :to="base" :class="{ active: page === 'home' }"
          >首页</RouterLink
        ><RouterLink
          :to="`${base}/products`"
          :class="{ active: page === 'products' }"
          >全部商品</RouterLink
        ><RouterLink
          v-if="demo && session.tenant === 'qinghe'"
          :to="`${base}/products?category=针织`"
          >秋日针织</RouterLink
        ><RouterLink
          :to="`${base}/seckill`"
          :class="{ active: page.startsWith('seckill') }"
          >限量上新</RouterLink
        ><RouterLink :to="`${base}/coupons`">会员礼遇</RouterLink>
      </nav>
      <div class="shop-header-actions">
        <form class="shop-search" @submit.prevent="goSearch">
          <input
            v-model="search"
            aria-label="搜索商城商品"
            placeholder="寻找喜欢的那一件"
          /><button class="icon-button" aria-label="搜索" type="submit">
            <Icon name="search" />
          </button>
        </form>
        <RouterLink
          class="icon-button"
          :to="`${base}/account`"
          aria-label="我的账户"
          ><Icon name="users" /></RouterLink
        ><RouterLink
          class="icon-button bag-link"
          :to="`${base}/bag`"
          aria-label="购物袋"
          ><Icon name="bag" /><span v-if="cartCount">{{
            cartCount
          }}</span></RouterLink
        >
      </div>
    </header>
    <div
      v-if="activeTenant && activeTenant.status !== '正常'"
      class="shop-closed"
    >
      店铺暂未营业。<RouterLink :to="`${base}/orders`">查看已有订单</RouterLink>
    </div>
    <SeckillShop v-if="demo && page.startsWith('seckill')" />
    <MemberSeckillPanel v-else-if="!demo && page.startsWith('seckill')" />
    <MemberProductPanel
      v-else-if="!demo && ['home', 'products', 'product'].includes(page)"
      @count="realCartCount = $event"
    />
    <main v-else-if="page === 'home'" class="shop-home">
      <section class="shop-hero">
        <div class="hero-copy">
          <span class="collection-label">AUTUMN / WINTER 2026</span>
          <h1>
            {{
              session.tenant === "qinghe"
                ? "把舒适，\n穿进日常。"
                : "山野好味，\n新鲜抵达。"
            }}
          </h1>
          <p>
            {{
              session.tenant === "qinghe"
                ? "柔软的触感，恰到好处的廓形。\n为每一个平常的日子，认真挑选。"
                : "精选自然好食材，\n把山野的馈赠，带到你的餐桌。"
            }}
          </p>
          <RouterLink class="shop-button" :to="`${base}/products`"
            >{{ session.tenant === "qinghe" ? "发现秋日衣橱" : "发现山野好物"
            }}<Icon name="arrow" :size="17"
          /></RouterLink>
          <div class="hero-note">
            <span />{{
              session.tenant === "qinghe"
                ? "自在穿着 · 长久陪伴"
                : "自然原味 · 新鲜烘焙"
            }}
          </div>
        </div>
        <div class="hero-image">
          <img
            :src="
              session.tenant === 'qinghe'
                ? '/images/hero.jpg'
                : '/images/nuts.jpg'
            "
            :alt="session.tenant === 'qinghe' ? '秋日衣橱精选' : '山野原味坚果'"
          /><span class="hero-image-label">THE EVERYDAY COLLECTION</span>
        </div>
      </section>
      <section class="shop-products-section">
        <div class="shop-section-heading">
          <div>
            <span class="collection-label">SELECTED FOR YOU</span>
            <h2>日常精选</h2>
          </div>
          <RouterLink :to="`${base}/products`"
            >探索全部<Icon name="arrow" :size="17"
          /></RouterLink>
        </div>
        <div class="product-grid">
          <RouterLink
            v-for="p in products.slice(0, 4)"
            :key="p.id"
            class="shop-product-card"
            :to="`${base}/product/${p.id}`"
            ><div class="product-photo">
              <img :src="p.image" :alt="p.name" loading="lazy" /><span
                v-if="p.id === 102"
                class="photo-label"
                >秋日上新</span
              ><span class="photo-arrow"><Icon name="arrow" /></span>
            </div>
            <div class="shop-product-info">
              <h3>{{ p.name }}</h3>
              <span>¥{{ money(p.price) }}</span>
            </div>
            <p>{{ p.subtitle }}</p>
            <div class="color-dots">
              <i
                v-for="(c, i) in p.colors"
                :key="c"
                :style="{
                  background: ['#e1ded5', '#566159', '#b6bcb8'][i % 3],
                }"
                :title="c"
              /></div
          ></RouterLink>
        </div>
      </section>
      <div class="shop-values">
        <div>
          <Icon name="leaf" /><strong>认真选材</strong
          ><span>细节，藏在每一次触碰里</span>
        </div>
        <div>
          <Icon name="truck" /><strong>满299元包邮</strong
          ><span>安心等待，美好抵达</span>
        </div>
        <div>
          <Icon name="heart" /><strong>用心相伴</strong
          ><span>每一件，都值得长久喜欢</span>
        </div>
      </div>
    </main>
    <main v-else-if="page === 'products'" class="shop-container">
      <div class="shop-page-heading">
        <span class="collection-label">THE COLLECTION</span>
        <h1>{{ category || "全部商品" }}</h1>
        <p v-if="search" class="muted">“{{ search }}” 的搜索结果</p>
      </div>
      <div class="shop-filters">
        <div>
          <button :class="{ active: !category }" @click="category = ''">
            全部</button
          ><button
            v-for="c in categories"
            :key="c"
            :class="{ active: category === c }"
            @click="category = c"
          >
            {{ c }}
          </button>
        </div>
        <select v-model="sort" aria-label="商品排序">
          <option>推荐</option>
          <option>热销</option>
          <option>价格从低到高</option>
          <option>价格从高到低</option>
        </select>
      </div>
      <div class="product-grid">
        <RouterLink
          v-for="p in products"
          :key="p.id"
          class="shop-product-card"
          :to="`${base}/product/${p.id}`"
          ><div class="product-photo">
            <img :src="p.image" :alt="p.name" /><span class="photo-arrow"
              ><Icon name="arrow"
            /></span>
          </div>
          <div class="shop-product-info">
            <h3>{{ p.name }}</h3>
            <span>¥{{ money(p.price) }}</span>
          </div>
          <p>{{ p.subtitle }}</p></RouterLink
        >
      </div>
      <Empty v-if="!products.length" title="没有找到相关商品" icon="search"
        ><button
          class="shop-button secondary"
          @click="
            category = '';
            search = '';
          "
        >
          查看全部商品
        </button></Empty
      >
    </main>
    <main v-else-if="page === 'product'" class="shop-container">
      <div v-if="selectedProduct" class="product-detail">
        <div class="detail-photo">
          <img :src="selectedProduct.image" :alt="selectedProduct.name" />
        </div>
        <div class="product-purchase">
          <div class="shop-breadcrumb">
            <RouterLink :to="`${base}/products`">全部商品</RouterLink
            ><Icon name="right" :size="12" />{{ selectedProduct.category }}
          </div>
          <h1>{{ selectedProduct.name }}</h1>
          <p class="muted">{{ selectedProduct.subtitle }}</p>
          <div class="detail-price">
            ¥{{ money(selectedSku?.price || selectedProduct.price) }}
          </div>
          <div class="variant-section">
            <label
              >颜色<span>{{ color }}</span></label
            >
            <div class="variant-options">
              <button
                v-for="c in selectedProduct.colors"
                :key="c"
                :class="{ active: color === c }"
                @click="color = c"
              >
                {{ c }}
              </button>
            </div>
          </div>
          <div class="variant-section">
            <label
              >尺码<span>{{ size }}</span></label
            >
            <div class="variant-options">
              <button
                v-for="s in selectedProduct.sizes"
                :key="s"
                :class="{ active: size === s }"
                @click="size = s"
              >
                {{ s }}
              </button>
            </div>
          </div>
          <div class="quantity-field">
            <span>数量</span>
            <div class="quantity-stepper">
              <button
                aria-label="减少数量"
                :disabled="quantity <= 1"
                @click="quantity--"
              >
                <Icon name="minus" :size="14" /></button
              ><input
                v-model.number="quantity"
                type="number"
                min="1"
                :max="selectedProduct.stock"
                aria-label="购买数量"
              /><button
                aria-label="增加数量"
                :disabled="quantity >= selectedProduct.stock"
                @click="quantity++"
              >
                <Icon name="plus" :size="14" />
              </button>
            </div>
            <small class="muted">{{
              color && size
                ? "该规格可售 " + (selectedSku?.stock || 0) + " 件"
                : "请选择颜色和尺码"
            }}</small>
          </div>
          <div class="purchase-actions">
            <button
              class="shop-button secondary"
              :disabled="
                !selectedProduct.stock || activeTenant?.status !== '正常'
              "
              @click="buy(selectedProduct)"
            >
              加入购物袋</button
            ><button
              class="shop-button"
              :disabled="
                !selectedProduct.stock || activeTenant?.status !== '正常'
              "
              @click="buy(selectedProduct, true)"
            >
              立即购买
            </button>
          </div>
          <div class="product-promises">
            <span><Icon name="truck" :size="16" />快递配送</span
            ><span><Icon name="refund" :size="16" />支持售后申请</span>
          </div>
          <details open>
            <summary>商品详情</summary>
            <p>
              {{
                selectedProduct.subtitle
              }}。精心选用舒适面料，兼顾日常穿着与轻松搭配。
            </p>
            <dl>
              <div>
                <dt>品牌</dt>
                <dd>{{ brand.name }}</dd>
              </div>
              <div>
                <dt>品类</dt>
                <dd>{{ selectedProduct.category }}</dd>
              </div>
            </dl>
          </details>
        </div>
      </div>
      <Empty v-else title="商品暂不可购买"
        ><RouterLink class="shop-button" :to="`${base}/products`"
          >继续选购</RouterLink
        ></Empty
      >
    </main>
    <MemberCartPanel
      v-else-if="!demo && page === 'bag'"
      @count="realCartCount = $event"
    />
    <main v-else-if="page === 'bag'" class="shop-container">
      <div class="shop-page-heading">
        <h1>
          购物袋<span class="muted">（{{ cartCount }}）</span>
        </h1>
      </div>
      <Empty v-if="!cart.length" title="购物袋还是空的" icon="bag"
        ><RouterLink class="shop-button" :to="`${base}/products`"
          >去挑选喜欢的商品</RouterLink
        ></Empty
      >
      <div v-else class="shopping-layout">
        <section class="bag-items">
          <div
            v-for="line in cart"
            :key="`${line.productId}-${line.spec}`"
            class="bag-item"
          >
            <input
              type="checkbox"
              :checked="line.checked"
              :aria-label="`结算${line.product.name}`"
              @change="
                checkCart(
                  line.productId,
                  line.spec,
                  ($event.target as HTMLInputElement).checked,
                )
              "
            /><img :src="line.product.image" :alt="line.product.name" />
            <div class="bag-description">
              <RouterLink :to="`${base}/product/${line.productId}`">{{
                line.product.name
              }}</RouterLink>
              <p>{{ line.spec }}</p>
              <strong>¥{{ money(line.unitPrice) }}</strong>
              <div class="quantity-stepper">
                <button
                  aria-label="减少商品数量"
                  :disabled="line.quantity <= 1"
                  @click="
                    updateCart(line.productId, line.spec, line.quantity - 1)
                  "
                >
                  <Icon name="minus" :size="13" /></button
                ><span>{{ line.quantity }}</span
                ><button
                  aria-label="增加商品数量"
                  @click="
                    updateCart(line.productId, line.spec, line.quantity + 1)
                  "
                >
                  <Icon name="plus" :size="13" />
                </button>
              </div>
            </div>
            <button
              class="icon-button bag-remove"
              :aria-label="`移除${line.product.name}`"
              @click="removeCart(line.productId, line.spec)"
            >
              <Icon name="close" :size="17" /></button
            ><strong class="bag-line-total"
              >¥{{ money(line.unitPrice * line.quantity) }}</strong
            >
          </div>
        </section>
        <aside class="order-summary">
          <h2>订单小计</h2>
          <div>
            <span>商品金额</span
            ><span>¥{{ money(quoteResult.data?.subtotal || 0) }}</span>
          </div>
          <p class="muted small">运费及优惠将在结算时确认</p>
          <p v-if="quoteResult.error" class="form-error">
            {{ quoteResult.error }}
          </p>
          <RouterLink :to="`${base}/checkout`" class="shop-button full-width"
            >去结算<Icon name="arrow" :size="17" /></RouterLink
          ><RouterLink class="continue-shopping" :to="`${base}/products`"
            >继续选购</RouterLink
          >
        </aside>
      </div>
    </main>
    <MemberCheckoutPanel v-else-if="!demo && page === 'checkout'" />
    <main v-else-if="page === 'checkout'" class="shop-container">
      <div class="shop-page-heading"><h1>确认订单</h1></div>
      <div v-if="cart.some((l) => l.checked)" class="shopping-layout">
        <section>
          <div class="checkout-block">
            <div class="shop-section-heading">
              <h2>收货地址</h2>
              <button class="text-link" @click="openAddress()">
                <Icon name="plus" :size="15" />添加地址
              </button>
            </div>
            <label
              v-for="a in addresses"
              :key="a.id"
              class="address-option"
              :class="{ selected: addressId === a.id }"
              ><input
                v-model="addressId"
                type="radio"
                :value="a.id"
                name="address"
              />
              <div>
                <strong
                  >{{ a.name }} <span>{{ a.phone }}</span
                  ><i v-if="a.isDefault" class="tag">默认</i></strong
                >
                <p>{{ a.region }} {{ a.detail }}</p>
              </div>
              <button
                type="button"
                class="text-link"
                @click.prevent="openAddress(a)"
              >
                编辑
              </button></label
            ><Empty v-if="!addresses.length" title="请添加收货地址" icon="pin"
              ><button class="button" @click="openAddress()">
                添加地址
              </button></Empty
            >
          </div>
          <div class="checkout-block">
            <h2>商品清单</h2>
            <div
              v-for="l in cart.filter((l) => l.checked)"
              :key="`${l.productId}-${l.spec}`"
              class="checkout-item"
            >
              <img :src="l.product.image" :alt="l.product.name" />
              <div>
                <strong>{{ l.product.name }}</strong
                ><small>{{ l.spec }} × {{ l.quantity }}</small>
              </div>
              <span>¥{{ money(l.unitPrice * l.quantity) }}</span>
            </div>
          </div>
          <div class="checkout-block">
            <h2>配送方式</h2>
            <div class="delivery-method">
              <Icon name="truck" />快递配送<span>以商家实际发货为准</span>
            </div>
          </div>
        </section>
        <aside class="order-summary">
          <h2>订单金额</h2>
          <label class="coupon-select"
            >优惠券<select v-model="couponId">
              <option :value="null">不使用优惠券</option>
              <option v-for="c in availableCoupons" :key="c.id" :value="c.id">
                {{ c.name }} · 满{{ c.threshold / 100 }}减{{ c.amount / 100 }}
              </option>
            </select></label
          >
          <div>
            <span>商品金额</span
            ><span>¥{{ money(quoteResult.data?.subtotal || 0) }}</span>
          </div>
          <div>
            <span>优惠券</span
            ><span class="green-text"
              >− ¥{{ money(quoteResult.data?.discount || 0) }}</span
            >
          </div>
          <div>
            <span>运费</span
            ><span>¥{{ money(quoteResult.data?.shipping || 0) }}</span>
          </div>
          <div class="summary-total">
            <span>应付合计</span
            ><strong>¥{{ money(quoteResult.data?.total || 0) }}</strong>
          </div>
          <p v-if="quoteResult.error" class="form-error" role="alert">
            {{ quoteResult.error }}
          </p>
          <button
            class="shop-button full-width"
            :disabled="!quoteResult.data || !addressId"
            @click="placeOrder"
          >
            提交订单
          </button>
        </aside>
      </div>
      <Empty v-else title="还没有选择商品" icon="bag"
        ><RouterLink class="shop-button" :to="`${base}/bag`"
          >返回购物袋</RouterLink
        ></Empty
      >
    </main>
    <MemberPaymentPanel v-else-if="!demo && page === 'payment'" />
    <main
      v-else-if="page === 'payment'"
      class="shop-container payment-container"
    >
      <section v-if="selectedOrder" class="payment-panel">
        <div class="payment-icon">
          <Icon
            :name="selectedOrder.status === '待付款' ? 'pay' : 'success'"
            :size="30"
          />
        </div>
        <h1>
          {{
            selectedOrder.status === "待付款"
              ? "完成付款"
              : selectedOrder.status === "已取消"
                ? "订单已取消"
                : "付款已确认"
          }}
        </h1>
        <p class="muted">订单 {{ selectedOrder.id }}</p>
        <strong class="payment-amount">¥{{ money(selectedOrder.total) }}</strong
        ><template v-if="selectedOrder.status === '待付款'"
          ><div class="payment-method">
            <Icon name="pay" />{{ demo ? "演示支付" : "在线支付"
            }}<Icon name="success" class="green-text" />
          </div>
          <p v-if="demo" class="payment-preview-note">
            演示环境，不会产生真实扣款。
          </p>
          <button
            v-if="selectedOrder.status === '待付款'"
            class="shop-button full-width"
            @click="attempt(() => payOrder(selectedOrder!.id))"
          >
            前往付款</button
          ><button v-if="!demo" class="continue-shopping" @click="loadPage()">
            已完成付款，查询结果
          </button></template
        ><RouterLink
          v-else
          class="shop-button full-width"
          :to="`${base}/order/${selectedOrder.id}`"
          >查看订单</RouterLink
        ><RouterLink class="continue-shopping" :to="`${base}/orders`"
          >返回我的订单</RouterLink
        >
      </section>
      <Empty v-else title="订单不存在" icon="order" />
    </main>
    <MemberOrdersPanel
      v-else-if="!demo && (page === 'orders' || page === 'order')"
    />
    <main
      v-else-if="page === 'orders' || page === 'order'"
      class="shop-container"
    >
      <div class="shop-page-heading">
        <h1>{{ page === "order" ? "订单详情" : "我的订单" }}</h1>
      </div>
      <nav v-if="page === 'orders'" class="shop-order-tabs">
        <button
          v-for="s in ['全部', '待付款', '待发货', '已发货', '已完成']"
          :key="s"
          :class="{ active: orderTab === s }"
          @click="orderTab = s"
        >
          {{ s }}</button
        ><RouterLink :to="`${base}/aftersales`">退货退款</RouterLink>
      </nav>
      <article
        v-for="o in page === 'order'
          ? selectedOrder
            ? [selectedOrder]
            : []
          : orders"
        :key="o.id"
        class="consumer-order"
      >
        <header>
          <div>
            <time>{{ o.created }}</time
            ><span>订单 {{ o.id }}</span>
          </div>
          <span class="green-text semibold">{{ o.status }}</span>
        </header>
        <div
          v-for="(item, index) in o.items"
          :key="index"
          class="consumer-order-item"
        >
          <img :src="item.image" :alt="item.name" />
          <div>
            <h3>{{ item.name }}</h3>
            <p>{{ item.spec }} × {{ item.quantity }}</p>
            <span v-if="item.refunded" class="small green-text"
              >已退 ¥{{ money(item.refunded) }}</span
            >
          </div>
          <strong
            >¥{{ money(item.price * item.quantity - item.discount) }}</strong
          ><button
            v-if="
              ['待发货', '已发货', '已完成'].includes(o.status) &&
              refundable(item) > 0
            "
            class="text-link"
            @click="openRefund(o, index)"
          >
            申请售后
          </button>
        </div>
        <div v-if="page === 'order'" class="consumer-order-detail">
          <div>
            <Icon name="pin" /><span
              >{{ o.buyer }} · {{ o.phone }}<br />{{ o.address }}</span
            >
          </div>
          <div v-if="o.tracking">
            <Icon name="truck" /><span>{{ o.carrier }} · {{ o.tracking }}</span>
          </div>
          <ol class="timeline">
            <li v-for="(h, i) in o.history" :key="i">{{ h }}</li>
          </ol>
        </div>
        <footer>
          <span
            >实付 <strong>¥{{ money(o.total) }}</strong
            ><small>（含运费 ¥{{ money(o.shipping) }}）</small></span
          >
          <div>
            <RouterLink
              v-if="page === 'orders'"
              class="button"
              :to="`${base}/order/${o.id}`"
              >订单详情</RouterLink
            ><button
              v-if="o.status === '待付款'"
              class="button"
              @click="confirmCancel = o"
            >
              取消订单</button
            ><RouterLink
              v-if="o.status === '待付款'"
              class="button primary"
              :to="`${base}/payment/${o.id}`"
              >去付款</RouterLink
            ><button
              v-if="o.status === '已发货'"
              class="button primary"
              @click="confirmReceive = o"
            >
              确认收货
            </button>
          </div>
        </footer>
      </article>
      <Empty
        v-if="
          (page === 'orders' && !orders.length) ||
          (page === 'order' && !selectedOrder)
        "
        title="暂无订单"
        icon="order"
        ><RouterLink class="shop-button" :to="`${base}/products`"
          >去逛逛</RouterLink
        ></Empty
      >
    </main>
    <MemberAfterSalesPanel v-else-if="!demo && page === 'aftersales'" />
    <main v-else-if="page === 'aftersales'" class="shop-container">
      <div class="shop-page-heading"><h1>退货退款</h1></div>
      <article v-for="a in afters" :key="a.id" class="consumer-order">
        <header>
          <span>{{ a.id }}</span
          ><span class="green-text">{{ a.status }}</span>
        </header>
        <div class="consumer-after-body">
          <h3>
            {{
              db.orders.find((o) => o.id === a.orderId)?.items[a.itemIndex]
                ?.name
            }}
          </h3>
          <p>{{ a.type }} · {{ a.quantity }} 件 · ¥{{ money(a.amount) }}</p>
          <p class="muted">{{ a.reason }}</p>
          <p v-if="a.tracking">退货物流 {{ a.tracking }}</p>
        </div>
        <footer>
          <span class="small muted">{{ a.created }}</span>
          <div>
            <button
              v-if="a.status === '待审核'"
              class="button"
              @click="
                a.status = '已撤销';
                notify('申请已撤销');
              "
            >
              撤销申请</button
            ><button
              v-if="a.status === '待买家退货'"
              class="button primary"
              @click="
                returnCase = a;
                returnTracking = '';
              "
            >
              填写退货物流</button
            ><RouterLink class="button" :to="`${base}/order/${a.orderId}`"
              >查看订单</RouterLink
            >
          </div>
        </footer>
      </article>
      <Empty v-if="!afters.length" title="暂无售后申请" icon="refund" />
    </main>
    <MemberCouponPanel v-else-if="!demo && page === 'coupons'" />
    <main v-else-if="page === 'coupons'" class="shop-container">
      <div class="shop-page-heading">
        <span class="collection-label">MEMBER BENEFITS</span>
        <h1>专属好礼，为你而来。</h1>
      </div>
      <div class="shop-coupon-grid">
        <article v-for="c in couponTemplates" :key="c.id" class="shop-coupon">
          <div class="coupon-value">
            <strong><small>¥</small>{{ c.amount / 100 }}</strong
            ><span>满{{ c.threshold / 100 }}元可用</span>
          </div>
          <div class="coupon-description">
            <h3>{{ c.name }}</h3>
            <p>{{ c.end }} 前可用</p>
            <p
              v-if="ownedCoupons.some((o) => o.couponId === c.id && o.orderId)"
              class="small muted"
            >
              已用于订单
            </p>
            <button
              v-if="!ownedCoupons.some((o) => o.couponId === c.id)"
              class="button primary"
              :disabled="c.status !== '发放中'"
              @click="
                attempt(async () => {
                  await claimCoupon(c.id);
                  notify('优惠券已领取');
                })
              "
            >
              立即领取</button
            ><RouterLink
              v-else-if="
                ownedCoupons.some((o) => o.couponId === c.id && !o.orderId)
              "
              class="button"
              :to="`${base}/products`"
              >去使用</RouterLink
            ><span v-else class="tag">已使用</span>
          </div>
        </article>
      </div>
      <Empty
        v-if="!couponTemplates.length"
        title="暂无可用优惠券"
        icon="coupon"
      />
    </main>
    <MemberAccountPanel
      v-else-if="!demo && (page === 'addresses' || page === 'account')"
      :mode="page === 'addresses' ? 'addresses' : 'account'"
    >
      <template #logout
        ><button class="text-link" @click="logout">退出登录</button></template
      >
    </MemberAccountPanel>
    <main v-else-if="page === 'addresses'" class="shop-container">
      <div class="shop-page-heading inline">
        <h1>收货地址</h1>
        <button class="shop-button" @click="openAddress()">
          <Icon name="plus" :size="17" />添加地址
        </button>
      </div>
      <div class="address-grid">
        <article v-for="a in addresses" :key="a.id" class="address-card">
          <div>
            <strong>{{ a.name }}</strong
            ><span v-if="a.isDefault" class="tag">默认地址</span>
          </div>
          <p>{{ a.phone }}</p>
          <p>{{ a.region }}<br />{{ a.detail }}</p>
          <button class="text-link" @click="openAddress(a)">编辑地址</button>
        </article>
      </div>
      <Empty v-if="!addresses.length" title="暂无收货地址" icon="pin" />
    </main>
    <main v-else-if="page === 'login'" class="shop-container consumer-login">
      <form @submit.prevent="login">
        <span class="collection-label">WELCOME TO {{ brand.logo }}</span>
        <h1>
          {{
            loginMode === "登录"
              ? "欢迎回来"
              : loginMode === "注册"
                ? "成为我们的新朋友"
                : "找回密码"
          }}
        </h1>
        <label
          >手机号<input
            v-model.trim="loginPhone"
            required
            inputmode="tel"
            autocomplete="tel"
            :disabled="loginBusy || codeBusy"
            pattern="1[3-9][0-9]{9}" /></label
        ><label
          >{{ loginMode === "找回密码" ? "新密码" : "密码"
          }}<input
            v-model="loginPassword"
            type="password"
            :minlength="loginMode === '登录' ? 1 : 8"
            maxlength="72"
            :disabled="loginBusy || codeBusy"
            required
            :autocomplete="
              loginMode === '登录' ? 'current-password' : 'new-password'
            "
        /></label>
        <template v-if="loginMode !== '登录'">
          <p class="muted">密码至少8个字符，最多72个UTF-8字节。</p>
          <label
            >短信验证码<input
              v-model.trim="verificationCode"
              inputmode="numeric"
              autocomplete="one-time-code"
              pattern="[0-9]{6}"
              maxlength="6"
              required
              :disabled="loginBusy || codeBusy"
          /></label>
          <button
            class="continue-shopping"
            type="button"
            :disabled="loginBusy || codeBusy || codeWait > 0"
            @click="sendCode"
          >
            {{
              codeBusy
                ? "正在发送…"
                : codeWait
                  ? `${codeWait}秒后可重发`
                  : "获取验证码"
            }}
          </button>
        </template>
        <p v-if="loginNotice" role="status">{{ loginNotice }}</p>
        <p v-if="loginError" class="form-error" role="alert">
          {{ loginError }}
        </p>
        <button
          class="shop-button full-width"
          :disabled="loginBusy || codeBusy"
        >
          {{ loginBusy ? "正在处理…" : loginMode }}</button
        ><button
          class="continue-shopping"
          type="button"
          :disabled="loginBusy || codeBusy"
          @click="changeLoginMode(loginMode === '登录' ? '注册' : '登录')"
        >
          {{
            loginMode === "登录" ? "还没有账号？立即注册" : "已有账号？返回登录"
          }}
        </button>
        <button
          v-if="loginMode === '登录'"
          class="continue-shopping"
          type="button"
          :disabled="loginBusy || codeBusy"
          @click="changeLoginMode('找回密码')"
        >
          忘记密码？
        </button>
      </form>
    </main>
    <main v-else class="shop-container account-page">
      <div class="account-hero">
        <div class="member-avatar large">
          {{ currentMember?.name.slice(-2) || "访客" }}
        </div>
        <div>
          <span class="collection-label">MY QINGHE</span>
          <h1>{{ currentMember?.name || "欢迎来到" + brand.logo }}</h1>
          <span class="muted">{{
            currentMember?.phone.replace(/(\d{3})\d{4}(\d{4})/, "$1****$2")
          }}</span>
        </div>
        <button v-if="currentMember" class="text-link" @click="logout">
          退出登录</button
        ><RouterLink v-else :to="`${base}/login`" class="text-link"
          >登录 / 注册</RouterLink
        >
      </div>
      <div class="account-links">
        <RouterLink :to="`${base}/orders`"
          ><Icon name="order" />
          <h3>我的订单</h3>
          <span
            >{{
              db.orders.filter(
                (o) =>
                  o.tenant === session.tenant &&
                  o.memberId === session.memberId,
              ).length
            }}
            笔</span
          ><Icon name="right" /></RouterLink
        ><RouterLink :to="`${base}/coupons`"
          ><Icon name="coupon" />
          <h3>我的优惠券</h3>
          <span>{{ availableCoupons.length }} 张可用</span
          ><Icon name="right" /></RouterLink
        ><RouterLink :to="`${base}/addresses`"
          ><Icon name="pin" />
          <h3>收货地址</h3>
          <Icon name="right" /></RouterLink
        ><RouterLink :to="`${base}/aftersales`"
          ><Icon name="refund" />
          <h3>退货退款</h3>
          <Icon name="right"
        /></RouterLink>
      </div>
      <section class="account-preferences">
        <h2>消息与偏好</h2>
        <label v-if="currentMember" class="preference-row"
          ><span
            ><strong>接收会员礼遇</strong
            ><small>新品上架、专属优惠和活动邀请</small></span
          ><input
            type="checkbox"
            role="switch"
            :checked="!currentMember.optOut"
            @change="
              attempt(async () => {
                const optOut = !($event.target as HTMLInputElement).checked;
                await command('member.preferences', { optOut }, () => {
                  currentMember!.optOut = optOut;
                });
                notify('消息偏好已更新');
              })
            "
        /></label>
        <div
          v-for="n in db.notifications.filter(
            (n) =>
              n.tenant === session.tenant && n.memberId === session.memberId,
          )"
          :key="n.id"
          class="account-notification"
        >
          <Icon name="mail" />
          <div>
            <strong>{{ n.title }}</strong>
            <p>{{ n.content }}</p>
          </div>
        </div>
      </section>
    </main>
    <footer v-if="demo" class="shop-footer">
      <div>
        <RouterLink class="shop-logo" :to="base"
          >{{ brand.logo
          }}<span>{{
            session.tenant === "qinghe" ? "QINGHE" : "SHANYE"
          }}</span></RouterLink
        >
        <p>{{ brand.tagline }}</p>
      </div>
      <div>
        <h3>购物与服务</h3>
        <RouterLink :to="`${base}/orders`">我的订单</RouterLink
        ><RouterLink :to="`${base}/aftersales`">退货退款</RouterLink
        ><RouterLink :to="`${base}/addresses`">收货地址</RouterLink>
      </div>
      <div>
        <h3>联系{{ brand.logo }}</h3>
        <span>{{ brand.phone }}</span
        ><span>周一至周日 09:00—18:00</span>
      </div>
      <div class="shop-copyright">
        © 2026 {{ brand.name }}<span>自在日常，长久喜欢。</span>
      </div>
    </footer>
    <footer v-else class="shop-footer">
      <div>
        <RouterLink class="shop-logo" :to="base">{{ ui.tenant }}</RouterLink>
      </div>
      <div>
        <h3>会员服务</h3>
        <RouterLink :to="`${base}/bag`">购物袋</RouterLink
        ><RouterLink :to="`${base}/coupons`">优惠券</RouterLink
        ><RouterLink :to="`${base}/addresses`">收货地址</RouterLink>
      </div>
    </footer>
  </div>
  <Modal
    v-if="addressEditor"
    :title="addressEditingId ? '编辑收货地址' : '添加收货地址'"
    @close="addressEditor = false"
    ><form id="address-form" @submit.prevent="submitAddress">
      <div class="form-grid">
        <label>收货人<input v-model.trim="addressForm.name" required /></label
        ><label
          >手机号<input
            v-model.trim="addressForm.phone"
            required
            pattern="1[0-9]{10}"
        /></label>
      </div>
      <label
        >省 / 市 / 区<input v-model.trim="addressForm.region" required /></label
      ><label
        >详细地址<textarea v-model.trim="addressForm.detail" required /></label
      ><label class="checkbox-label"
        ><input
          v-model="addressForm.isDefault"
          type="checkbox"
        />设为默认地址</label
      >
      <p v-if="addressError" class="form-error" role="alert">
        {{ addressError }}
      </p>
    </form>
    <template #footer
      ><button class="button" @click="addressEditor = false">取消</button
      ><button class="button primary" form="address-form" type="submit">
        保存地址
      </button></template
    ></Modal
  >
  <Modal
    v-if="demo && applyOrder"
    title="申请售后"
    @close="applyOrder = undefined"
    ><form
      id="refund-form"
      @submit.prevent="
        attempt(async () => {
          await applyAfterSale(
            applyOrder!.id,
            applyItem,
            refundQuantity,
            Math.round(refundAmount * 100),
            refundType,
            refundReason,
          );
          applyOrder = undefined;
          router.push(`${base}/aftersales`);
        })
      "
    >
      <div class="context-block">
        <strong>{{ applyOrder.items[applyItem]!.name }}</strong
        ><span>{{ applyOrder.items[applyItem]!.spec }}</span>
      </div>
      <label
        >售后类型<select v-model="refundType">
          <option>仅退款</option>
          <option v-if="applyOrder.status !== '待发货'">退货退款</option>
        </select></label
      >
      <div class="form-grid">
        <label
          >申请数量<input
            v-model.number="refundQuantity"
            type="number"
            min="1"
            :max="
              applyOrder.items[applyItem]!.quantity -
              applyOrder.items[applyItem]!.returnedQuantity
            "
            step="1"
            @change="updateRefund" /></label
        ><label
          >退款金额（元）<input
            v-model.number="refundAmount"
            type="number"
            min="0.01"
            step="0.01"
            :max="
              unitRefund(applyOrder.items[applyItem]!, refundQuantity) / 100
            "
            required
        /></label>
      </div>
      <label
        >申请原因<select v-model="refundReason">
          <option>尺码不合适</option>
          <option>商品质量问题</option>
          <option>不喜欢 / 不想要</option>
          <option>与描述不符</option>
        </select></label
      >
    </form>
    <template #footer
      ><button class="button" @click="applyOrder = undefined">取消</button
      ><button class="button primary" form="refund-form" type="submit">
        提交申请
      </button></template
    ></Modal
  >
  <Modal
    v-if="demo && returnCase"
    title="填写退货物流"
    @close="returnCase = undefined"
    ><form
      id="return-form"
      @submit.prevent="
        attempt(async () => {
          await returnShipment(returnCase!, returnTracking);
          returnCase = undefined;
        })
      "
    >
      <label
        >退货运单号<input
          v-model.trim="returnTracking"
          required
          minlength="6"
          maxlength="40"
      /></label>
    </form>
    <template #footer
      ><button class="button" @click="returnCase = undefined">取消</button
      ><button class="button primary" form="return-form" type="submit">
        保存物流
      </button></template
    ></Modal
  >
  <Modal
    v-if="confirmCancel"
    title="取消订单"
    @close="confirmCancel = undefined"
    ><p>确认取消订单 {{ confirmCancel.id }}？</p>
    <template #footer
      ><button class="button" @click="confirmCancel = undefined">
        暂不取消</button
      ><button
        class="button primary"
        @click="
          attempt(async () => {
            await cancelOrder(confirmCancel!.id);
            confirmCancel = undefined;
          })
        "
      >
        确认取消
      </button></template
    ></Modal
  >
  <Modal
    v-if="confirmReceive"
    title="确认收货"
    @close="confirmReceive = undefined"
    ><p>请确认已收到全部商品，且商品完好。</p>
    <template #footer
      ><button class="button" @click="confirmReceive = undefined">
        暂不确认</button
      ><button
        class="button primary"
        @click="
          attempt(async () => {
            await receiveOrder(confirmReceive!.id);
            confirmReceive = undefined;
          })
        "
      >
        确认收货
      </button></template
    ></Modal
  >
</template>
