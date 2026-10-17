<template>
  <div class="db-page">
    <header class="db-header">
      <div class="db-header-inner">
        <div class="db-logo">
          <span class="db-logo-icon">⬡</span>
          <span class="db-logo-text">账户数据库</span>
          <span class="db-version">v{{ stats.version }}</span>
        </div>
        <div class="db-actions">
          <button class="btn-ghost" @click="exportJson">导出 JSON</button>
          <button class="btn-danger" @click="confirmClear = true">清空数据库</button>
        </div>
      </div>
    </header>

    <main class="db-main">
      <!-- 统计卡片 -->
      <section class="stats-row">
        <div class="stat-card">
          <span class="stat-num">{{ stats.total }}</span>
          <span class="stat-label">账户总数</span>
        </div>
        <div class="stat-card accent-blue">
          <span class="stat-num">{{ stats.students }}</span>
          <span class="stat-label">学生</span>
        </div>
        <div class="stat-card accent-amber">
          <span class="stat-num">{{ stats.teachers }}</span>
          <span class="stat-label">教师</span>
        </div>
        <div class="stat-card accent-green">
          <span class="stat-num">{{ activeCount }}</span>
          <span class="stat-label">已激活</span>
        </div>
      </section>

      <!-- 搜索与筛选 -->
      <section class="toolbar">
        <input
          v-model="search"
          class="search-input"
          placeholder="搜索用户名 / 姓名 / 班级…"
        />
        <div class="filter-tabs">
          <button
            v-for="f in filters"
            :key="f.value"
            class="filter-tab"
            :class="{ active: activeFilter === f.value }"
            @click="activeFilter = f.value"
          >
            {{ f.label }}
          </button>
        </div>
      </section>

      <!-- 账户表格 -->
      <section class="table-wrap">
        <table class="db-table" v-if="filtered.length > 0">
          <thead>
            <tr>
              <th>用户ID</th>
              <th>用户名</th>
              <th>真实姓名</th>
              <th>角色</th>
              <th>班级</th>
              <th>状态</th>
              <th>注册时间</th>
              <th>最后登录</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="acc in filtered" :key="acc.username" class="db-row">
              <td class="mono">{{ acc.userId || '—' }}</td>
              <td class="mono bold">{{ acc.username }}</td>
              <td>{{ acc.realName }}</td>
              <td>
                <span class="badge" :class="acc.userType === 'TEACHER' ? 'badge-amber' : 'badge-blue'">
                  {{ acc.userType === 'TEACHER' ? '教师' : '学生' }}
                </span>
              </td>
              <td>{{ acc.className || '—' }}</td>
              <td>
                <span class="dot" :class="acc.active ? 'dot-green' : 'dot-red'"></span>
                {{ acc.active ? '激活' : '禁用' }}
              </td>
              <td class="mono small">{{ formatDate(acc.createdAt) }}</td>
              <td class="mono small">{{ acc.lastLogin ? formatDate(acc.lastLogin) : '从未' }}</td>
              <td>
                <button class="btn-icon danger" title="删除本地记录" @click="remove(acc.username)">
                  ✕
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="empty-state" v-else>
          <span class="empty-icon">◫</span>
          <p>暂无账户记录</p>
          <p class="empty-hint">登录或注册后，账户信息将自动写入本地数据库</p>
        </div>
      </section>

      <!-- 原始 JSON 预览 -->
      <section class="raw-section">
        <button class="btn-ghost small" @click="showRaw = !showRaw">
          {{ showRaw ? '收起' : '查看' }} 原始 JSON
        </button>
        <pre v-if="showRaw" class="raw-json">{{ rawJson }}</pre>
      </section>
    </main>

    <!-- 清空确认弹窗 -->
    <Transition name="modal">
      <div class="modal-overlay" v-if="confirmClear" @click.self="confirmClear = false">
        <div class="modal-box">
          <h3>确认清空数据库？</h3>
          <p>这将删除所有本地账户记录，<strong>不影响后端数据库</strong>。</p>
          <div class="modal-actions">
            <button class="btn-ghost" @click="confirmClear = false">取消</button>
            <button class="btn-danger" @click="doClear">确认清空</button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import {
  listAccounts,
  deleteAccount,
  clearAllAccounts,
  getDbStats,
  type LocalAccount,
} from '@/db/accountDb';

const accounts = ref<LocalAccount[]>([]);
const stats = ref(getDbStats());
const search = ref('');
const activeFilter = ref<'ALL' | 'STUDENT' | 'TEACHER'>('ALL');
const showRaw = ref(false);
const confirmClear = ref(false);

const filters = [
  { label: '全部', value: 'ALL' as const },
  { label: '学生', value: 'STUDENT' as const },
  { label: '教师', value: 'TEACHER' as const },
];

function refresh() {
  accounts.value = listAccounts();
  stats.value = getDbStats();
}

onMounted(refresh);

const activeCount = computed(() => accounts.value.filter((a) => a.active).length);

const filtered = computed(() => {
  let list = accounts.value;
  if (activeFilter.value !== 'ALL') {
    list = list.filter((a) => a.userType === activeFilter.value);
  }
  const q = search.value.trim().toLowerCase();
  if (q) {
    list = list.filter(
      (a) =>
        a.username.toLowerCase().includes(q) ||
        a.realName.toLowerCase().includes(q) ||
        (a.className ?? '').toLowerCase().includes(q),
    );
  }
  return list;
});

const rawJson = computed(() =>
  JSON.stringify(
    Object.fromEntries(accounts.value.map((a) => [a.username, a])),
    null,
    2,
  ),
);

function remove(username: string) {
  deleteAccount(username);
  refresh();
}

function doClear() {
  clearAllAccounts();
  confirmClear.value = false;
  refresh();
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function exportJson() {
  const blob = new Blob([rawJson.value], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `zhixue_accounts_${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600&family=Noto+Sans+SC:wght@400;500;700&display=swap');

* { box-sizing: border-box; margin: 0; padding: 0; }

.db-page {
  min-height: 100vh;
  background: #0d0f14;
  color: #c9d1e0;
  font-family: 'Noto Sans SC', sans-serif;
}

/* Header */
.db-header {
  border-bottom: 1px solid #1e2433;
  background: #10131a;
  position: sticky;
  top: 0;
  z-index: 10;
}
.db-header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 2rem;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.db-logo { display: flex; align-items: center; gap: 0.6rem; }
.db-logo-icon { font-size: 1.4rem; color: #3b82f6; }
.db-logo-text { font-size: 1rem; font-weight: 700; color: #e2e8f0; letter-spacing: 0.05em; }
.db-version {
  font-size: 0.7rem;
  background: #1e2d45;
  color: #60a5fa;
  padding: 2px 7px;
  border-radius: 999px;
  font-family: 'JetBrains Mono', monospace;
}
.db-actions { display: flex; gap: 0.6rem; }

/* Main */
.db-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* Stats */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
}
.stat-card {
  background: #13161f;
  border: 1px solid #1e2433;
  border-left: 3px solid #2a3550;
  border-radius: 10px;
  padding: 1.2rem 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  transition: border-color 0.2s;
}
.stat-card:hover { border-color: #2a3550; }
.stat-card.accent-blue  { border-left-color: #3b82f6; }
.stat-card.accent-amber { border-left-color: #f59e0b; }
.stat-card.accent-green { border-left-color: #22c55e; }
.stat-num {
  font-family: 'JetBrains Mono', monospace;
  font-size: 2rem;
  font-weight: 600;
  color: #e2e8f0;
  line-height: 1;
}
.stat-label {
  font-size: 0.78rem;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

/* Toolbar */
.toolbar { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
.search-input {
  flex: 1;
  min-width: 200px;
  background: #13161f;
  border: 1px solid #1e2433;
  border-radius: 8px;
  padding: 0.55rem 1rem;
  color: #c9d1e0;
  font-size: 0.9rem;
  outline: none;
  transition: border-color 0.2s;
  font-family: 'Noto Sans SC', sans-serif;
}
.search-input:focus { border-color: #3b82f6; }
.search-input::placeholder { color: #3a4258; }
.filter-tabs { display: flex; gap: 0.4rem; }
.filter-tab {
  padding: 0.45rem 1rem;
  border-radius: 6px;
  border: 1px solid #1e2433;
  background: transparent;
  color: #64748b;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.15s;
  font-family: 'Noto Sans SC', sans-serif;
}
.filter-tab:hover { border-color: #2a3550; color: #94a3b8; }
.filter-tab.active { background: #1e2d45; border-color: #3b82f6; color: #60a5fa; }

/* Table */
.table-wrap {
  background: #13161f;
  border: 1px solid #1e2433;
  border-radius: 10px;
  overflow: hidden;
}
.db-table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
.db-table thead { background: #0d0f14; }
.db-table th {
  padding: 0.75rem 1rem;
  text-align: left;
  color: #475569;
  font-weight: 500;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  border-bottom: 1px solid #1e2433;
}
.db-row { transition: background 0.15s; }
.db-row:hover { background: #161925; }
.db-row + .db-row { border-top: 1px solid #161925; }
.db-table td { padding: 0.75rem 1rem; vertical-align: middle; }
.mono { font-family: 'JetBrains Mono', monospace; font-size: 0.82rem; }
.bold { font-weight: 600; color: #e2e8f0; }
.small { font-size: 0.78rem; color: #64748b; }

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.73rem;
  font-weight: 500;
  letter-spacing: 0.04em;
}
.badge-blue  { background: #1e2d45; color: #60a5fa; }
.badge-amber { background: #2d2010; color: #f59e0b; }

.dot {
  display: inline-block;
  width: 7px; height: 7px;
  border-radius: 50%;
  margin-right: 5px;
  vertical-align: middle;
}
.dot-green { background: #22c55e; box-shadow: 0 0 6px #22c55e88; }
.dot-red   { background: #ef4444; }

.empty-state { padding: 4rem 2rem; text-align: center; color: #3a4258; }
.empty-icon  { font-size: 3rem; display: block; margin-bottom: 1rem; }
.empty-hint  { margin-top: 0.4rem; font-size: 0.82rem; }

/* Raw JSON */
.raw-section { display: flex; flex-direction: column; gap: 0.75rem; }
.raw-json {
  background: #0d0f14;
  border: 1px solid #1e2433;
  border-radius: 8px;
  padding: 1.25rem;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.78rem;
  color: #7dd3fc;
  overflow-x: auto;
  white-space: pre;
  line-height: 1.6;
  max-height: 400px;
  overflow-y: auto;
}

/* Buttons */
.btn-ghost {
  padding: 0.45rem 1rem;
  border-radius: 7px;
  border: 1px solid #2a3550;
  background: transparent;
  color: #94a3b8;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.15s;
  font-family: 'Noto Sans SC', sans-serif;
}
.btn-ghost:hover { background: #1a2236; color: #e2e8f0; }
.btn-ghost.small { font-size: 0.8rem; padding: 0.35rem 0.8rem; }
.btn-danger {
  padding: 0.45rem 1rem;
  border-radius: 7px;
  border: 1px solid #7f1d1d;
  background: #1f0a0a;
  color: #f87171;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.15s;
  font-family: 'Noto Sans SC', sans-serif;
}
.btn-danger:hover { background: #2d1010; border-color: #ef4444; }
.btn-icon {
  width: 28px; height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 0.8rem;
  transition: background 0.15s;
}
.btn-icon.danger { color: #64748b; }
.btn-icon.danger:hover { background: #2d1010; color: #f87171; }

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  backdrop-filter: blur(4px);
}
.modal-box {
  background: #13161f;
  border: 1px solid #2a3550;
  border-radius: 14px;
  padding: 2rem;
  width: 360px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.modal-box h3 { color: #e2e8f0; font-size: 1.1rem; }
.modal-box p  { color: #94a3b8; font-size: 0.9rem; line-height: 1.6; }
.modal-box strong { color: #f87171; }
.modal-actions { display: flex; gap: 0.75rem; justify-content: flex-end; margin-top: 0.5rem; }

/* Modal transition */
.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
