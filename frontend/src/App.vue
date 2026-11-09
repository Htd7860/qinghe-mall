<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import Icon from "./components/Icon.vue";
import Modal from "./components/Modal.vue";
import Empty from "./components/Empty.vue";
import {
  ui,
  db,
  brand,
  activeTenant,
  can,
  attempt,
  clearData,
  loadPage,
  resolvePending,
} from "./data/store";
import { merchantHome, platformHome } from "./api/policy";
import { navigationItems } from "./api/management";
import { context, demo, identities, signOut } from "./api/session";
const route = useRoute(),
  router = useRouter(),
  mobileMenu = ref(false),
  notices = ref(false),
  globalSearch = ref("");
const platform = computed(() => !!route.meta.platform),
  shop = computed(() => !!route.meta.shop);
const denied = computed(
  () =>
    !ui.error &&
    !ui.loading &&
    !shop.value &&
    (!demo && route.meta.permission
      ? !identities[context.realm]?.permissions.includes(
          String(route.meta.permission),
        )
      : platform.value
        ? !identities.platform?.permissions.includes(
            String(route.path.split("/").at(-1)),
          )
        : !!route.meta.permission && !can(String(route.meta.permission))),
);
const groups = computed(() =>
  platform.value
    ? [
        {
          label: "平台管理",
          items: [
            ["tenants", "租户管理", "store"],
            ["seckill", "活动监管", "shield"],
            ["packages", "套餐管理", "layers"],
            ["domains", "商城入口", "globe"],
            ["audit", "平台日志", "shield"],
          ],
        },
      ]
    : [
        { label: "", items: [["overview", "经营概览", "dashboard", "order"]] },
        {
          label: "店铺经营",
          items: [
            ["products", "商品管理", "product", "product"],
            ["inventory", "库存管理", "boxes", "product"],
            ["orders", "订单管理", "order", "order"],
            ["aftersales", "退货退款", "refund", "order"],
          ],
        },
        {
          label: "客户与营销",
          items: [
            ["members", "会员管理", "users", "member"],
            ["segments", "会员分群", "filter", "marketing"],
            ["coupons", "优惠券", "coupon", "marketing"],
            ["campaigns", "营销活动", "campaign", "marketing"],
            ["seckill", "限量秒杀", "clock", "seckill.view"],
          ],
        },
        {
          label: "经营管理",
          items: [
            ["analytics", "经营分析", "chart", "order"],
            ["staff", "员工与权限", "shield", "staff"],
            ["shipping", "配送设置", "truck", "settings"],
            ["settings", "店铺设置", "settings", "settings"],
            ["operations", "处理记录", "refresh", "settings"],
            ["audit", "操作日志", "layers", "settings"],
          ],
        },
      ],
);
async function logout() {
  await attempt(async () => {
    await signOut();
    clearData();
    await router.replace(
      context.realm === "platform" ? "/platform/login" : "/admin/login",
    );
  });
}
const identity = computed(() => identities[context.realm]);
const unread = computed(
  () =>
    db.orders.filter((o) => o.tenant === ui.tenant && o.status === "待发货")
      .length +
    db.afterSales.filter((a) => a.tenant === ui.tenant && a.status === "待审核")
      .length,
);
const visibleGroups = computed(() => {
  if (!demo) {
    const base = platform.value ? "/platform/" : "/admin/";
    const known = new Set(router.getRoutes().map((r) => r.path));
    const items = navigationItems.value
      .filter((m) => m.type === 2)
      .flatMap((m) => {
        const path = m.path.startsWith("/") ? m.path : `${base}${m.path}`;
        if (!path.startsWith(base) || !known.has(path) || /[?#\\]/.test(path))
          return [];
        const id = path.slice(base.length);
        return [[id, m.name, m.icon || "layers", m.permission]];
      });
    return items.length
      ? [{ label: platform.value ? "平台管理" : "工作台", items }]
      : [];
  }
  return groups.value
    .map((group) => ({
      ...group,
      items: group.items.filter((item) =>
        platform.value
          ? identities.platform?.permissions.includes(item[0]!)
          : !item[3] || can(item[3]),
      ),
    }))
    .filter((group) => group.items.length);
});
const home = computed(() =>
  platform.value
    ? !demo
      ? platformHome(identities.platform?.permissions || [])
      : "/platform/tenants"
    : merchantHome(identities.merchant?.permissions || []),
);
const session = reactive(ui);
</script>
<template>
  <div v-if="demo" class="demo-watermark" role="note">
    演示环境 · 数据仅保存在本机
  </div>
  <div v-if="session.uncertain" class="operation-notice" role="alert">
    上一项操作结果待确认，请先查询，避免重复提交。<button
      class="button"
      :disabled="session.busy"
      @click="attempt(resolvePending)"
    >
      查询处理结果
    </button>
  </div>
  <div v-if="session.storageFailure" class="storage-notice" role="alert">
    浏览器存储空间不足，本次修改可能无法保留。
  </div>
  <template v-if="shop || route.meta.login || route.meta.bare"
    ><div v-if="session.loading" class="public-status" role="status">
      <Empty title="正在加载…" icon="refresh" />
    </div>
    <div
      v-else-if="session.error && !route.meta.login"
      class="public-status"
      role="alert"
    >
      <Empty :title="session.error" icon="alert"
        ><button
          class="button"
          @click="
            route.meta.managementKind ||
            route.meta.productCatalog ||
            route.meta.deliveryTemplates ||
            route.meta.productManagement ||
            route.meta.merchantOrders ||
            route.meta.merchantSeckill ||
            route.meta.couponTemplates ||
            (route.meta.shop &&
              [
                'login',
                'account',
                'addresses',
                'coupons',
                'bag',
                'checkout',
                'orders',
                'order',
                'payment',
              ].includes(String(route.params.page || 'home')))
              ? router.go(0)
              : loadPage()
          "
        >
          重新加载
        </button></Empty
      >
    </div>
    <fieldset
      v-else
      class="page-actions"
      :disabled="session.busy || !!session.uncertain"
    >
      <RouterView :key="String(route.params.tenant || route.path)" /></fieldset
  ></template>
  <div v-else class="workspace">
    <div
      v-if="mobileMenu"
      class="sidebar-overlay"
      @click="mobileMenu = false"
    />
    <aside class="sidebar" :class="{ open: mobileMenu }">
      <RouterLink class="platform-logo" :to="home"
        ><span class="logo-mark"><Icon name="bag" :size="22" /></span>
        <div>青禾<span>COMMERCE</span></div></RouterLink
      >
      <div class="store-identity">
        <div class="brand-avatar">
          {{ platform ? "青" : brand.logo.slice(0, 1) }}
        </div>
        <div>
          <strong>{{ platform ? "平台运营中心" : brand.name }}</strong
          ><span>{{ platform ? "平台管理员" : "独立品牌商城" }}</span>
        </div>
      </div>
      <nav class="main-nav" aria-label="主导航">
        <section v-for="group in visibleGroups" :key="group.label">
          <p v-if="group.label">{{ group.label }}</p>
          <template v-for="item in group.items" :key="item[0]"
            ><RouterLink
              v-if="
                !demo ||
                (platform
                  ? identities.platform?.permissions.includes(item[0]!)
                  : !item[3] || can(item[3]))
              "
              :to="`/${platform ? 'platform' : 'admin'}/${item[0]}`"
              @click="mobileMenu = false"
              ><Icon :name="item[2]!" /><span>{{ item[1] }}</span
              ><span
                v-if="
                  item[0] === 'aftersales' &&
                  db.afterSales.filter(
                    (a) => a.tenant === session.tenant && a.status === '待审核',
                  ).length
                "
                class="nav-counter"
                >{{
                  db.afterSales.filter(
                    (a) => a.tenant === session.tenant && a.status === "待审核",
                  ).length
                }}</span
              ></RouterLink
            ></template
          >
        </section>
      </nav>
      <div class="sidebar-foot">
        <span class="tiny-leaf"><Icon name="leaf" :size="15" /></span>
        <div>
          <strong>{{
            platform ? "青禾 Commerce" : activeTenant?.package
          }}</strong
          ><small>{{
            platform
              ? "品牌经营，从这里开始"
              : `有效期至 ${activeTenant?.expire}`
          }}</small>
        </div>
      </div>
    </aside>
    <div class="workspace-body">
      <header class="topbar">
        <div class="breadcrumbs">
          <button
            class="icon-button mobile-toggle"
            aria-label="展开导航"
            @click="mobileMenu = !mobileMenu"
          >
            <Icon name="menu" /></button
          ><span>{{ platform ? "平台" : "工作台" }}</span
          ><Icon name="right" :size="13" /><strong>{{
            route.meta.title
          }}</strong>
        </div>
        <div class="topbar-actions">
          <form
            v-if="!platform && can('product')"
            class="global-search"
            @submit.prevent="
              router.push({
                path: '/admin/products',
                query: { q: globalSearch },
              })
            "
          >
            <Icon name="search" :size="16" /><input
              v-model="globalSearch"
              aria-label="搜索商品"
              placeholder="搜索商品"
            />
          </form>
          <RouterLink
            v-if="!platform"
            class="store-link"
            :to="`/shop/${session.tenant}`"
            ><Icon name="external" :size="15" />查看商城</RouterLink
          ><button
            v-if="!platform && can('order')"
            class="icon-button notification-button"
            aria-label="待办通知"
            @click="notices = true"
          >
            <Icon name="bell" /><i v-if="unread" />
          </button>
          <div class="topbar-divider" />
          <div class="account-label">
            <span class="user-avatar">{{ identity?.name.slice(0, 1) }}</span
            ><span
              >{{ identity?.name
              }}<small class="muted">{{ identity?.role }}</small></span
            >
          </div>
          <button class="text-link" @click="logout">退出登录</button>
        </div>
      </header>
      <main class="main-content">
        <div v-if="denied" class="panel">
          <Empty title="没有访问权限" icon="shield"
            ><RouterLink class="button" :to="home"
              >返回工作台</RouterLink
            ></Empty
          >
        </div>
        <div
          v-else-if="session.loading"
          class="panel"
          role="status"
          aria-live="polite"
        >
          <Empty title="正在加载…" icon="refresh" />
        </div>
        <div v-else-if="session.error" class="panel" role="alert">
          <Empty :title="session.error" icon="alert"
            ><button
              class="button"
              @click="
                route.meta.managementKind ||
                route.meta.productCatalog ||
                route.meta.deliveryTemplates ||
                route.meta.productManagement ||
                route.meta.merchantOrders ||
                route.meta.merchantSeckill ||
                route.meta.couponTemplates
                  ? router.go(0)
                  : loadPage()
              "
            >
              重新加载
            </button></Empty
          >
        </div>
        <fieldset
          v-else
          class="page-actions"
          :disabled="session.busy || !!session.uncertain"
          :aria-busy="session.busy"
        >
          <RouterView :key="route.path" />
        </fieldset>
      </main>
      <footer class="workspace-footer">
        <span>青禾 Commerce</span><span>把生意，做得更从容。</span>
      </footer>
    </div>
  </div>
  <Transition name="toast"
    ><div
      v-if="session.toast"
      class="toast"
      :class="session.toastType"
      role="status"
    >
      <Icon :name="session.toastType === 'error' ? 'alert' : 'success'" />{{
        session.toast
      }}
    </div></Transition
  >
  <Modal v-if="notices" title="待办事项" @close="notices = false"
    ><div class="notice-list">
      <RouterLink to="/admin/orders?status=待发货" @click="notices = false"
        ><Icon name="truck" />
        <div>
          <strong
            >{{
              db.orders.filter(
                (o) => o.tenant === session.tenant && o.status === "待发货",
              ).length
            }}
            笔订单待发货</strong
          ><small>查看订单并安排发货</small>
        </div>
        <Icon name="right" /></RouterLink
      ><RouterLink to="/admin/aftersales" @click="notices = false"
        ><Icon name="refund" />
        <div>
          <strong
            >{{
              db.afterSales.filter(
                (a) => a.tenant === session.tenant && a.status === "待审核",
              ).length
            }}
            笔售后待审核</strong
          ><small>查看申请及退款金额</small>
        </div>
        <Icon name="right"
      /></RouterLink></div
  ></Modal>
</template>
