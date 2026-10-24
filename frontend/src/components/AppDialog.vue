<template>
  <Teleport to="body">
    <dialog
      ref="element"
      class="app-dialog"
      :class="{ drawer, wide }"
      :aria-label="title"
      @cancel.prevent="requestClose"
      @click="backdrop"
    >
      <section class="dialog-surface">
        <header class="dialog-heading">
          <h2>{{ title }}</h2>
          <button type="button" aria-label="关闭" :disabled="busy" @click="requestClose">×</button>
        </header>
        <slot />
      </section>
    </dialog>
  </Teleport>
</template>
<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
const props = withDefaults(
  defineProps<{ open: boolean; title: string; drawer?: boolean; wide?: boolean; busy?: boolean }>(),
  { drawer: false, wide: false, busy: false },
);
const emit = defineEmits<{ close: [] }>();
const element = ref<HTMLDialogElement | null>(null);
let mounted = false;
async function sync() {
  await nextTick();
  if (!mounted || !element.value) return;
  if (props.open && !element.value.open) element.value.showModal();
  else if (!props.open && element.value.open) element.value.close();
}
function requestClose() {
  if (!props.busy) emit('close');
}
function backdrop(event: MouseEvent) {
  if (event.target === element.value) requestClose();
}
watch(() => props.open, sync);
onMounted(() => {
  mounted = true;
  void sync();
});
onBeforeUnmount(() => {
  mounted = false;
  element.value?.close();
});
</script>
<style scoped>
.app-dialog {
  border: 0;
  padding: 0;
  width: min(600px, calc(100vw - 32px));
  max-height: calc(100dvh - 48px);
  border-radius: 18px;
  color: #24362e;
  background: #fff;
  box-shadow: 0 24px 90px #132b3433;
  overflow: auto;
  overscroll-behavior: contain;
}
.app-dialog::backdrop {
  background: #20322b66;
  backdrop-filter: blur(2px);
}
.app-dialog.wide {
  width: min(980px, calc(100vw - 32px));
}
.dialog-surface {
  padding: 24px;
  min-width: 0;
}
.dialog-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}
.dialog-heading h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.dialog-heading button {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid #e2e8e4;
  border-radius: 9px;
  background: #fff;
  color: #667a70;
  font-size: 23px;
  cursor: pointer;
}
.drawer {
  position: fixed;
  inset: 0 0 0 auto;
  margin: 0;
  width: min(790px, 100vw);
  max-width: 100vw;
  height: 100dvh;
  max-height: 100dvh;
  border-radius: 20px 0 0 20px;
}
.drawer .dialog-surface {
  padding: 28px;
}
:global(body:has(.app-dialog[open])) {
  overflow: hidden;
}
@media (max-width: 600px) {
  .dialog-surface,
  .drawer .dialog-surface {
    padding: 20px 16px;
  }
  .drawer {
    border-radius: 0;
  }
}
</style>
