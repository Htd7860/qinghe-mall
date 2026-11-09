<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import Icon from "../components/Icon.vue";
import { demo, identities, signIn } from "../api/session";
import { safeReturn, merchantHome, platformHome } from "../api/policy";
const route = useRoute(),
  router = useRouter();
const platform = computed(() => !!route.meta.platform);
const code = ref(""),
  username = ref(""),
  password = ref(""),
  busy = ref(false);
const error = ref(
  route.query.unavailable ? "登录服务暂不可用，请稍后重试" : "",
);
async function login() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  const realm = platform.value ? "platform" : "merchant";
  try {
    if (import.meta.env.MODE === "prototype") {
      const { demoSignIn } = await import("../data/demo-auth");
      demoSignIn(realm, code.value, username.value, password.value);
    } else
      await signIn(realm, {
        tenant: platform.value ? undefined : code.value,
        username: username.value,
        password: password.value,
      });
    password.value = "";
    let target = safeReturn(route.query.redirect, realm);
    if (
      realm === "merchant" &&
      ((target === "/admin/overview" &&
        !identities.merchant?.permissions.includes("order")) ||
        !route.query.redirect)
    )
      target = merchantHome(identities.merchant?.permissions || []);
    if (!demo && realm === "platform" && !route.query.redirect)
      target = platformHome(identities.platform?.permissions || []);
    await router.replace(target);
  } catch (e) {
    password.value = "";
    error.value = e instanceof Error ? e.message : "登录未完成，请重试";
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="login-page">
    <section class="login-brand">
      <div class="login-logo"><Icon name="bag" :size="30" />青禾 Commerce</div>
      <div>
        <span>为独立品牌而生</span>
        <h1>好生意，<br />从这里开始。</h1>
        <p>让每一份用心，都有回响。</p>
      </div>
      <small>QINGHE COMMERCE</small>
    </section>
    <section class="login-form">
      <form @submit.prevent="login">
        <h2>{{ platform ? "登录平台运营中心" : "登录商家后台" }}</h2>
        <p class="muted">欢迎回来</p>
        <label v-if="!platform"
          >商家编码<input
            v-model.trim="code"
            required
            autocomplete="organization" /></label
        ><label
          >员工账号<input
            v-model.trim="username"
            required
            autocomplete="username" /></label
        ><label
          >密码<input
            v-model="password"
            type="password"
            required
            autocomplete="current-password"
        /></label>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="button primary full-width" :disabled="busy">
          {{ busy ? "登录中…" : "登录" }}
        </button>
        <p v-if="demo" class="muted small" style="margin-top: 16px">
          演示环境 · 使用独立演示账号
        </p>
      </form>
    </section>
  </main>
</template>
