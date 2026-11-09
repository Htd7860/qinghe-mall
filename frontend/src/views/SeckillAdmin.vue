<script setup lang="ts">
import { computed, reactive, ref, onMounted, onUnmounted } from "vue";
import { useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import Empty from "../components/Empty.vue";
import Modal from "../components/Modal.vue";
import Icon from "../components/Icon.vue";
import {
  db,
  ui,
  can,
  currentServerTime,
  attempt,
  notify,
  loadPage,
} from "../data/store";
import {
  saveActivity,
  transitionActivity,
  fetchSeckillRequest,
} from "../data/seckill";
import {
  activityState,
  type SeckillActivity,
  type SeckillRequest,
  type ActivityInput,
} from "../seckill";
import { money } from "../domain";
const session = reactive(ui);
const route = useRoute(),
  platform = computed(() => !!route.meta.platform),
  search = ref(""),
  status = ref(""),
  page = ref(1),
  now = ref(currentServerTime());
let ticker: ReturnType<typeof setInterval>;
onMounted(() => {
  ticker = setInterval(() => (now.value = currentServerTime()), 1000);
});
onUnmounted(() => clearInterval(ticker));
const rows = computed(() =>
  db.seckillActivities.filter(
    (a) =>
      (platform.value || a.tenant === ui.tenant) &&
      (!search.value ||
        [a.name, a.productName, a.id].some((v) => v.includes(search.value))) &&
      (!status.value || activityState(a, now.value) === status.value),
  ),
);
const totalPages = computed(() =>
    Math.max(1, Math.ceil(rows.value.length / 10)),
  ),
  visible = computed(() =>
    rows.value.slice(
      (Math.min(page.value, totalPages.value) - 1) * 10,
      Math.min(page.value, totalPages.value) * 10,
    ),
  );
const products = computed(() =>
  db.products.filter((p) => p.tenant === ui.tenant && p.status === "上架中"),
);
const editor = ref(false),
  editingId = ref<string>(),
  error = ref(""),
  saving = ref(false),
  detailId = ref<string>(),
  confirmation = ref<{
    id: string;
    version: number;
    name: string;
    operation: "publish" | "withdraw" | "stop" | "reconcile";
  }>(),
  reason = ref("");
const form = reactive({
  name: "",
  productId: 0,
  startsAt: "",
  endsAt: "",
  singleLimit: 1,
  memberLimit: 1,
});
const skuRows = ref<
  {
    spec: string;
    selected: boolean;
    price: number;
    allocated: number;
    stock: number;
  }[]
>([]);
const detail = computed(() =>
  db.seckillActivities.find((a) => a.id === detailId.value),
);
const requests = computed(() =>
  db.seckillRequests.filter(
    (r) =>
      r.activityId === detailId.value &&
      (platform.value || r.tenant === ui.tenant),
  ),
);
const date = (s: string) =>
  new Date(s).toLocaleString("zh-CN", {
    timeZone: "Asia/Shanghai",
    hour12: false,
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
const inputTime = (s: string) =>
  new Date(Date.parse(s) + 8 * 3600000).toISOString().slice(0, 16);
function selectProduct(a?: SeckillActivity) {
  const p = products.value.find((p) => p.id === form.productId);
  skuRows.value = (p?.skus || []).map((s) => {
    const old = a?.skus.find((x) => x.spec === s.spec);
    return {
      spec: s.spec,
      selected: !!old,
      price: (old?.price || s.price) / 100,
      allocated: old?.planned || old?.allocated || 1,
      stock: s.stock,
    };
  });
}
function openEditor(a?: SeckillActivity) {
  editingId.value = a?.id;
  error.value = "";
  Object.assign(
    form,
    a
      ? { ...a, startsAt: inputTime(a.startsAt), endsAt: inputTime(a.endsAt) }
      : {
          name: "",
          productId: products.value[0]?.id || 0,
          startsAt: inputTime(new Date(Date.now() + 3600000).toISOString()),
          endsAt: inputTime(new Date(Date.now() + 7200000).toISOString()),
          singleLimit: 1,
          memberLimit: 1,
        },
  );
  selectProduct(a);
  editor.value = true;
}
async function save() {
  if (saving.value) return;
  saving.value = true;
  error.value = "";
  try {
    const input: ActivityInput = {
      ...form,
      startsAt: `${form.startsAt}:00+08:00`,
      endsAt: `${form.endsAt}:00+08:00`,
      skus: skuRows.value
        .filter((s) => s.selected)
        .map((s) => ({
          spec: s.spec,
          price: Math.round(s.price * 100),
          allocated: s.allocated,
        })),
    };
    if (!input.skus.length) throw new Error("请至少选择一个活动规格");
    await saveActivity(input, editingId.value);
    editor.value = false;
    notify("活动草稿已保存");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    saving.value = false;
  }
}
function confirm(
  a: SeckillActivity,
  operation: "publish" | "withdraw" | "stop" | "reconcile",
) {
  confirmation.value = {
    id: a.id,
    version: a.version,
    name: a.name,
    operation,
  };
  reason.value = "";
  error.value = "";
}
const labels = {
  publish: "发布活动",
  withdraw: "撤回活动",
  stop: "停止接单",
  reconcile: "核查并清算",
};
async function execute() {
  if (!confirmation.value || saving.value) return;
  saving.value = true;
  error.value = "";
  try {
    const c = confirmation.value;
    if (c.operation === "stop" && !reason.value.trim())
      throw new Error("请填写停止原因");
    await transitionActivity(c.id, c.version, c.operation, reason.value);
    confirmation.value = undefined;
    notify("操作已提交，请查看活动状态");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    saving.value = false;
  }
}
async function check(r: SeckillRequest) {
  await attempt(async () => {
    await fetchSeckillRequest(r.id);
    await loadPage();
    notify("已查询最新处理结果");
  });
}
</script>
<template>
  <PageHeader
    :title="platform ? '活动监管' : '限量秒杀'"
    :eyebrow="platform ? '平台运营' : '客户与营销'"
    ><button class="button" @click="loadPage()">
      <Icon name="refresh" :size="15" />刷新</button
    ><button
      v-if="!platform && can('seckill.manage')"
      class="button primary"
      @click="openEditor()"
    >
      <Icon name="plus" :size="16" />创建活动
    </button></PageHeader
  >
  <div class="stats-strip seckill-stats">
    <article>
      <div class="stat-label">进行中</div>
      <strong>{{
        rows.filter((a) => activityState(a, now) === "进行中").length
      }}</strong
      ><small>当前可参与的活动</small>
    </article>
    <article>
      <div class="stat-label">待开始</div>
      <strong>{{
        rows.filter((a) => activityState(a, now) === "待开始").length
      }}</strong
      ><small>已发布且库存已划拨</small>
    </article>
    <article>
      <div class="stat-label">待处理</div>
      <strong>{{
        db.seckillRequests.filter(
          (r) =>
            (platform || r.tenant === session.tenant) &&
            ["排队中", "待确认"].includes(r.status),
        ).length
      }}</strong
      ><small>抢购结果待确认</small>
    </article>
  </div>
  <section class="panel">
    <div class="filter-bar">
      <label class="search-input"
        ><Icon name="search" :size="16" /><input
          v-model.trim="search"
          aria-label="搜索秒杀活动"
          placeholder="搜索活动、商品或编号"
          @input="page = 1" /></label
      ><select v-model="status" aria-label="活动状态" @change="page = 1">
        <option value="">全部状态</option>
        <option
          v-for="s in [
            '草稿',
            '发布准备中',
            '待开始',
            '进行中',
            '已抢完',
            '停止接单',
            '结束清算中',
            '已结束',
            '发布失败',
          ]"
          :key="s"
        >
          {{ s }}
        </option></select
      ><span class="muted">共 {{ rows.length }} 个活动</span>
    </div>
    <Empty
      v-if="!rows.length"
      :title="search || status ? '没有符合条件的活动' : '暂无秒杀活动'"
      icon="clock"
      ><button
        v-if="!platform && can('seckill.manage')"
        class="button primary"
        @click="openEditor()"
      >
        创建活动
      </button></Empty
    >
    <div v-else class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>活动 / 商品</th>
            <th v-if="platform">商家</th>
            <th>活动时间</th>
            <th>活动价</th>
            <th>可抢 / 占用 / 已售</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in visible" :key="a.id">
            <td>
              <div class="product-cell">
                <img :src="a.image" :alt="a.productName" />
                <div>
                  <strong>{{ a.name }}</strong
                  ><small>{{ a.productName }}</small>
                </div>
              </div>
            </td>
            <td v-if="platform">
              {{
                db.tenants.find((t) => t.code === a.tenant)?.name || a.tenant
              }}
            </td>
            <td class="small nowrap">
              {{ date(a.startsAt) }}<br />{{ date(a.endsAt) }}
            </td>
            <td>
              ¥{{ money(Math.min(...a.skus.map((s) => s.price)))
              }}<small class="muted"> 起</small>
            </td>
            <td>
              {{ a.skus.reduce((n, s) => n + s.available, 0) }} /
              {{ a.skus.reduce((n, s) => n + s.held, 0) }} /
              {{ a.skus.reduce((n, s) => n + s.sold, 0) }}
            </td>
            <td>
              <span
                class="status"
                :class="
                  activityState(a, now) === '进行中'
                    ? 'green'
                    : activityState(a, now) === '草稿'
                      ? 'gray'
                      : 'amber'
                "
                >{{ activityState(a, now) }}</span
              >
            </td>
            <td>
              <div class="row-actions">
                <button @click="detailId = a.id">详情</button
                ><button
                  v-if="
                    !platform &&
                    can('seckill.manage') &&
                    ['草稿', '发布失败'].includes(a.status)
                  "
                  @click="openEditor(a)"
                >
                  编辑</button
                ><button
                  v-if="
                    !platform &&
                    can('seckill.publish') &&
                    ['草稿', '发布失败'].includes(a.status)
                  "
                  @click="confirm(a, 'publish')"
                >
                  发布</button
                ><button
                  v-if="
                    !platform &&
                    can('seckill.publish') &&
                    activityState(a, now) === '待开始'
                  "
                  @click="confirm(a, 'withdraw')"
                >
                  撤回</button
                ><button
                  v-if="
                    (platform || can('seckill.publish')) &&
                    ['已发布', '发布准备中'].includes(a.status) &&
                    !['结束清算中'].includes(activityState(a, now))
                  "
                  @click="confirm(a, 'stop')"
                >
                  停止</button
                ><button
                  v-if="
                    !platform &&
                    can('seckill.publish') &&
                    ['停止接单', '结束清算中'].includes(activityState(a, now))
                  "
                  @click="confirm(a, 'reconcile')"
                >
                  清算
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="rows.length" class="pagination">
      <span>第 {{ Math.min(page, totalPages) }} / {{ totalPages }} 页</span>
      <div class="row-actions">
        <button class="button" :disabled="page <= 1" @click="page--">
          上一页</button
        ><button class="button" :disabled="page >= totalPages" @click="page++">
          下一页
        </button>
      </div>
    </div>
  </section>
  <Modal
    v-if="editor"
    :title="editingId ? '编辑活动' : '创建秒杀活动'"
    wide
    @close="editor = false"
    ><form id="seckill-editor" @submit.prevent="save">
      <label
        >活动名称<input
          v-model.trim="form.name"
          required
          maxlength="60"
          placeholder="例如：周末衣橱限量上新" /></label
      ><label
        >活动商品<select
          v-model.number="form.productId"
          required
          @change="selectProduct()"
        >
          <option v-for="p in products" :key="p.id" :value="p.id">
            {{ p.name }}
          </option>
        </select></label
      >
      <div class="form-grid">
        <label
          >开始时间（北京时间）<input
            v-model="form.startsAt"
            type="datetime-local"
            required /></label
        ><label
          >结束时间（北京时间）<input
            v-model="form.endsAt"
            type="datetime-local"
            required
        /></label>
      </div>
      <div class="form-grid">
        <label
          >每单最多（件）<input
            v-model.number="form.singleLimit"
            type="number"
            min="1"
            step="1"
            required /></label
        ><label
          >每人整场累计最多（件）<input
            v-model.number="form.memberLimit"
            type="number"
            :min="form.singleLimit"
            step="1"
            required
        /></label>
      </div>
      <p class="section-gap muted small">
        发布后将从商品可售库存中划拨活动库存，活动价不叠加优惠券。
      </p>
      <div class="table-scroll section-gap">
        <table>
          <thead>
            <tr>
              <th>参与</th>
              <th>规格</th>
              <th>秒杀价（元）</th>
              <th>活动库存</th>
              <th>当前可售</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in skuRows" :key="s.spec">
              <td>
                <input
                  v-model="s.selected"
                  type="checkbox"
                  :aria-label="`参与规格${s.spec}`"
                />
              </td>
              <td>{{ s.spec }}</td>
              <td>
                <input
                  v-model.number="s.price"
                  type="number"
                  min="0.01"
                  step="0.01"
                  :disabled="!s.selected"
                  :aria-label="`${s.spec}秒杀价`"
                  style="width: 110px"
                />
              </td>
              <td>
                <input
                  v-model.number="s.allocated"
                  type="number"
                  min="1"
                  step="1"
                  :disabled="!s.selected"
                  :aria-label="`${s.spec}活动库存`"
                  style="width: 95px"
                />
              </td>
              <td>{{ s.stock }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <template #footer
      ><button class="button" @click="editor = false">取消</button
      ><button
        class="button primary"
        form="seckill-editor"
        type="submit"
        :disabled="saving || session.busy"
      >
        {{ saving ? "保存中…" : "保存草稿" }}
      </button></template
    ></Modal
  >
  <Modal
    v-if="confirmation"
    :title="labels[confirmation.operation]"
    @close="confirmation = undefined"
    ><form id="seckill-confirm" @submit.prevent="execute">
      <p>
        确认{{ labels[confirmation.operation] }}「{{ confirmation.name }}」？
      </p>
      <p class="muted section-gap">
        {{
          confirmation.operation === "stop"
            ? "停止后不再接受新的抢购，已生成订单继续履约与售后。"
            : confirmation.operation === "publish"
              ? "全部规格库存划拨成功后，活动才会对消费者开放。"
              : confirmation.operation === "withdraw"
                ? "撤回并归还活动库存后可重新编辑。"
                : "仍有申请或待付款订单时，清算不会提前释放其库存。"
        }}
      </p>
      <label v-if="confirmation.operation === 'stop'" class="section-gap"
        >停止原因<textarea v-model.trim="reason" required maxlength="200" />
      </label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <template #footer
      ><button class="button" @click="confirmation = undefined">取消</button
      ><button
        class="button primary"
        form="seckill-confirm"
        type="submit"
        :disabled="saving || session.busy"
      >
        确认{{ labels[confirmation.operation] }}
      </button></template
    ></Modal
  >
  <Modal v-if="detail" :title="detail.name" wide @close="detailId = undefined"
    ><div class="context-block">
      <strong>{{ detail.productName }}</strong
      ><span>{{ detail.id }} · {{ activityState(detail, now) }}</span
      ><span>{{ date(detail.startsAt) }} — {{ date(detail.endsAt) }}</span
      ><span
        >每单最多{{ detail.singleLimit }}件 · 每人整场最多{{
          detail.memberLimit
        }}件</span
      >
    </div>
    <p v-if="detail.reason" class="form-error">{{ detail.reason }}</p>
    <div class="table-scroll section-gap">
      <table>
        <thead>
          <tr>
            <th>规格</th>
            <th>活动价</th>
            <th>计划 / 已划拨</th>
            <th>可抢</th>
            <th>占用</th>
            <th>已售</th>
            <th>已归还</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in detail.skus" :key="s.spec">
            <td>{{ s.spec }}</td>
            <td>¥{{ money(s.price) }}</td>
            <td>{{ s.planned ?? s.allocated }} / {{ s.allocated }}</td>
            <td>{{ s.available }}</td>
            <td>{{ s.held }}</td>
            <td>{{ s.sold }}</td>
            <td>{{ s.returned }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <h3 class="section-gap">抢购与处理记录</h3>
    <Empty v-if="!requests.length" title="暂无抢购记录" icon="order" />
    <div v-else class="table-scroll section-gap">
      <table>
        <thead>
          <tr>
            <th>申请号</th>
            <th>规格 / 数量</th>
            <th>状态</th>
            <th>订单 / 结果</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in requests" :key="r.id">
            <td class="small">{{ r.id }}</td>
            <td>{{ r.spec }} × {{ r.quantity }}</td>
            <td>{{ r.status }}</td>
            <td class="small">{{ r.orderId || r.reason || "正在处理" }}</td>
            <td>
              <button class="text-link" @click="check(r)">查询结果</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <template #footer
      ><button class="button" @click="detailId = undefined">关闭</button
      ><button class="button" @click="loadPage()">刷新记录</button></template
    ></Modal
  >
</template>
