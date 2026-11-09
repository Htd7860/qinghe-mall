<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";
import Modal from "./Modal.vue";
import { ApiError } from "../api/http";
import {
  productIdentity,
  productOwner,
  productTransfer,
  type ProductRow,
  type ProductOwner,
} from "../api/product";
import { allowed } from "../api/management";
const props = defineProps<{ row: ProductRow }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const userId = ref(""),
  reason = ref(""),
  target = ref<ProductOwner>(),
  busy = ref(false),
  error = ref(""),
  uncertain = ref(false);
let generation = 0,
  live = true;
const message = (cause: unknown) =>
  cause instanceof Error ? cause.message : "操作未完成，请稍后重试";
function selectedId() {
  if (
    !/^[1-9]\d{0,15}$/.test(userId.value.trim()) ||
    !Number.isSafeInteger(Number(userId.value))
  )
    throw new Error("请填写有效的目标员工编号");
  return Number(userId.value);
}
watch(userId, () => {
  target.value = undefined;
});
watch(productIdentity, () => {
  generation++;
  target.value = undefined;
  emit("close");
});
onBeforeUnmount(() => {
  live = false;
  generation++;
});
async function check() {
  if (busy.value || uncertain.value) return;
  const current = ++generation;
  error.value = "";
  target.value = undefined;
  try {
    const id = selectedId();
    busy.value = true;
    const result = await productOwner(id);
    if (live && current === generation && id === Number(userId.value))
      target.value = result;
  } catch (cause) {
    if (live && current === generation) error.value = message(cause);
  } finally {
    if (live && current === generation) busy.value = false;
  }
}
async function save() {
  if (busy.value || uncertain.value || !allowed("product:spu:transfer")) return;
  error.value = "";
  const current = generation;
  try {
    const id = selectedId();
    if (!target.value || target.value.ownerUserId !== id)
      throw new Error("请先核对目标员工及其所属部门");
    if (!reason.value.trim() || reason.value.trim().length > 512)
      throw new Error("请填写不超过512字的转移原因");
    busy.value = true;
    await productTransfer({
      id: props.row.id,
      expectedVersion: props.row.authVersion,
      targetUserId: id,
      expectedOwnerVersion: target.value.userVersion,
      reason: reason.value.trim(),
    });
    if (live && current === generation) emit("saved");
  } catch (cause) {
    if (!live || current !== generation) return;
    uncertain.value =
      cause instanceof ApiError &&
      (cause.outcomeUnknown || [404, 409].includes(cause.code));
    error.value = uncertain.value
      ? "商品、目标员工或操作结果需要确认，请关闭并重新读取商品后核对。"
      : message(cause);
  } finally {
    if (live && current === generation) busy.value = false;
  }
}
</script>
<template>
  <Modal title="转移商品归属" @close="!busy && emit('close')">
    <p>{{ row.name }} · 商品 {{ row.id }}</p>
    <p class="muted">
      当前归属员工 {{ row.ownerUserId }}，部门
      {{ row.ownerDeptId ?? "未分配" }}。转移会改变该商品的数据可见范围。
    </p>
    <form id="product-transfer" @submit.prevent="save">
      <fieldset
        class="management-fieldset"
        :disabled="busy || uncertain || !allowed('product:spu:transfer')"
      >
        <label
          >目标员工编号<input
            v-model="userId"
            required
            inputmode="numeric"
            maxlength="16" /></label
        ><button type="button" class="button" @click="check">核对员工</button>
        <p v-if="target" class="management-notice" role="status">
          {{ target.ownerName }}（{{ target.ownerUserId }}），所属部门：{{
            target.ownerDeptName ?? "未分配"
          }}{{ target.ownerDeptId ? " · " + target.ownerDeptId : "" }}
        </p>
        <label
          >转移原因<textarea
            v-model="reason"
            required
            maxlength="512"
            rows="3"
          />
        </label>
      </fieldset>
    </form>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <template #footer
      ><button class="button" :disabled="busy" @click="emit('close')">
        关闭</button
      ><button
        class="button primary"
        form="product-transfer"
        :disabled="busy || uncertain || !target"
      >
        {{ busy ? "处理中…" : "确认转移" }}
      </button></template
    >
  </Modal>
</template>
