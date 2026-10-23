<template>
  <AppShell :subtitle="`错题本 · ${auth.user?.realName ?? ''}`">
    <div class="wb-page student-font">
      <section class="wb-hero">
        <div class="wb-tools">
          <input v-model="searchText" class="wb-search" placeholder="搜索题目内容…" />
          <span v-if="!loading" class="wb-count">已显示 {{ records.length }} / {{ total }} 道</span>
        </div>
      </section>

      <div v-if="loading" class="wb-loading">
        <div class="loading-ring" />
        <p>加载错题记录…</p>
      </div>

      <div v-else-if="records.length === 0" class="wb-empty">
        <p class="wb-empty-title">暂无错题记录</p>
      </div>

      <div v-else-if="records.length === 0" class="wb-empty">
        <p class="wb-empty-title">无匹配题目</p>
      </div>

      <div v-else class="wb-list">
        <div
          v-for="(item, idx) in records"
          :key="item.recordId"
          class="wb-card"
          :class="{ expanded: expandedId === item.recordId }"
        >
          <div class="wb-card-head" @click="toggleExpand(item)">
            <span class="wb-idx">{{ idx + 1 }}</span>
            <div class="wb-card-preview">
              <LatexText :content="item.questionPreview?.slice(0, 80) ?? ''" />
              <span v-if="item.questionPreview?.length > 80">…</span>
            </div>
            <div class="wb-card-chips">
              <span class="wb-chip wrong">我选 {{ item.userAnswer }}</span>
              <span class="wb-chip correct">{{ item.correct ? '已掌握' : '待复习' }}</span>
            </div>
            <span class="wb-time">{{ formatTime(item.answeredAt) }}</span>
            <span class="wb-expand-icon">{{ expandedId === item.recordId ? '▲' : '▼' }}</span>
          </div>

          <div v-if="expandedId === item.recordId" class="wb-card-detail">
            <section class="wb-detail-block">
              <h3 class="wb-section-title">题目内容 · {{ (detailOf(item)?.correct ?? item.correct) ? '已通过补练' : '待掌握' }}</h3>
              <button class="wb-practice-btn" @click="practice(item)">针对性补练</button>
              <div v-if="detailOf(item)" class="wb-detail-q"><LatexText :content="detailOf(item)!.questionContent" /></div>
              <div v-else class="muted">{{ detailLoading[item.recordId] ? '正在加载详情…' : '详情加载失败' }}</div>
              <div v-if="detailOf(item)?.imageBase64" class="wb-question-image-wrap">
                <img :src="detailOf(item)!.imageBase64!" alt="题目配图" class="wb-question-image" />
              </div>
            </section>

            <section class="wb-detail-block">
              <h3 class="wb-section-title">选项与答案</h3>
              <div class="wb-detail-opts">
                <div
                  v-for="(text, key) in (detailOf(item)?.questionOptions ?? {})"
                  :key="key"
                  class="wb-opt"
                  :class="{
                    'wb-opt-correct': key === detailOf(item)?.correctAnswer,
                    'wb-opt-wrong': key === item.userAnswer && key !== detailOf(item)?.correctAnswer
                  }"
                >
                  <span class="wb-opt-key">{{ key }}</span>
                  <span><LatexText :content="text" /></span>
                </div>
              </div>
            </section>

            <section v-if="detailOf(item)?.questionAnalysis" class="wb-detail-block">
              <div class="wb-analysis">
                <span class="wb-analysis-label">解析</span>
                <LatexText :content="detailOf(item)!.questionAnalysis!" />
              </div>
            </section>

            <section v-if="detailOf(item)?.knowledgePointCodes?.length" class="wb-detail-block">
              <h3 class="wb-section-title">按知识点推荐练习</h3>
              <button class="wb-practice-btn" :disabled="recommendLoading[item.recordId]" @click="loadRecommendations(item)">
                {{ recommendLoading[item.recordId] ? '正在查找…' : '查找相同知识点练习' }}
              </button>
              <p v-if="recommendations[item.recordId]?.length === 0" class="muted">当前没有可安全推荐的题目。</p>
              <p v-if="recommendErrors[item.recordId]" class="muted">{{ recommendErrors[item.recordId] }}</p>
              <button v-for="candidate in (recommendations[item.recordId] ?? [])" :key="candidate.questionId"
                class="wb-recommendation" @click="practice({ eventId: item.eventId, questionId: candidate.questionId })">
                <LatexText :content="candidate.content.slice(0, 120)" />
                <span>难度 {{ candidate.difficulty ?? '未标注' }} · 开始练习</span>
              </button>
            </section>
          </div>
        </div>
      </div>

      <div v-if="page < pages" class="wb-pager">
        <button class="neon-btn ghost" :disabled="loadingMore" @click="loadPage(page + 1, true)">
          {{ loadingMore ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import LatexText from '@/components/LatexText.vue';
import { useAuthStore } from '@/stores/auth';
import { getAnswerRecordDetailApi, getPracticeReviewPageApi } from '@/api/answer-record';
import { getPracticeRecommendationsApi, type PracticeCandidate } from '@/api/question';
import type { AnswerRecordResponse, AnswerRecordSummary } from '@/types/answer-record';

const practiceRouter = useRouter();
const practice = (item: Pick<AnswerRecordSummary, 'eventId' | 'questionId'>) => {
 practiceRouter.push({path:'/student/practice',query:{eventId:item.eventId ?? undefined,questionId:item.questionId}});
};
const auth = useAuthStore();

const loading = ref(false);
const loadingMore = ref(false);
const records = ref<AnswerRecordSummary[]>([]);
const details = ref<Record<string, AnswerRecordResponse>>({});
const detailLoading = ref<Record<string, boolean>>({});
const recommendations = ref<Record<string, PracticeCandidate[]>>({});
const recommendLoading = ref<Record<string, boolean>>({});
const recommendErrors = ref<Record<string, string>>({});
const page = ref(1);
const pages = ref(0);
const total = ref(0);
const pageSize = 20;
const searchText = ref('');
const expandedId = ref<string | null>(null);
const detailOf = (item: AnswerRecordSummary) => details.value[item.recordId];
const loadRecommendations = async (item: AnswerRecordSummary) => {
  const codes = detailOf(item)?.knowledgePointCodes ?? [];
  if (!codes.length || recommendLoading.value[item.recordId]) return;
  recommendLoading.value[item.recordId] = true;
  delete recommendErrors.value[item.recordId];
  try { recommendations.value[item.recordId] = await getPracticeRecommendationsApi(codes, item.eventId); }
  catch { recommendErrors.value[item.recordId] = '推荐加载失败，请稍后重试。'; }
  finally { recommendLoading.value[item.recordId] = false; }
};
let searchTimer: ReturnType<typeof setTimeout> | undefined;
const toggleExpand = async (item: AnswerRecordSummary) => {
  if (expandedId.value === item.recordId) {
    expandedId.value = null;
    return;
  }
  expandedId.value = item.recordId;
  if (details.value[item.recordId] || detailLoading.value[item.recordId]) return;
  detailLoading.value[item.recordId] = true;
  try {
    details.value[item.recordId] = await getAnswerRecordDetailApi(item.recordId);
  } catch {
    // Keep the row usable; the retry follows when it is collapsed and opened again.
    delete details.value[item.recordId];
  } finally {
    detailLoading.value[item.recordId] = false;
  }
};



const formatTime = (time: string) =>
  new Date(time).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

const loadPage = async (targetPage = 1, append = false) => {
  if (targetPage === 1) loading.value = true;
  else loadingMore.value = true;
  try {
    const res = await getPracticeReviewPageApi({ page: targetPage, size: pageSize, keyword: searchText.value.trim() });
    const incoming = (res.records ?? []).filter(row => !records.value.some(existing => existing.recordId === row.recordId));
    records.value = append ? [...records.value, ...incoming] : incoming;
    page.value = res.currentPage;
    pages.value = res.pages;
    total.value = res.total;
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

watch(searchText, () => {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => void loadPage(1, false), 300);
});

onMounted(() => void loadPage(1, false));

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer);
});
</script>

<style scoped>
.wb-page {
  display: grid;
  gap: 18px;
}

.wb-recommendation { display:block; width:100%; margin-top:8px; padding:12px; text-align:left; border:1px solid rgba(151,172,249,.2); border-radius:10px; background:rgba(30,41,59,.4); color:#e2e8f0; cursor:pointer; }
.wb-recommendation span { display:block; margin-top:6px; color:#94a3b8; font-size:12px; }

.wb-hero,
.wb-card,
.wb-empty,
.wb-loading {
  border: 1px solid rgba(151, 172, 249, 0.14);
  border-radius: 22px;
  background: linear-gradient(160deg, rgba(12, 18, 36, 0.9), rgba(18, 14, 42, 0.84));
  box-shadow: 0 24px 60px rgba(4, 8, 20, 0.32);
}

.wb-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: end;
  padding: 24px 28px;
}

.wb-kicker {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(90, 243, 211, 0.2);
  background: rgba(90, 243, 211, 0.08);
  font-size: var(--font-size-xs);
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(183, 248, 235, 0.92);
}

.wb-title {
  margin: 14px 0 8px;
  font-size: clamp(28px, 4vw, 40px);
}

.wb-summary {
  color: var(--text-muted);
  line-height: 1.7;
}

.wb-tools {
  display: grid;
  gap: 10px;
  justify-items: end;
}

.wb-search {
  min-width: 280px;
}

.wb-count {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.wb-loading,
.wb-empty {
  padding: 40px 28px;
  text-align: center;
  color: var(--text-muted);
}

.wb-empty-icon {
  font-size: 40px;
  margin-bottom: 10px;
}

.wb-list {
  display: grid;
  gap: 14px;
}

.wb-card {
  overflow: hidden;
}

.wb-card-head {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto auto auto;
  gap: 12px;
  align-items: center;
  padding: 16px 18px;
  cursor: pointer;
}

.wb-idx {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(90, 243, 211, 0.08);
  color: var(--brand);
  font-weight: 800;
}

.wb-card-preview {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.wb-card-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.wb-chip {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: var(--font-size-sm);
}

.wb-chip.wrong {
  background: #fef2f2;
  color: #b91c1c;
}

.wb-chip.correct {
  background: #ecfdf5;
  color: #15803d;
}

.wb-time,
.wb-expand-icon {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.wb-card-detail {
  display: grid;
  gap: 16px;
  padding: 0 18px 18px;
}

.wb-detail-block {
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(151, 172, 249, 0.08);
}

.wb-section-title {
  margin: 0 0 12px;
  font-size: 16px;
}

.wb-practice-btn {
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid rgba(151, 172, 249, 0.2);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text);
  cursor: pointer;
}

.wb-detail-q {
  line-height: 1.75;
}

.wb-question-image-wrap {
  margin-top: 14px;
  padding: 12px;
  border-radius: 16px;
  border: 1px solid rgba(151, 172, 249, 0.14);
  background: rgba(255, 255, 255, 0.02);
}

.wb-question-image {
  display: block;
  max-width: 100%;
  max-height: 420px;
  margin: 0 auto;
  border-radius: 12px;
  object-fit: contain;
  box-shadow: 0 16px 34px rgba(4, 8, 20, 0.28);
}

.wb-detail-opts {
  display: grid;
  gap: 12px;
}

.wb-opt {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.02);
}

.wb-opt-key {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(151, 172, 249, 0.3);
}

.wb-opt-correct {
  border: 1px solid rgba(68, 232, 153, 0.3);
}

.wb-opt-wrong {
  border: 1px solid rgba(255, 107, 145, 0.3);
}

.wb-analysis {
  display: grid;
  gap: 8px;
}

.wb-analysis-label {
  font-size: 12px;
  color: var(--text-muted);
}

@media (max-width: 980px) {
  .wb-card-head {
    grid-template-columns: 40px 1fr;
  }
}

@media (max-width: 760px) {
  .wb-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .wb-tools {
    justify-items: stretch;
    width: 100%;
  }

  .wb-search {
    min-width: 0;
    width: 100%;
  }
}
</style>
