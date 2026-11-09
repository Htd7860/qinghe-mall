<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import Icon from "../components/Icon.vue";
import {
  db,
  ui,
  tenantOrders,
  tenantProducts,
  brand,
  overviewReport,
  dashboardRange,
  loadPage,
  can,
} from "../data/store";
import { demo } from "../api/session";
import { money } from "../domain";
const route = useRoute(),
  router = useRouter(),
  range = demo ? ref(7) : dashboardRange;
async function setRange(value: number) {
  range.value = value;
  if (!demo) await loadPage();
}
const asOf = computed(() =>
  demo
    ? "2026-10-19"
    : overviewReport.value?.asOf.slice(0, 10) ||
      new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Shanghai" }),
);
const paid = computed(() =>
  tenantOrders.value.filter((o) => !["待付款", "已取消"].includes(o.status)),
);
const revenue = computed(() =>
  demo
    ? paid.value.reduce((s, o) => s + o.total, 0)
    : overviewReport.value?.paymentAmount || 0,
);
const refunds = computed(() =>
  demo
    ? db.afterSales
        .filter((a) => a.tenant === ui.tenant && a.status === "退款成功")
        .reduce((s, a) => s + a.amount, 0)
    : overviewReport.value?.refundAmount || 0,
);
const pending = computed(() =>
  demo
    ? tenantOrders.value.filter((o) => o.status === "待发货").length
    : overviewReport.value?.pendingShip || 0,
);
const afterPending = computed(() =>
  demo
    ? db.afterSales.filter(
        (a) => a.tenant === ui.tenant && a.status === "待审核",
      ).length
    : overviewReport.value?.pendingAfterSale || 0,
);
const topProducts = computed(() =>
  [...tenantProducts.value].sort((a, b) => b.sales - a.sales).slice(0, 5),
);
const days = computed(() =>
  !demo
    ? (overviewReport.value?.trend || []).map((d) => ({
        label: d.date.slice(5).replace("-", "/"),
        value: d.amount,
      }))
    : Array.from({ length: range.value }, (_, i) => {
        const day = new Date("2026-10-19T12:00:00+08:00");
        day.setDate(day.getDate() - range.value + 1 + i);
        const key = day.toLocaleDateString("sv-SE");
        return {
          label: key.slice(5).replace("-", "/"),
          value: paid.value
            .filter((o) => o.created.startsWith(key))
            .reduce((s, o) => s + o.total, 0),
        };
      }),
);
const ceiling = computed(
  () => Math.max(10000, ...days.value.map((d) => d.value)) * 1.15,
);
const points = computed(() =>
  days.value
    .map(
      (d, i) =>
        `${56 + i * (644 / (days.value.length - 1))},${205 - (d.value / ceiling.value) * 170}`,
    )
    .join(" "),
);
const area = computed(() => `56,205 ${points.value} 700,205`);
const stats = computed(() => [
  {
    label: "支付金额",
    value: `¥${money(revenue.value)}`,
    note: `${demo ? paid.value.length : overviewReport.value?.paidCount || 0} 笔已支付订单`,
    icon: "pay",
  },
  {
    label: "支付订单",
    value: demo ? paid.value.length : overviewReport.value?.paidCount || 0,
    note: "按支付成功订单统计",
    icon: "order",
  },
  {
    label: "退款金额",
    value: `¥${money(refunds.value)}`,
    note: `${demo ? db.afterSales.filter((a) => a.tenant === ui.tenant && a.status === "退款成功").length : overviewReport.value?.refundCount || 0} 笔退款已完成`,
    icon: "refund",
  },
  {
    label: "会员人数",
    value: demo
      ? db.members.filter((m) => m.tenant === ui.tenant).length
      : overviewReport.value?.memberCount || 0,
    note: "店铺累计会员",
    icon: "users",
  },
]);
const session = reactive(ui);
</script>
<template>
  <PageHeader :title="String(route.meta.title)" :eyebrow="brand.name"
    ><div class="date-label"><Icon name="calendar" :size="16" />{{ asOf }}</div>
    <RouterLink
      v-if="can('product')"
      class="button primary"
      to="/admin/products?create=1"
      ><Icon name="plus" :size="16" />添加商品</RouterLink
    ></PageHeader
  >
  <div class="stats-strip">
    <article v-for="stat in stats" :key="stat.label">
      <div class="stat-label">
        {{ stat.label }}<Icon :name="stat.icon" :size="17" />
      </div>
      <strong>{{ stat.value }}</strong
      ><small>{{ stat.note }}</small>
    </article>
  </div>
  <div class="dashboard-grid">
    <section class="panel trend-panel">
      <div class="panel-heading">
        <div>
          <h2>交易趋势</h2>
          <span class="muted small">支付金额</span>
        </div>
        <div class="segmented">
          <button :class="{ active: range === 7 }" @click="setRange(7)">
            近7天</button
          ><button :class="{ active: range === 30 }" @click="setRange(30)">
            近30天
          </button>
        </div>
      </div>
      <div class="chart-summary">
        <strong>¥{{ money(revenue) }}</strong
        ><span class="legend"><i />支付金额</span>
      </div>
      <svg
        class="trend-chart"
        viewBox="0 0 740 255"
        role="img"
        :aria-label="`近${range}天支付金额趋势`"
      >
        <defs>
          <linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="#17634c" stop-opacity=".13" />
            <stop offset="1" stop-color="#17634c" stop-opacity=".01" />
          </linearGradient>
        </defs>
        <g v-for="(value, i) in [1, 0.75, 0.5, 0.25, 0]" :key="i">
          <line
            x1="56"
            x2="705"
            :y1="35 + i * 42.5"
            :y2="35 + i * 42.5"
            stroke="#edf0ee"
            stroke-dasharray="4 4"
          />
          <text x="45" :y="39 + i * 42.5" text-anchor="end">
            {{ Math.round((ceiling * value) / 100).toLocaleString() }}
          </text>
        </g>
        <polygon :points="area" fill="url(#chart-fill)" />
        <polyline
          :points="points"
          fill="none"
          stroke="#24775a"
          stroke-width="2.5"
          stroke-linejoin="round"
        />
        <g v-for="(day, i) in days" :key="day.label">
          <circle
            :cx="56 + i * (644 / (days.length - 1))"
            :cy="205 - (day.value / ceiling) * 170"
            r="3.4"
            fill="#fff"
            stroke="#24775a"
            stroke-width="2"
          >
            <title>{{ day.label }} ¥{{ money(day.value) }}</title>
          </circle>
          <text
            v-if="range === 7 || i % 5 === 0 || i === days.length - 1"
            :x="56 + i * (644 / (days.length - 1))"
            y="236"
            text-anchor="middle"
          >
            {{ day.label }}
          </text>
        </g>
      </svg>
    </section>
    <section class="panel todo-panel">
      <div class="panel-heading">
        <h2>待办事项</h2>
        <span class="dot-live" />
      </div>
      <button
        class="todo-row"
        @click="router.push('/admin/orders?status=待发货')"
      >
        <span class="todo-icon"><Icon name="truck" /></span
        ><span>待发货订单</span><strong>{{ pending }}</strong
        ><Icon name="right" :size="16" /></button
      ><button class="todo-row" @click="router.push('/admin/aftersales')">
        <span class="todo-icon amber"><Icon name="refund" /></span
        ><span>待审核售后</span><strong>{{ afterPending }}</strong
        ><Icon name="right" :size="16" /></button
      ><button class="todo-row" @click="router.push('/admin/inventory')">
        <span class="todo-icon"><Icon name="boxes" /></span
        ><span>库存不足商品</span
        ><strong>{{
          demo
            ? tenantProducts.filter((p) => p.stock < 20).length
            : overviewReport?.lowStock || 0
        }}</strong
        ><Icon name="right" :size="16" /></button
      ><button class="todo-row" @click="router.push('/admin/operations')">
        <span class="todo-icon blue"><Icon name="refresh" /></span
        ><span>需要重新处理</span
        ><strong>{{
          demo
            ? db.jobs.filter(
                (j) => j.tenant === session.tenant && j.status === "待重试",
              ).length
            : overviewReport?.failedJobs || 0
        }}</strong
        ><Icon name="right" :size="16" />
      </button>
      <div class="quick-entry">
        <span>常用操作</span>
        <div>
          <RouterLink to="/admin/coupons"
            ><Icon name="coupon" />创建优惠券</RouterLink
          ><RouterLink to="/admin/campaigns"
            ><Icon name="campaign" />发起活动</RouterLink
          >
        </div>
      </div>
    </section>
  </div>
  <div class="dashboard-bottom">
    <section class="panel">
      <div class="panel-heading">
        <h2>热销商品</h2>
        <RouterLink class="text-link" to="/admin/products"
          >全部商品<Icon name="right" :size="14"
        /></RouterLink>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th class="narrow">排名</th>
              <th>商品</th>
              <th class="numeric">售价</th>
              <th class="numeric">累计销量</th>
              <th class="numeric">可售库存</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(p, i) in topProducts" :key="p.id">
              <td>
                <span class="ranking" :class="{ top: i < 3 }">{{
                  String(i + 1).padStart(2, "0")
                }}</span>
              </td>
              <td>
                <RouterLink
                  class="product-cell"
                  :to="`/admin/products?q=${encodeURIComponent(p.name)}`"
                  ><img :src="p.image" :alt="p.name" />
                  <div>
                    <strong>{{ p.name }}</strong
                    ><small
                      >{{ p.category }} ·
                      {{ p.colors.length * p.sizes.length }} 个规格</small
                    >
                  </div></RouterLink
                >
              </td>
              <td class="numeric">¥{{ money(p.price) }}</td>
              <td class="numeric">{{ p.sales }}</td>
              <td class="numeric">{{ p.stock }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section class="panel activity-panel">
      <div class="panel-heading">
        <h2>最近动态</h2>
        <RouterLink class="text-link" to="/admin/audit"
          >查看全部<Icon name="right" :size="14"
        /></RouterLink>
      </div>
      <ol class="activity-list">
        <li
          v-for="a in db.audit
            .filter((a) => a.tenant === session.tenant)
            .slice(0, 5)"
          :key="a.id"
        >
          <span class="activity-dot" />
          <div>
            <p>
              <strong>{{ a.actor }}</strong> {{ a.action }}
            </p>
            <span>{{ a.subject }}</span
            ><small>{{ a.at }}</small>
          </div>
        </li>
      </ol>
    </section>
  </div>
</template>
