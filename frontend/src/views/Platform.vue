<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import PageHeader from "../components/PageHeader.vue";
import Icon from "../components/Icon.vue";
import Modal from "../components/Modal.vue";
import Empty from "../components/Empty.vue";
import { db, command, attempt, notify, log } from "../data/store";
import { demo } from "../api/session";
import { money, timestamp, type Tenant } from "../domain";
const route = useRoute(),
  mode = computed(() => route.path.split("/").at(-1)),
  query = ref(""),
  editor = ref(false),
  editing = ref<Tenant>(),
  confirmation = ref<Tenant>(),
  error = ref(""),
  form = reactive({
    code: "",
    name: "",
    owner: "",
    domain: "",
    package: "基础版",
    expire: "2027-10-19",
    quota: 10,
  });
const tenants = computed(() =>
  db.tenants.filter(
    (t) =>
      !query.value ||
      t.name.includes(query.value) ||
      t.code.includes(query.value),
  ),
);
function open(t?: Tenant) {
  editing.value = t;
  Object.assign(
    form,
    t || {
      code: "",
      name: "",
      owner: "",
      domain: "",
      package: "基础版",
      expire: "2027-10-19",
      quota: 10,
    },
  );
  error.value = "";
  editor.value = true;
}
async function save() {
  error.value = "";
  try {
    if (!demo) {
      await command(
        editing.value ? "tenant.update" : "tenant.create",
        { ...form },
        () => {},
      );
      editor.value = false;
      notify("租户信息已保存");
      return;
    }
    if (!/^[a-z0-9][a-z0-9-]{2,31}$/.test(form.code))
      throw new Error("商家编码需为3至32位小写字母、数字或短横线");
    if (!form.name.trim() || !form.owner.trim())
      throw new Error("请填写租户名称和联系人");
    if (!/^[a-z0-9]+([.-][a-z0-9]+)+$/.test(form.domain))
      throw new Error("请填写不含协议和路径的有效域名");
    if (
      db.tenants.some(
        (t) =>
          t !== editing.value &&
          (t.code === form.code || t.domain === form.domain),
      )
    )
      throw new Error("商家编码或域名已被使用");
    if (editing.value) {
      Object.assign(editing.value, form);
      log("更新租户", form.name, "platform");
    } else {
      db.tenants.push({ ...form, status: "正常" });
      db.settings[form.code] = {
        name: form.name,
        tagline: "发现好物，享受日常。",
        phone: "",
        announcement: "欢迎光临",
        logo: form.name.slice(0, 2),
      };
      db.staff.push({
        id: Date.now(),
        tenant: form.code,
        name: form.owner,
        username: "admin",
        role: "商家管理员",
        department: "经营管理",
        status: "正常",
      });
      db.roles.push({
        id: Date.now(),
        tenant: form.code,
        name: "商家管理员",
        code: "tenant_admin",
        permissions: [
          "product",
          "order",
          "refund",
          "member",
          "marketing",
          "settings",
          "staff",
        ],
        status: "正常",
      });
      log("开通租户", form.name, "platform");
    }
    editor.value = false;
    notify(editing.value ? "租户已更新" : "租户已开通");
  } catch (e) {
    error.value = (e as Error).message;
  }
}
</script>
<template>
  <PageHeader :title="String(route.meta.title)" eyebrow="平台运营"
    ><button v-if="mode === 'tenants'" class="button primary" @click="open()">
      <Icon name="plus" :size="16" />开通租户
    </button></PageHeader
  >
  <div v-if="mode === 'tenants'" class="platform-summary">
    <div>
      <span>品牌商家</span><strong>{{ db.tenants.length }}</strong>
    </div>
    <div>
      <span>正常经营</span
      ><strong>{{
        db.tenants.filter((t) => t.status === "正常").length
      }}</strong>
    </div>
    <div>
      <span>已停用</span
      ><strong>{{
        db.tenants.filter((t) => t.status === "已停用").length
      }}</strong>
    </div>
  </div>
  <section v-if="mode === 'tenants' || mode === 'domains'" class="panel">
    <div class="filter-bar">
      <label class="search-field"
        ><Icon name="search" /><input
          v-model.trim="query"
          placeholder="搜索租户名称或编码"
          aria-label="搜索租户" /></label
      ><span class="filter-count">共 {{ tenants.length }} 个租户</span>
    </div>
    <div class="table-scroll">
      <table v-if="mode === 'tenants'">
        <thead>
          <tr>
            <th>租户</th>
            <th>联系人</th>
            <th>使用套餐</th>
            <th>员工额度</th>
            <th>有效期至</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tenants" :key="t.code">
            <td>
              <div class="member-cell">
                <span class="brand-avatar">{{ t.name.slice(0, 1) }}</span>
                <div>
                  <strong>{{ t.name }}</strong
                  ><small>{{ t.code }}</small>
                </div>
              </div>
            </td>
            <td>{{ t.owner }}</td>
            <td>
              <span class="tag">{{ t.package }}</span>
            </td>
            <td>
              {{ db.staff.filter((s) => s.tenant === t.code).length }} /
              {{ t.quota }}
            </td>
            <td>{{ t.expire }}</td>
            <td>
              <span
                class="status"
                :class="t.status === '正常' ? 'green' : 'gray'"
                >{{ t.status }}</span
              >
            </td>
            <td>
              <div class="row-actions">
                <button @click="open(t)">管理</button
                ><button @click="confirmation = t">
                  {{ t.status === "正常" ? "停用" : "启用" }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <table v-else>
        <thead>
          <tr>
            <th>商城域名</th>
            <th>所属租户</th>
            <th>用途</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tenants" :key="t.code">
            <td>
              <strong>{{ t.domain }}</strong>
            </td>
            <td>{{ t.name }}</td>
            <td>品牌商城</td>
            <td>
              <span
                class="status"
                :class="t.status === '正常' ? 'green' : 'gray'"
                >{{ t.status === "正常" ? "已启用" : "已停用" }}</span
              >
            </td>
            <td>
              <button class="text-link" @click="open(t)">管理入口</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Empty v-if="!tenants.length" title="未找到租户" icon="store" />
  </section>
  <div v-else-if="mode === 'packages'" class="package-grid">
    <section v-for="p in db.packages" :key="p.id" class="panel package-card">
      <span class="tag">{{ p.status === "正常" ? "可开通" : "已停用" }}</span>
      <h2>{{ p.name }}</h2>
      <p class="package-price">¥{{ money(p.price) }}<span>/ 年</span></p>
      <p class="muted">{{ p.quota }} 个员工账号</p>
      <ul>
        <li v-for="f in p.features" :key="f">
          <Icon name="check" :size="17" />{{ f }}
        </li>
      </ul>
      <div class="package-footer">
        <span
          >{{
            db.tenants.filter((t) => t.package === p.name).length
          }}
          个租户使用</span
        ><button
          class="button"
          @click="
            attempt(async () => {
              await command(
                'package.status',
                { id: p.id, status: p.status === '正常' ? '已停用' : '正常' },
                () => {
                  p.status = p.status === '正常' ? '已停用' : '正常';
                  log('修改套餐状态', p.name, 'platform');
                },
              );
              notify('套餐状态已更新');
            })
          "
        >
          {{ p.status === "正常" ? "停用新开通" : "恢复开通" }}
        </button>
      </div>
    </section>
  </div>
  <section v-else class="panel">
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>操作</th>
            <th>对象</th>
            <th>操作人</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="a in db.audit.filter((a) => a.tenant === 'platform')"
            :key="a.id"
          >
            <td>{{ a.action }}</td>
            <td>{{ a.subject }}</td>
            <td>平台运营</td>
            <td>{{ a.at }}</td>
          </tr>
        </tbody>
      </table>
      <Empty
        v-if="!db.audit.some((a) => a.tenant === 'platform')"
        title="暂无平台操作记录"
        icon="shield"
      />
    </div>
  </section>
  <Modal
    v-if="editor"
    :title="editing ? '管理租户' : '开通租户'"
    @close="editor = false"
    ><form id="tenant-form" @submit.prevent="save">
      <label
        >租户名称<input
          v-model.trim="form.name"
          required
          maxlength="30" /></label
      ><label
        >商家编码<input
          v-model.trim="form.code"
          :disabled="!!editing"
          required
          minlength="3"
          maxlength="32" /></label
      ><label>联系人<input v-model.trim="form.owner" required /></label
      ><label
        >商城域名<input
          v-model.trim="form.domain"
          required
          placeholder="brand.mall.test"
      /></label>
      <div class="form-grid">
        <label
          >使用套餐<select
            v-model="form.package"
            @change="
              form.quota = db.packages.find(
                (p) => p.name === form.package,
              )!.quota
            "
          >
            <option
              v-for="p in db.packages.filter(
                (p) => p.status === '正常' || p.name === form.package,
              )"
              :key="p.id"
            >
              {{ p.name }}
            </option>
          </select></label
        ><label
          >有效期至<input v-model="form.expire" type="date" required
        /></label>
      </div>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <template #footer
      ><button class="button" @click="editor = false">取消</button
      ><button class="button primary" form="tenant-form" type="submit">
        {{ editing ? "保存更改" : "开通租户" }}
      </button></template
    ></Modal
  >
  <Modal
    v-if="confirmation"
    :title="confirmation.status === '正常' ? '停用租户' : '启用租户'"
    @close="confirmation = undefined"
    ><p>
      确认{{ confirmation.status === "正常" ? "停用" : "启用" }}「{{
        confirmation.name
      }}」？
    </p>
    <p v-if="confirmation.status === '正常'" class="muted">
      新订单和营销活动将停止，已有订单与售后继续处理。
    </p>
    <template #footer
      ><button class="button" @click="confirmation = undefined">取消</button
      ><button
        class="button primary"
        @click="
          attempt(async () => {
            await command(
              'tenant.status',
              {
                code: confirmation!.code,
                status: confirmation!.status === '正常' ? '已停用' : '正常',
              },
              () => {
                confirmation!.status =
                  confirmation!.status === '正常' ? '已停用' : '正常';
                log('修改租户状态', confirmation!.name, 'platform');
              },
            );
            confirmation = undefined;
            notify('租户状态已更新');
          })
        "
      >
        确认{{ confirmation.status === "正常" ? "停用" : "启用" }}
      </button></template
    ></Modal
  >
</template>
