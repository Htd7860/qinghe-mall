<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import PageHeader from "../components/PageHeader.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import { allowed } from "../api/management";
import { ApiError } from "../api/http";
import { productPage } from "../api/product";
import { catalogList } from "../api/product-catalog";
import {
  couponPermission,
  couponIdentity,
  couponPage,
  couponGet,
  couponSave,
  couponStatus,
  couponDelete,
  type CouponTemplate,
  type CouponRules,
} from "../api/coupon-template";

const rows = ref<CouponTemplate[]>([]),
  total = ref(0),
  page = ref(1),
  search = ref(""),
  status = ref("");
const busy = ref(false),
  loading = ref(false),
  error = ref(""),
  notice = ref(""),
  uncertain = ref(false);
const editor = ref(false),
  editing = ref<CouponTemplate>(),
  detail = ref<CouponTemplate>(),
  deleting = ref<CouponTemplate>();
const modalError = ref(""),
  picking = ref(false),
  scopeSearch = ref(""),
  scopePage = ref(1),
  scopeTotal = ref(0),
  scopeLoading = ref(false);
const scopeOptions = ref<{ id: number; name: string }[]>([]),
  chosen = ref<{ id: number; name: string }[]>([]);
const form = reactive({
  name: "",
  description: "",
  totalUnlimited: false,
  totalCount: 1000,
  personUnlimited: false,
  takeLimitCount: 1,
  takeType: 1,
  useYuan: "0.00",
  productScope: 1,
  validityType: 2,
  start: "",
  end: "",
  days: 7,
  discountType: 1,
  discountYuan: "10.00",
  percent: 80,
  limitYuan: "20.00",
});
const can = (action: string) => allowed(couponPermission + action);
const locked = computed(() => busy.value || loading.value || uncertain.value);
const scopeAllowed = computed(() =>
  allowed(
    form.productScope === 2 ? "product:spu:query" : "product:category:query",
  ),
);
let epoch = 0,
  readSequence = 0,
  scopeSequence = 0;
const message = (e: unknown) =>
  e instanceof ApiError && e.code === 409
    ? "模板已变化，请重新读取后再操作"
    : e instanceof Error
      ? e.message
      : "操作未完成";
const yuan = (value: number) => (value / 100).toFixed(2);
function cents(text: string) {
  if (!/^\d{1,8}(?:\.\d{1,2})?$/.test(text.trim()))
    throw new Error("金额须为非负数，最多两位小数");
  const [whole, fraction = ""] = text.trim().split(".");
  const value = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(value) || value > 2_000_000_000)
    throw new Error("金额超出支持范围");
  return value;
}
function date(text: string) {
  const value = text.length === 16 ? `${text}:00` : text;
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(value))
    throw new Error("请填写完整有效期");
  return value.replace("T", " ");
}
async function load() {
  const ticket = epoch,
    sequence = ++readSequence;
  loading.value = true;
  error.value = "";
  rows.value = [];
  try {
    const result = await couponPage(
      page.value,
      search.value.trim(),
      status.value,
    );
    if (ticket !== epoch || sequence !== readSequence) return false;
    rows.value = result.list;
    total.value = result.total;
    return true;
  } catch (e) {
    if (ticket === epoch && sequence === readSequence) error.value = message(e);
    return false;
  } finally {
    if (ticket === epoch && sequence === readSequence) loading.value = false;
  }
}
watch(
  couponIdentity,
  () => {
    epoch++;
    readSequence++;
    scopeSequence++;
    busy.value = false;
    editor.value = false;
    picking.value = false;
    editing.value = detail.value = deleting.value = undefined;
    chosen.value = [];
    scopeOptions.value = [];
    uncertain.value = false;
    page.value = 1;
    notice.value = "";
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  epoch++;
  readSequence++;
  scopeSequence++;
  rows.value = [];
  detail.value = editing.value = deleting.value = undefined;
});
function reset() {
  Object.assign(form, {
    name: "",
    description: "",
    totalUnlimited: false,
    totalCount: 1000,
    personUnlimited: false,
    takeLimitCount: 1,
    takeType: 1,
    useYuan: "0.00",
    productScope: 1,
    validityType: 2,
    start: "",
    end: "",
    days: 7,
    discountType: 1,
    discountYuan: "10.00",
    percent: 80,
    limitYuan: "20.00",
  });
  chosen.value = [];
}
async function open(row?: CouponTemplate, readOnly = false) {
  if (locked.value || !can(readOnly ? "query" : row ? "update" : "create"))
    return;
  const ticket = epoch;
  busy.value = true;
  error.value = "";
  modalError.value = "";
  try {
    const current = row ? await couponGet(row.id) : undefined;
    if (ticket !== epoch) return;
    if (readOnly && current) {
      detail.value = current;
      return;
    }
    reset();
    editing.value = current;
    if (current) {
      Object.assign(form, {
        name: current.name,
        description: current.description,
        totalUnlimited: current.totalCount === -1,
        totalCount: current.totalCount === -1 ? 1000 : current.totalCount,
        personUnlimited: current.takeLimitCount === -1,
        takeLimitCount:
          current.takeLimitCount === -1 ? 1 : current.takeLimitCount,
        takeType: current.takeType,
        useYuan: yuan(current.usePrice),
        productScope: current.productScope,
        validityType: current.validityType,
        start: current.validStartTime?.replace(" ", "T") || "",
        end: current.validEndTime?.replace(" ", "T") || "",
        days: current.fixedEndTerm || 7,
        discountType: current.discountType,
        discountYuan: yuan(current.discountPrice || 0),
        percent: current.discountPercent || 80,
        limitYuan: yuan(current.discountLimitPrice || 0),
      });
      chosen.value = current.productScopeValues.map((id) => ({
        id,
        name: `${current.productScope === 2 ? "商品" : "分类"} #${id}`,
      }));
    }
    editor.value = true;
  } catch (e) {
    if (ticket === epoch) error.value = message(e);
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
async function mutate(action: () => Promise<unknown>, success: string) {
  if (locked.value) return;
  const ticket = epoch;
  busy.value = true;
  error.value = modalError.value = "";
  notice.value = "";
  try {
    await action();
    if (ticket !== epoch) return;
    editor.value = false;
    deleting.value = undefined;
    detail.value = undefined;
    if (await load()) notice.value = success;
    else if (ticket === epoch) {
      uncertain.value = true;
      error.value = "操作已提交，但最新列表读取失败，请重新读取核对";
    }
  } catch (e) {
    if (ticket !== epoch) return;
    uncertain.value = e instanceof ApiError && e.outcomeUnknown;
    error.value = modalError.value = uncertain.value
      ? "操作结果待确认，请重新读取并核对，勿重复提交"
      : message(e);
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
function payload(): CouponRules {
  const budget = (unlimited: boolean, value: number) => {
    if (unlimited) return -1;
    if (!Number.isSafeInteger(value) || value < 1 || value > 2_000_000_000)
      throw new Error("发放数量须为有效正整数");
    return value;
  };
  const totalCount = budget(form.totalUnlimited, form.totalCount),
    takeLimitCount = budget(form.personUnlimited, form.takeLimitCount);
  if (totalCount !== -1 && takeLimitCount !== -1 && takeLimitCount > totalCount)
    throw new Error("每人限领不能超过发行总量");
  if (
    editing.value &&
    totalCount !== -1 &&
    totalCount < editing.value.takeCount
  )
    throw new Error("发行总量不能小于已成功发放数量");
  if (
    form.productScope !== 1 &&
    (!chosen.value.length || chosen.value.length > 200)
  )
    throw new Error("请选择1至200个适用商品或分类");
  const start = form.validityType === 1 ? date(form.start) : null,
    end = form.validityType === 1 ? date(form.end) : null;
  if (start && end && start >= end) throw new Error("结束时间须晚于开始时间");
  if (
    form.validityType === 2 &&
    (!Number.isSafeInteger(form.days) || form.days < 1 || form.days > 3650)
  )
    throw new Error("领取后有效天数须为1至3650的整数");
  const discountPrice =
      form.discountType === 1 ? cents(form.discountYuan) : null,
    discountLimitPrice = form.discountType === 2 ? cents(form.limitYuan) : null;
  if (
    (form.discountType === 1 && !discountPrice) ||
    (form.discountType === 2 &&
      (!Number.isSafeInteger(form.percent) ||
        form.percent < 1 ||
        form.percent > 99 ||
        !discountLimitPrice))
  )
    throw new Error("优惠金额、支付比例或优惠上限不正确");
  return {
    name: form.name.trim(),
    description: form.description,
    totalCount,
    takeLimitCount,
    takeType: form.takeType,
    usePrice: cents(form.useYuan),
    productScope: form.productScope,
    productScopeValues:
      form.productScope === 1 ? [] : chosen.value.map((x) => x.id),
    validityType: form.validityType,
    validStartTime: start,
    validEndTime: end,
    fixedStartTerm: form.validityType === 2 ? 0 : null,
    fixedEndTerm: form.validityType === 2 ? form.days : null,
    discountType: form.discountType,
    discountPrice,
    discountPercent: form.discountType === 2 ? form.percent : null,
    discountLimitPrice,
  };
}
function save() {
  if (!can(editing.value ? "update" : "create")) return;
  try {
    const body = payload();
    void mutate(() => couponSave(body, editing.value), "优惠券模板已保存");
  } catch (e) {
    modalError.value = message(e);
  }
}
function toggle(row: CouponTemplate) {
  if (can("update"))
    void mutate(
      () => couponStatus(row, row.status === 0 ? 1 : 0),
      row.status === 0 ? "已暂停新领取与发放" : "模板已启用",
    );
}
async function scopeLoad() {
  if (!scopeAllowed.value || busy.value) return;
  const ticket = epoch,
    sequence = ++scopeSequence,
    kind = form.productScope;
  scopeLoading.value = true;
  scopeOptions.value = [];
  modalError.value = "";
  try {
    if (kind === 2) {
      const result = await productPage(
        scopePage.value,
        undefined,
        scopeSearch.value.trim(),
      );
      if (ticket !== epoch || sequence !== scopeSequence) return;
      scopeOptions.value = result.list
        .filter((x) => x.status !== -1)
        .map((x) => ({ id: x.id, name: x.name }));
      scopeTotal.value = result.total;
    } else {
      const result = await catalogList("categories", 1, "");
      if (ticket !== epoch || sequence !== scopeSequence) return;
      const byId = new Map(result.list.map((x) => [x.id, x]));
      const labels = result.list
        .filter((x) => x.status === 0)
        .map((x) => {
          const names: string[] = [x.name];
          let p = x.parentId,
            steps = 0;
          while (p && byId.has(p) && steps++ < 3) {
            const parent = byId.get(p)!;
            names.unshift(parent.name);
            p = parent.parentId;
          }
          return { id: x.id, name: names.join(" / ") };
        });
      const filtered = labels.filter((x) =>
        x.name.includes(scopeSearch.value.trim()),
      );
      scopeTotal.value = filtered.length;
      scopeOptions.value = filtered.slice(
        (scopePage.value - 1) * 20,
        scopePage.value * 20,
      );
    }
    for (const option of scopeOptions.value) {
      const selected = chosen.value.find((x) => x.id === option.id);
      if (selected) selected.name = option.name;
    }
  } catch (e) {
    if (ticket === epoch && sequence === scopeSequence)
      modalError.value = message(e);
  } finally {
    if (ticket === epoch && sequence === scopeSequence)
      scopeLoading.value = false;
  }
}
function choose(option: { id: number; name: string }) {
  if (chosen.value.some((x) => x.id === option.id))
    chosen.value = chosen.value.filter((x) => x.id !== option.id);
  else if (chosen.value.length < 200) chosen.value.push({ ...option });
  else modalError.value = "最多选择200项";
}
async function reconcile() {
  editor.value = picking.value = false;
  deleting.value = detail.value = undefined;
  if (await load()) {
    uncertain.value = false;
    notice.value = "已读取最新列表，请核对刚才的操作结果";
  }
}
const discount = (row: CouponTemplate) =>
  row.discountType === 1
    ? `减 ¥${yuan(row.discountPrice!)}`
    : `${row.discountPercent! / 10}折，最多减 ¥${yuan(row.discountLimitPrice!)}`;
</script>

<template>
  <PageHeader title="优惠券模板" eyebrow="客户与营销"
    ><button
      v-if="can('create')"
      class="button primary"
      :disabled="locked"
      @click="open()"
    >
      新增模板
    </button></PageHeader
  >
  <div class="panel">
    <form
      class="management-filters"
      @submit.prevent="
        page = 1;
        load();
      "
    >
      <label>模板名称<input v-model="search" maxlength="64" /></label>
      <label
        >状态<select v-model="status">
          <option value="">全部</option>
          <option value="0">启用</option>
          <option value="1">暂停</option>
        </select></label
      >
      <button class="button" :disabled="busy || loading">查询</button
      ><button
        class="button"
        type="button"
        :disabled="busy || loading"
        @click="reconcile"
      >
        重新读取
      </button>
    </form>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="error" class="management-alert" role="alert">{{ error }}</p>
    <p v-if="uncertain" role="alert">
      请核对最新列表与详情后再操作，避免重复创建。
    </p>
    <p v-if="loading" role="status">正在读取…</p>
    <div v-else-if="rows.length" class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>模板名称</th>
            <th>优惠</th>
            <th>商品金额门槛</th>
            <th>有效期</th>
            <th>成功发放 / 总量</th>
            <th>领取方式</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>
              <button
                class="text-link"
                :disabled="locked"
                @click="open(row, true)"
              >
                {{ row.name }}
              </button>
            </td>
            <td>{{ discount(row) }}</td>
            <td>{{ row.usePrice ? `满 ¥${yuan(row.usePrice)}` : "无门槛" }}</td>
            <td>
              {{
                row.validityType === 1
                  ? `${row.validStartTime} 至 ${row.validEndTime}`
                  : `领取起${row.fixedEndTerm}天`
              }}
            </td>
            <td>
              {{ row.takeCount }} /
              {{ row.totalCount === -1 ? "不限" : row.totalCount }}
            </td>
            <td>{{ row.takeType === 1 ? "会员领取" : "定向发放" }}</td>
            <td>
              <span class="tag">{{ row.status === 0 ? "启用" : "暂停" }}</span>
            </td>
            <td>
              <div class="management-row-actions">
                <button
                  v-if="can('update')"
                  class="text-link"
                  :disabled="locked"
                  @click="open(row)"
                >
                  编辑</button
                ><button
                  v-if="can('update')"
                  class="text-link"
                  :disabled="locked"
                  @click="toggle(row)"
                >
                  {{ row.status === 0 ? "暂停" : "启用" }}
                </button>
                <button
                  v-if="can('delete')"
                  class="text-link management-danger"
                  :disabled="locked || row.status !== 1 || row.takeCount !== 0"
                  title="仅可删除已暂停且未发放的模板"
                  @click="deleting = row"
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Empty v-else-if="!loading && !error" title="暂无优惠券模板" />
    <div v-if="!error && !loading" class="pagination">
      <span>共 {{ total }} 条</span>
      <div class="pagination-right">
        <button
          class="button"
          :disabled="locked || page <= 1"
          @click="
            page--;
            load();
          "
        >
          上一页</button
        ><span>第 {{ page }} 页</span
        ><button
          class="button"
          :disabled="locked || page * 20 >= total"
          @click="
            page++;
            load();
          "
        >
          下一页
        </button>
      </div>
    </div>
  </div>
  <Modal
    v-if="editor"
    :title="editing ? '编辑优惠券模板' : '新增优惠券模板'"
    wide
    @close="!busy && (editor = false)"
  >
    <form id="coupon-template-editor" @submit.prevent="save">
      <fieldset class="management-fieldset" :disabled="locked">
        <label
          >模板名称<input v-model="form.name" required maxlength="64" /></label
        ><label
          >使用说明<textarea v-model="form.description" maxlength="500" />
        </label>
        <div class="form-grid">
          <label class="checkbox-label"
            ><input
              v-model="form.totalUnlimited"
              type="checkbox"
            />不限发行总量</label
          ><label v-if="!form.totalUnlimited"
            >发行总量<input
              v-model.number="form.totalCount"
              type="number"
              min="1"
              max="2000000000"
              step="1"
              required
          /></label>
          <label class="checkbox-label"
            ><input
              v-model="form.personUnlimited"
              type="checkbox"
            />不限每人领取数量</label
          ><label v-if="!form.personUnlimited"
            >每人限领<input
              v-model.number="form.takeLimitCount"
              type="number"
              min="1"
              max="2000000000"
              step="1"
              required
          /></label>
        </div>
        <div class="form-grid">
          <label
            >领取方式<select v-model.number="form.takeType">
              <option :value="1">会员主动领取</option>
              <option :value="2">定向发放</option>
            </select></label
          ><label
            >适用商品金额门槛（元）<input
              v-model="form.useYuan"
              inputmode="decimal"
              required
          /></label>
        </div>
        <label
          >商品范围<select
            v-model.number="form.productScope"
            @change="
              chosen = [];
              picking = false;
              scopeSequence++;
            "
          >
            <option :value="1">全部商品</option>
            <option :value="2">指定商品</option>
            <option :value="3">指定分类</option>
          </select></label
        >
        <template v-if="form.productScope !== 1"
          ><p>
            已选 {{ chosen.length }} 项：{{
              chosen.map((x) => x.name).join("、") || "尚未选择"
            }}
          </p>
          <button
            class="button"
            type="button"
            :disabled="!scopeAllowed"
            @click="
              picking = true;
              scopePage = 1;
              scopeSearch = '';
              scopeLoad();
            "
          >
            选择适用范围
          </button>
          <p v-if="!scopeAllowed" class="muted">
            修改选择范围需要相应的商品或分类查询权限；已有范围会保留。
          </p></template
        >
        <label
          >有效期<select v-model.number="form.validityType">
            <option :value="1">固定日期（北京时间）</option>
            <option :value="2">领取后连续天数</option>
          </select></label
        >
        <div v-if="form.validityType === 1" class="form-grid">
          <label
            >开始时间<input
              v-model="form.start"
              type="datetime-local"
              step="1"
              min="1000-01-01T00:00:00"
              max="9999-12-31T23:59:59"
              required /></label
          ><label
            >结束时间<input
              v-model="form.end"
              type="datetime-local"
              step="1"
              min="1000-01-01T00:00:00"
              max="9999-12-31T23:59:59"
              required
          /></label>
        </div>
        <label v-else
          >领取后有效天数<input
            v-model.number="form.days"
            type="number"
            min="1"
            max="3650"
            step="1"
            required
        /></label>
        <label
          >优惠类型<select v-model.number="form.discountType">
            <option :value="1">满减券</option>
            <option :value="2">折扣券</option>
          </select></label
        >
        <label v-if="form.discountType === 1"
          >减免金额（元）<input
            v-model="form.discountYuan"
            inputmode="decimal"
            required
        /></label>
        <div v-else class="form-grid">
          <label
            >支付比例（%，80表示8折）<input
              v-model.number="form.percent"
              type="number"
              min="1"
              max="99"
              step="1"
              required /></label
          ><label
            >最高减免金额（元）<input
              v-model="form.limitYuan"
              inputmode="decimal"
              required
          /></label>
        </div>
        <p class="muted">
          修改仅影响后续发放，已领取券的规则保持不变；暂停只停止新领取和新发放。
        </p>
      </fieldset>
      <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
    </form>
    <template #footer
      ><button class="button" :disabled="busy" @click="editor = false">
        取消</button
      ><button
        class="button primary"
        type="submit"
        form="coupon-template-editor"
        :disabled="locked || !can(editing ? 'update' : 'create')"
      >
        {{ busy ? "正在保存…" : "保存模板" }}</button
      ><button
        v-if="uncertain"
        class="button"
        :disabled="busy || loading"
        @click="reconcile"
      >
        重新读取核对
      </button></template
    >
  </Modal>
  <Modal
    v-if="picking"
    title="选择适用范围"
    wide
    @close="
      picking = false;
      scopeSequence++;
    "
    ><form
      class="management-filters"
      @submit.prevent="
        scopePage = 1;
        scopeLoad();
      "
    >
      <label>名称<input v-model="scopeSearch" maxlength="64" /></label
      ><button class="button" :disabled="scopeLoading">查询</button>
    </form>
    <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
    <p v-if="scopeLoading" role="status">正在读取…</p>
    <label
      v-for="option in scopeOptions"
      :key="option.id"
      class="checkbox-label"
      ><input
        type="checkbox"
        :checked="chosen.some((x) => x.id === option.id)"
        @change="choose(option)"
      />{{ option.name }}（{{ option.id }}）</label
    >
    <Empty
      v-if="!scopeLoading && !scopeOptions.length && !modalError"
      title="没有符合条件的商品或分类"
    />
    <div class="pagination">
      <button
        class="button"
        :disabled="scopeLoading || scopePage <= 1"
        @click="
          scopePage--;
          scopeLoad();
        "
      >
        上一页</button
      ><span>已选 {{ chosen.length }} 项</span
      ><button
        class="button"
        :disabled="scopeLoading || scopePage * 20 >= scopeTotal"
        @click="
          scopePage++;
          scopeLoad();
        "
      >
        下一页
      </button>
    </div>
    <template #footer
      ><button
        class="button primary"
        @click="
          picking = false;
          scopeSequence++;
        "
      >
        完成选择
      </button></template
    >
  </Modal>
  <Modal v-if="detail" title="优惠券模板详情" @close="detail = undefined"
    ><dl class="detail-grid">
      <dt>模板编号</dt>
      <dd>{{ detail.id }}</dd>
      <dt>名称</dt>
      <dd>{{ detail.name }}</dd>
      <dt>使用说明</dt>
      <dd>{{ detail.description || "无" }}</dd>
      <dt>优惠</dt>
      <dd>{{ discount(detail) }}</dd>
      <dt>商品金额门槛</dt>
      <dd>¥{{ yuan(detail.usePrice) }}</dd>
      <dt>适用范围</dt>
      <dd>
        {{
          detail.productScope === 1
            ? "全部商品"
            : `${detail.productScope === 2 ? "商品" : "分类"}：${detail.productScopeValues.join("、")}`
        }}
      </dd>
      <dt>每人限领</dt>
      <dd>
        {{ detail.takeLimitCount === -1 ? "不限" : detail.takeLimitCount }}
      </dd>
      <dt>有效期</dt>
      <dd>
        {{
          detail.validityType === 1
            ? `${detail.validStartTime} 至 ${detail.validEndTime}（北京时间）`
            : `领取起连续${detail.fixedEndTerm}天`
        }}
      </dd>
    </dl></Modal
  >
  <Modal
    v-if="deleting"
    title="删除优惠券模板"
    @close="!busy && (deleting = undefined)"
    ><p>确认删除“{{ deleting.name }}”？仅已暂停且从未发放的模板可以删除。</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <template #footer
      ><button class="button" :disabled="busy" @click="deleting = undefined">
        取消</button
      ><button
        class="button primary"
        :disabled="locked || !can('delete')"
        @click="mutate(() => couponDelete(deleting!), '优惠券模板已删除')"
      >
        确认删除
      </button></template
    ></Modal
  >
</template>
