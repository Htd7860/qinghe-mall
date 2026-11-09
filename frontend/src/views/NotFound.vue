<script setup lang="ts">
import Empty from "../components/Empty.vue";
</script>
<template>
  <main class="not-found">
    <Empty title="页面不存在" icon="search"
      ><RouterLink class="button primary" to="/admin/overview"
        >返回工作台</RouterLink
      ></Empty
    >
  </main>
</template>
