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
  brand,
  activeTenant,
  attempt,
  notify,
  log,
  requirePermission,
} from "../data/store";
import { demo } from "../api/session";
import { timestamp, money, type Staff, type Role } from "../domain";
const route = useRoute(),
  mode = computed(() => route.path.split("/").at(-1)),
  tab = ref("员工"),
  query = ref(""),
  editor = ref(false),
  editingRole = ref<Role>(),
  confirmation = ref<Staff>(),
  error = ref(""),
  rolePermissions = ref<string[]>([]);
const profile = reactive({ ...brand.value }),
  form = reactive({
    name: "",
    username: "",
    department: "商品部",
    role: "商品运营",
    first: 8,
    additional: 0,
    freeAbove: 299,
    region: "中国大陆（不含港澳台）",
  });
const permissions = [
  ["seckill.view", "查看秒杀活动"],
  ["seckill.manage", "编辑秒杀活动"],
  ["seckill.publish", "发布与停止秒杀"],
  ["product", "商品与库存"],
  ["order", "订单与售后审核"],
  ["refund", "执行退款"],
  ["member", "会员管理"],
  ["marketing", "优惠券与营销"],
  ["staff", "员工与权限"],
  ["settings", "店铺与配送设置"],
];
const staff = computed(() =>
  db.staff.filter(
    (s) =>
      s.tenant === ui.tenant &&
      (!query.value ||
        s.name.includes(query.value) ||
        s.username.includes(query.value)),
  ),
);
const audit = computed(() =>
  db.audit.filter(
    (a) =>
      a.tenant === ui.tenant &&
      (!query.value ||
        a.action.includes(query.value) ||
        a.subject.includes(query.value)),
  ),
);
function saveProfile() {
  attempt(async () => {
    requirePermission("settings");
    if (!profile.name.trim() || !profile.phone.trim())
      throw new Error("请填写店铺名称和联系电话");
    if (!demo) {
      await command("store.save", { ...profile }, () => {});
      notify("店铺设置已保存");
      return;
    }
    Object.assign(db.settings[ui.tenant]!, profile);
    activeTenant.value.name = profile.name;
    log("更新店铺设置", profile.name);
    notify("店铺设置已保存");
  });
}
async function submit() {
  error.value = "";
  try {
    if (!demo) {
      await command(
        mode.value === "staff" ? "staff.create" : "shipping.create",
        mode.value === "staff"
          ? {
              name: form.name,
              username: form.username,
              department: form.department,
              role: form.role,
            }
          : {
              name: form.name,
              region: form.region,
              first: Math.round(form.first * 100),
              additional: Math.round(form.additional * 100),
              freeAbove: Math.round(form.freeAbove * 100),
            },
        () => {},
      );
      editor.value = false;
      notify("已保存");
      return;
    }
    if (mode.value === "staff") {
      requirePermission("staff");
      if (!form.name.trim() || !form.username.trim())
        throw new Error("请输入姓名和员工账号");
      if (
        db.staff.some(
          (s) => s.tenant === ui.tenant && s.username === form.username,
        )
      )
        throw new Error("员工账号已存在");
      if (
        db.staff.filter((s) => s.tenant === ui.tenant).length >=
        activeTenant.value.quota
      )
        throw new Error("员工账号已达套餐上限");
      db.staff.push({
        id: Date.now(),
        tenant: ui.tenant,
        name: form.name,
        username: form.username,
        department: form.department,
        role: form.role,
        status: "正常",
      });
      log("添加员工", form.name);
    } else {
      requirePermission("settings");
      if (
        !form.name.trim() ||
        form.first < 0 ||
        form.additional < 0 ||
        form.freeAbove < 0
      )
        throw new Error("请检查模板名称和费用");
      db.shipping.push({
        id: Date.now(),
        tenant: ui.tenant,
        name: form.name,
        region: form.region,
        first: Math.round(form.first * 100),
        additional: Math.round(form.additional * 100),
        freeAbove: Math.round(form.freeAbove * 100),
        enabled: true,
      });
      log("添加配送模板", form.name);
    }
    editor.value = false;
    notify("已保存");
  } catch (e) {
    error.value = (e as Error).message;
  }
}
function open() {
  Object.assign(form, {
    name: "",
    username: "",
    department: "商品部",
    role: "商品运营",
    first: 8,
    additional: 0,
    freeAbove: 299,
    region: "中国大陆（不含港澳台）",
  });
  error.value = "";
  editor.value = true;
}
const session = reactive(ui);
</script>
<template>
  <PageHeader :title="String(route.meta.title)" eyebrow="经营管理"
    ><button
      v-if="mode === 'settings'"
      class="button primary"
      @click="saveProfile"
    >
      保存设置</button
    ><button
      v-else-if="mode === 'staff' && tab === '员工'"
      class="button primary"
      @click="open"
    >
      <Icon name="plus" :size="16" />添加员工</button
    ><button
      v-else-if="mode === 'shipping'"
      class="button primary"
      @click="open"
    >
      <Icon name="plus" :size="16" />添加配送模板
    </button></PageHeader
  >
  <section v-if="mode === 'settings'" class="panel settings-panel">
    <div class="settings-intro">
      <h2>品牌信息</h2>
      <span class="brand-avatar large">{{ brand.logo.slice(0, 1) }}</span>
    </div>
    <form class="settings-form" @submit.prevent="saveProfile">
      <label
        >店铺名称<input
          v-model.trim="profile.name"
          required
          maxlength="30" /></label
      ><label
        >品牌简称<input
          v-model.trim="profile.logo"
          required
          maxlength="4" /></label
      ><label
        >品牌标语<input v-model.trim="profile.tagline" maxlength="40" /></label
      ><label>联系电话<input v-model.trim="profile.phone" required /></label
      ><label
        >商城公告<input v-model.trim="profile.announcement" maxlength="80"
      /></label>
      <div class="readonly-field">
        <span>商城入口</span
        ><a :href="`#/shop/${session.tenant}`"
          >{{ activeTenant.domain }}<Icon name="external" :size="14"
        /></a>
      </div>
    </form>
  </section>
  <section v-else-if="mode === 'staff'" class="panel">
    <div class="tab-row">
      <button
        v-for="t in ['员工', '角色与权限']"
        :key="t"
        :class="{ active: tab === t }"
        @click="tab = t"
      >
        {{ t }}
      </button>
    </div>
    <template v-if="tab === '员工'"
      ><div class="filter-bar">
        <label class="search-field"
          ><Icon name="search" /><input
            v-model.trim="query"
            placeholder="搜索姓名或账号"
            aria-label="搜索员工" /></label
        ><span class="filter-count"
          >账号 {{ staff.length }} / {{ activeTenant.quota }}</span
        >
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>姓名</th>
              <th>账号</th>
              <th>部门</th>
              <th>角色</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in staff" :key="s.id">
              <td>
                <div class="member-cell">
                  <span class="member-avatar">{{ s.name.slice(-2) }}</span
                  ><strong>{{ s.name }}</strong>
                </div>
              </td>
              <td>{{ s.username }}</td>
              <td>{{ s.department }}</td>
              <td>{{ s.role }}</td>
              <td>
                <span
                  class="status"
                  :class="s.status === '正常' ? 'green' : 'gray'"
                  >{{ s.status }}</span
                >
              </td>
              <td>
                <button
                  v-if="s.username !== 'admin'"
                  class="text-link"
                  @click="confirmation = s"
                >
                  {{ s.status === "正常" ? "停用" : "启用" }}</button
                ><span v-else class="muted">管理员</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div></template
    >
    <div v-else class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>角色名称</th>
            <th>角色编码</th>
            <th>已授权功能</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="r in db.roles.filter((r) => r.tenant === session.tenant)"
            :key="r.id"
          >
            <td>
              <strong>{{ r.name }}</strong>
            </td>
            <td class="muted">{{ r.code }}</td>
            <td>{{ r.permissions.length }} 项</td>
            <td>
              <span
                class="status"
                :class="r.status === '正常' ? 'green' : 'gray'"
                >{{ r.status }}</span
              >
            </td>
            <td>
              <div class="row-actions">
                <button
                  v-if="r.code !== 'tenant_admin'"
                  @click="
                    editingRole = r;
                    rolePermissions = [...r.permissions];
                  "
                >
                  配置权限</button
                ><button
                  v-if="r.code !== 'tenant_admin'"
                  @click="
                    attempt(async () => {
                      requirePermission('staff');
                      await command(
                        'role.status',
                        {
                          id: r.id,
                          status: r.status === '正常' ? '已停用' : '正常',
                        },
                        () => {
                          r.status = r.status === '正常' ? '已停用' : '正常';
                          log('修改角色状态', r.name);
                        },
                      );
                      notify('角色状态已更新');
                    })
                  "
                >
                  {{ r.status === "正常" ? "停用" : "启用" }}</button
                ><span v-else class="muted">内置角色</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <section v-else-if="mode === 'shipping'" class="panel">
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>模板名称</th>
            <th>配送范围</th>
            <th>首件费用</th>
            <th>续件费用</th>
            <th>包邮门槛</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="s in db.shipping.filter((s) => s.tenant === session.tenant)"
            :key="s.id"
          >
            <td>
              <strong>{{ s.name }}</strong>
            </td>
            <td>{{ s.region }}</td>
            <td>¥{{ money(s.first) }}</td>
            <td>¥{{ money(s.additional) }}</td>
            <td>{{ s.freeAbove ? "满 ¥" + money(s.freeAbove) : "不包邮" }}</td>
            <td>
              <span class="status" :class="s.enabled ? 'green' : 'gray'">{{
                s.enabled ? "使用中" : "已停用"
              }}</span>
            </td>
            <td>
              <button
                class="text-link"
                @click="
                  attempt(async () => {
                    requirePermission('settings');
                    await command(
                      'shipping.status',
                      { id: s.id, enabled: !s.enabled },
                      () => {
                        s.enabled = !s.enabled;
                        log('修改配送模板', s.name);
                      },
                    );
                    notify('配送模板已更新');
                  })
                "
              >
                {{ s.enabled ? "停用" : "启用" }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Empty
      v-if="!db.shipping.some((s) => s.tenant === session.tenant)"
      title="暂无配送模板"
      ><button class="button primary" @click="open">添加配送模板</button></Empty
    >
  </section>
  <section v-else-if="mode === 'operations'" class="panel">
    <div class="panel-heading"><h2>待处理与历史记录</h2></div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>处理事项</th>
            <th>关联对象</th>
            <th>状态</th>
            <th>处理次数</th>
            <th>最近处理</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="j in db.jobs.filter((j) => j.tenant === session.tenant)"
            :key="j.id"
          >
            <td>{{ j.name }}</td>
            <td>{{ j.subject }}</td>
            <td>
              <span
                class="status"
                :class="j.status === '已完成' ? 'green' : 'amber'"
                >{{ j.status }}</span
              >
            </td>
            <td>{{ j.attempts }}</td>
            <td class="muted">{{ j.at }}</td>
            <td>
              <button
                v-if="j.status === '待重试'"
                class="text-link"
                @click="
                  attempt(async () => {
                    requirePermission('settings');
                    await command('operation.retry', { id: j.id }, () => {
                      j.status = '已完成';
                      j.attempts++;
                      j.at = timestamp();
                      log('重新处理', j.subject);
                    });
                    notify('已提交重新处理，请查看最新状态');
                  })
                "
              >
                重新处理</button
              ><span v-else class="muted">已完成</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
  <section v-else class="panel">
    <div class="filter-bar">
      <label class="search-field"
        ><Icon name="search" /><input
          v-model.trim="query"
          placeholder="搜索操作或对象"
          aria-label="搜索日志" /></label
      ><span class="filter-count">共 {{ audit.length }} 条</span>
    </div>
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
          <tr v-for="(a, i) in audit" :key="`${a.id}-${i}`">
            <td>{{ a.action }}</td>
            <td>{{ a.subject }}</td>
            <td>{{ a.actor }}</td>
            <td class="muted">{{ a.at }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <Empty v-if="!audit.length" title="暂无操作记录" icon="shield" />
  </section>
  <Modal
    v-if="editor"
    :title="mode === 'staff' ? '添加员工' : '添加配送模板'"
    @close="editor = false"
    ><form id="settings-editor" @submit.prevent="submit">
      <template v-if="mode === 'staff'"
        ><label
          >姓名<input v-model.trim="form.name" required maxlength="30" /></label
        ><label
          >员工账号<input
            v-model.trim="form.username"
            required
            pattern="[a-zA-Z0-9_-]{3,30}"
            minlength="3"
            maxlength="30" /></label
        ><label
          >所属部门<select v-model="form.department">
            <option>经营管理</option>
            <option>商品部</option>
            <option>客户服务</option>
            <option>财务部</option>
          </select></label
        ><label
          >角色<select v-model="form.role">
            <option
              v-for="r in db.roles.filter(
                (r) => r.tenant === session.tenant && r.status === '正常',
              )"
              :key="r.id"
            >
              {{ r.name }}
            </option>
          </select></label
        ></template
      ><template v-else
        ><label>模板名称<input v-model.trim="form.name" required /></label
        ><label
          >配送范围<select v-model="form.region">
            <option>中国大陆（不含港澳台）</option>
            <option>浙江、江苏、上海</option>
          </select></label
        >
        <div class="form-grid">
          <label
            >首件费用（元）<input
              v-model.number="form.first"
              type="number"
              min="0"
              step="0.01" /></label
          ><label
            >续件费用（元）<input
              v-model.number="form.additional"
              type="number"
              min="0"
              step="0.01"
          /></label>
        </div>
        <label
          >满多少元包邮<input
            v-model.number="form.freeAbove"
            type="number"
            min="0"
            step="0.01"
          /><small>填0表示不设置包邮门槛</small></label
        ></template
      >
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <template #footer
      ><button class="button" @click="editor = false">取消</button
      ><button class="button primary" form="settings-editor" type="submit">
        保存
      </button></template
    ></Modal
  >
  <Modal
    v-if="editingRole"
    title="配置角色权限"
    @close="editingRole = undefined"
    ><div class="context-block">
      <strong>{{ editingRole.name }}</strong
      ><span>{{ editingRole.code }}</span>
    </div>
    <div class="permission-list">
      <label v-for="p in permissions" :key="p[0]" class="checkbox-label"
        ><input v-model="rolePermissions" type="checkbox" :value="p[0]" />{{
          p[1]
        }}</label
      >
    </div>
    <template #footer
      ><button class="button" @click="editingRole = undefined">取消</button
      ><button
        class="button primary"
        @click="
          attempt(async () => {
            requirePermission('staff');
            await command(
              'role.permissions',
              { id: editingRole!.id, permissions: [...rolePermissions] },
              () => {
                editingRole!.permissions = [...rolePermissions];
                log('更新角色权限', editingRole!.name);
              },
            );
            editingRole = undefined;
            notify('权限已保存');
          })
        "
      >
        保存权限
      </button></template
    ></Modal
  >
  <Modal
    v-if="confirmation"
    :title="confirmation.status === '正常' ? '停用员工' : '启用员工'"
    @close="confirmation = undefined"
    ><p>
      确认{{ confirmation.status === "正常" ? "停用" : "启用" }}「{{
        confirmation.name
      }}」的账号？
    </p>
    <template #footer
      ><button class="button" @click="confirmation = undefined">取消</button
      ><button
        class="button primary"
        @click="
          attempt(async () => {
            requirePermission('staff');
            await command(
              'staff.status',
              {
                id: confirmation!.id,
                status: confirmation!.status === '正常' ? '已停用' : '正常',
              },
              () => {
                confirmation!.status =
                  confirmation!.status === '正常' ? '已停用' : '正常';
                log('修改员工状态', confirmation!.username);
              },
            );
            confirmation = undefined;
            notify('员工状态已更新');
          })
        "
      >
        确认
      </button></template
    ></Modal
  >
</template>
