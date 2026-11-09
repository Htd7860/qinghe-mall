<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import Icon from "./Icon.vue";
defineProps<{ title: string; wide?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const box = ref<HTMLElement>(),
  previous = document.activeElement as HTMLElement | null;
function key(e: KeyboardEvent) {
  if (e.key === "Escape") emit("close");
  if (e.key === "Tab") {
    const nodes = box.value?.querySelectorAll<HTMLElement>(
      "button:not(:disabled),input,select,textarea,a[href]",
    );
    if (!nodes?.length) return;
    const first = nodes[0],
      last = nodes[nodes.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last?.focus();
    }
    if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first?.focus();
    }
  }
}
onMounted(() => {
  document.body.style.overflow = "hidden";
  document.addEventListener("keydown", key);
  box.value?.querySelector<HTMLElement>("input,select,button")?.focus();
});
onUnmounted(() => {
  document.body.style.overflow = "";
  document.removeEventListener("keydown", key);
  previous?.focus();
});
</script>
<template>
  <Teleport to="body"
    ><div class="modal-backdrop" @mousedown.self="emit('close')">
      <section
        ref="box"
        class="modal"
        :class="{ wide }"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <header>
          <h2>{{ title }}</h2>
          <button
            class="icon-button"
            aria-label="关闭对话框"
            @click="emit('close')"
          >
            <Icon name="close" />
          </button>
        </header>
        <div class="modal-body"><slot /></div>
        <footer v-if="$slots.footer"><slot name="footer" /></footer>
      </section></div
  ></Teleport>
</template>
