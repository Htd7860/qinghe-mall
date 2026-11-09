<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from "vue";
import Modal from "./Modal.vue";
import Empty from "./Empty.vue";
import { allowed } from "../api/management";
import { context } from "../api/session";
import {
  listFiles,
  uploadFile,
  deleteFile,
  downloadFile,
  fileIdentityKey,
  type FilePurpose,
  type FileRecord,
} from "../api/file";

const emit = defineEmits<{ close: [] }>();
const rows = ref<FileRecord[]>([]),
  total = ref(0),
  page = ref(1),
  busy = ref(false),
  loading = ref(false);
const error = ref(""),
  notice = ref(""),
  selected = ref<File | null>(null),
  purpose = ref<FilePurpose>("store-logo");
const input = ref<HTMLInputElement>(),
  deleting = ref<FileRecord | null>(null);
const canUpload = computed(() => allowed("infra:file:create"));
const canDelete = computed(() => allowed("infra:file:delete"));
let live = true,
  generation = 0;
const identity = fileIdentityKey();
function active() {
  return live && identity === fileIdentityKey();
}
function message(cause: unknown) {
  return cause instanceof Error ? cause.message : "操作未完成，请稍后重试";
}
async function load() {
  const current = ++generation;
  loading.value = true;
  error.value = "";
  try {
    const result = await listFiles(page.value);
    if (active() && current === generation) {
      rows.value = result.list;
      total.value = result.total;
    }
  } catch (cause) {
    if (active() && current === generation) error.value = message(cause);
  } finally {
    if (active() && current === generation) loading.value = false;
  }
}
function choose(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null;
  selected.value = null;
  error.value = "";
  if (file && (file.size <= 0 || file.size > 10 * 1024 * 1024))
    error.value = "请选择不超过10 MiB的非空图片";
  else selected.value = file;
}
async function upload() {
  if (!selected.value || busy.value || !canUpload.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await uploadFile(selected.value, purpose.value);
    if (!active()) return;
    selected.value = null;
    if (input.value) input.value.value = "";
    page.value = 1;
    notice.value = "图片上传成功";
    await load();
  } catch (cause) {
    if (active()) error.value = message(cause);
  } finally {
    if (active()) busy.value = false;
  }
}
async function remove() {
  if (!deleting.value || busy.value || !canDelete.value) return;
  busy.value = true;
  error.value = "";
  try {
    await deleteFile(deleting.value.id);
    if (!active()) return;
    deleting.value = null;
    notice.value = "文件已删除";
    await load();
  } catch (cause) {
    if (active()) error.value = message(cause);
  } finally {
    if (active()) busy.value = false;
  }
}
async function download(file: FileRecord) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    const blob = await downloadFile(file);
    if (!active()) return;
    const url = URL.createObjectURL(blob),
      link = document.createElement("a");
    link.href = url;
    link.download = file.name;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (cause) {
    if (active()) error.value = message(cause);
  } finally {
    if (active()) busy.value = false;
  }
}
async function copy(file: FileRecord) {
  if (!file.url) return;
  try {
    await navigator.clipboard.writeText(file.url);
    if (active()) notice.value = "图片地址已复制";
  } catch {
    if (active()) error.value = "无法复制，请使用下载功能";
  }
}
onMounted(load);
onBeforeUnmount(() => {
  live = false;
  generation++;
});
</script>

<template>
  <Modal title="图片素材" wide @close="emit('close')">
    <p class="file-help">
      管理本人上传的图片。商品图片和店铺Logo可公开访问，售后凭证仅本人可读取。
    </p>
    <form v-if="canUpload" class="file-upload" @submit.prevent="upload">
      <label
        >图片用途<select v-model="purpose" :disabled="busy">
          <option value="store-logo">店铺Logo</option>
          <option v-if="context.realm === 'merchant'" value="product-image">
            商品图片
          </option>
          <option
            v-if="context.realm === 'merchant'"
            value="aftersale-evidence"
          >
            私有售后凭证
          </option>
        </select></label
      >
      <label
        >选择图片<input
          ref="input"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
          :disabled="busy"
          @change="choose"
      /></label>
      <button
        class="button primary"
        type="submit"
        :disabled="busy || !selected"
      >
        {{ busy ? "处理中…" : "上传图片" }}
      </button>
    </form>
    <p v-if="error" class="file-error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <div class="file-actions">
      <span>共 {{ total }} 个文件 · 单张图片默认上限10 MiB</span
      ><button class="button" :disabled="busy || loading" @click="load">
        刷新列表
      </button>
    </div>
    <p v-if="loading" role="status">正在读取文件…</p>
    <Empty v-else-if="!rows.length" title="暂无图片">上传后会显示在这里</Empty>
    <ul v-else class="file-list">
      <li v-for="file in rows" :key="file.id">
        <img
          v-if="file.url"
          :src="file.url"
          :alt="file.name"
          loading="lazy"
          referrerpolicy="no-referrer"
        />
        <span v-else class="file-private">私有凭证</span>
        <div class="file-name">
          <strong>{{ file.name }}</strong
          ><small
            >{{ Math.ceil(file.size / 1024) }} KiB ·
            {{ file.createTime.replace("T", " ") }}</small
          >
        </div>
        <button
          v-if="file.url"
          class="button"
          :disabled="busy"
          @click="copy(file)"
        >
          复制地址
        </button>
        <button class="button" :disabled="busy" @click="download(file)">
          下载
        </button>
        <button
          v-if="canDelete"
          class="button"
          :disabled="busy"
          @click="deleting = file"
        >
          删除
        </button>
      </li>
    </ul>
    <div v-if="deleting" class="file-confirm" role="alert">
      <span>确认删除“{{ deleting.name }}”？使用该地址的图片将无法显示。</span>
      <button class="button" :disabled="busy" @click="deleting = null">
        取消
      </button>
      <button class="button" :disabled="busy" @click="remove">确认删除</button>
    </div>
    <div class="file-actions">
      <button
        class="button"
        :disabled="page <= 1 || busy || loading"
        @click="
          page--;
          load();
        "
      >
        上一页</button
      ><span>第 {{ page }} 页</span
      ><button
        class="button"
        :disabled="page * 10 >= total || busy || loading"
        @click="
          page++;
          load();
        "
      >
        下一页
      </button>
    </div>
  </Modal>
</template>

<style scoped>
.file-help {
  color: var(--muted, #68736d);
  line-height: 1.6;
}
.file-upload,
.file-actions,
.file-confirm {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px;
  margin: 16px 0;
}
.file-upload label {
  display: grid;
  gap: 6px;
}
.file-actions {
  align-items: center;
  justify-content: space-between;
}
.file-list {
  list-style: none;
  padding: 0;
}
.file-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--line, #e3e8e4);
  padding: 12px 0;
  flex-wrap: wrap;
}
.file-list img,
.file-private {
  width: 64px;
  height: 64px;
  object-fit: contain;
  background: var(--surface, #f5f7f4);
  border-radius: 8px;
}
.file-private {
  display: grid;
  place-items: center;
  font-size: 12px;
}
.file-name {
  display: grid;
  gap: 5px;
  flex: 1;
  min-width: 140px;
  overflow-wrap: anywhere;
}
.file-name small {
  color: var(--muted, #68736d);
}
.file-error {
  color: #ad3030;
}
</style>
