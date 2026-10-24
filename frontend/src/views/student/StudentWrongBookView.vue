<template>
  <AppShell hide-heading subtitle="我的错题本">
    <div class="sw-page">
      <header class="sw-header">
        <div>
          <h1>我的错题本</h1>
          <p>回看错因，补练原题，再用相似题巩固。</p>
        </div>
        <RouterLink to="/student/ai" class="sw-button"
          ><AppIcon name="spark" />问学习助手</RouterLink
        >
      </header>
      <section class="sw-surface">
        <div class="sw-section-head">
          <div>
            <h2>复习清单</h2>
            <p>按最近一次作答更新状态，补练不改变原作业成绩。</p>
          </div>
          <button class="sw-link" :disabled="loading" @click="reload">刷新</button>
        </div>
        <div class="sw-tabs" role="tablist" aria-label="复习状态">
          <button
            v-for="(tab, index) in tabs"
            :id="`review-${tab.id}`"
            :key="tab.id"
            role="tab"
            :aria-selected="filter === tab.id"
            :tabindex="filter === tab.id ? 0 : -1"
            aria-controls="wrong-review-panel"
            @click="filter = tab.id"
            @keydown="navigateTabs($event, index)"
          >
            {{ tab.label }}
          </button>
        </div>
        <div class="sw-toolbar">
          <input
            v-model="searchText"
            class="sw-input sw-search"
            aria-label="搜索错题"
            placeholder="搜索题目内容…"
          /><span v-if="!loading && !loadError" class="sw-muted"
            >已显示 {{ records.length }} / {{ total }} 道</span
          >
        </div>
        <div id="wrong-review-panel" role="tabpanel" :aria-labelledby="`review-${filter}`">
          <div v-if="loading" class="sw-empty" role="status">正在整理复习清单…</div>
          <p v-else-if="loadError && !records.length" class="sw-error wb-load-error" role="alert">
            {{ loadError }} <button class="sw-link" @click="reload">重新加载</button>
          </p>
          <div v-else-if="!records.length" class="sw-empty">
            <strong>{{
              searchText
                ? '没有匹配的题目'
                : filter === 'mastered'
                  ? '暂时没有通过补练的题目'
                  : filter === 'pending'
                    ? '暂无待复习的错题'
                    : '还没有错题记录'
            }}</strong
            >{{
              searchText
                ? '试试其他关键词，或切换复习状态。'
                : filter === 'mastered'
                  ? '完成原题补练后，最近答对的题目会保留在这里。'
                  : '可以继续完成作业，也可以向学习助手提问。'
            }}
            <div class="sw-actions">
              <button v-if="searchText" class="sw-button" @click="searchText = ''">清除搜索</button
              ><RouterLink v-else class="sw-button" to="/student/task">查看我的作业</RouterLink>
            </div>
          </div>
          <div v-else class="wb-review-list">
            <article
              v-for="(item, index) in records"
              :key="item.recordId"
              class="wb-review-row"
              :class="{ expanded: expandedId === item.recordId }"
            >
              <div class="wb-review-heading">
                <button
                  type="button"
                  class="wb-expand-trigger"
                  :aria-expanded="expandedId === item.recordId"
                  :aria-controls="`wrong-detail-${item.recordId}`"
                  @click="toggleExpand(item)"
                >
                  <span class="wb-number">{{ String(index + 1).padStart(2, '0') }}</span
                  ><span class="wb-preview"
                    ><span class="wb-preview-text"
                      ><LatexText :content="item.questionPreview" /></span
                    ><span class="wb-preview-meta"
                      ><span>最近作答 {{ formatTime(item.answeredAt) }}</span
                      ><span>最近选择 {{ item.userAnswer }}</span></span
                    ></span
                  ><span class="sw-badge" :class="{ amber: !item.correct }">{{
                    item.correct ? '已通过补练' : '待复习'
                  }}</span
                  ><span class="wb-chevron" aria-hidden="true">{{
                    expandedId === item.recordId ? '−' : '＋'
                  }}</span>
                </button>
              </div>
              <div
                v-if="expandedId === item.recordId"
                :id="`wrong-detail-${item.recordId}`"
                class="wb-review-detail"
              >
                <div class="wb-review-actions">
                  <div>
                    <strong>{{
                      item.correct ? '这道题最近一次已答对' : '先回看这道题，再试着独立解答'
                    }}</strong>
                    <p>原题补练用于检验是否理解，推荐练习用于巩固同类知识。</p>
                  </div>
                  <button class="sw-button primary" @click="practice(item)">
                    {{ item.correct ? '再次补练原题' : '补练这道题' }}<AppIcon name="arrow" />
                  </button>
                </div>
                <p v-if="detailLoading[item.recordId]" class="sw-empty" role="status">
                  正在读取完整题目…
                </p>
                <p v-else-if="detailErrors[item.recordId]" class="sw-error" role="alert">
                  {{ detailErrors[item.recordId] }}
                  <button class="sw-link" @click="loadDetail(item)">重新加载题目</button>
                </p>
                <template v-else-if="detailOf(item)"
                  ><section class="wb-original-question">
                    <h3>题目与答案</h3>
                    <div class="sw-formula">
                      <LatexText :content="detailOf(item)!.questionContent" />
                    </div>
                    <img
                      v-if="detailOf(item)?.imageBase64"
                      :src="detailOf(item)!.imageBase64!"
                      alt="题目配图"
                      class="sw-question-image"
                    />
                    <div class="sw-result-options">
                      <div
                        v-for="(text, key) in detailOf(item)!.questionOptions"
                        :key="key"
                        class="sw-result-option"
                        :class="{
                          correct: key === detailOf(item)!.correctAnswer,
                          wrong: key === item.userAnswer && key !== detailOf(item)!.correctAnswer,
                        }"
                      >
                        <strong>{{ key }}</strong
                        ><LatexText :content="text" />
                      </div>
                    </div>
                    <p class="sw-feedback">
                      最近答案 {{ item.userAnswer }} · 正确答案 {{ detailOf(item)!.correctAnswer }}
                    </p>
                    <details v-if="detailOf(item)!.questionAnalysis" class="wb-explanation">
                      <summary>查看解题解析</summary>
                      <div class="sw-explanation">
                        <LatexText :content="detailOf(item)!.questionAnalysis!" />
                      </div>
                    </details>
                  </section>
                  <section
                    v-if="detailOf(item)?.knowledgePointCodes?.length"
                    class="wb-similar-section"
                  >
                    <div class="wb-similar-heading">
                      <div>
                        <h3>同知识点巩固</h3>
                        <p>原题弄懂后，再用另一道题检验思路。</p>
                      </div>
                      <button
                        class="sw-button"
                        :disabled="recommendLoading[item.recordId]"
                        @click="loadRecommendations(item)"
                      >
                        {{
                          recommendLoading[item.recordId]
                            ? '正在查找…'
                            : recommendations[item.recordId]
                              ? '换一批练习'
                              : '查找推荐练习'
                        }}
                      </button>
                    </div>
                    <p v-if="recommendErrors[item.recordId]" class="sw-error" role="alert">
                      {{ recommendErrors[item.recordId] }}
                    </p>
                    <p v-else-if="recommendations[item.recordId]?.length === 0" class="sw-feedback">
                      暂时没有可用的同知识点题目，可以先补练原题。
                    </p>
                    <button
                      v-for="candidate in recommendations[item.recordId] ?? []"
                      :key="candidate.questionId"
                      class="wb-recommendation"
                      @click="practice({ eventId: null, questionId: candidate.questionId })"
                    >
                      <span class="wb-candidate-content"
                        ><LatexText :content="candidate.content" /></span
                      ><span class="wb-candidate-action"
                        >难度 {{ candidate.difficulty ?? '未标注' }} · 开始练习 →</span
                      >
                    </button>
                  </section>
                </template>
              </div>
            </article>
          </div>
        </div>
        <footer v-if="records.length && !loading" class="sw-pagination">
          <span>已显示 {{ records.length }} / {{ total }} 道</span>
          <p v-if="loadError" class="sw-error" role="alert">{{ loadError }}</p>
          <button
            v-if="page < pages"
            class="sw-button"
            :disabled="loadingMore"
            @click="loadPage(page + 1, true)"
          >
            {{ loadingMore ? '正在加载…' : loadError ? '重试加载更多' : '加载更多' }}</button
          ><span v-else class="sw-muted">已显示全部</span>
        </footer>
      </section>
    </div>
  </AppShell>
</template>
<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import '@/assets/student-workspace.css';
import AppShell from '@/layouts/AppShell.vue';
import AppIcon from '@/components/AppIcon.vue';
import LatexText from '@/components/LatexText.vue';
import { getAnswerRecordDetailApi, getPracticeReviewPageApi } from '@/api/answer-record';
import { getPracticeRecommendationsApi, type PracticeCandidate } from '@/api/question';
import type { AnswerRecordResponse, AnswerRecordSummary } from '@/types/answer-record';
const router = useRouter(),
  route = useRoute();
const practice = (item: Pick<AnswerRecordSummary, 'eventId' | 'questionId'>) =>
  router.push({
    path: '/student/practice',
    query: { eventId: item.eventId ?? undefined, questionId: item.questionId },
  });
type Filter = 'all' | 'pending' | 'mastered';
const tabs: Array<{ id: Filter; label: string }> = [
  { id: 'all', label: '全部错题' },
  { id: 'pending', label: '待复习' },
  { id: 'mastered', label: '已通过补练' },
];
const filter = ref<Filter>(
    route.query.status === 'pending'
      ? 'pending'
      : route.query.status === 'mastered'
        ? 'mastered'
        : 'all',
  ),
  searchText = ref(''),
  loading = ref(true),
  loadingMore = ref(false),
  loadError = ref(''),
  records = ref<AnswerRecordSummary[]>([]),
  page = ref(1),
  pages = ref(0),
  total = ref(0),
  expandedId = ref<string | null>(null);
const details = ref<Record<string, AnswerRecordResponse>>({}),
  detailLoading = ref<Record<string, boolean>>({}),
  detailErrors = ref<Record<string, string>>({}),
  recommendations = ref<Record<string, PracticeCandidate[]>>({}),
  recommendLoading = ref<Record<string, boolean>>({}),
  recommendErrors = ref<Record<string, string>>({});
const detailOf = (item: AnswerRecordSummary) => details.value[item.recordId];
let alive = true,
  sequence = 0,
  timer: ReturnType<typeof setTimeout> | undefined;
async function loadDetail(item: AnswerRecordSummary) {
  if (detailLoading.value[item.recordId]) return;
  detailLoading.value[item.recordId] = true;
  delete detailErrors.value[item.recordId];
  try {
    const data = await getAnswerRecordDetailApi(item.recordId);
    if (alive) details.value[item.recordId] = data;
  } catch {
    if (alive) detailErrors.value[item.recordId] = '完整题目加载失败，请重试。';
  } finally {
    if (alive) detailLoading.value[item.recordId] = false;
  }
}
function toggleExpand(item: AnswerRecordSummary) {
  if (expandedId.value === item.recordId) {
    expandedId.value = null;
    return;
  }
  expandedId.value = item.recordId;
  if (!details.value[item.recordId]) void loadDetail(item);
}
async function loadRecommendations(item: AnswerRecordSummary) {
  const codes = detailOf(item)?.knowledgePointCodes ?? [];
  if (!codes.length || recommendLoading.value[item.recordId]) return;
  recommendLoading.value[item.recordId] = true;
  delete recommendErrors.value[item.recordId];
  try {
    const data = await getPracticeRecommendationsApi(codes, item.eventId);
    if (alive)
      recommendations.value[item.recordId] = data.filter(
        (candidate) => candidate.questionId !== item.questionId,
      );
  } catch {
    if (alive) recommendErrors.value[item.recordId] = '推荐练习暂时无法读取，请点击按钮重试。';
  } finally {
    if (alive) recommendLoading.value[item.recordId] = false;
  }
}
async function loadPage(targetPage = 1, append = false) {
  if (append && (loading.value || loadingMore.value)) return;
  const owner = ++sequence;
  loadError.value = '';
  if (append) loadingMore.value = true;
  else {
    loading.value = true;
    loadingMore.value = false;
  }
  try {
    const result = await getPracticeReviewPageApi({
      page: targetPage,
      size: 20,
      keyword: searchText.value.trim(),
      mastered: filter.value === 'all' ? undefined : filter.value === 'mastered',
    });
    if (!alive || owner !== sequence) return;
    const existing = new Set(append ? records.value.map((row) => row.recordId) : []);
    const incoming = (result.records ?? []).filter((row) => !existing.has(row.recordId));
    records.value = append ? [...records.value, ...incoming] : incoming;
    page.value = result.currentPage;
    pages.value = result.pages;
    total.value = result.total;
    if (typeof route.query.recordId === 'string') {
      const requested = records.value.find((row) => row.recordId === route.query.recordId);
      if (requested && !expandedId.value) {
        expandedId.value = requested.recordId;
        if (!details.value[requested.recordId]) void loadDetail(requested);
      }
    }
  } catch {
    if (alive && owner === sequence) loadError.value = '复习清单加载失败，请重试。';
  } finally {
    if (alive && owner === sequence) {
      loading.value = false;
      loadingMore.value = false;
    }
  }
}
function reload() {
  clearTimeout(timer);
  expandedId.value = null;
  records.value = [];
  void loadPage();
}
watch([searchText, filter], () => {
  ++sequence;
  clearTimeout(timer);
  expandedId.value = null;
  records.value = [];
  loading.value = true;
  loadingMore.value = false;
  loadError.value = '';
  timer = setTimeout(() => void loadPage(), 250);
});
const formatTime = (date: string) =>
  new Date(date).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
async function navigateTabs(event: KeyboardEvent, index: number) {
  let next: number;
  if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
  else if (event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length;
  else if (event.key === 'Home') next = 0;
  else if (event.key === 'End') next = tabs.length - 1;
  else return;
  event.preventDefault();
  filter.value = tabs[next]!.id;
  await nextTick();
  document.getElementById(`review-${filter.value}`)?.focus();
}
watch(
  () => route.query.status,
  (value) => {
    filter.value = value === 'pending' ? 'pending' : value === 'mastered' ? 'mastered' : 'all';
  },
);
onMounted(() => void loadPage());
onBeforeUnmount(() => {
  alive = false;
  ++sequence;
  clearTimeout(timer);
});
</script>
<style scoped>
.wb-load-error {
  margin: 0 24px 24px;
}
.wb-review-list {
  padding: 0 24px;
}
.wb-review-row {
  border-top: 1px solid #e8f0ea;
}
.wb-expand-trigger {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 24px 0;
  text-align: left;
  background: none;
  border: 0;
  font: inherit;
  cursor: pointer;
  color: #4d7057;
}
.wb-number {
  font-size: 12px;
  letter-spacing: 0.02em;
  color: #9cb0a2;
  width: 24px;
  flex-shrink: 0;
}
.wb-preview {
  flex: 1;
  min-width: 0;
}
.wb-preview-text {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.wb-preview-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 11px;
  color: #97aa9c;
  margin-top: 10px;
}
.wb-chevron {
  font-size: 17px;
  color: #9bb1a2;
}
.wb-review-detail {
  padding: 0 0 28px 40px;
}
.wb-review-actions {
  display: flex;
  gap: 20px;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  background: #f6faf7;
  border: 1px solid #e6eee8;
  border-radius: 10px;
  margin-bottom: 25px;
}
.wb-review-actions strong {
  font-weight: 500;
  font-size: 13px;
  color: #5b8064;
}
.wb-review-actions p {
  font-size: 11px;
  line-height: 1.8;
  color: #91a394;
  margin: 7px 0 0;
}
.wb-review-actions .sw-button {
  font-size: 12px;
}
.wb-original-question h3,
.wb-similar-heading h3 {
  font-size: 14px;
  font-weight: 500;
  color: #57735e;
  margin: 0 0 16px;
}
.wb-original-question .sw-formula {
  font-size: 14px;
}
.wb-explanation {
  margin-top: 22px;
  font-size: 12px;
  color: #6c9277;
}
.wb-explanation > summary {
  cursor: pointer;
}
.wb-explanation .sw-explanation {
  margin-top: 12px;
}
.wb-similar-section {
  margin-top: 28px;
  padding-top: 24px;
  border-top: 1px solid #e9f0eb;
}
.wb-similar-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.wb-similar-heading h3 {
  margin-bottom: 7px;
}
.wb-similar-heading p {
  font-size: 11px;
  color: #94a798;
  line-height: 1.8;
  margin: 0;
}
.wb-similar-heading > .sw-button {
  font-size: 12px;
}
.wb-recommendation {
  display: flex;
  align-items: center;
  gap: 20px;
  width: 100%;
  margin-top: 14px;
  padding: 16px;
  text-align: left;
  border: 1px solid #e2ece5;
  border-radius: 9px;
  background: white;
  color: #63816c;
  cursor: pointer;
  font: inherit;
}
.wb-candidate-content {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.8;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.wb-candidate-action {
  font-size: 11px;
  color: #789b82;
  flex-shrink: 0;
}
@media (max-width: 640px) {
  .wb-review-list {
    padding-inline: 16px;
  }
  .wb-expand-trigger {
    gap: 10px;
    flex-wrap: wrap;
    padding: 20px 0;
  }
  .wb-number {
    width: 20px;
  }
  .wb-preview-text {
    font-size: 13px;
  }
  .wb-expand-trigger > .sw-badge {
    margin-left: 30px;
  }
  .wb-chevron {
    margin-left: auto;
  }
  .wb-review-detail {
    padding-left: 0;
  }
  .wb-review-actions {
    align-items: stretch;
    flex-direction: column;
    gap: 14px;
    padding: 16px;
  }
  .wb-review-actions .sw-button {
    align-self: flex-start;
  }
  .wb-similar-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }
  .wb-recommendation {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .wb-preview-meta {
    font-size: 10px;
    gap: 5px 12px;
  }
}
</style>
