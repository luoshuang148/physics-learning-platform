<template>
  <div class="auth-password">
    <input
      :id="id"
      v-model="value"
      :type="revealed ? 'text' : 'password'"
      :autocomplete="autocomplete"
      :placeholder="placeholder"
      :disabled="disabled"
      :aria-invalid="invalid || undefined"
      :aria-describedby="describedby"
      required
    />
    <button
      class="auth-password-toggle"
      type="button"
      :aria-label="revealed ? '隐藏密码' : '显示密码'"
      :aria-controls="id"
      :aria-pressed="revealed"
      :disabled="disabled"
      @click="revealed = !revealed"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
        <circle cx="12" cy="12" r="3" />
        <path v-if="revealed" d="m3 3 18 18" />
      </svg>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

withDefaults(defineProps<{
  id: string;
  autocomplete?: string;
  placeholder?: string;
  disabled?: boolean;
  invalid?: boolean;
  describedby?: string;
}>(), { autocomplete: 'current-password', placeholder: '请输入密码', disabled: false, invalid: false });
const value = defineModel<string>({ required: true });
const revealed = ref(false);
</script>
