<template>
  <AppDialog :open="open" title="计划补练" :busy="saving" wide @close="$emit('close')">
    <p class="practice-intro">第 {{ item?.day ?? '—' }} 天的练习 · 提交后保存到这份复习计划。</p>
    <p v-if="loading" class="sw-empty" role="status">正在读取练习题目…</p>
    <p v-else-if="loadError" class="sw-error" role="alert">
      {{ loadError }}
      <button class="sw-link" :disabled="saving" @click="loadQuestion">重新加载</button>
    </p>
    <form v-if="question && !loading" @submit.prevent="submit">
      <div class="sw-formula"><LatexText :content="question.content" /></div>
      <img
        v-if="question.imageBase64"
        :src="question.imageBase64"
        alt="题目配图"
        class="sw-question-image"
      />
      <fieldset class="practice-options">
        <legend class="practice-sr-only">选择本题答案</legend>
        <label
          v-for="(text, key) in question.options"
          :key="key"
          class="practice-option"
          :class="{ selected: selected === String(key) }"
          ><input
            v-model="selected"
            type="radio"
            :name="`plan-item-${item?.id}`"
            :value="String(key)"
            :disabled="saving || !!pending || result !== null" /><strong>{{ key }}</strong
          ><LatexText :content="text"
        /></label>
      </fieldset>
      <p v-if="error" class="sw-error" role="alert">{{ error }}</p>
      <p v-if="result !== null" class="practice-result" :class="{ wrong: !result }" role="status">
        {{ result ? '这次答对了，补练记录已保存。' : '这次还没有答对，记录已保存，可以再试一次。' }}
      </p>
      <p v-else-if="pending" class="sw-feedback">
        已保留上次提交的答案 {{ pending.userAnswer }}，重试会继续保存同一条作答。
      </p>
      <div class="sw-dialog-footer">
        <button type="button" class="sw-button" :disabled="saving" @click="$emit('close')">
          返回计划</button
        ><button v-if="result !== null" type="button" class="sw-button primary" @click="restart">
          再练一次</button
        ><button v-else class="sw-button primary" :disabled="saving || !selected">
          {{ saving ? '正在保存…' : pending ? '重试保存答案' : '提交答案' }}
        </button>
      </div>
    </form>
    <template v-else-if="!loading"
      ><p v-if="pending" class="sw-feedback">
        已保留上次的答案 {{ pending.userAnswer }}，可以重试保存。
      </p>
      <p v-if="error" class="sw-error" role="alert">{{ error }}</p>
      <div class="sw-dialog-footer">
        <button class="sw-button" :disabled="saving" @click="$emit('close')">返回计划</button
        ><button v-if="pending" class="sw-button primary" :disabled="saving" @click="submit">
          {{ saving ? '正在保存…' : '重试保存答案' }}
        </button>
      </div></template
    >
  </AppDialog>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import '@/assets/student-workspace.css';
import AppDialog from '@/components/AppDialog.vue';
import { createRequestId } from '@/utils/requestId';
import LatexText from '@/components/LatexText.vue';
import { useAuthStore } from '@/stores/auth';
import { getPlanItemQuestionApi, submitPlanItemAnswerApi } from '@/api/learning-plan';
import type { LearningPlanItem } from '@/types/learning-plan';
const props = defineProps<{ open: boolean; planId: number; item: LearningPlanItem | null }>();
const emit = defineEmits<{ close: []; completed: [] }>();
const auth = useAuthStore();
type Pending = { requestId: string; userAnswer: string };
const loading = ref(false),
  saving = ref(false),
  loadError = ref(''),
  error = ref(''),
  selected = ref(''),
  pending = ref<Pending | null>(null),
  result = ref<boolean | null>(null),
  question = ref<Awaited<ReturnType<typeof getPlanItemQuestionApi>> | null>(null);
let alive = true,
  sequence = 0;
const storageKey = () =>
  `student_plan_pending_${auth.user?.id ?? 'anonymous'}_${props.planId}_${props.item?.id ?? 'none'}`;
function restore() {
  pending.value = null;
  selected.value = '';
  try {
    const raw = sessionStorage.getItem(storageKey());
    if (raw) {
      const data = JSON.parse(raw) as Pending;
      if (typeof data.requestId === 'string' && typeof data.userAnswer === 'string') {
        pending.value = data;
        selected.value = data.userAnswer;
      }
    }
  } catch {
    /* Invalid local state must not prevent loading the plan. */
  }
}
async function loadQuestion() {
  if (saving.value) return;
  const id = props.item?.id,
    planId = props.planId,
    owner = ++sequence;
  if (!props.open || !id || !planId) return;
  loading.value = true;
  loadError.value = '';
  try {
    const data = await getPlanItemQuestionApi(planId, id);
    if (alive && owner === sequence) question.value = data;
  } catch (e: any) {
    if (alive && owner === sequence)
      loadError.value = e?.response?.data?.message ?? '题目暂时无法读取，请重试。';
  } finally {
    if (alive && owner === sequence) loading.value = false;
  }
}
async function submit() {
  const id = props.item?.id,
    planId = props.planId;
  if (
    !id ||
    !planId ||
    saving.value ||
    result.value !== null ||
    (!pending.value && !selected.value)
  )
    return;
  error.value = '';
  const key = storageKey(),
    owner = sequence;
  try {
    if (!pending.value) {
      const body = { requestId: createRequestId(), userAnswer: selected.value };
      sessionStorage.setItem(key, JSON.stringify(body));
      pending.value = body;
    }
    const body = pending.value;
    saving.value = true;
    const response = await submitPlanItemAnswerApi(planId, id, body.userAnswer, body.requestId);
    if (!alive || owner !== sequence) return;
    sessionStorage.removeItem(key);
    pending.value = null;
    result.value = response.correct;
    emit('completed');
  } catch (e: any) {
    if (alive && owner === sequence)
      error.value = e?.response?.data?.message ?? '答案暂未确认保存，请重试。';
  } finally {
    if (alive && owner === sequence) saving.value = false;
  }
}
function restart() {
  if (saving.value) return;
  selected.value = '';
  pending.value = null;
  result.value = null;
  error.value = '';
}
watch(
  [() => props.open, () => props.planId, () => props.item?.id],
  () => {
    ++sequence;
    loading.value = false;
    saving.value = false;
    question.value = null;
    loadError.value = '';
    error.value = '';
    result.value = null;
    if (!props.open) return;
    if (!props.item?.id) {
      loadError.value = '请返回并刷新复习计划，再打开练习。';
      return;
    }
    restore();
    void loadQuestion();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  alive = false;
  ++sequence;
});
</script>
<style scoped>
.practice-intro {
  font-size: 12px;
  color: #8da393;
  line-height: 1.8;
  margin: 0 0 23px;
}
.practice-options {
  display: grid;
  gap: 12px;
  padding: 0;
  margin: 22px 0;
  border: 0;
  min-width: 0;
}
.practice-option {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e0eae3;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  line-height: 1.9;
  color: #6a8572;
  overflow-wrap: anywhere;
}
.practice-option.selected {
  border-color: #adcdb7;
  background: #f5f9f6;
}
.practice-option input {
  accent-color: #527e61;
  flex-shrink: 0;
  margin-top: 5px;
}
.practice-option strong {
  font-weight: 500;
  flex-shrink: 0;
}
.practice-result {
  padding: 14px 16px;
  background: #f1f8f3;
  border: 1px solid #d9eadd;
  border-radius: 8px;
  color: #70987e;
  font-size: 13px;
  line-height: 1.8;
}
.practice-result.wrong {
  background: #fcf7ef;
  border-color: #e9deca;
  color: #a08d65;
}
.practice-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
.sw-formula {
  font-size: 14px;
}
</style>
