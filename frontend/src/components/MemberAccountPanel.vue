<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import Modal from "./Modal.vue";
import Empty from "./Empty.vue";
import Icon from "./Icon.vue";
import { ApiError } from "../api/http";
import { context } from "../api/session";
import {
  memberIdentity,
  readMemberProfile,
  listMemberAddresses,
  memberAreas,
  saveMemberAddress,
  deleteMemberAddress,
  updateMemberPreference,
  type MemberAddress,
  type MemberArea,
  type MemberProfile,
} from "../api/member-profile";

const props = defineProps<{ mode: "addresses" | "account" }>();
const profile = ref<MemberProfile | null>(null),
  rows = ref<MemberAddress[]>([]),
  areas = ref<MemberArea[]>([]);
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  uncertain = ref(false);
const editor = ref(false),
  editing = ref<MemberAddress>(),
  deleting = ref<MemberAddress>();
const form = reactive({
  name: "",
  mobile: "",
  detailAddress: "",
  defaultStatus: false,
});
const province = ref(0),
  city = ref(0),
  district = ref(0);
const base = computed(() => `/shop/${context.tenant}`);
const locked = computed(
  () => !profile.value || loading.value || busy.value || uncertain.value,
);
const provinces = computed(() => areas.value.filter((x) => x.parentId === 0));
const cities = computed(() =>
  province.value
    ? areas.value.filter((x) => x.parentId === province.value)
    : [],
);
const districts = computed(() =>
  city.value ? areas.value.filter((x) => x.parentId === city.value) : [],
);
const areaId = computed(
  () =>
    district.value || (city.value && !districts.value.length ? city.value : 0),
);
let epoch = 0;
onBeforeUnmount(() => {
  epoch++;
  profile.value = null;
  rows.value = [];
  areas.value = [];
});

async function reload() {
  const ticket = epoch;
  loading.value = true;
  error.value = "";
  profile.value = null;
  rows.value = [];
  try {
    const [p, a] = await Promise.all([
      readMemberProfile(),
      props.mode === "addresses" ? listMemberAddresses() : Promise.resolve([]),
    ]);
    if (ticket !== epoch) return false;
    profile.value = p;
    rows.value = a;
    uncertain.value = false;
    return true;
  } catch (e) {
    if (ticket !== epoch) return false;
    error.value = e instanceof Error ? e.message : "读取失败，请重试";
    if (e instanceof ApiError && e.code === 401)
      window.dispatchEvent(new Event("commerce:unauthorized"));
    return false;
  } finally {
    if (ticket === epoch) loading.value = false;
  }
}
watch(
  [memberIdentity, () => props.mode],
  () => {
    epoch++;
    busy.value = false;
    editor.value = false;
    deleting.value = undefined;
    areas.value = [];
    notice.value = "";
    uncertain.value = false;
    void reload();
  },
  { immediate: true },
);

async function change(action: () => Promise<unknown>, message: string) {
  if (locked.value) return;
  const ticket = epoch;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await action();
    if (ticket !== epoch) return;
    editor.value = false;
    deleting.value = undefined;
    if (await reload()) notice.value = message;
    else if (ticket === epoch) {
      uncertain.value = true;
      error.value = "修改已提交，但最新资料读取失败，请重新读取核对";
    }
  } catch (e) {
    if (ticket !== epoch) return;
    uncertain.value = e instanceof ApiError && e.outcomeUnknown;
    error.value = uncertain.value
      ? "操作结果待确认，请重新读取并核对，勿重复提交"
      : e instanceof ApiError && e.code === 409
        ? "资料已变化，请重新读取后再操作"
        : e instanceof Error
          ? e.message
          : "操作未完成";
    if (e instanceof ApiError && e.code === 401)
      window.dispatchEvent(new Event("commerce:unauthorized"));
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
async function open(row?: MemberAddress) {
  if (locked.value) return;
  const ticket = epoch;
  busy.value = true;
  error.value = "";
  try {
    const options = areas.value.length ? areas.value : await memberAreas();
    if (ticket !== epoch) return;
    areas.value = options;
    editing.value = row;
    Object.assign(
      form,
      row
        ? {
            name: row.name,
            mobile: row.mobile,
            detailAddress: row.detailAddress,
            defaultStatus: row.defaultStatus,
          }
        : {
            name: profile.value?.nickname || "",
            mobile: "",
            detailAddress: "",
            defaultStatus: rows.value.length === 0,
          },
    );
    province.value = city.value = district.value = 0;
    if (row) {
      const selected = areas.value.find((x) => x.id === row.areaId);
      const parent = areas.value.find((x) => x.id === selected?.parentId);
      if (selected?.type === 4 && parent?.type === 3) {
        district.value = selected.id;
        city.value = selected.parentId;
        province.value =
          areas.value.find((x) => x.id === city.value)?.parentId || 0;
      } else if (selected && parent?.type === 2) {
        city.value = selected.id;
        province.value = selected.parentId;
      }
    }
    editor.value = true;
  } catch (e) {
    if (ticket === epoch)
      error.value = e instanceof Error ? e.message : "地区读取失败";
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
function save() {
  if (!areaId.value) {
    error.value = "请选择有效的末级收货地区";
    return;
  }
  const payload = { ...form, areaId: areaId.value };
  void change(() => saveMemberAddress(payload, editing.value), "地址已保存");
}
function preference(event: Event) {
  const checkbox = event.target as HTMLInputElement;
  if (!profile.value) return;
  const optOut = !checkbox.checked;
  checkbox.checked = !profile.value.marketingOptOut; // 只回显服务端确认的选择。
  const version = profile.value.authVersion;
  void change(() => updateMemberPreference(optOut, version), "消息偏好已更新");
}
async function reconcile() {
  editor.value = false;
  deleting.value = undefined;
  if (await reload()) notice.value = "已读取最新资料，请核对刚才的操作结果";
}
</script>

<template>
  <main class="shop-container">
    <div class="shop-page-heading inline">
      <h1>{{ mode === "addresses" ? "收货地址" : "个人中心" }}</h1>
      <button
        v-if="mode === 'addresses'"
        class="shop-button"
        :disabled="locked"
        @click="open()"
      >
        <Icon name="plus" :size="17" />添加地址
      </button>
    </div>
    <p v-if="loading" role="status">正在读取…</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <div v-if="error" role="alert">
      <p class="form-error">{{ error }}</p>
      <button class="button" :disabled="busy || loading" @click="reconcile">
        重新读取
      </button>
    </div>
    <template v-if="profile && !loading">
      <template v-if="mode === 'addresses'">
        <div class="address-grid">
          <article v-for="row in rows" :key="row.id" class="address-card">
            <div>
              <strong>{{ row.name }}</strong
              ><span v-if="row.defaultStatus" class="tag">默认地址</span>
            </div>
            <p>{{ row.mobile }}</p>
            <p>{{ row.areaName }}<br />{{ row.detailAddress }}</p>
            <button class="text-link" :disabled="locked" @click="open(row)">
              编辑地址
            </button>
            <button
              v-if="!row.defaultStatus"
              class="text-link"
              :disabled="locked"
              @click="
                change(
                  () => saveMemberAddress({ ...row, defaultStatus: true }, row),
                  '默认地址已更新',
                )
              "
            >
              设为默认
            </button>
            <button
              class="text-link"
              :disabled="locked"
              @click="deleting = row"
            >
              删除
            </button>
          </article>
        </div>
        <Empty v-if="!rows.length && !error" title="暂无收货地址" icon="pin" />
      </template>
      <template v-else>
        <div class="account-hero">
          <div class="member-avatar large">
            {{ profile.nickname.slice(-2) || "会员" }}
          </div>
          <div>
            <h2>{{ profile.nickname || "会员" }}</h2>
            <p class="muted">{{ profile.mobileMasked }}</p>
          </div>
          <slot name="logout" />
        </div>
        <div class="account-links">
          <RouterLink :to="`${base}/orders`"
            ><Icon name="order" />
            <h3>我的订单</h3>
            <Icon name="right"
          /></RouterLink>
          <RouterLink :to="`${base}/coupons`"
            ><Icon name="coupon" />
            <h3>我的优惠券</h3>
            <Icon name="right"
          /></RouterLink>
          <RouterLink :to="`${base}/addresses`"
            ><Icon name="pin" />
            <h3>收货地址</h3>
            <Icon name="right"
          /></RouterLink>
          <RouterLink :to="`${base}/aftersales`"
            ><Icon name="refund" />
            <h3>退货退款</h3>
            <Icon name="right"
          /></RouterLink>
        </div>
        <section class="account-preferences">
          <h2>消息与偏好</h2>
          <label class="preference-row"
            ><span
              ><strong>接收会员礼遇</strong
              ><small>新品上架、专属优惠和活动邀请</small></span
            >
            <input
              type="checkbox"
              role="switch"
              :checked="!profile.marketingOptOut"
              :disabled="locked"
              @change="preference"
            />
          </label>
          <p class="muted">关闭后不再参与营销触达，订单和退款通知照常接收。</p>
        </section>
      </template>
    </template>
  </main>
  <Modal
    v-if="editor"
    :title="editing ? '编辑收货地址' : '添加收货地址'"
    @close="!busy && (editor = false)"
  >
    <form id="member-address-form" @submit.prevent="save">
      <fieldset class="page-actions" :disabled="locked">
        <div class="form-grid">
          <label
            >收货人<input
              v-model.trim="form.name"
              required
              maxlength="50"
              autocomplete="shipping name"
          /></label>
          <label
            >手机号<input
              v-model.trim="form.mobile"
              required
              maxlength="11"
              pattern="1[3-9][0-9]{9}"
              inputmode="tel"
              autocomplete="shipping tel"
          /></label>
        </div>
        <label
          >省 / 自治区 / 直辖市<select
            v-model.number="province"
            required
            @change="
              city = 0;
              district = 0;
            "
          >
            <option :value="0" disabled>请选择</option>
            <option v-for="x in provinces" :key="x.id" :value="x.id">
              {{ x.name }}
            </option>
          </select></label
        >
        <label
          >市 / 地区<select
            v-model.number="city"
            required
            @change="district = 0"
          >
            <option :value="0" disabled>请选择</option>
            <option v-for="x in cities" :key="x.id" :value="x.id">
              {{ x.name }}
            </option>
          </select></label
        >
        <label v-if="districts.length"
          >区 / 县<select v-model.number="district" required>
            <option :value="0" disabled>请选择</option>
            <option v-for="x in districts" :key="x.id" :value="x.id">
              {{ x.name }}
            </option>
          </select></label
        >
        <label
          >详细地址<textarea
            v-model.trim="form.detailAddress"
            required
            maxlength="255"
            autocomplete="shipping street-address"
          />
        </label>
        <label class="checkbox-label"
          ><input
            v-model="form.defaultStatus"
            type="checkbox"
          />设为默认地址</label
        >
      </fieldset>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <template #footer
      ><button class="button" :disabled="busy" @click="editor = false">
        取消</button
      ><button
        class="button primary"
        form="member-address-form"
        type="submit"
        :disabled="locked"
      >
        {{ busy ? "正在保存…" : "保存地址" }}
      </button></template
    >
  </Modal>
  <Modal
    v-if="deleting"
    title="删除收货地址"
    @close="!busy && (deleting = undefined)"
  >
    <p>
      确认删除 {{ deleting.name }} 的这条地址？历史订单中的收货信息保持不变。
    </p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <template #footer
      ><button class="button" :disabled="busy" @click="deleting = undefined">
        取消</button
      ><button
        class="button primary"
        :disabled="locked"
        @click="change(() => deleteMemberAddress(deleting!), '地址已删除')"
      >
        确认删除
      </button></template
    >
  </Modal>
</template>
