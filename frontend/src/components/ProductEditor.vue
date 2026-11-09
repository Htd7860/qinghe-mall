<script setup lang="ts">
// Adapts yudao-ui-admin-vue3 SPU form flow. MIT (c) 2021-present Archer; see third-party/yudao-ui-admin-vue3.
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import Modal from "./Modal.vue";
import { allowed } from "../api/management";
import { ApiError } from "../api/http";
import { uploadFile } from "../api/file";
import {
  productDetail,
  productEditorOptions,
  productIdentity,
  productPropertyValues,
  productSave,
  type EditorOption,
  type ProductDetail,
  type ProductEditorOptions,
  type SkuProperty,
} from "../api/product";
import {
  buildSkuCombinations,
  positiveMeasure,
  skuCombinationKey,
  skuFromSaved,
  skuSaveFields,
  type EditorSku,
} from "../api/product-editor";
const props = defineProps<{ id?: number }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const loading = ref(true),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  uncertain = ref(false),
  loaded = ref(false);
const options = ref<ProductEditorOptions>(),
  saved = ref<ProductDetail>(),
  values = ref<Record<number, EditorOption[]>>({});
const dimensions = ref<{ propertyId: number; valueIds: number[] }[]>([]),
  display = ref<{ propertyId: number; valueId: number }[]>([]);
const skus = ref<EditorSku[]>([]),
  cache = new Map<string, EditorSku>();
const generated = ref("");
const form = reactive({
  name: "",
  keyword: "",
  introduction: "",
  description: "",
  categoryId: 0,
  brandId: 0,
  picUrl: "",
  sliderPicUrls: [] as string[],
  sort: 0,
  specType: false,
  deliveryTemplateId: 0,
});
let epoch = 0,
  live = true;
const canSave = computed(() =>
  allowed(`product:spu:${props.id ? "update" : "create"}`),
);
const sales = computed(
  () =>
    options.value?.properties.filter(
      (value) => value.type === 1 && value.status === 0,
    ) || [],
);
const displays = computed(
  () =>
    options.value?.properties.filter(
      (value) => value.type === 2 && value.status === 0,
    ) || [],
);
const selectedTemplate = computed(() =>
  options.value?.templates.find(
    (value) => value.id === form.deliveryTemplateId,
  ),
);
const signature = () =>
  JSON.stringify([
    form.specType,
    dimensions.value.map((value) => [
      value.propertyId,
      [...value.valueIds].sort((a, b) => a - b),
    ]),
  ]);
const combinationsStale = computed(() => generated.value !== signature());
const activeKeys = computed(
  () => new Set(skus.value.map((sku) => skuCombinationKey(sku.properties))),
);
const retained = computed(
  () =>
    saved.value?.skus.filter(
      (sku) => !activeKeys.value.has(skuCombinationKey(sku.properties)),
    ) || [],
);
const message = (cause: unknown) =>
  cause instanceof Error ? cause.message : "操作未完成，请稍后重试";
function categoryLabel(id: number) {
  const names: string[] = [],
    seen = new Set<number>();
  let row = options.value?.categories.find((item) => item.id === id);
  while (row && !seen.has(row.id)) {
    seen.add(row.id);
    names.unshift(row.name);
    row = options.value?.categories.find((item) => item.id === row!.parentId);
  }
  return names.join(" / ") || String(id);
}
const categories = computed(() => {
  const rows = options.value?.categories || [],
    index = new Map(rows.map((row) => [row.id, row]));
  return rows.filter((row) => {
    if (row.status !== 0 || rows.some((child) => child.parentId === row.id))
      return false;
    let cursor: EditorOption | undefined = row;
    const seen = new Set<number>();
    while (cursor) {
      if (cursor.status !== 0 || seen.has(cursor.id)) return false;
      seen.add(cursor.id);
      if (cursor.parentId === 0) return seen.size <= 3;
      cursor = index.get(cursor.parentId!);
    }
    return false;
  });
});
const selectedCategoryAvailable = computed(
  () =>
    !form.categoryId ||
    categories.value.some((row) => row.id === form.categoryId),
);
function copySku(sku: EditorSku): EditorSku {
  return { ...sku, properties: sku.properties.map((value) => ({ ...value })) };
}
function remember() {
  for (const sku of skus.value)
    cache.set(skuCombinationKey(sku.properties), copySku(sku));
}
function blank(properties: SkuProperty[]): EditorSku {
  return {
    properties: properties.map((value) => ({ ...value })),
    price: "",
    marketPrice: "0.00",
    costPrice: "0.00",
    barCode: "",
    picUrl: form.picUrl,
    stock: "0",
    weight: "",
    volume: "",
    status: 0,
  };
}
async function ensureValues(id: number, generation = epoch) {
  if (!id || values.value[id]) return;
  const result = await productPropertyValues(id);
  if (live && generation === epoch) values.value[id] = result;
}
async function load() {
  const generation = ++epoch;
  loading.value = true;
  loaded.value = false;
  error.value = "";
  uncertain.value = false;
  try {
    const [dependencies, detail] = await Promise.all([
      productEditorOptions(),
      props.id ? productDetail(props.id) : Promise.resolve(undefined),
    ]);
    if (!live || generation !== epoch) return;
    options.value = dependencies;
    saved.value = detail;
    if (detail?.status === -1)
      throw new Error("请先将回收站商品恢复为下架再编辑");
    cache.clear();
    values.value = {};
    dimensions.value = [];
    display.value = [];
    notice.value = "";
    Object.assign(form, {
      name: "",
      keyword: "",
      introduction: "",
      description: "",
      categoryId: 0,
      brandId: 0,
      picUrl: "",
      sliderPicUrls: [],
      sort: 0,
      specType: false,
      deliveryTemplateId: 0,
    });
    if (detail) {
      Object.assign(form, {
        name: detail.name,
        keyword: detail.keyword ?? "",
        introduction: detail.introduction ?? "",
        description: detail.description,
        categoryId: detail.categoryId,
        brandId: detail.brandId,
        picUrl: detail.picUrl,
        sliderPicUrls: [...detail.sliderPicUrls],
        sort: detail.sort,
        specType: detail.specType,
        deliveryTemplateId: detail.deliveryTemplateId,
      });
      for (const sku of detail.skus)
        cache.set(skuCombinationKey(sku.properties), skuFromSaved(sku));
      const active = detail.skus.filter((sku) => sku.status === 0);
      const compatible = active.length
        ? active
        : detail.skus.filter((sku) =>
            detail.specType
              ? sku.properties.every((value) => value.propertyId > 0)
              : sku.properties.length === 1 &&
                sku.properties[0].propertyId === 0,
          );
      skus.value = compatible.map(skuFromSaved);
      if (detail.specType) {
        const dims = new Map<number, Set<number>>();
        for (const sku of compatible)
          for (const item of sku.properties) {
            if (!dims.has(item.propertyId))
              dims.set(item.propertyId, new Set());
            dims.get(item.propertyId)!.add(item.valueId);
          }
        dimensions.value = [...dims]
          .sort(([a], [b]) => a - b)
          .map(([propertyId, valueIds]) => ({
            propertyId,
            valueIds: [...valueIds],
          }));
      }
      display.value = detail.displayProperties.map((value) => ({
        propertyId: value.propertyId,
        valueId: value.valueId,
      }));
    } else {
      form.sliderPicUrls = [""];
      skus.value = [
        blank([
          {
            propertyId: 0,
            propertyName: "默认",
            valueId: 0,
            valueName: "默认",
          },
        ]),
      ];
    }
    const ids = [
      ...new Set([
        ...dimensions.value.map((value) => value.propertyId),
        ...display.value.map((value) => value.propertyId),
      ]),
    ];
    for (let i = 0; i < ids.length; i += 4) {
      await Promise.all(
        ids.slice(i, i + 4).map((id) => ensureValues(id, generation)),
      );
      if (!live || generation !== epoch) return;
    }
    if (!skus.value.length && !form.specType)
      skus.value = [
        cache.get("0:0")
          ? copySku(cache.get("0:0")!)
          : blank([
              {
                propertyId: 0,
                propertyName: "默认",
                valueId: 0,
                valueName: "默认",
              },
            ]),
      ];
    generated.value = signature();
    loaded.value = true;
  } catch (cause) {
    if (live && generation === epoch) error.value = message(cause);
  } finally {
    if (live && generation === epoch) loading.value = false;
  }
}
watch(
  () => `${props.id ?? "new"}:${productIdentity()}`,
  () => {
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  live = false;
  epoch++;
  cache.clear();
});
async function changeProperty(index: number, kind: "sales" | "display") {
  const generation = epoch;
  error.value = "";
  busy.value = true;
  try {
    if (kind === "sales") {
      dimensions.value[index].valueIds = [];
      await ensureValues(dimensions.value[index].propertyId, generation);
    } else {
      display.value[index].valueId = 0;
      await ensureValues(display.value[index].propertyId, generation);
    }
  } catch (cause) {
    if (live && generation === epoch) error.value = message(cause);
  } finally {
    if (live && generation === epoch) busy.value = false;
  }
}
function generate() {
  error.value = "";
  try {
    remember();
    let combos: SkuProperty[][];
    if (!form.specType)
      combos = [
        [
          {
            propertyId: 0,
            propertyName: "默认",
            valueId: 0,
            valueName: "默认",
          },
        ],
      ];
    else {
      if (
        !dimensions.value.length ||
        new Set(dimensions.value.map((value) => value.propertyId)).size !==
          dimensions.value.length
      )
        throw new Error("请选择不重复的销售规格");
      const selected = dimensions.value.map((dimension) => {
        const property = options.value?.properties.find(
          (value) =>
            value.id === dimension.propertyId &&
            value.type === 1 &&
            value.status === 0,
        );
        if (!property) throw new Error("请选择启用的销售规格");
        return dimension.valueIds.map((id) => {
          const value = values.value[property.id]?.find(
            (item) => item.id === id && item.status === 0,
          );
          if (!value) throw new Error("请选择启用且属于当前规格的值");
          return {
            propertyId: property.id,
            propertyName: property.name,
            valueId: value.id,
            valueName: value.name,
          };
        });
      });
      combos = buildSkuCombinations(selected, options.value!.maxSkus);
    }
    skus.value = combos.map((properties) => {
      const previous = cache.get(skuCombinationKey(properties));
      return previous
        ? {
            ...copySku(previous),
            properties: properties.map((value) => ({ ...value })),
          }
        : blank(properties);
    });
    generated.value = signature();
    notice.value = "已生成所选组合，相同组合保留原SKU编号和已填写资料。";
  } catch (cause) {
    error.value = message(cause);
  }
}
function removeSku(index: number) {
  remember();
  skus.value.splice(index, 1);
}
function restoreSku(id: number) {
  if (combinationsStale.value) {
    error.value = "请先生成当前规格组合，再放回历史SKU。";
    return;
  }
  const row = saved.value?.skus.find((value) => value.id === id);
  if (!row) return;
  const key = skuCombinationKey(row.properties);
  if (activeKeys.value.has(key)) return;
  if (skus.value.length >= options.value!.maxSkus) {
    error.value = "已达到SKU数量上限";
    return;
  }
  const expected = new Set(dimensions.value.map((value) => value.propertyId));
  if (
    (form.specType &&
      (row.properties.length !== expected.size ||
        row.properties.some((value) => !expected.has(value.propertyId)))) ||
    (!form.specType && key !== "0:0")
  ) {
    error.value = "该历史组合与当前规格类型不同，请先调整销售规格并重新生成。";
    return;
  }
  skus.value.push(
    cache.get(key) ? copySku(cache.get(key)!) : skuFromSaved(row),
  );
  for (const property of row.properties) {
    const axis = dimensions.value.find(
      (value) => value.propertyId === property.propertyId,
    );
    if (axis && !axis.valueIds.includes(property.valueId))
      axis.valueIds.push(property.valueId);
  }
  generated.value = signature();
}
async function upload(event: Event, target: "cover" | "gallery" | number) {
  const input = event.target as HTMLInputElement,
    file = input.files?.[0];
  input.value = "";
  if (!file || busy.value) return;
  error.value = "";
  if (file.size <= 0 || file.size > 10 * 1024 * 1024) {
    error.value = "请选择不超过10 MiB的图片";
    return;
  }
  const generation = epoch;
  busy.value = true;
  try {
    const value = await uploadFile(file, "product-image");
    if (!live || generation !== epoch) return;
    if (!value.url) throw new Error("图片地址不存在");
    if (target === "cover") form.picUrl = value.url;
    else if (target === "gallery") {
      const empty = form.sliderPicUrls.findIndex((value) => !value);
      if (empty >= 0) form.sliderPicUrls[empty] = value.url;
      else if (form.sliderPicUrls.length < 20)
        form.sliderPicUrls.push(value.url);
      else throw new Error("轮播图最多20张");
    } else if (skus.value[target]) skus.value[target].picUrl = value.url;
    notice.value = "图片已上传并填入地址，保存商品时会再次核对归属。";
  } catch (cause) {
    if (live && generation === epoch) error.value = message(cause);
  } finally {
    if (live && generation === epoch) busy.value = false;
  }
}
function body() {
  if (!options.value || combinationsStale.value)
    throw new Error("销售规格已变化，请先生成并核对SKU组合");
  if (
    !form.name.trim() ||
    !form.description.trim() ||
    !selectedCategoryAvailable.value ||
    !form.categoryId ||
    !options.value.brands.some((value) => value.id === form.brandId) ||
    !selectedTemplate.value
  )
    throw new Error("请完整填写标题、详情、有效末级分类、品牌和配送模板");
  if (
    !form.picUrl.trim() ||
    !form.sliderPicUrls.length ||
    form.sliderPicUrls.some(
      (value) => !value.trim() || value.trim().length > 1024,
    )
  )
    throw new Error("请填写封面和至少一张有效轮播图地址");
  if (
    !skus.value.length ||
    skus.value.length > options.value.maxSkus ||
    (!form.specType && skus.value.length !== 1)
  )
    throw new Error("请检查SKU数量与规格类型");
  const keys = new Set<string>(),
    dimensionsSet = new Set<number>(
      skus.value[0].properties.map((value) => value.propertyId),
    );
  for (const sku of skus.value) {
    const key = skuCombinationKey(sku.properties);
    if (keys.has(key)) throw new Error("SKU组合重复");
    keys.add(key);
    if (
      new Set(sku.properties.map((value) => value.propertyId)).size !==
        sku.properties.length ||
      sku.properties.length !== dimensionsSet.size ||
      sku.properties.some((value) => !dimensionsSet.has(value.propertyId))
    )
      throw new Error("各SKU的销售规格维度必须一致");
    if (
      selectedTemplate.value.chargeMode === 2 &&
      sku.status === 0 &&
      !positiveMeasure(sku.weight)
    )
      throw new Error("重量模板的启用SKU必须填写有效重量");
  }
  const displayIds = new Set<number>();
  const displayProperties = display.value.map((item) => {
    if (!item.propertyId || !item.valueId || displayIds.has(item.propertyId))
      throw new Error("展示属性须选择不重复的属性及有效值");
    displayIds.add(item.propertyId);
    const property = options.value!.properties.find(
        (value) =>
          value.id === item.propertyId &&
          value.type === 2 &&
          value.status === 0,
      ),
      value = values.value[item.propertyId]?.find(
        (value) => value.id === item.valueId && value.status === 0,
      );
    if (!property || !value) throw new Error("请选择启用的展示属性及属性值");
    return { propertyId: item.propertyId, valueId: item.valueId };
  });
  const payload: Record<string, unknown> = {
    name: form.name.trim(),
    keyword: form.keyword.trim(),
    introduction: form.introduction,
    description: form.description,
    categoryId: form.categoryId,
    brandId: form.brandId,
    picUrl: form.picUrl.trim(),
    sliderPicUrls: form.sliderPicUrls.map((value) => value.trim()),
    sort: form.sort,
    specType: form.specType,
    deliveryTemplateId: form.deliveryTemplateId,
    displayProperties,
    skus: skus.value.map(skuSaveFields),
  };
  if (saved.value)
    Object.assign(payload, {
      id: saved.value.id,
      expectedVersion: saved.value.authVersion,
    });
  return payload;
}
async function save() {
  if (
    busy.value ||
    loading.value ||
    uncertain.value ||
    !canSave.value ||
    !loaded.value
  )
    return;
  error.value = "";
  const generation = epoch;
  try {
    const payload = body();
    busy.value = true;
    await productSave(payload, !!saved.value);
    if (!live || generation !== epoch) return;
    emit("saved");
  } catch (cause) {
    if (!live || generation !== epoch) return;
    uncertain.value =
      cause instanceof ApiError &&
      (cause.outcomeUnknown || [404, 409].includes(cause.code));
    error.value = uncertain.value
      ? "商品或操作结果需要确认。请关闭弹窗，重新读取列表或详情；不要直接重复创建。"
      : message(cause);
  } finally {
    if (live && generation === epoch) busy.value = false;
  }
}
</script>
<template>
  <Modal
    :title="id ? '编辑商品' : '新增商品'"
    wide
    @close="!busy && emit('close')"
  >
    <p v-if="loading" role="status">正在读取商品和可用选项…</p>
    <form v-if="loaded" id="product-editor" @submit.prevent="save">
      <fieldset
        class="management-fieldset"
        :disabled="busy || uncertain || !canSave"
      >
        <h3 class="management-section-title">基本资料</h3>
        <p v-if="!id" class="muted">
          完整保存后进入下架列表，上架时会再次校验商品资料。
        </p>
        <div class="form-grid">
          <label
            >商品名称<input
              v-model="form.name"
              required
              maxlength="128" /></label
          ><label>关键词<input v-model="form.keyword" maxlength="255" /></label>
          <label
            >末级分类<select v-model.number="form.categoryId" required>
              <option :value="0" disabled>请选择分类</option>
              <option
                v-if="!selectedCategoryAvailable"
                :value="form.categoryId"
                disabled
              >
                原分类不可用，请重新选择
              </option>
              <option
                v-for="item in categories"
                :key="item.id"
                :value="item.id"
              >
                {{ categoryLabel(item.id) }}
              </option>
            </select></label
          >
          <label
            >品牌<select v-model.number="form.brandId" required>
              <option :value="0" disabled>请选择品牌</option>
              <option
                v-if="
                  form.brandId &&
                  !options?.brands.some((item) => item.id === form.brandId)
                "
                :value="form.brandId"
                disabled
              >
                原品牌不可用，请重新选择
              </option>
              <option
                v-for="item in options?.brands"
                :key="item.id"
                :value="item.id"
              >
                {{ item.name }}
              </option>
            </select></label
          >
          <label
            >配送模板<select v-model.number="form.deliveryTemplateId" required>
              <option :value="0" disabled>请选择运费模板</option>
              <option
                v-if="form.deliveryTemplateId && !selectedTemplate"
                :value="form.deliveryTemplateId"
                disabled
              >
                原模板不可用，请重新选择
              </option>
              <option
                v-for="item in options?.templates"
                :key="item.id"
                :value="item.id"
              >
                {{ item.name }}（{{
                  item.chargeMode === 1 ? "按件" : "按重量"
                }}）
              </option>
            </select></label
          >
          <label
            >排序<input
              v-model.number="form.sort"
              type="number"
              required
              min="0"
              max="10000"
              step="1"
          /></label>
        </div>
        <p
          v-if="
            !categories.length ||
            !options?.brands.length ||
            !options?.templates.length
          "
          class="management-alert"
        >
          请先配置启用的末级分类、品牌和有效运费模板，再保存商品。
        </p>
        <label
          >商品简介<textarea v-model="form.introduction" maxlength="512" />
        </label>
        <label
          >封面图地址<input
            v-model="form.picUrl"
            required
            maxlength="1024" /></label
        ><label v-if="allowed('infra:file:create')"
          >上传封面图片<input
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
            @change="upload($event, 'cover')"
        /></label>
        <h3 class="management-section-title">轮播图片</h3>
        <div
          v-for="(_url, index) in form.sliderPicUrls"
          :key="index"
          class="product-image-row"
        >
          <input
            v-model="form.sliderPicUrls[index]"
            required
            maxlength="1024"
            :aria-label="`轮播图 ${index + 1} 地址`"
          /><button
            type="button"
            class="text-link management-danger"
            @click="form.sliderPicUrls.splice(index, 1)"
          >
            移除
          </button>
        </div>
        <button
          type="button"
          class="button"
          :disabled="form.sliderPicUrls.length >= 20"
          @click="form.sliderPicUrls.push('')"
        >
          添加图片地址</button
        ><label v-if="allowed('infra:file:create')"
          >上传轮播图片<input
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
            @change="upload($event, 'gallery')"
        /></label>
        <h3 class="management-section-title">销售规格与SKU</h3>
        <label
          >规格类型<select v-model="form.specType">
            <option :value="false">单规格</option>
            <option :value="true">多规格</option>
          </select></label
        >
        <div v-if="form.specType">
          <section
            v-for="(dimension, index) in dimensions"
            :key="index"
            class="product-option-row"
          >
            <label
              >销售规格<select
                v-model.number="dimension.propertyId"
                @change="changeProperty(index, 'sales')"
              >
                <option :value="0" disabled>请选择规格</option>
                <option
                  v-if="
                    dimension.propertyId &&
                    !sales.some((item) => item.id === dimension.propertyId)
                  "
                  :value="dimension.propertyId"
                  disabled
                >
                  原属性已停用
                </option>
                <option v-for="item in sales" :key="item.id" :value="item.id">
                  {{ item.name }}
                </option>
              </select></label
            >
            <label
              >规格值（可多选）<select
                v-model="dimension.valueIds"
                multiple
                size="4"
              >
                <option
                  v-for="item in values[dimension.propertyId] || []"
                  :key="item.id"
                  :value="item.id"
                  :disabled="item.status !== 0"
                >
                  {{ item.name }}{{ item.status === 1 ? "（停用）" : "" }}
                </option>
              </select></label
            ><button
              type="button"
              class="text-link management-danger"
              @click="dimensions.splice(index, 1)"
            >
              移除维度
            </button>
          </section>
          <button
            type="button"
            class="button"
            :disabled="dimensions.length >= (options?.maxDimensions ?? 20)"
            @click="dimensions.push({ propertyId: 0, valueIds: [] })"
          >
            添加销售规格
          </button>
        </div>
        <button type="button" class="button" @click="generate">
          生成所选组合
        </button>
        <p class="muted">
          最多
          {{ options?.maxSkus }}
          条。相同组合保留原SKU编号；未保留的旧组合会停用，库存和历史记录继续保留。
        </p>
        <p v-if="combinationsStale" class="management-alert">
          销售规格已变化，请重新生成并核对组合后保存。
        </p>
        <section
          v-for="(sku, index) in skus"
          :key="skuCombinationKey(sku.properties)"
          class="product-sku-card"
        >
          <div class="product-sku-heading">
            <strong>{{
              sku.properties
                .map((value) => `${value.propertyName}：${value.valueName}`)
                .join(" / ")
            }}</strong
            ><button
              v-if="form.specType"
              type="button"
              class="text-link management-danger"
              @click="removeSku(index)"
            >
              移除此组合
            </button>
          </div>
          <p v-if="sku.id" class="muted">
            SKU {{ sku.id }} · 当前库存
            {{ sku.currentStock }}（库存请通过库存调整修改）
          </p>
          <div class="form-grid">
            <label
              >售价（元）<input
                v-model="sku.price"
                inputmode="decimal"
                required
                maxlength="11" /></label
            ><label
              >市场价（元）<input
                v-model="sku.marketPrice"
                inputmode="decimal"
                maxlength="11" /></label
            ><label
              >成本价（元）<input
                v-model="sku.costPrice"
                inputmode="decimal"
                maxlength="11"
            /></label>
            <label v-if="!sku.id"
              >初始库存<input
                v-model="sku.stock"
                inputmode="numeric"
                required
                maxlength="10" /></label
            ><label
              >状态<select v-model.number="sku.status">
                <option :value="0">启用</option>
                <option :value="1">停用</option>
              </select></label
            ><label>条码<input v-model="sku.barCode" maxlength="100" /></label
            ><label
              >重量（kg）<input
                v-model="sku.weight"
                inputmode="decimal"
                maxlength="19"
                :required="
                  selectedTemplate?.chargeMode === 2 && sku.status === 0
                " /></label
            ><label
              >体积（m³）<input
                v-model="sku.volume"
                inputmode="decimal"
                maxlength="19"
            /></label>
          </div>
          <label
            >SKU图片地址<input
              v-model="sku.picUrl"
              required
              maxlength="1024" /></label
          ><button
            type="button"
            class="text-link"
            @click="sku.picUrl = form.picUrl"
          >
            使用封面图片</button
          ><label v-if="allowed('infra:file:create')"
            >上传SKU图片<input
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
              @change="upload($event, index)"
          /></label>
        </section>
        <details v-if="retained.length">
          <summary>未纳入当前编辑的历史组合（{{ retained.length }}）</summary>
          <p class="muted">
            这些SKU编号、库存和历史引用会保留；原来启用的组合在保存时停用。
          </p>
          <p v-for="sku in retained" :key="sku.id">
            SKU {{ sku.id }} ·
            {{ sku.properties.map((value) => value.valueName).join(" / ") }} ·
            库存 {{ sku.stock }}
            <button type="button" class="text-link" @click="restoreSku(sku.id)">
              放回当前规格
            </button>
          </p>
        </details>
        <h3 class="management-section-title">展示属性</h3>
        <p class="muted">材质、产地等展示属性不会生成SKU库存组合。</p>
        <div
          v-for="(item, index) in display"
          :key="index"
          class="product-option-row"
        >
          <label
            >展示属性<select
              v-model.number="item.propertyId"
              @change="changeProperty(index, 'display')"
            >
              <option :value="0" disabled>请选择属性</option>
              <option
                v-if="
                  item.propertyId &&
                  !displays.some((value) => value.id === item.propertyId)
                "
                :value="item.propertyId"
                disabled
              >
                原属性不可用
              </option>
              <option
                v-for="value in displays"
                :key="value.id"
                :value="value.id"
              >
                {{ value.name }}
              </option>
            </select></label
          ><label
            >属性值<select v-model.number="item.valueId">
              <option :value="0" disabled>请选择属性值</option>
              <option
                v-for="value in values[item.propertyId] || []"
                :key="value.id"
                :value="value.id"
                :disabled="value.status !== 0"
              >
                {{ value.name }}
              </option>
            </select></label
          ><button
            type="button"
            class="text-link management-danger"
            @click="display.splice(index, 1)"
          >
            移除
          </button>
        </div>
        <button
          type="button"
          class="button"
          :disabled="display.length >= 100"
          @click="display.push({ propertyId: 0, valueId: 0 })"
        >
          添加展示属性
        </button>
        <h3 class="management-section-title">商品详情</h3>
        <label
          >详情正文<textarea
            v-model="form.description"
            required
            maxlength="200000"
            rows="8"
          />
        </label>
      </fieldset>
    </form>
    <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <button v-if="!loaded && !loading" class="button" @click="load">
      重新读取
    </button>
    <template #footer
      ><button class="button" :disabled="busy" @click="emit('close')">
        关闭</button
      ><button
        v-if="loaded && canSave"
        class="button primary"
        form="product-editor"
        :disabled="busy || loading || uncertain || combinationsStale"
      >
        {{ busy ? "处理中…" : "保存商品" }}
      </button></template
    >
  </Modal>
</template>
<style scoped>
.product-option-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  align-items: end;
  gap: 16px;
  margin: 16px 0;
}
.product-sku-card {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 18px;
  margin: 16px 0;
}
.product-sku-heading,
.product-image-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}
.product-image-row input {
  flex: 1;
  min-width: 0;
}
.product-sku-card .muted {
  line-height: 1.7;
}
@media (max-width: 640px) {
  .product-option-row {
    grid-template-columns: 1fr;
  }
  .product-sku-heading {
    align-items: start;
    flex-direction: column;
  }
}
</style>
