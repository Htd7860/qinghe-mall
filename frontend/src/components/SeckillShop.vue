<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import Icon from "./Icon.vue";
import Empty from "./Empty.vue";
import { db, ui, currentServerTime } from "../data/store";
import { identities } from "../api/session";
import {
  getSeckillQuote,
  joinSeckill,
  fetchSeckillRequest,
} from "../data/seckill";
import {
  activityState,
  type SeckillQuote,
  type SeckillRequest,
} from "../seckill";
import { money } from "../domain";
const route = useRoute(),
  router = useRouter(),
  base = computed(() => `/shop/${route.params.tenant}`),
  mode = computed(() => String(route.params.page)),
  id = computed(() => String(route.params.id || ""));
const now = ref(currentServerTime()),
  error = ref(""),
  quote = ref<SeckillQuote>(),
  quoteBusy = ref(false),
  busy = ref(false),
  spec = ref(""),
  quantity = ref(1),
  addressId = ref(0),
  result = ref<SeckillRequest>(),
  checking = ref(false),
  pollPaused = ref(false);
const activities = computed(() =>
    db.seckillActivities.filter(
      (a) =>
        a.tenant === ui.tenant &&
        !["草稿", "发布准备中", "发布失败"].includes(a.status),
    ),
  ),
  activity = computed(() => activities.value.find((a) => a.id === id.value));
const addresses = computed(() =>
  db.addresses.filter(
    (a) => a.tenant === ui.tenant && a.memberId === ui.memberId,
  ),
);
const selectedSku = computed(() =>
  activity.value?.skus.find((s) => s.spec === spec.value),
);
const state = computed(() =>
  activity.value ? activityState(activity.value, now.value) : "",
);
const myRequests = computed(() =>
  db.seckillRequests.filter(
    (r) => r.tenant === ui.tenant && r.memberId === ui.memberId,
  ),
);
const used = computed(() =>
  myRequests.value
    .filter(
      (r) =>
        r.activityId === id.value && !["失败", "已取消"].includes(r.status),
    )
    .reduce((n, r) => n + r.quantity, 0),
);
const limit = computed(() =>
  Math.max(
    0,
    Math.min(
      activity.value?.singleLimit || 0,
      (activity.value?.memberLimit || 0) - used.value,
      selectedSku.value?.available || 0,
    ),
  ),
);
const countdown = computed(() => {
  if (!activity.value || !Number.isFinite(now.value)) return "正在校时";
  const end =
    state.value === "待开始" ? activity.value.startsAt : activity.value.endsAt;
  const secs = Math.max(0, Math.ceil((Date.parse(end) - now.value) / 1000));
  const h = Math.floor(secs / 3600),
    m = Math.floor((secs % 3600) / 60),
    s = secs % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
});
const date = (s: string) =>
  new Date(s).toLocaleString("zh-CN", {
    timeZone: "Asia/Shanghai",
    hour12: false,
  });
let ticker: ReturnType<typeof setInterval>,
  poll: ReturnType<typeof setTimeout>,
  controller: AbortController | undefined,
  quoteController: AbortController | undefined,
  quoteVersion = 0,
  run = 0,
  started = 0;
const requestKey = ref(crypto.randomUUID());
const quoteRefresh = ref(0);
watch(
  [id, () => addresses.value.length],
  () => {
    spec.value = activity.value?.skus[0]?.spec || "";
    addressId.value =
      addresses.value.find((a) => a.isDefault)?.id ||
      addresses.value[0]?.id ||
      0;
    quantity.value = 1;
  },
  { immediate: true },
);
watch(
  [spec, quantity, addressId, id, quoteRefresh],
  async () => {
    const version = ++quoteVersion;
    quote.value = undefined;
    error.value = "";
    requestKey.value = crypto.randomUUID();
    quoteController?.abort();
    if (
      !activity.value ||
      !identities.member ||
      !spec.value ||
      !addressId.value ||
      !Number.isSafeInteger(quantity.value) ||
      quantity.value < 1
    )
      return;
    quoteController = new AbortController();
    quoteBusy.value = true;
    try {
      const value = await getSeckillQuote(
        {
          activityId: activity.value.id,
          version: activity.value.version,
          spec: spec.value,
          quantity: quantity.value,
          addressId: addressId.value,
        },
        quoteController.signal,
      );
      if (version === quoteVersion) quote.value = value;
    } catch (e) {
      if (version === quoteVersion) error.value = (e as Error).message;
    } finally {
      if (version === quoteVersion) quoteBusy.value = false;
    }
  },
  { immediate: true },
);
async function submit() {
  if (!identities.member) {
    await router.push({
      path: `${base.value}/login`,
      query: { redirect: route.fullPath },
    });
    return;
  }
  if (busy.value || ui.busy) return;
  busy.value = true;
  error.value = "";
  try {
    if (!activity.value || state.value !== "进行中")
      throw new Error("当前活动暂不可抢购");
    if (!addressId.value) throw new Error("请先添加收货地址");
    if (!quote.value || quote.value.expiresAt <= now.value)
      throw new Error("报价已失效，请重新确认规格或刷新页面");
    const requestId = await joinSeckill({
      activityId: activity.value.id,
      version: activity.value.version,
      spec: spec.value,
      quantity: quantity.value,
      addressId: addressId.value,
      quoteToken: quote.value.token,
      requestKey: requestKey.value,
    });
    if (typeof requestId !== "string" || !requestId)
      throw new Error("抢购结果待确认，请查询处理记录");
    await router.push(
      `${base.value}/seckill-result/${encodeURIComponent(requestId)}`,
    );
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function check(manual = false) {
  if (checking.value) return;
  clearTimeout(poll);
  if (manual) pollPaused.value = false;
  const generation = run;
  checking.value = true;
  error.value = "";
  controller = new AbortController();
  try {
    const value = await fetchSeckillRequest(id.value, controller.signal);
    if (generation !== run) return;
    result.value = value;
    if (["排队中", "待确认"].includes(value.status)) {
      if (manual) started = performance.now();
      if (performance.now() - started < 30000 && !document.hidden)
        poll = setTimeout(() => check(), 2000);
      else pollPaused.value = true;
    }
  } catch (e) {
    if (generation === run) {
      error.value = (e as Error).message;
      pollPaused.value = true;
    }
  } finally {
    if (generation === run) checking.value = false;
  }
}
watch(
  [id, mode],
  () => {
    run++;
    clearTimeout(poll);
    controller?.abort();
    checking.value = false;
    result.value = undefined;
    pollPaused.value = false;
    if (mode.value === "seckill-result") {
      started = performance.now();
      void check();
    }
  },
  { immediate: true },
);
onMounted(() => {
  ticker = setInterval(() => (now.value = currentServerTime()), 1000);
});
onUnmounted(() => {
  run++;
  clearTimeout(poll);
  clearInterval(ticker);
  controller?.abort();
  quoteController?.abort();
});
</script>
<template>
  <main class="shop-container seckill-consumer">
    <template v-if="mode === 'seckill-result'">
      <RouterLink class="text-link" :to="`${base}/seckill`"
        >← 返回限量上新</RouterLink
      >
      <section class="seckill-result-card">
        <span class="result-symbol"
          ><Icon
            :name="
              result?.status === '订单已创建'
                ? 'success'
                : result && ['失败', '已取消'].includes(result.status)
                  ? 'alert'
                  : 'clock'
            "
            :size="32" /></span
        ><span class="collection-label">YOUR RESERVATION</span>
        <h1>{{ result?.status || "查询抢购结果" }}</h1>
        <p>
          {{
            result?.status === "排队中"
              ? "已收到你的抢购申请，正在确认订单。"
              : result?.status === "订单已创建"
                ? "抢购订单已生成，可查看订单和后续状态。"
                : result?.reason || "正在确认，请稍候。"
          }}
        </p>
        <div v-if="result" class="seckill-result-lines">
          <div>
            <span>申请号</span><strong>{{ result.id }}</strong>
          </div>
          <div>
            <span>规格 / 数量</span
            ><strong>{{ result.spec }} × {{ result.quantity }}</strong>
          </div>
          <div>
            <span>商品金额</span
            ><strong>¥{{ money(result.price * result.quantity) }}</strong>
          </div>
          <div>
            <span>运费</span><strong>¥{{ money(result.shipping) }}</strong>
          </div>
          <div>
            <span>应付合计</span><strong>¥{{ money(result.total) }}</strong>
          </div>
        </div>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <RouterLink
          v-if="result?.orderId"
          class="shop-button full-width"
          :to="`${base}/payment/${encodeURIComponent(result.orderId)}`"
          >查看订单</RouterLink
        ><button
          v-if="!result || ['排队中', '待确认'].includes(result.status)"
          class="shop-button full-width"
          :disabled="checking"
          @click="check(true)"
        >
          {{ checking ? "正在查询…" : "查询最新结果" }}
        </button>
        <p v-if="pollPaused" class="muted small">
          自动查询已暂停，你可以稍后继续查询。
        </p>
        <RouterLink class="continue-shopping" :to="`${base}/seckill`"
          >继续逛逛</RouterLink
        >
      </section>
    </template>
    <template v-else-if="id">
      <RouterLink class="text-link" :to="`${base}/seckill`"
        >← 全部限量上新</RouterLink
      >
      <Empty v-if="!activity" title="活动不存在或暂不可访问" icon="clock" />
      <div v-else class="seckill-detail-grid">
        <div class="seckill-detail-image">
          <img :src="activity.image" :alt="activity.productName" />
        </div>
        <section class="seckill-buy">
          <span class="collection-label">LIMITED RELEASE</span>
          <h1>{{ activity.name }}</h1>
          <p class="muted">{{ activity.productName }}</p>
          <div class="seckill-countdown">
            <span>{{
              state === "待开始"
                ? "距开始"
                : state === "进行中"
                  ? "距结束"
                  : state
            }}</span
            ><strong v-if="['待开始', '进行中'].includes(state)">{{
              countdown
            }}</strong>
          </div>
          <div class="seckill-price">
            <span>¥</span
            >{{
              money(
                selectedSku?.price ||
                  Math.min(...activity.skus.map((s) => s.price)),
              )
            }}<small>限量上新价</small>
          </div>
          <label
            >选择规格<select v-model="spec" aria-label="秒杀规格">
              <option v-for="s in activity.skus" :key="s.spec" :value="s.spec">
                {{ s.spec }} ·
                {{ s.available > 0 ? "剩余" + s.available + "件" : "已抢完" }}
              </option>
            </select></label
          >
          <div class="form-grid section-gap">
            <label
              >购买数量<input
                v-model.number="quantity"
                type="number"
                min="1"
                :max="Math.max(1, limit)"
                step="1"
                aria-label="秒杀数量"
            /></label>
            <div class="seckill-limit">
              每单最多{{ activity.singleLimit }}件<br />每人整场最多{{
                activity.memberLimit
              }}件
            </div>
          </div>
          <template v-if="identities.member"
            ><label class="section-gap"
              >收货地址<select
                v-if="addresses.length"
                v-model.number="addressId"
                aria-label="秒杀收货地址"
              >
                <option v-for="a in addresses" :key="a.id" :value="a.id">
                  {{ a.name }} · {{ a.region }} {{ a.detail }}
                </option></select
              ><RouterLink v-else class="text-link" :to="`${base}/addresses`"
                >添加收货地址 →</RouterLink
              ></label
            >
            <div v-if="quote" class="seckill-result-lines">
              <div>
                <span>商品金额</span
                ><strong>¥{{ money(quote.subtotal) }}</strong>
              </div>
              <div>
                <span>运费</span><strong>¥{{ money(quote.shipping) }}</strong>
              </div>
              <div>
                <span>应付</span><strong>¥{{ money(quote.total) }}</strong>
              </div>
            </div>
            <p v-if="quoteBusy" role="status" class="muted small section-gap">
              正在确认金额…
            </p>
            <button
              v-else-if="!quote || quote.expiresAt <= now"
              class="text-link section-gap"
              :disabled="!addressId"
              @click="quoteRefresh++"
            >
              重新确认金额
            </button></template
          >
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button
            class="shop-button full-width section-gap"
            :disabled="
              busy ||
              ui.busy ||
              (!!identities.member &&
                (!quote ||
                  quoteBusy ||
                  !addressId ||
                  quantity > limit ||
                  state !== '进行中' ||
                  quote.expiresAt <= now))
            "
            @click="submit"
          >
            {{
              !identities.member
                ? "登录后抢购"
                : busy
                  ? "正在提交…"
                  : state === "待开始"
                    ? "尚未开始"
                    : state === "进行中"
                      ? used >= activity.memberLimit
                        ? "已达本场限购"
                        : "立即抢购"
                      : state
            }}
          </button>
          <p class="seckill-rule">
            活动价不叠加优惠券。抢购申请需确认结果，生成订单后请在30分钟内付款。
          </p>
          <p class="muted small">
            {{ date(activity.startsAt) }} —
            {{ date(activity.endsAt) }}（北京时间）
          </p>
          <RouterLink :to="`${base}/seckill-records`" class="text-link"
            >查看我的抢购记录 →</RouterLink
          >
        </section>
      </div>
    </template>
    <template v-else-if="mode === 'seckill-records'"
      ><div class="shop-page-heading">
        <span class="collection-label">MY RESERVATIONS</span>
        <h1>我的抢购</h1>
      </div>
      <Empty v-if="!myRequests.length" title="还没有抢购记录" icon="clock"
        ><RouterLink class="shop-button" :to="`${base}/seckill`"
          >发现限量好物</RouterLink
        ></Empty
      >
      <section v-else class="panel seckill-records">
        <RouterLink
          v-for="r in myRequests"
          :key="r.id"
          :to="`${base}/seckill-result/${r.id}`"
          ><div>
            <strong>{{
              activities.find((a) => a.id === r.activityId)?.name || "限量上新"
            }}</strong>
            <p>{{ r.spec }} × {{ r.quantity }} · ¥{{ money(r.total) }}</p>
            <small>{{ date(r.createdAt) }}</small>
          </div>
          <span
            class="status"
            :class="r.status === '订单已创建' ? 'green' : 'amber'"
            >{{ r.status }}</span
          ><Icon name="right" :size="16"
        /></RouterLink></section
    ></template>
    <template v-else
      ><div class="seckill-hero">
        <span class="collection-label">A LITTLE RARER. JUST FOR YOU.</span>
        <h1>好物限量，<br />心意不打折。</h1>
        <p>把喜欢的日常，留在这一刻。</p>
        <RouterLink :to="`${base}/seckill-records`" class="text-link"
          >我的抢购记录 <Icon name="arrow" :size="16" /></RouterLink
        ><Icon name="clock" :size="88" class="seckill-hero-clock" />
      </div>
      <Empty v-if="!activities.length" title="新的惊喜，正在准备" icon="clock"
        ><RouterLink class="shop-button" :to="`${base}/products`"
          >先逛逛日常精选</RouterLink
        ></Empty
      >
      <div v-else class="seckill-cards">
        <article v-for="a in activities" :key="a.id">
          <RouterLink :to="`${base}/seckill/${a.id}`"
            ><div class="seckill-card-image">
              <img :src="a.image" :alt="a.productName" /><span
                class="status"
                :class="activityState(a, now) === '进行中' ? 'green' : 'gray'"
                >{{ activityState(a, now) }}</span
              >
            </div>
            <div class="seckill-card-content">
              <span class="collection-label">LIMITED RELEASE</span>
              <h2>{{ a.name }}</h2>
              <p>{{ a.productName }}</p>
              <div>
                <strong
                  >¥{{ money(Math.min(...a.skus.map((s) => s.price))) }}</strong
                ><span class="muted small">每人限{{ a.memberLimit }}件</span>
              </div>
              <span class="seckill-card-action"
                >{{
                  activityState(a, now) === "待开始" ? "查看预告" : "查看活动"
                }}<Icon name="arrow" :size="17"
              /></span></div
          ></RouterLink>
        </article></div
    ></template>
  </main>
</template>
