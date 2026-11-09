<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import Icon from "../components/Icon.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import {
  db,
  command,
  ui,
  attempt,
  notify,
  log,
  executeCampaign,
  requirePermission,
} from "../data/store";
import { money, type Member, type Coupon, type Campaign } from "../domain";
import { demo } from "../api/session";
import { maskPhone } from "../utils";
const route = useRoute(),
  mode = computed(() => route.path.split("/").at(-1)),
  query = ref(""),
  tag = ref(""),
  minimum = ref(0),
  memberDetail = ref<Member>(),
  tagText = ref(""),
  editor = ref(false),
  stopCampaign = ref<Campaign>(),
  campaignDetail = ref<Campaign>(),
  error = ref("");
const form = reactive({
  name: "",
  amount: 20,
  threshold: 150,
  total: 500,
  limit: 1,
  end: "2026-12-31",
  couponId: 1,
  tag: "回购会员",
  minimumOrders: 2,
  scheduled: "2026-10-20T10:00",
});
const members = computed(() =>
  db.members.filter(
    (m) =>
      m.tenant === ui.tenant &&
      (!query.value ||
        m.name.includes(query.value) ||
        m.phone.includes(query.value)) &&
      (!tag.value || m.tags.includes(tag.value)) &&
      m.orders >= minimum.value,
  ),
);
const coupons = computed(() =>
  db.coupons.filter(
    (c) =>
      c.tenant === ui.tenant && (!query.value || c.name.includes(query.value)),
  ),
);
const campaigns = computed(() =>
  db.campaigns.filter(
    (c) =>
      c.tenant === ui.tenant && (!query.value || c.name.includes(query.value)),
  ),
);
const tags = computed(() => [
  ...new Set(
    db.members.filter((m) => m.tenant === ui.tenant).flatMap((m) => m.tags),
  ),
]);
function openEditor() {
  Object.assign(form, {
    name: "",
    amount: 20,
    threshold: 150,
    total: 500,
    limit: 1,
    end: "2026-12-31",
    couponId: db.coupons.find((c) => c.tenant === ui.tenant)?.id || 0,
    tag: tag.value || "回购会员",
    minimumOrders: minimum.value || 2,
    scheduled: "2026-10-20T10:00",
  });
  error.value = "";
  editor.value = true;
}
async function save() {
  error.value = "";
  try {
    requirePermission("marketing");
    if (!demo) {
      await command(
        mode.value === "coupons" ? "coupon.create" : "campaign.create",
        mode.value === "coupons"
          ? {
              name: form.name,
              amount: Math.round(form.amount * 100),
              threshold: Math.round(form.threshold * 100),
              total: form.total,
              limit: form.limit,
              end: form.end,
            }
          : {
              name: form.name,
              couponId: form.couponId,
              tag: form.tag,
              minimumOrders: form.minimumOrders,
              scheduled: `${form.scheduled}:00+08:00`,
            },
        () => {},
      );
      editor.value = false;
      notify("已创建");
      return;
    }
    if (!form.name.trim()) throw new Error("请输入名称");
    if (mode.value === "coupons") {
      if (
        form.amount <= 0 ||
        form.threshold < form.amount ||
        !Number.isInteger(form.total) ||
        form.total < 1 ||
        !Number.isInteger(form.limit) ||
        form.limit < 1
      )
        throw new Error("请检查优惠金额、门槛、数量和领取上限");
      db.coupons.unshift({
        id: Date.now(),
        tenant: ui.tenant,
        name: form.name,
        amount: Math.round(form.amount * 100),
        threshold: Math.round(form.threshold * 100),
        total: form.total,
        issued: 0,
        status: "发放中",
        end: form.end,
        limit: form.limit,
      });
      log("创建优惠券", form.name);
    } else {
      const coupon = db.coupons.find(
        (c) =>
          c.id === form.couponId &&
          c.tenant === ui.tenant &&
          c.status === "发放中",
      );
      if (!coupon) throw new Error("请选择可发放的优惠券");
      const recipients = db.members
        .filter(
          (m) =>
            m.tenant === ui.tenant &&
            (!form.tag || m.tags.includes(form.tag)) &&
            m.orders >= form.minimumOrders &&
            !m.optOut,
        )
        .map((m) => m.id);
      if (!recipients.length) throw new Error("当前条件下没有可触达会员");
      db.campaigns.unshift({
        id: Date.now(),
        tenant: ui.tenant,
        name: form.name,
        couponId: form.couponId,
        tag: form.tag,
        minimumOrders: form.minimumOrders,
        recipients,
        sent: [],
        skipped: [],
        status: "待执行",
        scheduled: form.scheduled,
      });
      log("创建营销活动", form.name);
    }
    editor.value = false;
    notify(mode.value === "coupons" ? "优惠券已创建" : "活动已创建");
  } catch (e) {
    error.value = (e as Error).message;
  }
}
function toggleCoupon(c: Coupon) {
  attempt(async () => {
    requirePermission("marketing");
    await command(
      "coupon.status",
      { id: c.id, status: c.status === "发放中" ? "已暂停" : "发放中" },
      () => {
        c.status = c.status === "发放中" ? "已暂停" : "发放中";
        log("修改优惠券状态", c.name);
      },
    );
    notify(c.status === "发放中" ? "已恢复发放" : "已暂停新发放");
  });
}
const session = reactive(ui);
</script>
<template>
  <PageHeader :title="String(route.meta.title)" eyebrow="客户与营销"
    ><button
      v-if="mode === 'coupons' || mode === 'campaigns'"
      class="button primary"
      @click="openEditor"
    >
      <Icon name="plus" :size="16" />{{
        mode === "coupons" ? "创建优惠券" : "创建活动"
      }}</button
    ><button
      v-else-if="mode === 'segments'"
      class="button primary"
      @click="openEditor"
    >
      <Icon name="campaign" :size="16" />向该人群发券
    </button></PageHeader
  >
  <div v-if="mode === 'segments'" class="segment-layout">
    <section class="panel segment-builder">
      <div class="panel-heading"><h2>筛选会员</h2></div>
      <div class="form-padding">
        <label
          >会员标签<select v-model="tag">
            <option value="">不限标签</option>
            <option v-for="t in tags" :key="t">{{ t }}</option>
          </select></label
        ><label
          >购买次数不少于<input
            v-model.number="minimum"
            type="number"
            min="0"
            step="1"
        /></label>
        <div class="segment-result">
          <small>符合条件</small
          ><strong
            >{{ members.filter((m) => !m.optOut).length
            }}<span>人</span></strong
          >
          <p>{{ members.filter((m) => m.optOut).length }} 人已退出营销</p>
        </div>
      </div>
    </section>
    <section class="panel">
      <div class="panel-heading">
        <h2>人群预览</h2>
        <span class="muted small">截至 2026-10-19</span>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>会员</th>
              <th>标签</th>
              <th>购买次数</th>
              <th>营销状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in members" :key="m.id">
              <td>
                {{ m.name
                }}<small class="cell-sub">{{ maskPhone(m.phone) }}</small>
              </td>
              <td>
                <span v-for="t in m.tags" :key="t" class="tag">{{ t }}</span>
              </td>
              <td>{{ m.orders }}</td>
              <td>
                <span class="status" :class="m.optOut ? 'gray' : 'green'">{{
                  m.optOut ? "已退出" : "可触达"
                }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <Empty v-if="!members.length" title="没有符合条件的会员" icon="users" />
      </div>
    </section>
  </div>
  <section v-else class="panel">
    <div class="filter-bar">
      <label class="search-field"
        ><Icon name="search" :size="17" /><input
          v-model.trim="query"
          :placeholder="
            mode === 'members' ? '搜索会员姓名或手机号' : '搜索名称'
          "
          aria-label="搜索记录" /></label
      ><select v-if="mode === 'members'" v-model="tag" aria-label="筛选标签">
        <option value="">全部标签</option>
        <option v-for="t in tags" :key="t">{{ t }}</option></select
      ><button
        class="button text-button"
        @click="
          query = '';
          tag = '';
        "
      >
        重置</button
      ><span class="filter-count"
        >共
        {{
          mode === "members"
            ? members.length
            : mode === "coupons"
              ? coupons.length
              : campaigns.length
        }}
        条</span
      >
    </div>
    <div class="table-scroll">
      <table v-if="mode === 'members'">
        <thead>
          <tr>
            <th>会员</th>
            <th>会员标签</th>
            <th class="numeric">购买次数</th>
            <th class="numeric">商品实付</th>
            <th>注册时间</th>
            <th>营销状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in members" :key="m.id">
            <td>
              <div class="member-cell">
                <span class="member-avatar">{{ m.name.slice(-2) }}</span>
                <div>
                  <strong>{{ m.name }}</strong
                  ><small>{{ maskPhone(m.phone) }}</small>
                </div>
              </div>
            </td>
            <td>
              <span v-for="t in m.tags" :key="t" class="tag">{{ t }}</span>
            </td>
            <td class="numeric">{{ m.orders }}</td>
            <td class="numeric">¥{{ money(m.paid) }}</td>
            <td class="muted">{{ m.registered }}</td>
            <td>
              <span class="status" :class="m.optOut ? 'gray' : 'green'">{{
                m.optOut ? "已退出" : "可触达"
              }}</span>
            </td>
            <td>
              <button
                class="text-link"
                @click="
                  memberDetail = m;
                  tagText = m.tags.join(',');
                "
              >
                查看
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else-if="mode === 'coupons'">
        <thead>
          <tr>
            <th>优惠券</th>
            <th>使用门槛</th>
            <th>已发 / 总量</th>
            <th>每人限领</th>
            <th>有效期至</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in coupons" :key="c.id">
            <td>
              <div class="coupon-mini">
                <Icon name="coupon" />
                <div>
                  <strong>{{ c.name }}</strong
                  ><small>减 ¥{{ money(c.amount) }}</small>
                </div>
              </div>
            </td>
            <td>满 ¥{{ money(c.threshold) }}</td>
            <td>
              <div class="progress-label">{{ c.issued }} / {{ c.total }}</div>
              <div class="mini-progress">
                <i :style="{ width: `${(c.issued / c.total) * 100}%` }" />
              </div>
            </td>
            <td>{{ c.limit }} 张</td>
            <td>{{ c.end }}</td>
            <td>
              <span
                class="status"
                :class="c.status === '发放中' ? 'green' : 'gray'"
                >{{ c.status }}</span
              >
            </td>
            <td>
              <button class="text-link" @click="toggleCoupon(c)">
                {{ c.status === "发放中" ? "暂停发放" : "恢复发放" }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else>
        <thead>
          <tr>
            <th>活动名称</th>
            <th>目标人群</th>
            <th>计划时间</th>
            <th>发放进度</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in campaigns" :key="c.id">
            <td>
              <strong>{{ c.name }}</strong
              ><small class="cell-sub">{{
                db.coupons.find((x) => x.id === c.couponId)?.name
              }}</small>
            </td>
            <td>
              {{ c.tag || "全部会员"
              }}<small class="cell-sub">{{ c.recipients.length }} 人</small>
            </td>
            <td>{{ c.scheduled.replace("T", " ") }}</td>
            <td>
              {{ c.sent.length }} 成功 · {{ c.skipped.length }} 跳过
              <div class="mini-progress">
                <i
                  :style="{
                    width: `${c.recipients.length ? ((c.sent.length + c.skipped.length) / c.recipients.length) * 100 : 0}%`,
                  }"
                />
              </div>
            </td>
            <td>
              <span
                class="status"
                :class="
                  c.status === '已完成'
                    ? 'green'
                    : c.status === '已中止'
                      ? 'gray'
                      : 'amber'
                "
                >{{ c.status }}</span
              >
            </td>
            <td>
              <div class="row-actions">
                <button @click="campaignDetail = c">详情</button
                ><button
                  v-if="c.status === '待执行'"
                  @click="attempt(() => executeCampaign(c.id))"
                >
                  执行</button
                ><button
                  v-if="['待执行', '执行中'].includes(c.status)"
                  @click="stopCampaign = c"
                >
                  中止
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Empty
      v-if="
        !(mode === 'members'
          ? members.length
          : mode === 'coupons'
            ? coupons.length
            : campaigns.length)
      "
      title="暂无记录"
      icon="users"
    />
  </section>
  <Modal
    v-if="editor"
    :title="mode === 'coupons' ? '创建优惠券' : '创建营销活动'"
    @close="editor = false"
    ><form id="customer-form" @submit.prevent="save">
      <label
        >{{ mode === "coupons" ? "优惠券名称" : "活动名称"
        }}<input v-model.trim="form.name" required maxlength="40" /></label
      ><template v-if="mode === 'coupons'"
        ><div class="form-grid">
          <label
            >优惠金额（元）<input
              v-model.number="form.amount"
              type="number"
              min="0.01"
              step="0.01"
              required /></label
          ><label
            >使用门槛（元）<input
              v-model.number="form.threshold"
              type="number"
              min="0"
              step="0.01"
              required /></label
          ><label
            >发放总量<input
              v-model.number="form.total"
              type="number"
              min="1"
              step="1"
              required /></label
          ><label
            >每人限领<input
              v-model.number="form.limit"
              type="number"
              min="1"
              step="1"
              required
          /></label>
        </div>
        <label
          >有效期至<input
            v-model="form.end"
            type="date"
            required /></label></template
      ><template v-else
        ><label
          >发放优惠券<select v-model="form.couponId" required>
            <option
              v-for="c in db.coupons.filter(
                (c) => c.tenant === session.tenant && c.status === '发放中',
              )"
              :key="c.id"
              :value="c.id"
            >
              {{ c.name }} · 满{{ c.threshold / 100 }}减{{ c.amount / 100 }}
            </option>
          </select></label
        >
        <div class="form-grid">
          <label
            >会员标签<select v-model="form.tag">
              <option value="">不限标签</option>
              <option v-for="t in tags" :key="t">{{ t }}</option>
            </select></label
          ><label
            >购买次数不少于<input
              v-model.number="form.minimumOrders"
              type="number"
              min="0"
              step="1"
          /></label>
        </div>
        <label
          >执行时间<input
            v-model="form.scheduled"
            type="datetime-local"
            required
        /></label>
        <div class="context-block">
          <span>可触达会员</span
          ><strong
            >{{
              db.members.filter(
                (m) =>
                  m.tenant === session.tenant &&
                  (!form.tag || m.tags.includes(form.tag)) &&
                  m.orders >= form.minimumOrders &&
                  !m.optOut,
              ).length
            }}
            人</strong
          >
        </div></template
      >
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <template #footer
      ><button class="button" @click="editor = false">取消</button
      ><button class="button primary" form="customer-form" type="submit">
        {{ mode === "coupons" ? "创建优惠券" : "创建活动" }}
      </button></template
    ></Modal
  >
  <Modal v-if="memberDetail" title="会员详情" @close="memberDetail = undefined"
    ><div class="member-profile">
      <span class="member-avatar large">{{ memberDetail.name.slice(-2) }}</span>
      <div>
        <h3>{{ memberDetail.name }}</h3>
        <span class="muted">{{ maskPhone(memberDetail.phone) }}</span>
      </div>
    </div>
    <dl class="detail-grid">
      <div>
        <dt>注册时间</dt>
        <dd>{{ memberDetail.registered }}</dd>
      </div>
      <div>
        <dt>购买次数</dt>
        <dd>{{ memberDetail.orders }}</dd>
      </div>
      <div>
        <dt>商品实付</dt>
        <dd>¥{{ money(memberDetail.paid) }}</dd>
      </div>
      <div>
        <dt>营销状态</dt>
        <dd>{{ memberDetail.optOut ? "已退出营销" : "可触达" }}</dd>
      </div>
    </dl>
    <label
      >会员标签<input
        v-model="tagText"
        placeholder="多个标签用逗号分隔" /></label
    ><template #footer
      ><button class="button" @click="memberDetail = undefined">关闭</button
      ><button
        class="button primary"
        @click="
          attempt(async () => {
            requirePermission('member');
            const tags = [
              ...new Set(
                tagText
                  .split(/[,，]/)
                  .map((t) => t.trim())
                  .filter(Boolean),
              ),
            ];
            await command('member.tags', { id: memberDetail!.id, tags }, () => {
              memberDetail!.tags = tags;
              log('更新会员标签', memberDetail!.name);
            });
            memberDetail = undefined;
            notify('标签已保存');
          })
        "
      >
        保存标签
      </button></template
    ></Modal
  >
  <Modal v-if="stopCampaign" title="中止活动" @close="stopCampaign = undefined"
    ><p>确认中止「{{ stopCampaign.name }}」？</p>
    <p class="muted">已发放的优惠券继续有效，未发放部分停止。</p>
    <template #footer
      ><button class="button" @click="stopCampaign = undefined">取消</button
      ><button
        class="button danger"
        @click="
          attempt(async () => {
            requirePermission('marketing');
            await command('campaign.stop', { id: stopCampaign!.id }, () => {
              stopCampaign!.status = '已中止';
              log('中止活动', stopCampaign!.name);
            });
            stopCampaign = undefined;
            notify('活动已中止');
          })
        "
      >
        确认中止
      </button></template
    ></Modal
  >
  <Modal
    v-if="campaignDetail"
    title="活动结果"
    wide
    @close="campaignDetail = undefined"
    ><h3>{{ campaignDetail.name }}</h3>
    <div class="mini-stats">
      <div>
        <small>目标会员</small
        ><strong>{{ campaignDetail.recipients.length }}</strong>
      </div>
      <div>
        <small>成功发放</small><strong>{{ campaignDetail.sent.length }}</strong>
      </div>
      <div>
        <small>跳过</small><strong>{{ campaignDetail.skipped.length }}</strong>
      </div>
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>会员</th>
            <th>发放状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="id in campaignDetail.recipients" :key="id">
            <td>{{ db.members.find((m) => m.id === id)?.name }}</td>
            <td>
              {{
                campaignDetail.sent.includes(id)
                  ? "发放成功"
                  : campaignDetail.skipped.includes(id)
                    ? "已跳过（退出或达到领取上限）"
                    : "未执行"
              }}
            </td>
          </tr>
        </tbody>
      </table>
    </div></Modal
  >
</template>
