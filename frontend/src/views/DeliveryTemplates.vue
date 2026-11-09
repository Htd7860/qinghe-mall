<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import PageHeader from "../components/PageHeader.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import { allowed } from "../api/management";
import { ApiError } from "../api/http";
import {
  deliveryAreas,
  deliveryDelete,
  deliveryGet,
  deliveryIdentity,
  deliveryPage,
  deliveryPermission,
  deliverySave,
  type AreaOption,
  type DeliveryRow,
} from "../api/delivery";
type Charge = {
  id?: number;
  defaultRule: boolean;
  areaIds: number[];
  startCount: string;
  extraCount: string;
  startYuan: string;
  extraYuan: string;
};
type Free = {
  id?: number;
  defaultRule: boolean;
  areaIds: number[];
  freeYuan: string;
  freeCount: number;
};
const rows = ref<DeliveryRow[]>([]),
  total = ref(0),
  page = ref(1),
  search = ref("");
const busy = ref(false),
  loading = ref(false),
  error = ref(""),
  notice = ref("");
const editor = ref(false),
  editing = ref<DeliveryRow>(),
  deleting = ref<DeliveryRow>(),
  modalError = ref(""),
  uncertain = ref(false);
const areas = ref<AreaOption[]>([]),
  picking = ref<{ kind: "charges" | "frees"; index: number }>(),
  areaSearch = ref("");
const form = reactive({
  name: "",
  chargeMode: 1,
  status: 0,
  sort: 0,
  charges: [] as Charge[],
  frees: [] as Free[],
});
const can = (action: string) => allowed(deliveryPermission + action);
let epoch = 0,
  sequence = 0;
const areaMap = computed(
  () => new Map(areas.value.map((area) => [area.id, area])),
);
const chosenRule = computed(() =>
  picking.value ? form[picking.value.kind][picking.value.index] : undefined,
);
function label(id: number) {
  const names: string[] = [],
    visited = new Set<number>();
  let current = areaMap.value.get(id);
  while (current && !visited.has(current.id)) {
    visited.add(current.id);
    names.unshift(current.name);
    current = areaMap.value.get(current.parentId);
  }
  return names.join(" / ") || String(id);
}
const options = computed(() =>
  areas.value
    .filter((area) =>
      areaSearch.value.trim()
        ? label(area.id).includes(areaSearch.value.trim())
        : area.type === 2,
    )
    .slice(0, 100),
);
const message = (cause: unknown) =>
  cause instanceof Error ? cause.message : "操作未完成，请稍后重试";
const yuan = (value: number) => (value / 100).toFixed(2);
function cents(value: string) {
  if (!/^\d{1,8}(?:\.\d{1,2})?$/.test(value.trim()))
    throw new Error("金额须为非负数，最多两位小数");
  const [whole, fraction = ""] = value.trim().split(".");
  const result = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(result) || result > 2147483647)
    throw new Error("金额超出支持范围");
  return result;
}
function decimal(value: string) {
  if (!/^\d{1,12}(?:\.\d{1,6})?$/.test(value) || /^0+(?:\.0+)?$/.test(value))
    throw new Error("首件与续件计量必须为正数，最多六位小数");
  if (form.chargeMode === 1 && !/^\d+(?:\.0{1,6})?$/.test(value))
    throw new Error("按件计费的首件、续件数量必须为整数");
  return value;
}
function covers(parent: number, child: number) {
  const seen = new Set<number>();
  let cursor = areaMap.value.get(child);
  while (cursor && !seen.has(cursor.id)) {
    if (cursor.id === parent) return true;
    seen.add(cursor.id);
    cursor = areaMap.value.get(cursor.parentId);
  }
  return false;
}
function toggleArea(id: number) {
  const rule = chosenRule.value;
  if (!rule || busy.value) return;
  if (rule.areaIds.includes(id))
    rule.areaIds = rule.areaIds.filter((value) => value !== id);
  else
    rule.areaIds = [
      ...rule.areaIds.filter(
        (value) => !covers(id, value) && !covers(value, id),
      ),
      id,
    ];
}
function regions(
  rules: { defaultRule: boolean; areaIds: number[] }[],
  title: string,
) {
  const selected = new Set<number>(),
    ancestors = new Set<number>();
  let defaults = 0;
  for (const rule of rules) {
    if (rule.defaultRule) {
      if (++defaults > 1 || rule.areaIds.length)
        throw new Error(`${title}最多一条默认规则，默认规则不指定地区`);
      continue;
    }
    if (!rule.areaIds.length) throw new Error(`请为${title}选择地区`);
    for (const id of rule.areaIds) {
      if (!areaMap.value.has(id) || ancestors.has(id))
        throw new Error(`${title}的地区不存在或重叠`);
      let cursor = areaMap.value.get(id);
      const visited = new Set<number>();
      while (cursor && !visited.has(cursor.id)) {
        if (selected.has(cursor.id))
          throw new Error(`${title}的省市区存在重叠`);
        visited.add(cursor.id);
        cursor = areaMap.value.get(cursor.parentId);
      }
      selected.add(id);
      cursor = areaMap.value.get(id);
      while (cursor && cursor.parentId) {
        ancestors.add(cursor.parentId);
        cursor = areaMap.value.get(cursor.parentId);
      }
    }
  }
}
async function load() {
  const generation = epoch,
    current = ++sequence;
  loading.value = true;
  error.value = "";
  rows.value = [];
  total.value = 0;
  try {
    if (!can("query")) throw new ApiError("没有配送模板查询权限", 403);
    const result = await deliveryPage(page.value, search.value.trim());
    if (generation === epoch && current === sequence) {
      rows.value = result.list;
      total.value = result.total;
    }
  } catch (cause) {
    if (generation === epoch && current === sequence)
      error.value = message(cause);
  } finally {
    if (generation === epoch && current === sequence) loading.value = false;
  }
}
watch(
  deliveryIdentity,
  () => {
    epoch++;
    editor.value = false;
    editing.value = undefined;
    deleting.value = undefined;
    picking.value = undefined;
    busy.value = false;
    notice.value = "";
    areas.value = [];
    form.name = "";
    form.charges = [];
    form.frees = [];
    page.value = 1;
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  epoch++;
});
function addCharge() {
  if (form.charges.length >= 200) return;
  form.charges.push({
    defaultRule: form.charges.length === 0,
    areaIds: [],
    startCount: "1",
    extraCount: "1",
    startYuan: "",
    extraYuan: "",
  });
}
function addFree() {
  if (form.frees.length >= 200) return;
  form.frees.push({
    defaultRule: form.frees.length === 0,
    areaIds: [],
    freeYuan: "",
    freeCount: 0,
  });
}
function removeRule(kind: "charges" | "frees", index: number) {
  picking.value = undefined;
  form[kind].splice(index, 1);
}
function choose(kind: "charges" | "frees", index: number) {
  picking.value = { kind, index };
  areaSearch.value = "";
}
async function open(row?: DeliveryRow) {
  if (busy.value) return;
  const generation = epoch;
  busy.value = true;
  error.value = "";
  try {
    const [detail, options] = await Promise.all([
      row ? deliveryGet(row.id) : Promise.resolve(undefined),
      deliveryAreas(),
    ]);
    if (generation !== epoch) return;
    areas.value = options;
    editing.value = detail;
    picking.value = undefined;
    modalError.value = "";
    uncertain.value = false;
    Object.assign(form, {
      name: detail?.name ?? "",
      chargeMode: detail?.chargeMode ?? 1,
      status: detail?.status ?? 0,
      sort: detail?.sort ?? 0,
    });
    form.charges = detail
      ? detail.charges.map((item) => ({
          id: item.id,
          defaultRule: item.defaultRule,
          areaIds: [...item.areaIds],
          startCount: item.startCount,
          extraCount: item.extraCount,
          startYuan: yuan(item.startPrice),
          extraYuan: yuan(item.extraPrice),
        }))
      : [];
    form.frees = detail
      ? detail.frees.map((item) => ({
          id: item.id,
          defaultRule: item.defaultRule,
          areaIds: [...item.areaIds],
          freeYuan: yuan(item.freePrice),
          freeCount: item.freeCount,
        }))
      : [];
    if (!detail) addCharge();
    editor.value = true;
  } catch (cause) {
    if (generation === epoch) error.value = message(cause);
  } finally {
    if (generation === epoch) busy.value = false;
  }
}
function failure(cause: unknown) {
  uncertain.value =
    cause instanceof ApiError &&
    (cause.outcomeUnknown || [404, 409].includes(cause.code));
  modalError.value = uncertain.value
    ? "记录或操作结果需要确认，请关闭弹窗并重新读取列表后再操作。"
    : message(cause);
}
async function save() {
  if (
    busy.value ||
    uncertain.value ||
    !can(editing.value ? "update" : "create")
  )
    return;
  const generation = epoch;
  modalError.value = "";
  try {
    if (!form.name.trim() || !form.charges.length)
      throw new Error("请填写模板名称并配置计费规则");
    regions(form.charges, "计费规则");
    regions(form.frees, "包邮规则");
    const charges = form.charges.map((item) => ({
      id: item.id,
      defaultRule: item.defaultRule,
      areaIds: item.areaIds,
      startCount: decimal(item.startCount),
      extraCount: decimal(item.extraCount),
      startPrice: cents(item.startYuan),
      extraPrice: cents(item.extraYuan),
    }));
    const frees = form.frees.map((item) => {
      const price = cents(item.freeYuan);
      if (
        !Number.isSafeInteger(item.freeCount) ||
        item.freeCount < 0 ||
        item.freeCount > 2147483647 ||
        (price === 0 && item.freeCount === 0)
      )
        throw new Error("包邮须设置正金额或正件数门槛");
      return {
        id: item.id,
        defaultRule: item.defaultRule,
        areaIds: item.areaIds,
        freePrice: price,
        freeCount: item.freeCount,
      };
    });
    const body: Record<string, unknown> = {
      name: form.name.trim(),
      chargeMode: form.chargeMode,
      status: form.status,
      sort: form.sort,
      charges,
      frees,
    };
    if (editing.value)
      Object.assign(body, {
        id: editing.value.id,
        expectedVersion: editing.value.authVersion,
      });
    busy.value = true;
    await deliverySave(body, !!editing.value);
    if (generation !== epoch) return;
    editor.value = false;
    notice.value = "配送模板已保存";
    await load();
  } catch (cause) {
    if (generation === epoch) failure(cause);
  } finally {
    if (generation === epoch) busy.value = false;
  }
}
function confirmDelete(row: DeliveryRow) {
  deleting.value = row;
  modalError.value = "";
  uncertain.value = false;
}
async function remove() {
  if (!deleting.value || busy.value || uncertain.value || !can("delete"))
    return;
  const generation = epoch;
  busy.value = true;
  modalError.value = "";
  try {
    await deliveryDelete(deleting.value);
    if (generation !== epoch) return;
    deleting.value = undefined;
    notice.value = "配送模板已删除";
    await load();
  } catch (cause) {
    if (generation === epoch) failure(cause);
  } finally {
    if (generation === epoch) busy.value = false;
  }
}
</script>
<template>
  <div class="management-page">
    <PageHeader title="运费模板" eyebrow="配送管理"
      ><button class="button" :disabled="busy || loading" @click="load">
        重新读取</button
      ><button
        v-if="can('create')"
        class="button primary"
        :disabled="busy || loading"
        @click="open()"
      >
        新增模板
      </button></PageHeader
    >
    <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>
    <div class="panel">
      <form
        class="filter-bar"
        @submit.prevent="
          page = 1;
          load();
        "
      >
        <label class="management-filter"
          >模板名称<input v-model="search" maxlength="100" /></label
        ><button class="button" :disabled="busy || loading">查询</button>
      </form>
      <div v-if="error" class="management-alert" role="alert">{{ error }}</div>
      <p v-if="loading" class="management-empty" role="status">正在读取…</p>
      <div v-else-if="rows.length" class="management-table-scroll">
        <table class="management-table">
          <thead>
            <tr>
              <th>模板名称</th>
              <th>计费方式</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>{{ row.name }}</td>
              <td>{{ row.chargeMode === 1 ? "按件" : "按重量" }}</td>
              <td>{{ row.status === 0 ? "启用" : "停用" }}</td>
              <td>
                <div class="management-row-actions">
                  <button
                    v-if="can('update')"
                    class="text-link"
                    :disabled="busy"
                    @click="open(row)"
                  >
                    修改</button
                  ><button
                    v-if="can('delete')"
                    class="text-link management-danger"
                    :disabled="busy"
                    @click="confirmDelete(row)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Empty v-else-if="!error" title="暂无运费模板"
        ><p>配置配送地区、首费和续费后，商品才可选择该模板。</p></Empty
      >
      <div class="pagination">
        <span>共 {{ total }} 条</span>
        <div class="pagination-right">
          <button
            class="button"
            :disabled="busy || loading || page <= 1"
            @click="
              page--;
              load();
            "
          >
            上一页</button
          ><span>第 {{ page }} 页</span
          ><button
            class="button"
            :disabled="busy || loading || page * 20 >= total"
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
      :title="editing ? '修改运费模板' : '新增运费模板'"
      wide
      @close="!busy && (editor = false)"
    >
      <form id="delivery-editor" @submit.prevent="save">
        <fieldset class="management-fieldset" :disabled="busy || uncertain">
          <div class="form-grid">
            <label
              >模板名称<input
                v-model="form.name"
                required
                maxlength="100" /></label
            ><label
              >计费方式<select v-model.number="form.chargeMode">
                <option :value="1">按件</option>
                <option :value="2">按重量（kg）</option>
              </select></label
            ><label
              >状态<select v-model.number="form.status">
                <option :value="0">启用</option>
                <option :value="1">停用</option>
              </select></label
            ><label
              >排序<input
                v-model.number="form.sort"
                type="number"
                required
                min="0"
                max="10000"
                step="1"
            /></label>
          </div>
          <h3 class="management-section-title">计费规则</h3>
          <p class="muted">
            同模板合并计量，只收一次首费。默认规则适用于未匹配地区；未配置默认规则时，未匹配地区不配送。
          </p>
          <section
            v-for="(rule, index) in form.charges"
            :key="index"
            class="delivery-rule"
          >
            <div class="delivery-rule-heading">
              <strong>计费规则 {{ index + 1 }}</strong
              ><button
                type="button"
                class="text-link management-danger"
                @click="removeRule('charges', index)"
              >
                移除
              </button>
            </div>
            <label class="management-checkbox"
              ><input
                v-model="rule.defaultRule"
                type="checkbox"
                @change="rule.defaultRule && (rule.areaIds = [])"
              />默认地区规则</label
            >
            <template v-if="!rule.defaultRule"
              ><button
                type="button"
                class="button"
                @click="choose('charges', index)"
              >
                选择地区
              </button>
              <p class="muted">
                {{ rule.areaIds.map(label).join("、") || "尚未选择地区" }}
              </p></template
            >
            <div class="form-grid">
              <label
                >{{ form.chargeMode === 1 ? "首件数量" : "首重（kg）"
                }}<input
                  v-model="rule.startCount"
                  inputmode="decimal"
                  required
                  maxlength="20" /></label
              ><label
                >首费（元）<input
                  v-model="rule.startYuan"
                  inputmode="decimal"
                  required
                  maxlength="11"
                  placeholder="例如 8.00" /></label
              ><label
                >{{ form.chargeMode === 1 ? "续件数量" : "续重（kg）"
                }}<input
                  v-model="rule.extraCount"
                  inputmode="decimal"
                  required
                  maxlength="20" /></label
              ><label
                >续费（元）<input
                  v-model="rule.extraYuan"
                  inputmode="decimal"
                  required
                  maxlength="11"
                  placeholder="可明确填 0"
              /></label>
            </div>
          </section>
          <button
            type="button"
            class="button"
            :disabled="form.charges.length >= 200"
            @click="addCharge"
          >
            添加计费规则
          </button>
          <h3 class="management-section-title">包邮规则</h3>
          <p class="muted">
            金额按本模板商品优惠后实付款计算。金额与件数都设置时须同时满足，填 0
            表示不限制该项；包邮不能绕过不可配送地区。
          </p>
          <section
            v-for="(rule, index) in form.frees"
            :key="index"
            class="delivery-rule"
          >
            <div class="delivery-rule-heading">
              <strong>包邮规则 {{ index + 1 }}</strong
              ><button
                type="button"
                class="text-link management-danger"
                @click="removeRule('frees', index)"
              >
                移除
              </button>
            </div>
            <label class="management-checkbox"
              ><input
                v-model="rule.defaultRule"
                type="checkbox"
                @change="rule.defaultRule && (rule.areaIds = [])"
              />默认地区规则</label
            >
            <template v-if="!rule.defaultRule"
              ><button
                type="button"
                class="button"
                @click="choose('frees', index)"
              >
                选择地区
              </button>
              <p class="muted">
                {{ rule.areaIds.map(label).join("、") || "尚未选择地区" }}
              </p></template
            >
            <div class="form-grid">
              <label
                >包邮金额（元）<input
                  v-model="rule.freeYuan"
                  inputmode="decimal"
                  required
                  maxlength="11"
                  placeholder="不限制金额请填 0" /></label
              ><label
                >包邮件数<input
                  v-model.number="rule.freeCount"
                  type="number"
                  min="0"
                  max="2147483647"
                  step="1"
                  required
              /></label>
            </div>
          </section>
          <button
            type="button"
            class="button"
            :disabled="form.frees.length >= 200"
            @click="addFree"
          >
            添加包邮规则
          </button>
          <section
            v-if="chosenRule && picking"
            class="delivery-rule"
            aria-label="选择配送地区"
          >
            <div class="delivery-rule-heading">
              <strong
                >选择{{ picking.kind === "charges" ? "计费" : "包邮" }}规则
                {{ picking.index + 1 }} 的地区</strong
              ><button
                type="button"
                class="text-link"
                @click="picking = undefined"
              >
                完成选择
              </button>
            </div>
            <label
              >查找省、市或区县<input
                v-model="areaSearch"
                placeholder="例如 杭州"
            /></label>
            <p class="muted">
              选择下级会替换已选上级，选择上级会合并已选下级。最多显示100项，可继续输入名称查找。
            </p>
            <p class="muted">
              已选：{{ chosenRule.areaIds.map(label).join("、") || "无" }}
            </p>
            <div class="management-checklist">
              <label v-for="area in options" :key="area.id"
                ><input
                  type="checkbox"
                  :checked="chosenRule.areaIds.includes(area.id)"
                  @change="toggleArea(area.id)"
                />{{ label(area.id) }}</label
              >
            </div>
          </section>
        </fieldset>
        <p v-if="modalError" class="form-error" role="alert">
          {{ modalError }}
        </p>
      </form>
      <template #footer
        ><button class="button" :disabled="busy" @click="editor = false">
          关闭</button
        ><button
          class="button primary"
          form="delivery-editor"
          :disabled="busy || uncertain"
        >
          {{ busy ? "保存中…" : "保存模板" }}
        </button></template
      >
    </Modal>
    <Modal
      v-if="deleting"
      title="确认删除运费模板"
      @close="!busy && (deleting = undefined)"
      ><p>
        确定删除「{{
          deleting.name
        }}」吗？关联商品需要更换模板才能继续核算新运费。
      </p>
      <p v-if="modalError" class="form-error" role="alert">{{ modalError }}</p>
      <template #footer
        ><button class="button" :disabled="busy" @click="deleting = undefined">
          关闭</button
        ><button
          class="button primary"
          :disabled="busy || uncertain"
          @click="remove"
        >
          确认删除
        </button></template
      ></Modal
    >
  </div>
</template>
<style scoped>
.delivery-rule {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 18px;
  margin: 16px 0;
}
.delivery-rule-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.delivery-rule .form-grid {
  margin-top: 16px;
}
.delivery-rule .muted {
  overflow-wrap: anywhere;
  line-height: 1.7;
}
</style>
