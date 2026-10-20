<template>
  <AppShell :subtitle="`学生名册 · ${classroom.activeClass?.name ?? auth.user?.realName ?? ''}`">
    <div class="user-page ops-typography">
      <section class="user-hero">
        <div class="hero-stats">
          <div class="stat-card cyan">
            <span class="stat-num">{{ users.length }}</span>
            <span class="stat-label">学生总数</span>
          </div>
          <div class="stat-card amber">
            <span class="stat-num">{{ activeCount }}</span>
            <span class="stat-label">正常</span>
          </div>
          <div class="stat-card rose">
            <span class="stat-num">{{ inactiveCount }}</span>
            <span class="stat-label">停用</span>
          </div>
          <div class="stat-card ghost">
            <span class="stat-num">{{ classCount }}</span>
            <span class="stat-label">班级数</span>
          </div>
        </div>
      </section>

      <section class="toolbar">
        <div class="toolbar-left">
          <input v-model="searchText" class="toolbar-search" placeholder="搜索姓名 / 账号 / 班级…" />
          <select v-model="statusFilter" class="toolbar-select">
            <option value="">全部状态</option>
            <option value="active">正常</option>
            <option value="inactive">停用</option>
          </select>
        </div>
        <div class="toolbar-right">
          <div class="toolbar-summary">当前展示 {{ filteredUsers.length }} 位</div>
          <button class="ghost-btn" :disabled="loading" @click="loadUsers">刷新列表</button>
        </div>
      </section>

      <section class="user-layout">
        <div class="user-list-panel">
          <div class="panel-head">
            <div>
              <div class="panel-kicker">学生名册</div>
              <div class="panel-title">学生列表</div>
            </div>
            <span class="panel-count">{{ filteredUsers.length }} / {{ users.length }}</span>
          </div>

          <div v-if="loading" class="panel-empty">正在加载学生数据…</div>
          <div v-else-if="filteredUsers.length === 0" class="panel-empty">暂无匹配学生</div>
          <div v-else class="user-list">
            <button
              v-for="user in filteredUsers"
              :key="user.id"
              class="user-item"
              :class="{ active: selectedUser?.id === user.id, inactive: !user.active }"
              @click="selectUser(user)"
            >
              <div class="user-item-top">
                <div>
                  <div class="user-name">{{ user.realName }}</div>
                  <div class="user-item-meta">@{{ user.username }} · {{ user.className || '未分班' }}</div>
                </div>
                <span class="user-role student">学生</span>
              </div>
              <div class="user-item-footer">
                <span class="status-pack">
                  <span class="status-dot" :class="user.active ? 'ok' : 'off'" />
                  <span>{{ user.active ? '正常' : '停用' }}</span>
                </span>
                <span class="footer-time">{{ user.lastLogin ? formatDate(user.lastLogin) : '从未登录' }}</span>
              </div>
            </button>
          </div>
        </div>

        <div class="editor-panel">
          <div v-if="!selectedUser" class="panel-empty large">从左侧选择一名学生</div>
          <template v-else>
            <div class="panel-head editor-head">
              <div>
                <div class="panel-kicker">编辑学生</div>
                <div class="editor-title">编辑学生 #{{ selectedUser.id }}</div>

              </div>
              <button class="ghost-btn" @click="reloadSelected">同步最新信息</button>
            </div>

            <div class="editor-overview">
              <div class="overview-chip">
                <span class="overview-label">账号</span>
                <strong>@{{ selectedUser.username }}</strong>
              </div>
              <div class="overview-chip">
                <span class="overview-label">创建时间</span>
                <strong>{{ formatDate(selectedUser.createTime) }}</strong>
              </div>
              <div class="overview-chip wide">
                <span class="overview-label">最近登录</span>
                <strong>{{ selectedUser.lastLogin ? formatDate(selectedUser.lastLogin) : '从未登录' }}</strong>
              </div>
            </div>

            <div class="editor-grid">
              <label class="field-block">
                <span class="field-label">真实姓名</span>
                <input v-model="form.realName" class="field-input" placeholder="请输入真实姓名" />
              </label>

              <label class="field-block">
                <span class="field-label">班级</span>
                <input v-model="form.className" class="field-input"  />
              </label>

              <label class="field-block toggle-field">
                <span class="field-label">账号状态</span>
                <button class="toggle-btn" :class="form.active ? 'on' : 'off'" @click="form.active = !form.active">
                  {{ form.active ? '正常启用' : '已停用' }}
                </button>
              </label>
            </div>

            <div class="editor-actions">
              <button class="ghost-btn" :disabled="saving" @click="resetForm">重置修改</button>
              <button class="primary-btn" :disabled="saving || !selectedUser" @click="saveUser">
                {{ saving ? '保存中…' : '保存学生信息' }}
              </button>
              <button class="danger-btn" :disabled="saving || deleting" @click="deleteUser">
                {{ deleting ? '删除中…' : '删除该学生' }}
              </button>
            </div>
          </template>
        </div>
      </section>
    </div>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';
import { useClassroomStore } from '@/stores/classroom';
import { getClassStudentsApi } from '@/api/classroom';
import { deleteUserByAdminApi, getAllUsersApi, getUserByIdApi, updateUserByAdminApi } from '@/api/user';
import type { UserAdminUpdateRequest, UserResponse } from '@/types/user';

const auth = useAuthStore();
const classroom = useClassroomStore();
const { visible, type, message, show } = useToast();


const loading = ref(false);
const saving = ref(false);
const deleting = ref(false);
const searchText = ref('');
const statusFilter = ref('');
const users = ref<UserResponse[]>([]);
const selectedUser = ref<UserResponse | null>(null);

const form = reactive({
  realName: '',
  className: '',
  active: true,
});

const activeCount = computed(() => users.value.filter(user => user.active).length);
const inactiveCount = computed(() => users.value.filter(user => !user.active).length);
const classCount = computed(() => new Set(users.value.map(user => user.className).filter(Boolean)).size);

const filteredUsers = computed(() => {
  const keyword = searchText.value.trim().toLowerCase();
  return users.value.filter((user) => {
    const hitKeyword = !keyword
      || user.realName.toLowerCase().includes(keyword)
      || user.username.toLowerCase().includes(keyword)
      || (user.className ?? '').toLowerCase().includes(keyword);
    const hitStatus = !statusFilter.value
      || (statusFilter.value === 'active' && user.active)
      || (statusFilter.value === 'inactive' && !user.active);
    return hitKeyword && hitStatus;
  });
});

const syncForm = (user: UserResponse) => {
  form.realName = user.realName;
  form.className = user.className ?? '';
  form.active = user.active;
};

const formatDate = (date: string) => new Date(date).toLocaleString('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const loadUsers = async () => {
  loading.value = true;
  try {
    const classId = classroom.activeClassId;
    if (!classId) { users.value = []; return; }
    const memberIds = new Set((await getClassStudentsApi(classId)).map(row => row.userId));
    users.value = (await getAllUsersApi()).filter(user => user.userType === 'STUDENT' && memberIds.has(user.id));
    if (selectedUser.value) {
      const matched = users.value.find(user => user.id === selectedUser.value?.id);
      if (matched) {
        selectedUser.value = matched;
        syncForm(matched);
      } else {
        selectedUser.value = null;
      }
    }
  } catch (error: any) {
    show(error?.response?.data?.message ?? '学生名册加载失败', 'error');
  } finally {
    loading.value = false;
  }
};

const selectUser = async (user: UserResponse) => {
  selectedUser.value = user;
  syncForm(user);
  try {
    const latest = await getUserByIdApi(user.id);
    selectedUser.value = latest;
    syncForm(latest);
  } catch {
    // ignore fallback to list data
  }
};

const reloadSelected = async () => {
  if (!selectedUser.value) return;
  try {
    const latest = await getUserByIdApi(selectedUser.value.id);
    selectedUser.value = latest;
    syncForm(latest);
    show('已同步最新学生信息', 'success');
  } catch (error: any) {
    show(error?.response?.data?.message ?? '重新读取失败', 'error');
  }
};

const resetForm = () => {
  if (!selectedUser.value) return;
  syncForm(selectedUser.value);
};

const saveUser = async () => {
  if (!selectedUser.value) return;
  if (!form.realName.trim()) {
    show('真实姓名不能为空', 'error');
    return;
  }

  saving.value = true;
  try {
    const payload: UserAdminUpdateRequest = {
      userId: selectedUser.value.id,
      realName: form.realName.trim(),
      className: form.className.trim() || null,
      active: form.active,
    };
    const updated = await updateUserByAdminApi(payload);
    selectedUser.value = updated;
    syncForm(updated);
    await loadUsers();
    show('学生信息已更新', 'success');
  } catch (error: any) {
    show(error?.response?.data?.message ?? '更新学生失败', 'error');
  } finally {
    saving.value = false;
  }
};

const deleteUser = async () => {
  if (!selectedUser.value) return;

  const currentId = selectedUser.value.id;
  deleting.value = true;
  try {
    await deleteUserByAdminApi(currentId);
    selectedUser.value = null;
    await loadUsers();
    show(`学生 #${currentId} 已删除`, 'success');
  } catch (error: any) {
    show(error?.response?.data?.message ?? '删除学生失败', 'error');
  } finally {
    deleting.value = false;
  }
};

watch(() => classroom.activeClassId, () => { loadUsers().catch(() => {}); });
onMounted(async () => {
  if (!classroom.classes.length) await classroom.loadClasses().catch(() => {});
  await loadUsers();
});
</script>

<style scoped>
.user-page {
  display: grid;
  gap: 18px;
}

.ops-typography {
  font-family: 'Georgia', 'Times New Roman', 'Songti SC', 'STSong', serif;
}

.user-hero {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 16px;
  padding: 24px;
  border-radius: 28px;
  border: 1px solid rgba(140, 154, 255, 0.15);
  background:
    radial-gradient(circle at right top, rgba(255, 121, 168, 0.16), transparent 30%),
    radial-gradient(circle at left bottom, rgba(90, 243, 211, 0.12), transparent 30%),
    linear-gradient(145deg, rgba(13, 18, 38, 0.98), rgba(7, 9, 22, 0.98));
  box-shadow: 0 24px 70px rgba(4, 7, 20, 0.36);
}

.hero-copy {
  display: grid;
  gap: 12px;
  align-content: center;
}

.hero-kicker,
.panel-kicker {
  font-size: var(--font-size-xs);
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(189, 200, 233, 0.62);
}

.hero-title {
  margin: 0;
  font-size: 34px;
  line-height: 1.05;
  color: #f7fbff;
}

.hero-desc {
  max-width: 620px;
  font-size: var(--font-size-md);
  line-height: 1.9;
  color: #b9c7ea;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 18px;
  min-height: 120px;
  border-radius: 20px;
  border: 1px solid rgba(255,255,255,0.08);
  background: rgba(255,255,255,0.04);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.stat-num { font-size: 30px; font-weight: 800; color: #fff; }
.stat-label { font-size: var(--font-size-sm); color: #c7d2ef; }
.cyan { box-shadow: inset 0 0 0 1px rgba(90,243,211,0.08); }
.amber { box-shadow: inset 0 0 0 1px rgba(253,163,72,0.08); }
.rose { box-shadow: inset 0 0 0 1px rgba(255,107,145,0.08); }
.ghost { box-shadow: inset 0 0 0 1px rgba(140,154,255,0.08); }

.toolbar,
.user-list-panel,
.editor-panel {
  border: 1px solid rgba(140,154,255,0.14);
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(11,15,30,0.98), rgba(8,10,22,0.98));
  box-shadow: 0 14px 46px rgba(5, 7, 18, 0.24);
}

.toolbar {
  padding: 14px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.toolbar-left { flex: 1; }

.toolbar-summary {
  font-size: var(--font-size-sm);
  color: #9fb0da;
}

.toolbar-search,
.toolbar-select,
.field-input {
  border: 1px solid rgba(140,154,255,0.18);
  border-radius: 14px;
  background: rgba(255,255,255,0.04);
  color: #eef4ff;
  padding: 11px 14px;
  outline: none;
}

.toolbar-select,
select.field-input {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  background:
    linear-gradient(180deg, rgba(17, 24, 45, 0.98), rgba(10, 14, 28, 0.98)),
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23b9c7ea' stroke-width='2.4' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E") no-repeat right 14px center / 12px;
  border-color: rgba(123, 146, 255, 0.28);
  color: #f5f8ff;
  padding-right: 42px;
  cursor: pointer;
}

.toolbar-select:focus,
select.field-input:focus {
  border-color: rgba(90,243,211,0.56);
  box-shadow: 0 0 0 3px rgba(90,243,211,0.12);
}

.toolbar-select option,
select.field-input option {
  background: #11182d;
  color: #eef4ff;
}

.toolbar-search { min-width: 260px; flex: 1; }
.toolbar-select { min-width: 130px; }

.user-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
  min-height: 560px;
}

.user-list-panel,
.editor-panel {
  padding: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-title {
  margin-top: 4px;
  color: #f4f8ff;
  font-size: 22px;
}

.panel-count {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(140,154,255,0.1);
  color: #c8d5f4;
  font-size: var(--font-size-xs);
}

.user-list {
  display: grid;
  gap: 10px;
  max-height: 720px;
  overflow: auto;
}

.user-item {
  border: 1px solid rgba(140,154,255,0.12);
  border-radius: 18px;
  background: rgba(255,255,255,0.03);
  color: inherit;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  display: grid;
  gap: 12px;
}

.user-item.active {
  border-color: rgba(90,243,211,0.36);
  background: rgba(90,243,211,0.08);
}

.user-item.inactive { opacity: 0.76; }

.user-item-top,
.user-item-footer,
.editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.user-name { font-size: 18px; font-weight: 700; color: #f7fbff; }

.user-role,
.status-dot,
.toggle-btn,
.ghost-btn,
.primary-btn,
.danger-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.user-role {
  min-width: 58px;
  height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.user-role.teacher { background: rgba(255,107,145,0.12); color: #ff9fbc; }
.user-role.student { background: rgba(90,243,211,0.12); color: #8df7e0; }
.user-item-meta,
.footer-time { color: #9fb0da; font-size: var(--font-size-sm); }

.status-pack {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.status-dot { width: 8px; height: 8px; border-radius: 999px; }
.status-dot.ok { background: #63f3c8; }
.status-dot.off { background: #ff7ba8; }

.editor-head { margin-bottom: 18px; }
.editor-title { font-size: 24px; color: #f7fbff; }
.editor-sub { margin-top: 4px; color: #9fb0da; font-size: 12px; }

.editor-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.overview-chip {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(140,154,255,0.12);
  background: rgba(255,255,255,0.03);
  display: grid;
  gap: 6px;
}

.overview-chip.wide { grid-column: span 1; }
.overview-label { font-size: 11px; color: #93a5d6; text-transform: uppercase; letter-spacing: 0.12em; }
.overview-chip strong { color: #f1f6ff; font-size: 13px; word-break: break-all; }

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field-block { display: grid; gap: 8px; }
.field-label { font-size: 12px; font-weight: 700; color: #c4d0f0; }
.toggle-field { align-content: end; }

.toggle-btn,
.ghost-btn,
.primary-btn,
.danger-btn {
  height: 42px;
  padding: 0 16px;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 700;
  border: 1px solid transparent;
}

.toggle-btn.on { background: rgba(90,243,211,0.1); border-color: rgba(90,243,211,0.28); color: #8df7e0; }
.toggle-btn.off { background: rgba(255,107,145,0.1); border-color: rgba(255,107,145,0.24); color: #ff9fbc; }

.editor-actions {
  margin-top: 20px;
  justify-content: flex-start;
  flex-wrap: wrap;
}

.ghost-btn {
  border-color: rgba(140,154,255,0.16);
  background: rgba(255,255,255,0.03);
  color: #c7d4f3;
}

.primary-btn {
  border-color: rgba(90,243,211,0.46);
  background: linear-gradient(120deg, rgba(90,243,211,0.92), rgba(124,245,221,0.88));
  color: #061016;
  box-shadow: 0 8px 22px rgba(90,243,211,0.2);
}

.danger-btn {
  border-color: rgba(255,107,145,0.28);
  background: rgba(255,107,145,0.12);
  color: #ff9ab7;
}

.panel-empty {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(140,154,255,0.14);
  border-radius: 18px;
  color: #91a3d8;
}

.panel-empty.large {
  min-height: 520px;
}

@media (max-width: 1180px) {
  .user-hero,
  .user-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hero-stats,
  .editor-overview,
  .editor-grid {
    grid-template-columns: 1fr;
  }

  .toolbar,
  .panel-head,
  .editor-actions,
  .user-item-top,
  .user-item-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .ghost-btn,
  .primary-btn,
  .danger-btn,
  .toggle-btn {
    width: 100%;
  }
}

/* Light workspace overrides for the roster page */
.user-hero {
  display: block !important;
  padding: 0 !important;
  border: none !important;
  background: transparent !important;
  box-shadow: none !important;
}
.hero-stats { grid-template-columns: repeat(4, minmax(0, 1fr)) !important; gap: 12px !important; }
.editor-panel {
  padding: 16px !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 12px !important;
  background: #fff !important;
  box-shadow: none !important;
}
.editor-title { color: #172033 !important; font-size: 18px !important; }
.panel-kicker { color: #94a3b8 !important; }
.overview-chip { background: #f8fafc !important; border: 1px solid #eef2f7 !important; }
.overview-label { color: #64748b !important; }
.overview-chip strong { color: #172033 !important; }
.field-label { color: #334155 !important; }
.field-input { color: #172033 !important; background: #fff !important; border: 1px solid #dbe3ec !important; }
.user-role.student { color: #0f766e !important; background: #ecfdf5 !important; }
.user-role.teacher { color: #9d174d !important; background: #fdf2f8 !important; }
.user-item-meta,
.footer-time,
.editor-sub {
  color: #64748b !important;
}
.danger-btn { color: #b91c1c !important; background: #fef2f2 !important; border-color: #fecaca !important; }
.toggle-btn.on { color: #0f766e !important; background: #ecfdf5 !important; border-color: #a7f3d0 !important; }
.toggle-btn.off { color: #b91c1c !important; background: #fef2f2 !important; border-color: #fecaca !important; }
@media (max-width: 900px) { .hero-stats { grid-template-columns: repeat(2, minmax(0, 1fr)) !important; } }
</style>