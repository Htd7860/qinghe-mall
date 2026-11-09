import { createRouter, createWebHashHistory } from "vue-router";
import { catalogSpec } from "./api/product-catalog";
import {
  db,
  ui,
  loadPage,
  clearData,
  bindIdentity,
  restorePending,
} from "./data/store";
import { context, demo, identities, restore } from "./api/session";
import { safeReturn, type Realm } from "./api/policy";
import {
  managementSpec,
  navigationItems,
  refreshNavigation,
} from "./api/management";
const dashboard = () => import("./views/Dashboard.vue");
const commerce = () => import("./views/Commerce.vue");
const customers = () => import("./views/Customers.vue");
const settings = () => import("./views/Settings.vue");
const platform = () => import("./views/Platform.vue");
const shop = () => import("./views/Shop.vue");
export const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    ...(!demo
      ? [
          {
            path: "/admin/shipping/templates",
            component: () => import("./views/DeliveryTemplates.vue"),
            meta: {
              title: "运费模板",
              permission: "trade:delivery:express-template:query",
              deliveryTemplates: true,
            },
          },
        ]
      : []),
    ...(!demo
      ? (["categories", "brands", "properties"] as const).map((kind) => ({
          path: `/admin/catalog/${kind}`,
          component: () => import("./views/ProductCatalog.vue"),
          meta: {
            title: catalogSpec[kind].title,
            permission: `product:${catalogSpec[kind].permission}:query`,
            productCatalog: kind,
          },
        }))
      : []),
    ...(!demo
      ? (["staff", "roles", "departments"] as const).map((kind) => ({
          path: `/admin/${kind}`,
          component: () => import("./views/Management.vue"),
          meta: {
            title: managementSpec[kind].title,
            permission: `${managementSpec[kind].permission}:query`,
            managementKind: kind,
          },
        }))
      : []),
    ...(!demo
      ? (
          [
            "staff",
            "roles",
            "departments",
            "tenants",
            "packages",
            "menus",
          ] as const
        ).map((kind) => ({
          path: `/platform/${kind}`,
          component: () => import("./views/Management.vue"),
          meta: {
            title: managementSpec[kind].title,
            permission: `${managementSpec[kind].permission}:query`,
            managementKind: kind,
            platform: true,
          },
        }))
      : []),
    {
      path: "/admin/seckill",
      component: demo
        ? () => import("./views/SeckillAdmin.vue")
        : () => import("./views/MerchantSeckill.vue"),
      meta: {
        title: "限量秒杀",
        permission: demo ? "seckill.view" : "promotion:seckill-activity:query",
        merchantSeckill: !demo,
      },
    },
    {
      path: "/platform/seckill",
      component: demo
        ? () => import("./views/SeckillAdmin.vue")
        : () => import("./views/PlatformSeckill.vue"),
      meta: {
        title: "活动监管",
        platform: true,
        permission: demo ? undefined : "promotion:seckill-operation:query",
        platformSeckill: !demo,
      },
    },
    { path: "/", redirect: "/admin/overview" },
    {
      path: "/admin/login",
      component: () => import("./views/Login.vue"),
      meta: { title: "商家登录", login: true },
    },
    {
      path: "/platform/login",
      component: () => import("./views/Login.vue"),
      meta: { title: "平台登录", login: true, platform: true },
    },
    {
      path: "/admin/overview",
      component: dashboard,
      meta: { title: "经营概览", permission: "order" },
    },
    {
      path: "/admin/products",
      component: demo
        ? commerce
        : () => import("./views/ProductManagement.vue"),
      meta: {
        title: "商品管理",
        permission: demo ? "product" : "product:spu:query",
        productManagement: !demo,
      },
    },
    {
      path: "/admin/inventory",
      component: commerce,
      meta: { title: "库存管理", permission: "product" },
    },
    {
      path: "/admin/orders",
      component: demo ? commerce : () => import("./views/MerchantOrders.vue"),
      meta: {
        title: "订单管理",
        permission: demo ? "order" : "trade:order:query",
        merchantOrders: !demo,
      },
    },
    {
      path: demo ? "/admin/aftersales" : "/admin/after-sales",
      alias: demo ? [] : ["/admin/aftersales"],
      component: demo
        ? commerce
        : () => import("./views/MerchantAfterSales.vue"),
      meta: {
        title: "退货退款",
        permission: demo ? "order" : "trade:after-sale:query",
        merchantAfterSales: !demo,
      },
    },
    {
      path: "/admin/members",
      component: customers,
      meta: { title: "会员管理", permission: "member" },
    },
    {
      path: "/admin/segments",
      component: customers,
      meta: { title: "会员分群", permission: "marketing" },
    },
    {
      path: "/admin/coupons",
      component: demo ? customers : () => import("./views/CouponTemplates.vue"),
      meta: {
        title: "优惠券模板",
        permission: demo ? "marketing" : "promotion:coupon-template:query",
        couponTemplates: !demo,
      },
    },
    {
      path: "/admin/campaigns",
      component: customers,
      meta: { title: "营销活动", permission: "marketing" },
    },
    {
      path: "/admin/analytics",
      component: dashboard,
      meta: { title: "经营分析", permission: "order" },
    },
    ...(demo
      ? [
          {
            path: "/admin/staff",
            component: settings,
            meta: { title: "员工与权限", permission: "staff" },
          },
        ]
      : []),
    {
      path: "/admin/settings",
      component: settings,
      meta: { title: "店铺设置", permission: "settings" },
    },
    {
      path: "/admin/shipping",
      component: settings,
      meta: { title: "配送设置", permission: "settings" },
    },
    {
      path: "/admin/operations",
      component: settings,
      meta: { title: "处理记录", permission: "settings" },
    },
    {
      path: "/admin/audit",
      component: settings,
      meta: { title: "操作日志", permission: "settings" },
    },
    ...(demo
      ? ["tenants", "packages", "domains", "audit"]
      : ["domains", "audit"]
    ).map((name) => ({
      path: `/platform/${name}`,
      component: platform,
      meta: {
        title: (
          {
            tenants: "租户管理",
            packages: "套餐管理",
            domains: "商城入口",
            audit: "平台日志",
          } as Record<string, string>
        )[name],
        platform: true,
      },
    })),
    {
      path: "/shop/:tenant/:page?/:id?",
      component: shop,
      meta: { title: "青禾服饰", shop: true },
    },
    {
      path: "/:pathMatch(.*)*",
      component: () => import("./views/NotFound.vue"),
      meta: { title: "页面不存在", bare: true },
    },
  ],
});
router.beforeEach(async (to) => {
  document.title = `${String(to.meta.title || "青禾")} · 青禾 Commerce`;
  clearData();
  const realm: Realm = to.meta.shop
    ? "member"
    : to.meta.platform
      ? "platform"
      : "merchant";
  context.realm = realm;
  context.tenant = to.meta.shop ? String(to.params.tenant) : "";
  const login =
    realm === "member"
      ? `/shop/${context.tenant}/login`
      : realm === "platform"
        ? "/platform/login"
        : "/admin/login";
  const shopPage = String(to.params.page || "home");
  const loginPage = !!to.meta.login || (!!to.meta.shop && shopPage === "login");
  const publicPage =
    !!to.meta.shop &&
    ["home", "products", "product", "coupons", "login", "seckill"].includes(
      shopPage,
    );
  if (to.meta.bare) {
    document.title = "页面不存在 · 青禾 Commerce";
    return;
  }
  if (to.meta.shop && !/^[a-z0-9][a-z0-9-]{2,31}$/.test(context.tenant))
    return "/not-found";
  if (loginPage) {
    bindIdentity();
    if (to.meta.shop && demo)
      await loadPage("login", `?store=${encodeURIComponent(context.tenant)}`);
    return;
  }
  try {
    const identity = await restore(
      realm,
      realm === "member" ? context.tenant : undefined,
    );
    if (!identity && !publicPage)
      return {
        path: login,
        query: { redirect: safeReturn(to.fullPath, realm, context.tenant) },
      };
    if (
      demo &&
      to.meta.shop &&
      !db.tenants.some((t) => t.code === context.tenant)
    )
      return "/not-found";
    bindIdentity();
    restorePending();
    // These pages now own their concrete member API reads, not the unimplemented generic projection.
    if (
      !demo &&
      to.meta.shop &&
      (shopPage.startsWith("seckill") ||
        [
          "home",
          "products",
          "product",
          "account",
          "addresses",
          "coupons",
          "bag",
          "checkout",
          "orders",
          "aftersales",
          "order",
          "payment",
        ].includes(shopPage))
    )
      return;
    if (!demo && realm !== "member") {
      navigationItems.value = [];
      try {
        await refreshNavigation();
      } catch (e) {
        ui.error = e instanceof Error ? e.message : "导航读取失败，请重新登录";
        return;
      }
    }
    if (
      !demo &&
      (to.meta.managementKind ||
        to.meta.productCatalog ||
        to.meta.deliveryTemplates ||
        to.meta.productManagement ||
        to.meta.merchantOrders ||
        to.meta.merchantAfterSales ||
        to.meta.merchantSeckill ||
        to.meta.platformSeckill ||
        to.meta.couponTemplates)
    )
      return;
    await loadPage(
      to.meta.shop ? shopPage : String(to.path.split("/").at(-1)),
      `?store=${encodeURIComponent(ui.tenant)}${to.params.id ? `&id=${encodeURIComponent(String(to.params.id))}` : ""}`,
    );
  } catch (e) {
    identities[realm] = null;
    if (!publicPage) return { path: login, query: { unavailable: "1" } };
    ui.error = e instanceof Error ? e.message : "连接失败，请稍后重试";
  }
  document.title = `${String(to.meta.title || "青禾")} · 青禾 Commerce`;
});
window.addEventListener("commerce:unauthorized", () => {
  const to = router.currentRoute.value;
  clearData();
  navigationItems.value = [];
  const path =
    context.realm === "member"
      ? `/shop/${context.tenant}/login`
      : context.realm === "platform"
        ? "/platform/login"
        : "/admin/login";
  void router.replace({
    path,
    query: { redirect: safeReturn(to.fullPath, context.realm, context.tenant) },
  });
});
// Revalidate grants while the page is visible; the server still authorizes every request.
let checkingSession = false;
setInterval(async () => {
  if (
    demo ||
    document.visibilityState !== "visible" ||
    !identities[context.realm] ||
    ui.busy ||
    checkingSession
  )
    return;
  const realm = context.realm,
    tenant = context.tenant;
  checkingSession = true;
  try {
    const identity = await restore(
      realm,
      realm === "member" ? tenant : undefined,
    );
    if (context.realm !== realm || context.tenant !== tenant) return;
    if (!identity) window.dispatchEvent(new Event("commerce:unauthorized"));
    else if (realm !== "member") await refreshNavigation();
  } catch {
    if (context.realm === realm && context.tenant === tenant) {
      clearData();
      ui.error = "无法确认账号权限，请刷新后重试";
    }
  } finally {
    checkingSession = false;
  }
}, 5000);
