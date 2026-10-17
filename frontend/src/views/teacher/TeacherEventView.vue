<template>
  <AppShell :subtitle="`实验任务 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="event-page teacher-font">
      <section class="event-hero">
        <div>
          <span class="event-kicker">Experiment task workspace</span>
          <h1 class="event-title">实验任务</h1>
          <p class="event-summary">在一个工作台里完成选题、排题、发布和查看实验学习报告。</p>
        </div>
        <div class="event-status-card" :class="current ? 'live' : 'idle'">
          <span class="event-status-label">当前状态</span>
          <strong>{{ current ? '已发布' : '未发布' }}</strong>
          <span>{{ current ? current.eventTitle : '暂无实验任务' }}</span>
          <div class="event-status-actions">
            <button
              v-if="current"
              class="status-report-btn"
              @click="openEventReport"
            >{{ eventReportButtonText }}</button>
            <button v-if="current" class="status-delete-btn" @click="deleteTask">归档任务</button>
          </div>
        </div>
      </section>

      <details class="event-report-panel">
        <summary>已归档任务（{{ historyTotal }}）</summary>
        <p>归档后保留答题、对话和学习报告。</p>
        <p v-for="event in archivedEvents" :key="event.id">#{{ event.id }} · {{ event.eventTitle }} · {{ event.eventTimestamp }}</p>
        <button class="status-report-btn" :disabled="historyPage <= 1" @click="historyPage--; loadHistory()">上一页</button>
        <button class="status-report-btn" :disabled="historyPage * 10 >= historyTotal" @click="historyPage++; loadHistory()">下一页</button>
      </details>

      <section v-if="shouldShowEventReportPanel" class="event-report-panel">
        <div class="report-panel-head">
          <div>
            <div class="report-panel-title">实验学习报告</div>
            <div class="report-panel-sub">当前实验任务：{{ reportEventTitle }}</div>
          </div>
          <div class="report-panel-actions">
            <button
              v-if="eventReportState.loading"
              class="stop-inline-btn"
              @click="stopEventReportStream"
            >停止生成</button>
            <button class="close-inline-btn" @click="closeEventReport">收起</button>
          </div>
        </div>
        <StreamOutput
          :loading="eventReportState.loading"
          :progress="eventReportState.progress"
          :content="eventReportState.content"
          :reasoning="eventReportState.reasoning"
          :token-usage="eventReportState.tokenUsage"
          :error="eventReportState.error"
        />
      </section>

      <div class="main-layout">
        <section class="left-panel">
          <div class="exp-tabs">
            <button
              v-for="series in experimentSeries"
              :key="series.key"
              class="exp-tab"
              :class="{ active: editingSeries === series.key, published: current?.eventTitle === series.key }"
              @click="editingSeries = series.key"
            >
              <span class="exp-tab-name">{{ series.label }}</span>
              <span class="exp-tab-badge">{{ seriesConfig[series.key]?.length ?? 0 }}</span>
            </button>
          </div>

          <div class="left-content">
            <div class="series-summary">
              <div>
                <div class="series-summary-label">当前系列</div>
                <div class="series-summary-title">{{ currentSeriesMeta.label }}</div>
              </div>
              <span class="series-chip">{{ currentSeriesMeta.experiments.length }} 个实验</span>
            </div>

            <div class="series-exp-list">
              <div v-for="exp in currentSeriesMeta.experiments" :key="exp.id" class="series-exp-item">
                <span class="series-exp-index">{{ exp.order }}</span>
                <div class="series-exp-body">
                  <div class="series-exp-name">{{ exp.title }}</div>
                  <div class="series-exp-path">{{ exp.folder }}</div>
                </div>
              </div>
            </div>

            <div class="left-head">
              <span class="left-head-title">已选题目</span>
              <span class="left-head-count">{{ currentSeriesIds.length }} 题</span>
            </div>

            <div v-if="currentSeriesIds.length === 0" class="empty-hint">
              <div class="empty-icon">📋</div>
              <p>右侧题库中选择题目加入当前系列。</p>
            </div>

            <div v-else class="selected-list">
              <div
                v-for="(id, idx) in currentSeriesIds" :key="id"
                class="sel-item"
                :class="{ expanded: expandedId === id }"
              >
                <div class="sel-item-main" @click="toggleExpand(id)">
                  <span class="sel-rank">{{ idx + 1 }}</span>
                  <span class="sel-badge">#{{ id }}</span>
                  <span class="sel-preview"><LatexText :content="questionMap[id]?.content?.slice(0, 40) ?? ''" />…</span>
                  <div class="sel-actions" @click.stop>
                    <button class="icon-btn" :disabled="idx === 0" @click="moveUp(idx)">↑</button>
                    <button class="icon-btn" :disabled="idx === currentSeriesIds.length - 1" @click="moveDown(idx)">↓</button>
                    <button class="icon-btn danger" @click="removeFromSeries(id)">✕</button>
                  </div>
                </div>
                <div v-if="expandedId === id" class="sel-detail">
                  <div class="sel-detail-q"><LatexText :content="questionMap[id]?.content ?? ''" /></div>
                  <div v-if="questionMap[id]?.imageBase64" class="sel-detail-image-wrap">
                    <img :src="questionMap[id]?.imageBase64 ?? ''" alt="题目图片预览" class="sel-detail-image" />
                  </div>
                  <div v-if="previewOptions(questionMap[id]?.options).length" class="sel-detail-options">
                    <div
                      v-for="option in previewOptions(questionMap[id]?.options)"
                      :key="`${id}-${option.key}`"
                      class="sel-detail-option"
                    >
                      <span class="sel-detail-option-key">{{ option.key }}</span>
                      <span class="sel-detail-option-text"><LatexText :content="option.text" /></span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="publish-zone">
            <button
              class="publish-btn"
              :disabled="!currentSeriesIds.length"
              :class="{ active: currentSeriesIds.length > 0 }"
              @click="publishTask"
            >
              发布 {{ currentSeriesMeta.label }}
            </button>
          </div>
        </section>

        <section class="right-panel">
          <div class="right-head">
            <span class="right-head-title">题库</span>
            <span class="right-head-count">{{ allQuestions.length }} 题</span>
            <div class="search-box">
              <input v-model="searchText" placeholder="搜索题目 ID 或内容…" class="search-input" />
            </div>
          </div>

          <div v-if="loadingQ" class="loading-hint">加载题目中…</div>
          <div v-else-if="allQuestions.length === 0" class="empty-hint">
            <div class="empty-icon">📭</div>
            <p>题库暂无题目</p>
          </div>
          <div v-else class="bank-list">
            <div
              v-for="q in filteredQuestions" :key="q.id"
              class="bank-item"
              :class="{ added: currentSeriesIds.includes(q.id), expanded: expandedId === q.id }"
            >
              <div class="bank-item-main" @click="toggleExpand(q.id)">
                <span class="bank-badge">#{{ q.id }}</span>
                <span class="bank-preview"><LatexText :content="q.content?.slice(0, 60) ?? ''" /></span>
                <button
                  class="add-btn"
                  :class="{ added: currentSeriesIds.includes(q.id) }"
                  :disabled="currentSeriesIds.includes(q.id)"
                  @click.stop="addToSeries(q.id)"
                >
                  {{ currentSeriesIds.includes(q.id) ? '已加入' : '加入' }}
                </button>
              </div>
              <div v-if="expandedId === q.id" class="bank-detail">
                <div class="bank-detail-q"><LatexText :content="q.content" /></div>
                <div v-if="q.imageBase64" class="bank-detail-image-wrap">
                  <img :src="q.imageBase64" alt="题目图片预览" class="bank-detail-image" />
                </div>
                <div v-if="previewOptions(q.options).length" class="bank-detail-options">
                  <div
                    v-for="option in previewOptions(q.options)"
                    :key="`${q.id}-${option.key}`"
                    class="bank-detail-option"
                  >
                    <span class="bank-detail-option-key">{{ option.key }}</span>
                    <span class="bank-detail-option-text"><LatexText :content="option.text" /></span>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="filteredQuestions.length === 0" class="empty-hint">
              <p class="muted">无匹配题目</p>
            </div>
          </div>
        </section>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import LatexText from '@/components/LatexText.vue';
import StreamOutput from '@/components/StreamOutput.vue';
import { teacherNavLinks } from '@/constants/navigation';
import { useAuthStore } from '@/stores/auth';
import { useTeacherEventReportStore } from '@/stores/teacherEventReport';
import { useToast } from '@/composables/useToast';
import { useAIStreamState } from '@/composables/useAIStreamState';
import {
  createTeacherEventApi,
  deleteTeacherEventApi,
  getTeacherEventHistoryApi,
  getTeacherEventApi,
} from '@/api/teacher-event';
import { getAllQuestionsApi } from '@/api/question';
import { streamTeacherReport } from '@/api/ai';
import type { QuestionResponse } from '@/types/question';
import type { TeacherEventResponse } from '@/types/teacher-event';

const auth = useAuthStore();
const reportStore = useTeacherEventReportStore();
const { show } = useToast();
const { state: eventReportState, reset: resetEventReport } = useAIStreamState();

const links = teacherNavLinks;

const experimentSeries = [
  {
    key: '原子物理系列实验',
    label: '原子物理系列实验',
    experiments: [
      { id: 'alpha', order: '01', title: '卢瑟福 α 粒子散射实验', folder: 'Alpha particle scattering experiment' },
      { id: 'bohr', order: '02', title: '波尔理论实验', folder: 'Bohr theory' },
      { id: 'cloud', order: '03', title: '电子云模型实验', folder: 'Electron cloud model' },
      { id: 'photoelectric', order: '04', title: '光电效应实验', folder: 'Photoelectric' },
    ],
  },
  {
    key: '万有引力与宇宙航天系列实验',
    label: '万有引力与宇宙航天系列实验',
    experiments: [
      { id: 'kepler', order: '01', title: '开普勒三定律实验', folder: 'Kepler' },
      { id: 'cosmic', order: '02', title: '三大宇宙速度实验', folder: 'Cosmis_Velocity' },
      { id: 'hohmann', order: '03', title: '卫星变轨实验', folder: 'Hohmann_Transfer' },
      { id: 'lightspeed', order: '04', title: '光速视界实验', folder: 'LightspeedVista' },
    ],
  },
] as const;

type SeriesKey = typeof experimentSeries[number]['key'];

const seriesOptions = experimentSeries.map(item => item.key);
const defaultSeries = seriesOptions[0];
const seriesConfig = ref<Record<SeriesKey, number[]>>({
  '原子物理系列实验': [],
  '万有引力与宇宙航天系列实验': [],
});
const editingSeries = ref<SeriesKey>(defaultSeries);
const expandedId = ref<number | null>(null);
const searchText = ref('');
const showEventReport = ref(false);
const activeReportSessionType = ref('');
const activeAbortController = ref<AbortController | null>(null);

const toggleExpand = (id: number) => {
  expandedId.value = expandedId.value === id ? null : id;
};

const allQuestions = ref<QuestionResponse[]>([]);
const loadingQ = ref(false);
const current = ref<TeacherEventResponse | null>(null);

const currentSeriesIds = computed(() => seriesConfig.value[editingSeries.value] ?? []);
const currentSeriesMeta = computed(() =>
  experimentSeries.find(item => item.key === editingSeries.value) ?? experimentSeries[0]
);

const questionMap = computed<Record<number, QuestionResponse>>(() => {
  const m: Record<number, QuestionResponse> = {};
  for (const q of allQuestions.value) m[q.id] = q;
  return m;
});

const previewOptions = (options?: QuestionResponse['options']) => {
  if (!options) return [] as Array<{ key: string; text: string }>;
  return Object.entries(options).map(([key, text]) => ({ key, text: text ?? '' }));
};

const filteredQuestions = computed(() => {
  const s = searchText.value.trim().toLowerCase();
  if (!s) return allQuestions.value;
  return allQuestions.value.filter(q =>
    String(q.id).includes(s) || q.content?.toLowerCase().includes(s)
  );
});

const shouldShowEventReportPanel = computed(() =>
  showEventReport.value
);

const reportEventTitle = computed(() =>
  reportStore.cachedReport?.currentEventTitle || current.value?.eventTitle || '未发布'
);

const eventReportButtonText = computed(() => {
  if (eventReportState.value.loading) return showEventReport.value ? '生成中…' : '查看生成中的实验学习报告';
  if (eventReportState.value.content || eventReportState.value.reasoning || eventReportState.value.error) return '查看实验学习报告';
  return '生成实验学习报告';
});

const addToSeries = (id: number) => {
  const list = seriesConfig.value[editingSeries.value];
  if (!list.includes(id)) list.push(id);
};

const removeFromSeries = (id: number) => {
  seriesConfig.value[editingSeries.value] = seriesConfig.value[editingSeries.value].filter(x => x !== id);
};

const moveUp = (idx: number) => {
  const list = seriesConfig.value[editingSeries.value];
  if (idx <= 0) return;
  [list[idx - 1], list[idx]] = [list[idx], list[idx - 1]];
};

const moveDown = (idx: number) => {
  const list = seriesConfig.value[editingSeries.value];
  if (idx >= list.length - 1) return;
  [list[idx + 1], list[idx]] = [list[idx], list[idx + 1]];
};

const parseQuestionIds = (description: string | null): number[] => {
  if (!description?.startsWith('QIDS:')) return [];
  return description.slice(5).split(',').map(Number).filter(n => n > 0);
};


const normalizeEventReportError = (raw: string | undefined): string => {
  const text = raw ?? '';
  if (!text) return '实验学习报告暂时无法生成，请稍后重试。';

  const friendlyMappings = [
    { patterns: ['暂无任何学生答题数据', '暂无学生答题记录', '没有学生答题记录', '答题记录不存在'], message: '当前还没有学生答题记录哦' },
    { patterns: ['无法生成教师报告', '无法生成教师学情报告'], message: '当前还没有学生答题记录哦' },
  ];

  for (const mapping of friendlyMappings) {
    if (mapping.patterns.some(pattern => text.includes(pattern))) {
      return mapping.message;
    }
  }

  const jsonMessageMatch = text.match(/"message"\s*:\s*"([^"]+)"/);
  if (jsonMessageMatch?.[1]) {
    return normalizeEventReportError(jsonMessageMatch[1]);
  }

  return '实验学习报告暂时无法生成，请稍后重试。';
};

const isAbortError = (error: unknown) => {
  if (!error) return false;
  return (error instanceof DOMException && error.name === 'AbortError')
    || (typeof error === 'object' && (error as { name?: string }).name === 'AbortError');
};

const restoreCachedEventReport = () => {
  reportStore.hydrateFromStorage();
  const cached = reportStore.cachedReport;
  if (!cached) return;

  const normalizedCachedError = normalizeEventReportError(cached.error);
  const shouldDropStaleError = !cached.loading && !cached.content && !cached.reasoning
    && normalizedCachedError === '当前还没有学生答题记录哦';

  if (shouldDropStaleError) {
    reportStore.clearCachedReport();
    return;
  }

  eventReportState.value.loading = cached.loading;
  eventReportState.value.progress = cached.progress;
  eventReportState.value.content = cached.content;
  eventReportState.value.reasoning = cached.reasoning;
  eventReportState.value.error = normalizedCachedError;
  eventReportState.value.tokenUsage = cached.tokenUsage;
  activeReportSessionType.value = cached.currentEventTitle ? `teacher_event_report_${cached.currentEventTitle}` : '';
};

const persistEventReport = () => {
  if (!reportEventTitle.value || reportEventTitle.value === '未发布') return;
  if (!eventReportState.value.loading && !eventReportState.value.content && !eventReportState.value.reasoning && !eventReportState.value.error) return;
  reportStore.setCachedReport({
    loading: eventReportState.value.loading,
    progress: eventReportState.value.progress,
    content: eventReportState.value.content,
    reasoning: eventReportState.value.reasoning,
    error: eventReportState.value.error,
    tokenUsage: eventReportState.value.tokenUsage,
    currentEventTitle: reportEventTitle.value,
  });
};

const clearEventReport = () => {
  resetEventReport();
  activeReportSessionType.value = '';
  reportStore.clearCachedReport();
};

const startEventReportStream = async (eventTitle: string) => {
  activeReportSessionType.value = `teacher_event_report_${eventTitle}`;
  resetEventReport();
  eventReportState.value.loading = true;
  eventReportState.value.error = '';
  persistEventReport();
  const abortController = new AbortController();
  activeAbortController.value = abortController;
  try {
    await streamTeacherReport({
      signal: abortController.signal,
      sessionType: activeReportSessionType.value,
      onMessage: (payload) => {
        eventReportState.value.content += payload.content;
        persistEventReport();
      },
      onProgress: (payload) => {
        eventReportState.value.progress = payload.progress ?? eventReportState.value.progress;
        persistEventReport();
      },
      onReasoning: (payload) => {
        eventReportState.value.reasoning += payload.reasoning;
        persistEventReport();
      },
      onComplete: (payload) => {
        eventReportState.value.loading = false;
        eventReportState.value.tokenUsage = `Token: ${payload.totalTokens ?? 0}`;
        persistEventReport();
      },
      onError: (payload) => {
        eventReportState.value.loading = false;
        eventReportState.value.error = normalizeEventReportError(payload.message);
        persistEventReport();
      },
    });
  } catch (error: any) {
    if (isAbortError(error)) {
      eventReportState.value.loading = false;
      persistEventReport();
      return;
    }
    eventReportState.value.loading = false;
    eventReportState.value.error = normalizeEventReportError(error?.message);
    persistEventReport();
  } finally {
    if (activeAbortController.value === abortController) {
      activeAbortController.value = null;
    }
  }
};

const stopEventReportStream = () => {
  if (!eventReportState.value.loading) return;
  activeAbortController.value?.abort();
};

const openEventReport = async () => {
  if (!current.value) {
    show('当前没有已发布实验任务，无法生成实验学习报告', 'info');
    return;
  }

  showEventReport.value = true;
  const sessionType = `teacher_event_report_${current.value.eventTitle}`;
  const hasCachedCurrentReport = reportStore.cachedReport?.currentEventTitle === current.value.eventTitle;

  if (eventReportState.value.loading && activeReportSessionType.value === sessionType) return;
  if (hasCachedCurrentReport && (eventReportState.value.content || eventReportState.value.reasoning || eventReportState.value.error)) return;

  await startEventReportStream(current.value.eventTitle);
};

const closeEventReport = () => {
  showEventReport.value = false;
};

watch(eventReportState, () => {
  persistEventReport();
}, { deep: true });

const load = async () => {
  loadingQ.value = true;
  try {
    const [qData, eventData] = await Promise.allSettled([
      getAllQuestionsApi(),
      getTeacherEventApi(),
    ]);
    if (qData.status === 'fulfilled') {
      allQuestions.value = Array.isArray(qData.value) ? qData.value : [];
    }
    if (eventData.status === 'fulfilled' && eventData.value) {
      current.value = eventData.value;
      const ids = parseQuestionIds(eventData.value.eventDescription ?? null);
      const title = eventData.value.eventTitle;
      if ((seriesOptions as readonly string[]).includes(title) && ids.length) {
        seriesConfig.value[title as SeriesKey] = ids;
        editingSeries.value = title as SeriesKey;
      }
    }
  } finally {
    loadingQ.value = false;
  }
};

onMounted(() => {
  restoreCachedEventReport();
  load();
});

onUnmounted(() => {
  activeAbortController.value?.abort();
});

const publishTask = async () => {
  const ids = seriesConfig.value[editingSeries.value] ?? [];
  if (!ids.length) { show('请先为当前实验系列加入至少一道题目', 'info'); return; }
  const encoded = 'QIDS:' + ids.join(',');
  if (encoded.length > 100) { show('题目过多，超出字段长度限制', 'error'); return; }
  const payload = {
    eventTitle: editingSeries.value,
    eventDescription: encoded,
    eventTimestamp: new Date().toISOString().slice(0, 19),
  };
  try {
    current.value = await createTeacherEventApi(payload);
    show(`${editingSeries.value} 已发布，共 ${ids.length} 题`, 'success');
  } catch (error: any) {
    if (error?.response?.status === 409) {
      try {
        await deleteTeacherEventApi();
        current.value = await createTeacherEventApi(payload);
        await loadHistory();
        show('旧任务已归档，新任务发布成功', 'success');
      } catch (e: any) {
        show(e?.response?.data?.message ?? '替换任务失败', 'error');
      }
      return;
    }
    show(error?.response?.data?.message ?? '发布失败，请重试', 'error');
  }
};

const archivedEvents = ref<import('@/types/teacher-event').TeacherEventResponse[]>([]);
const historyPage = ref(1);
const historyTotal = ref(0);
const loadHistory = async () => {
  try {
    const data = await getTeacherEventHistoryApi(historyPage.value);
    archivedEvents.value = data.records;
    historyTotal.value = data.total;
  } catch (error: any) { show(error?.response?.data?.message ?? '读取归档任务失败', 'error'); }
};
onMounted(loadHistory);

const deleteTask = async () => {
  try {
    await deleteTeacherEventApi();
    current.value = null;
    showEventReport.value = false;
    clearEventReport();
    await loadHistory();
    show('任务已归档，学习记录已保留', 'success');
  } catch {
    show('操作失败', 'error');
  }
};
</script>

<style scoped>
.event-page {
  display: grid;
  gap: 18px;
}

.event-hero,
.event-status-card,
.left-panel,
.right-panel,
.event-report-panel {
  border: 1px solid rgba(123, 146, 255, 0.14);
  border-radius: 22px;
  background: linear-gradient(160deg, rgba(12, 18, 37, 0.96), rgba(7, 11, 24, 0.98));
  box-shadow: 0 24px 60px rgba(2, 6, 18, 0.32);
}

.event-hero {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 18px;
  padding: 24px 28px;
}

.event-kicker {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(123, 146, 255, 0.18);
  color: #95abd8;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  font-size: var(--font-size-xs);
}

.event-title {
  margin: 14px 0 8px;
  font-size: clamp(30px, 4vw, 42px);
}

.event-summary {
  color: #b7c4e8;
  line-height: 1.8;
}

.event-status-card {
  padding: 18px;
  display: grid;
  gap: 8px;
}

.event-status-card strong {
  font-size: 28px;
}

.event-status-label {
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.event-status-card.live strong {
  color: rgba(120, 220, 150, 0.95);
}

.event-status-card.idle strong {
  color: #f4fbff;
}

.event-status-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.event-report-panel {
  padding: 18px;
  display: grid;
  gap: 14px;
}

.report-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}

.report-panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.report-panel-title {
  font-size: 22px;
  font-weight: 800;
  color: #f4fbff;
}

.report-panel-sub {
  margin-top: 4px;
  color: #9fb0d6;
  font-size: 13px;
}

.close-inline-btn {
  cursor: pointer;
  border-radius: 12px;
  border: 1px solid rgba(151, 172, 249, 0.2);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
  padding: 10px 14px;
}

.stop-inline-btn {
  cursor: pointer;
  border-radius: 12px;
  border: 1px solid rgba(255, 107, 145, 0.45);
  background: rgba(255, 107, 145, 0.12);
  color: #ffd5e2;
  padding: 10px 14px;
  font-weight: 700;
}

.status-delete-btn,
.status-report-btn,
.publish-btn,
.add-btn,
.icon-btn,
.close-btn {
  cursor: pointer;
}

.status-delete-btn,
.status-report-btn,
.publish-btn,
.add-btn,
.icon-btn {
  border-radius: 12px;
  border: 1px solid rgba(151, 172, 249, 0.2);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
}

.status-delete-btn,
.status-report-btn,
.publish-btn,
.add-btn {
  padding: 10px 14px;
}

.status-delete-btn {
  border-color: rgba(255, 100, 100, 0.3);
  color: rgba(255, 140, 140, 0.95);
}

.status-report-btn,
.publish-btn.active {
  border-color: rgba(90, 243, 211, 0.45);
  background: linear-gradient(120deg, rgba(90, 243, 211, 0.88), rgba(255, 199, 122, 0.88));
  color: #061016;
  font-weight: 800;
}

.sel-detail,
.bank-detail {
  display: grid;
  gap: 12px;
}

.sel-detail-q,
.bank-detail-q {
  color: #edf3ff;
  line-height: 1.8;
}

.sel-detail-image-wrap,
.bank-detail-image-wrap {
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid rgba(151, 172, 249, 0.16);
  background: rgba(255, 255, 255, 0.03);
}

.sel-detail-image,
.bank-detail-image {
  display: block;
  width: 100%;
  max-height: 240px;
  object-fit: contain;
  background: rgba(7, 11, 24, 0.92);
}

.sel-detail-options,
.bank-detail-options {
  display: grid;
  gap: 10px;
}

.sel-detail-option,
.bank-detail-option {
  display: grid;
  grid-template-columns: 30px 1fr;
  gap: 12px;
  align-items: start;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(151, 172, 249, 0.12);
  background: rgba(255, 255, 255, 0.03);
}

.sel-detail-option-key,
.bank-detail-option-key {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(123, 146, 255, 0.16);
  color: #f4fbff;
  font-weight: 700;
}

.sel-detail-option-text,
.bank-detail-option-text {
  color: #dfe9ff;
  line-height: 1.75;
}

.main-layout {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 18px;
}

.left-panel,
.right-panel {
  overflow: hidden;
}

.exp-tabs {
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid rgba(151, 172, 249, 0.08);
  background: rgba(151, 172, 249, 0.03);
}

.exp-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  text-align: left;
}

.exp-tab.active {
  background: rgba(151, 172, 249, 0.08);
  color: #f4fbff;
}

.exp-tab.published {
  box-shadow: inset 3px 0 0 rgba(90, 243, 211, 0.8);
}

.exp-tab-name {
  flex: 1;
  font-size: 14px;
}

.exp-tab-badge,
.series-chip,
.left-head-count,
.right-head-count {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(151, 172, 249, 0.1);
  color: #9fb0db;
}

.left-content,
.right-panel {
  padding: 18px;
}

.series-summary,
.left-head,
.right-head,
.panel-head,
.report-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.series-summary {
  margin-bottom: 16px;
}

.series-summary-label,
.series-exp-path,
.loading-hint,
.muted,
.report-dialog-sub {
  color: var(--text-muted);
}

.series-summary-title,
.right-head-title,
.left-head-title,
.report-dialog-title {
  font-size: 18px;
  font-weight: 700;
}

.series-exp-list,
.selected-list,
.bank-list {
  display: grid;
  gap: 10px;
}

.series-exp-item,
.sel-item-main,
.bank-item-main {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(151, 172, 249, 0.08);
}

.series-exp-index,
.sel-rank,
.bank-badge {
  min-width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(151, 172, 249, 0.12);
  color: #a9bcf0;
  font-size: 12px;
  font-weight: 700;
}

.series-exp-body,
.sel-preview,
.bank-preview {
  min-width: 0;
  flex: 1;
}

.series-exp-name {
  font-size: 14px;
  color: #f4fbff;
}

.empty-hint {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
}

.empty-icon {
  font-size: 36px;
  margin-bottom: 10px;
}

.sel-item,
.bank-item {
  display: grid;
  gap: 8px;
}

.sel-actions {
  display: flex;
  gap: 8px;
}

.icon-btn {
  width: 34px;
  height: 34px;
}

.icon-btn.danger {
  border-color: rgba(255, 100, 100, 0.3);
  color: rgba(255, 140, 140, 0.95);
}

.sel-detail,
.bank-detail {
  padding: 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(151, 172, 249, 0.08);
}

.publish-zone {
  padding: 18px;
  border-top: 1px solid rgba(151, 172, 249, 0.08);
}

.publish-btn {
  width: 100%;
}

.search-box {
  min-width: 240px;
}

.loading-hint {
  padding: 40px;
  text-align: center;
}

.add-btn.added {
  opacity: 0.7;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 140;
  background: rgba(6, 8, 20, 0.72);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.report-dialog {
  width: min(100%, 920px);
  max-height: calc(100vh - 48px);
  display: grid;
  grid-template-rows: auto 1fr;
  overflow: hidden;
}

.report-dialog-head {
  padding: 16px 18px;
  border-bottom: 1px solid rgba(151, 172, 249, 0.1);
}

.close-btn {
  width: 34px;
  height: 34px;
}

.report-dialog-body {
  min-height: 0;
  overflow: auto;
  padding: 18px;
}

@media (max-width: 1100px) {
  .event-hero,
  .main-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .series-summary,
  .left-head,
  .right-head,
  .event-status-actions,
  .report-dialog-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .search-box {
    min-width: 0;
    width: 100%;
  }
}
</style>
