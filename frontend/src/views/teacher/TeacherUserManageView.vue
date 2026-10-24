<template>
  <AppShell hide-heading :subtitle="`学生名册 · ${classroom.activeClass?.name ?? ''}`">
    <div class="tw-page">
      <header class="tw-header">
        <div>
          <h1>学生名册</h1>
          <p>{{ classroom.activeClass?.name ?? '当前班级' }} · 查看账号信息，按需编辑学生资料。</p>
        </div>
        <RouterLink to="/teacher/classroom" class="tw-button"
          ><AppIcon name="users" />管理班级成员</RouterLink
        >
      </header>
      <section class="tw-surface" aria-label="学生名册">
        <div class="tw-section-head">
          <div>
            <h2>
              班级学生 <span class="tw-count">{{ loading || loadError ? '—' : users.length }}</span>
            </h2>
            <p>
              {{
                loading || loadError
                  ? '名册统计暂未读取'
                  : `正常 ${activeCount} 人 · 停用 ${users.length - activeCount} 人`
              }}
            </p>
          </div>
          <button class="tw-link" :disabled="loading" @click="loadUsers">刷新列表</button>
        </div>
        <div class="tw-toolbar">
          <div class="tw-filters">
            <input
              v-model="searchText"
              class="tw-input tw-search"
              aria-label="搜索学生"
              placeholder="搜索姓名或账号…"
            /><select v-model="statusFilter" class="tw-input" aria-label="账号状态筛选">
              <option value="">全部状态</option>
              <option value="active">正常</option>
              <option value="inactive">停用</option>
            </select>
          </div>
          <span class="tw-muted"
            >{{ loading || loadError ? '—' : filteredUsers.length }} 位学生</span
          >
        </div>
        <p v-if="loadError" role="alert" class="tw-error roster-error">
          {{ loadError }} <button class="tw-link" @click="loadUsers">重新加载</button>
        </p>
        <div v-if="loading" class="tw-empty" role="status">正在读取学生名册…</div>
        <div v-else-if="!loadError && !filteredUsers.length" class="tw-empty">
          <strong>{{ users.length ? '没有匹配的学生' : '当前班级暂无学生' }}</strong
          >{{ users.length ? '试试其他关键词或账号状态。' : '可在班级管理中添加学生。' }}
        </div>
        <div v-else-if="!loadError" class="tw-table-wrap">
          <table class="tw-table">
            <thead>
              <tr>
                <th scope="col">学生</th>
                <th scope="col">账号状态</th>
                <th scope="col">最近登录</th>
                <th scope="col"><span class="roster-actions-label">操作</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in pagedUsers" :key="user.id">
                <td>
                  <div class="tw-person">
                    <span class="tw-avatar" aria-hidden="true">{{
                      user.realName.slice(0, 1)
                    }}</span>
                    <div>
                      <strong>{{ user.realName }}</strong
                      ><small>@{{ user.username }}</small>
                    </div>
                  </div>
                </td>
                <td>
                  <span class="tw-badge" :class="{ off: !user.active }">{{
                    user.active ? '正常' : '停用'
                  }}</span>
                </td>
                <td class="tw-muted">
                  {{ user.lastLogin ? formatDate(user.lastLogin) : '从未登录' }}
                </td>
                <td>
                  <div class="tw-cell-actions">
                    <button
                      class="tw-link"
                      :aria-label="`编辑${user.realName}`"
                      @click="selectUser(user)"
                    >
                      编辑资料
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer v-if="!loading && !loadError && filteredUsers.length" class="tw-pagination">
          <span>共 {{ filteredUsers.length }} 位 · 每页 20 位</span>
          <nav aria-label="学生名册分页">
            <button class="tw-button" :disabled="page <= 1" @click="page--">上一页</button
            ><span>{{ page }} / {{ pages }}</span
            ><button class="tw-button" :disabled="page >= pages" @click="page++">下一页</button>
          </nav>
        </footer>
      </section>
    </div>
    <TeacherDialog
      :open="!!selectedUser"
      :title="`编辑学生 · ${selectedUser?.realName ?? ''}`"
      drawer
      :busy="saving"
      @close="closeEditor"
    >
      <template v-if="selectedUser"
        ><p class="tw-dialog-copy">
          更新学生资料与账号状态。班级成员的添加和移出，请在班级管理中操作。
        </p>
        <p v-if="editorError" class="tw-error" role="alert">{{ editorError }}</p>
        <dl class="tw-dialog-meta">
          <div>
            <dt>登录账号</dt>
            <dd>@{{ selectedUser.username }}</dd>
          </div>
          <div>
            <dt>当前班级</dt>
            <dd>{{ classroom.activeClass?.name ?? '—' }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatDate(selectedUser.createTime) }}</dd>
          </div>
        </dl>
        <form class="tw-form" @submit.prevent="requestSave">
          <label class="tw-field"
            >真实姓名<input
              v-model="form.realName"
              class="tw-input"
              maxlength="50"
              required
              :disabled="saving" /></label
          ><label class="tw-field"
            >班级备注<input
              v-model="form.className"
              class="tw-input"
              maxlength="50"
              :disabled="saving"
            /><small>个人资料中的班级信息，成员归属以班级管理为准。</small></label
          ><label class="tw-field"
            >账号状态<select v-model="form.active" class="tw-input" :disabled="saving">
              <option :value="true">正常启用</option>
              <option :value="false">停用账号</option></select
            ><small>停用后，该学生将无法使用账号登录。</small></label
          >
          <div class="tw-dialog-footer">
            <button type="button" class="tw-button" :disabled="saving" @click="closeEditor">
              取消</button
            ><button
              class="tw-button primary"
              :disabled="saving || !dirty || !form.realName.trim()"
            >
              {{ saving ? '正在保存…' : '保存修改' }}
            </button>
          </div>
        </form>
        <section class="roster-danger-zone">
          <div>
            <strong>删除学生账号</strong>
            <p>这会删除学生账号。如需调整班级成员，请使用“移出班级”。</p>
          </div>
          <button class="tw-link danger" :disabled="saving" @click="pendingAction = 'delete'">
            删除账号
          </button>
        </section></template
      >
    </TeacherDialog>
    <TeacherDialog
      :open="!!pendingAction"
      :title="
        pendingAction === 'delete' ? '删除学生账号' : form.active ? '启用学生账号' : '停用学生账号'
      "
      :busy="saving"
      @close="pendingAction = null"
      ><p class="tw-dialog-copy">
        确定{{ pendingAction === 'delete' ? '删除' : form.active ? '启用' : '停用' }}「<strong>{{
          selectedUser?.realName
        }}</strong
        >」（@{{ selectedUser?.username }}）的账号吗？{{
          pendingAction === 'delete'
            ? '删除操作无法撤销。'
            : form.active
              ? '学生将可以使用该账号登录。'
              : '学生将无法使用该账号登录。'
        }}
      </p>
      <p v-if="confirmationError" role="alert" class="tw-error">{{ confirmationError }}</p>
      <div class="tw-dialog-footer">
        <button class="tw-button" :disabled="saving" @click="pendingAction = null">取消</button
        ><button
          class="tw-button"
          :class="pendingAction === 'delete' || !form.active ? 'danger' : 'primary'"
          :disabled="saving"
          @click="pendingAction === 'delete' ? deleteUser() : saveUser()"
        >
          {{ saving ? '正在处理…' : '确认' }}
        </button>
      </div></TeacherDialog
    >
    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import '@/assets/teacher-workspace.css';
import AppShell from '@/layouts/AppShell.vue';
import AppIcon from '@/components/AppIcon.vue';
import AppToast from '@/components/AppToast.vue';
import TeacherDialog from '@/components/teacher/TeacherDialog.vue';
import { useToast } from '@/composables/useToast';
import { useClassroomStore } from '@/stores/classroom';
import { getClassStudentsApi } from '@/api/classroom';
import { deleteUserByAdminApi, getAllUsersApi, updateUserByAdminApi } from '@/api/user';
import type { UserResponse } from '@/types/user';
const classroom = useClassroomStore(),
  { visible, type, message, show } = useToast();
const loading = ref(false),
  saving = ref(false),
  loadError = ref(''),
  editorError = ref(''),
  confirmationError = ref('');
const searchText = ref(''),
  statusFilter = ref(''),
  users = ref<UserResponse[]>([]),
  selectedUser = ref<UserResponse | null>(null),
  pendingAction = ref<'status' | 'delete' | null>(null),
  page = ref(1);
const form = reactive({ realName: '', className: '', active: true });
const baseline = ref('');
const snapshot = () => JSON.stringify([form.realName, form.className, form.active]);
const dirty = computed(() => !!selectedUser.value && baseline.value !== snapshot());
const activeCount = computed(() => users.value.filter((user) => user.active).length);
const filteredUsers = computed(() => {
  const keyword = searchText.value.trim().toLowerCase();
  return users.value.filter(
    (user) =>
      (!keyword || `${user.realName} ${user.username}`.toLowerCase().includes(keyword)) &&
      (!statusFilter.value || (statusFilter.value === 'active' ? user.active : !user.active)),
  );
});
const pages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / 20)));
const pagedUsers = computed(() =>
  filteredUsers.value.slice((page.value - 1) * 20, page.value * 20),
);
watch(pendingAction, () => {
  confirmationError.value = '';
});
watch([searchText, statusFilter], () => {
  page.value = 1;
});
watch(pages, (value) => {
  page.value = Math.min(page.value, value);
});
const formatDate = (date: string) =>
  new Date(date).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
let alive = true,
  loadSequence = 0;
async function loadUsers() {
  const owner = ++loadSequence,
    classId = classroom.activeClassId;
  loading.value = true;
  loadError.value = '';
  if (!classId) {
    users.value = [];
    loading.value = false;
    return;
  }
  try {
    const [members, all] = await Promise.all([getClassStudentsApi(classId), getAllUsersApi()]);
    if (!alive || owner !== loadSequence || classroom.activeClassId !== classId) return;
    const ids = new Set(members.map((row) => row.userId));
    users.value = all.filter((user) => user.userType === 'STUDENT' && ids.has(user.id));
  } catch (error: any) {
    if (alive && owner === loadSequence) {
      users.value = [];
      loadError.value = error?.response?.data?.message ?? '学生名册加载失败，请稍后重试。';
    }
  } finally {
    if (alive && owner === loadSequence) loading.value = false;
  }
}
function selectUser(user: UserResponse) {
  selectedUser.value = user;
  Object.assign(form, {
    realName: user.realName,
    className: user.className ?? '',
    active: user.active,
  });
  baseline.value = snapshot();
  editorError.value = '';
  confirmationError.value = '';
}
function canLeave() {
  return !saving.value && (!dirty.value || window.confirm('学生资料尚未保存，确定放弃修改吗？'));
}
function closeEditor() {
  if (!canLeave()) return;
  pendingAction.value = null;
  selectedUser.value = null;
}
function requestSave() {
  if (saving.value || !dirty.value || !form.realName.trim()) return;
  editorError.value = '';
  confirmationError.value = '';
  if (form.active !== selectedUser.value?.active) pendingAction.value = 'status';
  else void saveUser();
}
async function saveUser() {
  if (!selectedUser.value || saving.value || !form.realName.trim()) return;
  const id = selectedUser.value.id;
  saving.value = true;
  editorError.value = '';
  confirmationError.value = '';
  try {
    await updateUserByAdminApi({
      userId: id,
      realName: form.realName.trim(),
      className:
        form.className === (selectedUser.value.className ?? '') ? undefined : form.className.trim(),
      active: form.active === selectedUser.value.active ? undefined : form.active,
    });
    if (!alive) return;
    baseline.value = snapshot();
    pendingAction.value = null;
    selectedUser.value = null;
    show('学生资料已更新', 'success');
    await loadUsers();
  } catch (error: any) {
    if (alive) {
      const text = error?.response?.data?.message ?? '更新失败，请稍后重试。';
      if (pendingAction.value) confirmationError.value = text;
      else editorError.value = text;
    }
  } finally {
    if (alive) saving.value = false;
  }
}
async function deleteUser() {
  if (!selectedUser.value || saving.value) return;
  const id = selectedUser.value.id;
  saving.value = true;
  confirmationError.value = '';
  try {
    await deleteUserByAdminApi(id);
    if (!alive) return;
    pendingAction.value = null;
    selectedUser.value = null;
    show('学生账号已删除', 'success');
    await classroom.loadClasses();
    await loadUsers();
  } catch (error: any) {
    if (alive) confirmationError.value = error?.response?.data?.message ?? '删除失败，请稍后重试。';
  } finally {
    if (alive) saving.value = false;
  }
}
let reverting = false;
watch(
  () => classroom.activeClassId,
  (_id, old) => {
    if (reverting) {
      reverting = false;
      return;
    }
    if (selectedUser.value && !canLeave()) {
      reverting = true;
      classroom.setActiveClass(old);
      return;
    }
    selectedUser.value = null;
    pendingAction.value = null;
    users.value = [];
    page.value = 1;
    searchText.value = '';
    void loadUsers();
  },
);
onBeforeRouteLeave(canLeave);
onMounted(async () => {
  if (!classroom.classes.length) await classroom.loadClasses().catch(() => {});
  if (alive) await loadUsers();
});
onBeforeUnmount(() => {
  alive = false;
  ++loadSequence;
});
</script>
<style scoped>
.roster-error {
  margin: 0 24px 18px;
}
.roster-actions-label {
  display: block;
  text-align: right;
}
.roster-danger-zone {
  margin-top: 38px;
  padding-top: 22px;
  border-top: 1px solid #eee5df;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.roster-danger-zone strong {
  font-size: 13px;
  color: #9a6558;
  font-weight: 500;
}
.roster-danger-zone p {
  font-size: 12px;
  color: #9d8b82;
  line-height: 1.8;
  margin: 8px 0 0;
}
.roster-danger-zone > .tw-link {
  flex-shrink: 0;
}
</style>
