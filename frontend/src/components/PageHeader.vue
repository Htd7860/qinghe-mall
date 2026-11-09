<script setup lang="ts">
defineProps<{ title: string; eyebrow?: string }>();
</script>
<template>
  <div class="page-heading">
    <div>
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
    </div>
    <div class="heading-actions"><slot /></div>
  </div>
</template>
