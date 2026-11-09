<script setup lang="ts">
import Icon from "./Icon.vue";
defineProps<{ title?: string; icon?: string }>();
</script>
<template>
  <div class="empty">
    <div class="empty-icon"><Icon :name="icon || 'product'" :size="28" /></div>
    <h3>{{ title || "暂无记录" }}</h3>
    <slot />
  </div>
</template>
