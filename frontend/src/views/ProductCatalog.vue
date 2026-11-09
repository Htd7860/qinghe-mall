<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import { allowed } from "../api/management";
import { ApiError } from "../api/http";
import { uploadFile } from "../api/file";
import {
  catalogDelete,
  catalogGet,
  catalogIdentity,
  catalogList,
  catalogSave,
  catalogSpec,
  type CatalogKind,
  type CatalogRow,
} from "../api/product-catalog";

const route = useRoute();
const section = computed(
  () => String(route.meta.productCatalog) as Exclude<CatalogKind, "values">,
);
const property = ref<CatalogRow>();
const kind = computed<CatalogKind>(() =>
  property.value ? "values" : section.value,
);
const spec = computed(() => catalogSpec[kind.value]);
const rows = ref<CatalogRow[]>([]),
  total = ref(0),
  page = ref(1),
  search = ref("");
const loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref("");
const editor = ref(false),
  editing = ref<CatalogRow>(),
  deleting = ref<CatalogRow>();
const modalError = ref(""),
  uncertain = ref(false),
  image = ref<File>();
const form = reactive({
  name: "",
  status: 0,
  sort: 0,
  parentId: 0,
  type: 1,
  picUrl: "",
  description: "",
  remark: "",
});
let epoch = 0,
  requestSequence = 0;
const can = (action: string) =>
  allowed(`product:${spec.value.permission}:${action}`);
const pictures = computed(
  () => kind.value === "categories" || kind.value === "brands",
);
const displayed = computed(() =>
  kind.value === "categories"
    ? rows.value.filter((row) => row.name.includes(search.value.trim()))
    : rows.value,
);
const message = (cause: unknown) =>
  cause instanceof Error ? cause.message : "操作未完成，请稍后重试";

async function load() {
  const generation = epoch,
    sequence = ++requestSequence;
  loading.value = true;
  error.value = "";
  rows.value = [];
  total.value = 0;
  try {
    if (!can("query")) throw new ApiError("没有查看商品资料的权限", 403);
    const result = await catalogList(
      kind.value,
      page.value,
      search.value.trim(),
      property.value?.id,
    );
    if (generation !== epoch || sequence !== requestSequence) return;
    rows.value = result.list;
    total.value = result.total;
  } catch (cause) {
    if (generation === epoch && sequence === requestSequence)
      error.value = message(cause);
  } finally {
    if (generation === epoch && sequence === requestSequence)
      loading.value = false;
  }
}
function reset() {
  epoch++;
  property.value = undefined;
  page.value = 1;
  search.value = "";
  editor.value = false;
  deleting.value = undefined;
  busy.value = false;
  notice.value = "";
  image.value = undefined;
  void load();
}
watch(() => `${section.value}:${catalogIdentity()}`, reset, {
  immediate: true,
});
onBeforeUnmount(() => {
  epoch++;
});
function values(row?: CatalogRow) {
  if (busy.value) return;
  epoch++;
  property.value = row;
  page.value = 1;
  search.value = "";
  notice.value = "";
  editor.value = false;
  deleting.value = undefined;
  void load();
}
async function open(row?: CatalogRow) {
  if (busy.value) return;
  const generation = epoch,
    selectedKind = kind.value;
  busy.value = true;
  error.value = "";
  try {
    const current = row ? await catalogGet(selectedKind, row.id) : undefined;
    if (generation !== epoch) return;
    editing.value = current;
    image.value = undefined;
    modalError.value = "";
    uncertain.value = false;
    Object.assign(
      form,
      {
        name: "",
        status: 0,
        sort: 0,
        parentId: 0,
        type: 1,
        picUrl: "",
        description: "",
        remark: "",
      },
      current || {},
    );
    editor.value = true;
  } catch (cause) {
    if (generation === epoch) error.value = message(cause);
  } finally {
    if (generation === epoch) busy.value = false;
  }
}
function chooseImage(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0];
  image.value = undefined;
  modalError.value = "";
  if (selected && (selected.size <= 0 || selected.size > 10 * 1024 * 1024)) {
    modalError.value = "请选择不超过10 MiB的图片";
    return;
  }
  image.value = selected;
}
async function upload() {
  if (!image.value || busy.value || !allowed("infra:file:create")) return;
  const generation = epoch;
  busy.value = true;
  modalError.value = "";
  try {
    const result = await uploadFile(image.value, "product-image");
    if (generation !== epoch) return;
    if (!result.url) throw new ApiError("图片地址不存在，请重新上传", -2);
    form.picUrl = result.url;
    image.value = undefined;
  } catch (cause) {
    if (generation === epoch) modalError.value = message(cause);
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
  if (!form.name.trim()) {
    modalError.value = "请填写名称";
    return;
  }
  const generation = epoch,
    selectedKind = kind.value;
  const body: Record<string, unknown> = {
    name: form.name.trim(),
    status: form.status,
  };
  if (pictures.value)
    Object.assign(body, {
      sort: form.sort,
      picUrl: form.picUrl,
      description: form.description,
    });
  else body.remark = form.remark;
  if (selectedKind === "categories") body.parentId = form.parentId;
  if (selectedKind === "properties") body.type = form.type;
  if (selectedKind === "values") body.propertyId = property.value?.id;
  if (editing.value)
    Object.assign(body, {
      id: editing.value.id,
      expectedVersion: editing.value.authVersion,
    });
  busy.value = true;
  modalError.value = "";
  try {
    await catalogSave(selectedKind, body, !!editing.value);
    if (generation !== epoch) return;
    editor.value = false;
    notice.value = "已保存";
    await load();
  } catch (cause) {
    if (generation === epoch) failure(cause);
  } finally {
    if (generation === epoch) busy.value = false;
  }
}
function confirmDelete(row: CatalogRow) {
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
    await catalogDelete(kind.value, deleting.value);
    if (generation !== epoch) return;
    deleting.value = undefined;
    notice.value = "已删除";
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
    <PageHeader
      :title="property ? `${property.name} · 属性值` : spec.title"
      eyebrow="商品资料"
    >
      <button v-if="property" class="button" :disabled="busy" @click="values()">
        返回属性
      </button>
      <button class="button" :disabled="loading || busy" @click="load">
        重新读取
      </button>
      <button
        v-if="can('create')"
        class="button primary"
        :disabled="loading || busy"
        @click="open()"
      >
        新增{{ spec.title }}
      </button>
    </PageHeader>
    <nav class="management-tabs" aria-label="商品资料分类">
      <template
        v-for="item in ['categories', 'brands', 'properties'] as const"
        :key="item"
      >
        <RouterLink
          v-if="allowed(`product:${catalogSpec[item].permission}:query`)"
          :to="`/admin/catalog/${item}`"
          >{{ catalogSpec[item].title }}</RouterLink
        >
      </template>
    </nav>
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
          >名称<input v-model="search" maxlength="64" placeholder="按名称查找"
        /></label>
        <button class="button" :disabled="busy || loading">查询</button>
      </form>
      <div v-if="error" class="management-alert" role="alert">{{ error }}</div>
      <p v-if="loading" class="management-empty" role="status">正在读取…</p>
      <div v-else-if="displayed.length" class="management-table-scroll">
        <table class="management-table">
          <thead>
            <tr>
              <th>编号</th>
              <th>名称</th>
              <th v-if="kind === 'categories'">上级分类</th>
              <th v-if="kind === 'properties'">类型</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in displayed" :key="row.id">
              <td>{{ row.id }}</td>
              <td>{{ row.name }}</td>
              <td v-if="kind === 'categories'">
                {{
                  row.parentId === 0
                    ? "顶级分类"
                    : rows.find((item) => item.id === row.parentId)?.name ||
                      row.parentId
                }}
              </td>
              <td v-if="kind === 'properties'">
                {{ row.type === 1 ? "销售规格" : "展示属性" }}
              </td>
              <td>{{ row.status === 0 ? "启用" : "停用" }}</td>
              <td>
                <div class="management-row-actions">
                  <button
                    v-if="kind === 'properties'"
                    class="text-link"
                    :disabled="busy"
                    @click="values(row)"
                  >
                    管理属性值
                  </button>
                  <button
                    v-if="can('update')"
                    class="text-link"
                    :disabled="busy || loading"
                    @click="open(row)"
                  >
                    修改
                  </button>
                  <button
                    v-if="can('delete')"
                    class="text-link management-danger"
                    :disabled="busy || loading"
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
      <Empty v-else-if="!error" title="暂无商品资料"
        ><p>添加分类、品牌和规格，为发布商品做好准备。</p></Empty
      >
      <div class="pagination">
        <span>共 {{ total }} 条</span>
        <div v-if="kind !== 'categories'" class="pagination-right">
          <button
            class="button"
            :disabled="busy || loading || page <= 1"
            @click="
              page--;
              load();
            "
          >
            上一页
          </button>
          <span>第 {{ page }} 页</span>
          <button
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
      :title="`${editing ? '修改' : '新增'}${spec.title}`"
      @close="!busy && (editor = false)"
    >
      <form id="catalog-editor" @submit.prevent="save">
        <fieldset class="management-fieldset" :disabled="busy || uncertain">
          <div class="form-grid">
            <label
              >名称<input v-model="form.name" required maxlength="64"
            /></label>
            <label
              >状态<select v-model.number="form.status">
                <option :value="0">启用</option>
                <option :value="1">停用</option>
              </select></label
            >
            <label v-if="kind === 'categories'"
              >上级分类<select v-model.number="form.parentId">
                <option :value="0">顶级分类</option>
                <option
                  v-for="row in rows.filter((item) => item.id !== editing?.id)"
                  :key="row.id"
                  :value="row.id"
                >
                  {{ row.name }}（{{ row.id }}）
                </option>
              </select></label
            >
            <label v-if="kind === 'properties'"
              >属性类型<select v-model.number="form.type" :disabled="!!editing">
                <option :value="1">销售规格</option>
                <option :value="2">展示属性</option>
              </select></label
            >
            <template v-if="pictures">
              <label
                >排序<input
                  v-model.number="form.sort"
                  type="number"
                  required
                  min="0"
                  max="10000"
                  step="1"
              /></label>
              <label
                >图片地址<input
                  v-model="form.picUrl"
                  required
                  maxlength="1024"
                  placeholder="填写本人已上传的商品图片地址"
              /></label>
              <label v-if="allowed('infra:file:create')"
                >上传商品图片<input
                  type="file"
                  accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
                  @change="chooseImage"
              /></label>
              <button
                v-if="allowed('infra:file:create')"
                type="button"
                class="button"
                :disabled="!image"
                @click="upload"
              >
                上传并填入地址
              </button>
              <label
                >描述<textarea v-model="form.description" maxlength="512" />
              </label>
            </template>
            <label v-else
              >备注<textarea v-model="form.remark" maxlength="500" />
            </label>
          </div>
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
          form="catalog-editor"
          :disabled="busy || uncertain"
        >
          {{ busy ? "处理中…" : "保存" }}
        </button></template
      >
    </Modal>
    <Modal
      v-if="deleting"
      title="确认删除"
      @close="!busy && (deleting = undefined)"
    >
      <p>确定删除「{{ deleting.name }}」吗？被商品引用的资料不能删除。</p>
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
      >
    </Modal>
  </div>
</template>
