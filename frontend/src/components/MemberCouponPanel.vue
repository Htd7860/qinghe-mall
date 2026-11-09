<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import Empty from "./Empty.vue";
import Modal from "./Modal.vue";
import { context } from "../api/session";
import { ApiError } from "../api/http";
import {
  memberCouponIdentity,
  memberCouponReady,
  memberTemplatePage,
  memberCouponPage,
  getMemberCoupon,
  claimMemberCoupon,
  readPendingClaims,
  createPendingClaim,
  clearPendingClaim,
  type MemberCouponRules,
  type MemberCouponTemplate,
  type MemberCoupon,
  type CouponClaim,
} from "../api/member-coupon";
const ready = computed(memberCouponReady),
  mode = ref<"receive" | "owned">("receive"),
  status = ref<number | null>(null);
const templates = ref<MemberCouponTemplate[]>([]),
  coupons = ref<MemberCoupon[]>([]),
  total = ref(0),
  pageNo = ref(1);
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  pending = ref<CouponClaim | null>(null);
const storageError = ref(""),
  detail = ref<MemberCoupon | null>(null),
  detailBusy = ref(false),
  resolvedMissing = ref(false);
const pendingClaims = ref<CouponClaim[]>([]);
function hasPending(templateId: number) {
  return pendingClaims.value.some((x) => x.templateId === templateId);
}
function selectPending(requestId: string) {
  pending.value =
    pendingClaims.value.find((x) => x.requestId === requestId) || null;
  resolvedMissing.value = false;
}
const base = computed(() => `/shop/${context.tenant}`);
let epoch = 0,
  reads = 0,
  detailReads = 0;
function failure(e: unknown) {
  error.value = e instanceof Error ? e.message : "读取失败，请稍后重试";
  if (e instanceof ApiError && e.code === 401)
    window.dispatchEvent(new Event("commerce:unauthorized"));
}
function restorePending() {
  storageError.value = "";
  try {
    pendingClaims.value = readPendingClaims();
    pending.value =
      pendingClaims.value.find(
        (x) => x.requestId === pending.value?.requestId,
      ) ||
      pendingClaims.value[0] ||
      null;
  } catch (e) {
    storageError.value = e instanceof Error ? e.message : "无法读取领取请求";
  }
}
async function reload() {
  const ticket = epoch,
    serial = ++reads;
  loading.value = true;
  error.value = "";
  templates.value = [];
  coupons.value = [];
  try {
    const data =
      mode.value === "receive"
        ? await memberTemplatePage(pageNo.value)
        : await memberCouponPage(pageNo.value, status.value);
    if (ticket !== epoch || serial !== reads) return;
    total.value = data.total;
    if (mode.value === "receive")
      templates.value = data.list as MemberCouponTemplate[];
    else coupons.value = data.list as MemberCoupon[];
  } catch (e) {
    if (ticket === epoch && serial === reads) failure(e);
  } finally {
    if (ticket === epoch && serial === reads) loading.value = false;
  }
}
watch(
  [memberCouponIdentity, ready],
  () => {
    epoch++;
    reads++;
    detailReads++;
    busy.value = false;
    detailBusy.value = false;
    detail.value = null;
    templates.value = [];
    coupons.value = [];
    pending.value = null;
    pendingClaims.value = [];
    error.value = "";
    notice.value = "";
    pageNo.value = 1;
    resolvedMissing.value = false;
    if (ready.value) {
      restorePending();
      void reload();
    }
  },
  { immediate: true },
);
watch([mode, status], () => {
  pageNo.value = 1;
  detail.value = null;
  detailReads++;
  if (ready.value) void reload();
});
onBeforeUnmount(() => {
  epoch++;
  reads++;
  detailReads++;
  templates.value = [];
  coupons.value = [];
  detail.value = null;
});
function money(cents: number) {
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, "0")}`;
}
function offer(row: MemberCouponRules) {
  return row.discountType === 1
    ? `减 ¥${money(row.discountPrice!)}`
    : `${row.discountPercent! / 10} 折`;
}
function scope(row: MemberCouponRules) {
  return row.productScope === 1
    ? "全店商品适用"
    : row.productScope === 2
      ? `指定商品（${row.productScopeValues.map((id) => `#${id}`).join("、")}）`
      : `指定分类（${row.productScopeValues.map((id) => `#${id}`).join("、")}）`;
}
function couponState(row: MemberCoupon) {
  if (row.status !== 1)
    return (
      { 2: "已使用", 3: "已过期", 4: "订单占用中", 5: "已作废" } as Record<
        number,
        string
      >
    )[row.status];
  // 状态/时间以服务端为准；未使用也可能尚未到生效时间，不能称可立即核销。
  return "未使用";
}
async function showDetail(row: MemberCoupon) {
  const ticket = epoch,
    serial = ++detailReads;
  detailBusy.value = true;
  detail.value = null;
  error.value = "";
  try {
    const data = await getMemberCoupon(row.id);
    if (ticket === epoch && serial === detailReads) detail.value = data;
  } catch (e) {
    if (ticket === epoch && serial === detailReads) failure(e);
  } finally {
    if (ticket === epoch && serial === detailReads) detailBusy.value = false;
  }
}
async function submit(row?: MemberCouponTemplate) {
  if (busy.value || storageError.value || !ready.value) return;
  const ticket = epoch;
  busy.value = true;
  error.value = "";
  notice.value = "";
  let claim = pending.value;
  try {
    if (row) {
      if (hasPending(row.id) || !row.canTake) return;
      claim = createPendingClaim(row.id);
      pending.value = claim;
      pendingClaims.value = readPendingClaims();
    }
    if (!claim) return;
    resolvedMissing.value = false;
    const result = await claimMemberCoupon(claim);
    if (ticket !== epoch) return;
    if (result.found) {
      clearPendingClaim(claim);
      restorePending();
      notice.value = `领取成功，优惠券编号 ${result.couponId}。`;
      await reload();
      if (error.value) notice.value += " 列表未能刷新，可稍后重读。";
    }
  } catch (e) {
    if (ticket !== epoch) return;
    if (
      e instanceof ApiError &&
      !e.outcomeUnknown &&
      e.code >= 400 &&
      e.code < 500 &&
      claim
    ) {
      // 明确拒绝也保留原键，先查询；同一请求可已在另一并发请求成功。
      notice.value = "本次请求未确认领取成功，请查询原结果。";
    } else
      notice.value =
        "领取结果尚未确认，请查询原结果；再次提交将沿用同一个请求号。";
    failure(e);
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
async function resolve() {
  if (!pending.value || busy.value || storageError.value) return;
  const claim = pending.value,
    ticket = epoch;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const result = await claimMemberCoupon(claim, true);
    if (ticket !== epoch) return;
    if (result.found) {
      clearPendingClaim(claim);
      restorePending();
      resolvedMissing.value = false;
      notice.value = `已确认领取成功，优惠券编号 ${result.couponId}。`;
      await reload();
    } else {
      resolvedMissing.value = true;
      notice.value =
        "暂未查到成功记录，原请求仍可能在处理。可稍后再查，或用原请求号再次提交。";
    }
  } catch (e) {
    if (ticket === epoch) failure(e);
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
function next(delta: number) {
  if (loading.value || busy.value) return;
  pageNo.value += delta;
  void reload();
}
</script>
<template>
  <main class="shop-container member-coupons">
    <div class="shop-page-heading">
      <span class="collection-label">MEMBER BENEFITS</span>
      <h1>会员礼遇</h1>
    </div>
    <Empty v-if="!ready" title="登录后查看本店优惠券" icon="coupon"
      ><RouterLink
        class="button primary"
        :to="`${base}/login?redirect=${encodeURIComponent(`${base}/coupons`)}`"
        >前往登录</RouterLink
      ></Empty
    >
    <template v-else>
      <div class="coupon-toolbar">
        <div class="coupon-tabs" aria-label="优惠券栏目">
          <button
            class="button"
            :class="{ primary: mode === 'receive' }"
            :disabled="busy"
            @click="mode = 'receive'"
          >
            领券中心
          </button>
          <button
            class="button"
            :class="{ primary: mode === 'owned' }"
            :disabled="busy"
            @click="mode = 'owned'"
          >
            我的优惠券
          </button>
        </div>
        <button
          class="button"
          :disabled="busy || loading"
          @click="
            restorePending();
            reload();
          "
        >
          刷新列表
        </button>
      </div>
      <div v-if="mode === 'owned'" class="coupon-status">
        <label
          >状态
          <select v-model="status" :disabled="busy || loading">
            <option :value="null">全部</option>
            <option :value="1">未使用</option>
            <option :value="2">已使用</option>
            <option :value="3">已过期</option>
            <option :value="4">订单占用中</option>
            <option :value="5">已作废</option>
          </select></label
        >
      </div>
      <p v-if="notice" class="coupon-message" role="status">{{ notice }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="storageError" class="error" role="alert">{{ storageError }}</p>
      <section v-if="pending" class="claim-recovery" aria-label="待确认领取">
        <strong>模板 #{{ pending.templateId }} 的领取结果待确认</strong>
        <p>此模板再次领取将沿用原请求号。</p>
        <label v-if="pendingClaims.length > 1"
          >待确认领取
          <select
            :value="pending.requestId"
            :disabled="busy"
            @change="selectPending(($event.target as HTMLSelectElement).value)"
          >
            <option
              v-for="item in pendingClaims"
              :key="item.requestId"
              :value="item.requestId"
            >
              模板 #{{ item.templateId }}
            </option>
          </select></label
        >
        <div class="coupon-tabs">
          <button
            class="button primary"
            :disabled="busy || !!storageError"
            @click="resolve"
          >
            查询原结果
          </button>
          <button
            v-if="resolvedMissing"
            class="button"
            :disabled="busy || !!storageError"
            @click="submit()"
          >
            用原请求号再次提交
          </button>
        </div>
      </section>
      <Empty v-if="loading" title="正在读取优惠券…" icon="refresh" />
      <template v-else-if="!error">
        <div v-if="mode === 'receive'" class="shop-coupon-grid">
          <article v-for="row in templates" :key="row.id" class="shop-coupon">
            <div class="coupon-value">
              <strong>{{ offer(row) }}</strong
              ><span>{{
                row.usePrice ? `满 ¥${money(row.usePrice)} 可用` : "无金额门槛"
              }}</span>
            </div>
            <div class="coupon-description">
              <h3>{{ row.name }}</h3>
              <p v-if="row.description">{{ row.description }}</p>
              <p>
                {{
                  row.validityType === 2
                    ? `领取起连续 ${row.fixedEndTerm} 天有效`
                    : `${row.validStartTime} 至 ${row.validEndTime}（北京时间，结束时刻不可用）`
                }}
              </p>
              <p class="coupon-scope">{{ scope(row) }}</p>
              <p v-if="row.discountType === 2">
                最多减 ¥{{ money(row.discountLimitPrice!) }}
              </p>
              <p>
                {{
                  row.remainingCount === -1
                    ? "不限总量"
                    : `剩余 ${row.remainingCount} 张`
                }}
                ·
                {{
                  row.takeLimitCount === -1
                    ? "不限个人领取次数"
                    : `每人限领 ${row.takeLimitCount} 张`
                }}
                · 已领 {{ row.takenCount }} 张
              </p>
              <button
                class="button primary"
                :disabled="
                  !row.canTake || busy || hasPending(row.id) || !!storageError
                "
                @click="submit(row)"
              >
                {{ row.canTake ? "立即领取" : row.unavailableReason }}
              </button>
            </div>
          </article>
        </div>
        <div v-else class="shop-coupon-grid">
          <article v-for="row in coupons" :key="row.id" class="shop-coupon">
            <div class="coupon-value">
              <strong>{{ offer(row) }}</strong
              ><span>{{
                row.usePrice ? `满 ¥${money(row.usePrice)} 可用` : "无金额门槛"
              }}</span>
            </div>
            <div class="coupon-description">
              <span class="tag">{{ couponState(row) }}</span>
              <h3>{{ row.name }}</h3>
              <p>{{ row.validStartTime }} 至 {{ row.validEndTime }}</p>
              <p>北京时间，结束时刻不可用</p>
              <p class="coupon-scope">{{ scope(row) }}</p>
              <button
                class="button"
                :disabled="busy || detailBusy"
                @click="showDetail(row)"
              >
                查看详情
              </button>
            </div>
          </article>
        </div>
        <Empty
          v-if="mode === 'receive' ? !templates.length : !coupons.length"
          :title="
            mode === 'receive' ? '暂无可领取优惠券' : '暂无该状态的优惠券'
          "
          icon="coupon"
        />
        <div v-if="total > 12" class="coupon-pagination">
          <button
            class="button"
            :disabled="pageNo <= 1 || busy"
            @click="next(-1)"
          >
            上一页</button
          ><span>第 {{ pageNo }} 页 · 共 {{ total }} 项</span
          ><button
            class="button"
            :disabled="pageNo * 12 >= total || busy"
            @click="next(1)"
          >
            下一页
          </button>
        </div>
      </template>
    </template>
    <Modal
      v-if="detail"
      title="优惠券详情"
      @close="
        detail = null;
        detailReads++;
      "
    >
      <h3>{{ detail.name }}</h3>
      <p>
        {{ offer(detail) }} ·
        {{
          detail.usePrice ? `满 ¥${money(detail.usePrice)} 可用` : "无金额门槛"
        }}
      </p>
      <p v-if="detail.discountType === 2">
        最多减 ¥{{ money(detail.discountLimitPrice!) }}
      </p>
      <p>状态：{{ couponState(detail) }}</p>
      <p>券编号：{{ detail.id }} · 模板编号：{{ detail.templateId }}</p>
      <p class="coupon-scope">{{ scope(detail) }}</p>
      <p>
        北京时间 {{ detail.validStartTime }} 起生效，{{ detail.validEndTime }}
        到期（结束时刻不可用）。
      </p>
      <template #footer
        ><button class="button" @click="detail = null">关闭</button></template
      >
    </Modal>
  </main>
</template>
<style scoped>
.member-coupons {
  padding-top: 32px;
  padding-bottom: 48px;
}
.coupon-toolbar,
.coupon-tabs,
.coupon-pagination {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.coupon-toolbar {
  justify-content: space-between;
  margin-bottom: 20px;
}
.coupon-status {
  margin-bottom: 20px;
}
.coupon-status select {
  margin-left: 8px;
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface);
}
.claim-recovery {
  padding: 20px;
  margin: 20px 0;
  border: 1px solid var(--border);
  border-left: 3px solid var(--primary);
  background: var(--surface);
  border-radius: 8px;
}
.coupon-message {
  margin: 16px 0;
  color: var(--primary);
}
.coupon-pagination {
  justify-content: center;
  margin-top: 24px;
}
.coupon-scope {
  overflow-wrap: anywhere;
}
.coupon-value strong {
  font-size: 28px;
}
.coupon-description p {
  margin: 8px 0;
}
.coupon-description .button {
  margin-top: 8px;
}
@media (max-width: 600px) {
  .coupon-toolbar {
    align-items: stretch;
  }
  .coupon-toolbar > .button {
    width: 100%;
  }
  .coupon-tabs .button {
    flex: 1;
  }
  .shop-coupon {
    flex-direction: column;
  }
  .coupon-value {
    padding: 20px;
  }
}
</style>
