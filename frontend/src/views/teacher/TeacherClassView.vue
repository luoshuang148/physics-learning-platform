<template>
  <AppShell hide-heading :subtitle="`班级管理 · ${auth.user?.realName ?? ''}`">
    <div class="tw-page">
      <header class="tw-header">
        <div>
          <h1>班级管理</h1>
          <p>管理班级成员与协作教师，让教学安排有序进行。</p>
        </div>
        <button class="tw-button primary" :disabled="busy" @click="createOpen = true">
          ＋ 创建班级
        </button>
      </header>
      <p v-if="directoryError" class="tw-error" role="alert">
        {{ directoryError }}
        <button class="tw-link" :disabled="classroom.loading || busy" @click="loadClassroom">
          重新加载班级
        </button>
      </p>
      <div class="class-workspace">
        <aside class="tw-surface class-directory" aria-label="我的班级">
          <div class="tw-section-head">
            <h2>我的班级</h2>
            <span class="tw-count">{{ classroom.classes.length }}</span>
          </div>
          <div class="class-directory-list">
            <button
              v-for="item in classroom.classes"
              :key="item.id"
              class="class-directory-item"
              :class="{ selected: classroom.activeClassId === item.id }"
              :aria-pressed="classroom.activeClassId === item.id"
              :disabled="busy"
              @click="classroom.setActiveClass(item.id)"
            >
              <span class="class-directory-icon"><AppIcon name="users" /></span
              ><span class="class-directory-copy"
                ><strong>{{ item.name }}</strong
                ><small
                  >{{ item.studentCount }} 位学生 ·
                  {{ item.role === 'OWNER' ? '负责教师' : '协作教师' }}</small
                ></span
              >
            </button>
            <p v-if="!directoryError && !classroom.classes.length" class="tw-empty">
              创建班级后，在这里管理成员。
            </p>
          </div>
        </aside>
        <section class="tw-surface class-members" aria-label="班级成员">
          <template v-if="classroom.activeClass"
            ><div class="tw-section-head">
              <div>
                <h2>{{ classroom.activeClass.name }}</h2>
                <p>
                  {{ classroom.activeClass.grade || '未设置年级' }} ·
                  {{ classroom.activeClass.studentCount }} 位学生 ·
                  {{ classroom.activeClass.teacherCount }} 位教师
                </p>
              </div>
              <span class="tw-badge">{{
                classroom.activeClass.role === 'OWNER' ? '负责教师' : '协作教师'
              }}</span>
            </div>
            <div class="tw-tabs" role="tablist" aria-label="成员类型">
              <button
                id="class-students-tab"
                role="tab"
                :aria-selected="tab === 'students'"
                aria-controls="class-member-panel"
                :tabindex="tab === 'students' ? 0 : -1"
                @click="tab = 'students'"
                @keydown.right.prevent="switchTab('teachers')"
                @keydown.left.prevent="switchTab('teachers')"
              >
                学生 <span>{{ classStudents.length }}</span></button
              ><button
                id="class-teachers-tab"
                role="tab"
                :aria-selected="tab === 'teachers'"
                aria-controls="class-member-panel"
                :tabindex="tab === 'teachers' ? 0 : -1"
                @click="tab = 'teachers'"
                @keydown.right.prevent="switchTab('students')"
                @keydown.left.prevent="switchTab('students')"
              >
                教师 <span>{{ classTeachers.length }}</span>
              </button>
            </div>
            <div
              id="class-member-panel"
              role="tabpanel"
              :aria-labelledby="tab === 'students' ? 'class-students-tab' : 'class-teachers-tab'"
            >
              <div class="tw-toolbar member-toolbar">
                <input
                  v-model="search"
                  class="tw-input tw-search"
                  aria-label="搜索班级成员"
                  placeholder="搜索姓名或账号…"
                /><button
                  v-if="canAdd"
                  class="tw-button"
                  :disabled="loading || busy"
                  @click="openAdd"
                >
                  ＋ 添加{{ tab === 'students' ? '学生' : '教师' }}
                </button>
              </div>
              <p v-if="memberError" class="tw-error member-error" role="alert">
                {{ memberError }} <button class="tw-link" @click="loadMembers">重新加载</button>
              </p>
              <div v-if="loading" class="tw-empty" role="status">正在读取班级成员…</div>
              <div v-else-if="!memberError && !filteredMembers.length" class="tw-empty">
                {{
                  search
                    ? '没有匹配的成员，试试其他关键词。'
                    : tab === 'students'
                      ? '班级暂无学生，点击添加学生完善名单。'
                      : '班级暂无教师。'
                }}
              </div>
              <div v-else-if="!memberError" class="tw-table-wrap">
                <table class="tw-table">
                  <thead>
                    <tr>
                      <th scope="col">{{ tab === 'students' ? '学生' : '教师' }}</th>
                      <th scope="col">{{ tab === 'students' ? '账号状态' : '班级角色' }}</th>
                      <th scope="col"><span class="class-operation-title">操作</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="member in filteredMembers" :key="member.userId">
                      <td>
                        <div class="tw-person">
                          <span class="tw-avatar" aria-hidden="true">{{
                            member.realName.slice(0, 1)
                          }}</span>
                          <div>
                            <strong
                              >{{ member.realName
                              }}<span v-if="member.userId === auth.user?.id" class="self-label"
                                >我</span
                              ></strong
                            ><small>@{{ member.username }}</small>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span
                          class="tw-badge"
                          :class="{ off: tab === 'students' && !member.active }"
                          >{{
                            tab === 'students'
                              ? member.active
                                ? '正常'
                                : '停用'
                              : member.role === 'OWNER'
                                ? '负责教师'
                                : '协作教师'
                          }}</span
                        >
                      </td>
                      <td>
                        <div class="tw-cell-actions">
                          <button
                            v-if="canRemove(member)"
                            class="tw-link danger"
                            :disabled="busy"
                            :aria-label="`移出${member.realName}`"
                            @click="askRemove(member)"
                          >
                            移出班级</button
                          ><span v-else class="tw-muted">—</span>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p class="member-count">
                {{ filteredMembers.length }} 位{{ tab === 'students' ? '学生' : '教师' }}
              </p>
            </div></template
          >
          <div v-else class="tw-empty">
            <strong>{{ directoryError ? '班级资料暂未加载' : '选择或创建一个班级' }}</strong
            >班级的成员与协作教师会显示在这里。
          </div>
        </section>
      </div>
    </div>
    <TeacherDialog :open="createOpen" title="创建班级" :busy="busy" @close="createOpen = false"
      ><p class="tw-dialog-copy">填写班级名称，创建后即可添加学生和协作教师。</p>
      <form class="tw-form" @submit.prevent="createClass">
        <label class="tw-field"
          >班级名称<input
            v-model="newName"
            class="tw-input"
            placeholder="例如：高一（3）班"
            maxlength="80"
            required
            :disabled="busy" /></label
        ><label class="tw-field"
          >年级 <small>选填</small
          ><input
            v-model="newGrade"
            class="tw-input"
            placeholder="例如：高一"
            maxlength="30"
            :disabled="busy"
        /></label>
        <p v-if="dialogError" class="tw-error" role="alert">{{ dialogError }}</p>
        <div class="tw-dialog-footer">
          <button type="button" class="tw-button" :disabled="busy" @click="createOpen = false">
            取消</button
          ><button class="tw-button primary" :disabled="busy || !newName.trim()">
            {{ busy ? '正在创建…' : '创建班级' }}
          </button>
        </div>
      </form></TeacherDialog
    >
    <TeacherDialog
      :open="addOpen"
      :title="`添加${tab === 'students' ? '学生' : '教师'}到${classroom.activeClass?.name ?? '班级'}`"
      :busy="busy"
      @close="addOpen = false"
      ><div class="tw-form">
        <input
          v-model="candidateSearch"
          class="tw-input"
          aria-label="搜索可添加成员"
          placeholder="搜索姓名或账号…"
          :disabled="busy"
        />
        <p v-if="candidatesLoading" class="tw-empty" role="status">正在读取可添加成员…</p>
        <p v-else-if="candidateError" class="tw-error" role="alert">
          {{ candidateError }} <button class="tw-link" @click="loadCandidates">重新加载</button>
        </p>
        <div v-else class="candidate-list">
          <label
            v-for="person in filteredCandidates"
            :key="person.userId"
            class="candidate-option"
            :class="{ checked: person.userId === selectedId }"
            ><input
              v-model="selectedId"
              type="radio"
              name="new-class-member"
              :value="person.userId"
              :disabled="busy"
            /><span class="tw-person"
              ><span class="tw-avatar" aria-hidden="true">{{ person.realName.slice(0, 1) }}</span
              ><span
                ><strong>{{ person.realName }}</strong
                ><small>@{{ person.username }}</small></span
              ></span
            ></label
          >
          <p v-if="!filteredCandidates.length" class="tw-empty">
            {{ candidateSearch ? '没有匹配的成员。' : '暂无可添加的成员。' }}
          </p>
        </div>
        <p v-if="dialogError" class="tw-error" role="alert">{{ dialogError }}</p>
      </div>
      <div class="tw-dialog-footer">
        <button class="tw-button" :disabled="busy" @click="addOpen = false">取消</button
        ><button
          class="tw-button primary"
          :disabled="busy || !selectedId || candidatesLoading || !!candidateError"
          @click="addMember"
        >
          {{ busy ? '正在添加…' : '加入班级' }}
        </button>
      </div></TeacherDialog
    >
    <TeacherDialog :open="!!removeTarget" title="移出班级" :busy="busy" @close="removeTarget = null"
      ><p class="tw-dialog-copy">
        确定将「<strong>{{ removeTarget?.realName }}</strong
        >」（@{{ removeTarget?.username }}）移出「{{
          classroom.activeClass?.name
        }}」吗？该成员将不再出现在当前班级名单中。
      </p>
      <p v-if="dialogError" class="tw-error" role="alert">{{ dialogError }}</p>
      <div class="tw-dialog-footer">
        <button class="tw-button" :disabled="busy" @click="removeTarget = null">取消</button
        ><button class="tw-button danger" :disabled="busy" @click="removeMember">
          {{ busy ? '正在移出…' : '确认移出' }}
        </button>
      </div></TeacherDialog
    >
    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import '@/assets/teacher-workspace.css';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import AppIcon from '@/components/AppIcon.vue';
import TeacherDialog from '@/components/teacher/TeacherDialog.vue';
import { useAuthStore } from '@/stores/auth';
import { useClassroomStore } from '@/stores/classroom';
import { useToast } from '@/composables/useToast';
import {
  addClassStudentApi,
  addClassTeacherApi,
  createClassApi,
  getAvailableStudentsApi,
  getAvailableTeachersApi,
  getClassStudentsApi,
  getClassTeachersApi,
  removeClassStudentApi,
  removeClassTeacherApi,
} from '@/api/classroom';
import type { ClassStudentRow, ClassTeacherRow } from '@/types/classroom';
type Member = {
  userId: number;
  realName: string;
  username: string;
  active?: boolean;
  role?: 'OWNER' | 'CO_TEACHER';
};
const auth = useAuthStore(),
  classroom = useClassroomStore(),
  { visible, type, message, show } = useToast();
const tab = ref<'students' | 'teachers'>('students'),
  search = ref(''),
  classStudents = ref<ClassStudentRow[]>([]),
  classTeachers = ref<ClassTeacherRow[]>([]);
const loading = ref(false),
  busy = ref(false),
  memberError = ref(''),
  dialogError = ref(''),
  createOpen = ref(false),
  addOpen = ref(false),
  removeTarget = ref<Member | null>(null);
const directoryError = ref('');
const newName = ref(''),
  newGrade = ref(''),
  candidateSearch = ref(''),
  candidates = ref<Member[]>([]),
  candidatesLoading = ref(false),
  candidateError = ref(''),
  selectedId = ref<number | null>(null);
let alive = true,
  memberSequence = 0,
  candidateSequence = 0,
  reverting = false,
  internalClassChange = false;
const members = computed<Member[]>(() =>
  tab.value === 'students' ? classStudents.value : classTeachers.value,
);
const filteredMembers = computed(() => {
  const keyword = search.value.trim().toLowerCase();
  return members.value.filter((member) =>
    `${member.realName} ${member.username}`.toLowerCase().includes(keyword),
  );
});
const filteredCandidates = computed(() => {
  const keyword = candidateSearch.value.trim().toLowerCase();
  return candidates.value.filter((member) =>
    `${member.realName} ${member.username}`.toLowerCase().includes(keyword),
  );
});
const canAdd = computed(() => tab.value === 'students' || classroom.activeClass?.role === 'OWNER');
const canRemove = (member: Member) =>
  tab.value === 'students' ||
  (classroom.activeClass?.role === 'OWNER' &&
    member.userId !== auth.user?.id &&
    member.role !== 'OWNER');
async function switchTab(value: 'students' | 'teachers') {
  tab.value = value;
  await nextTick();
  document.getElementById(`class-${value}-tab`)?.focus();
}
watch(tab, () => {
  search.value = '';
});
watch([createOpen, addOpen, removeTarget], () => {
  dialogError.value = '';
});
async function loadMembers() {
  const id = classroom.activeClassId,
    owner = ++memberSequence;
  loading.value = true;
  memberError.value = '';
  if (!id) {
    classStudents.value = [];
    classTeachers.value = [];
    loading.value = false;
    return;
  }
  try {
    const [students, teachers] = await Promise.all([
      getClassStudentsApi(id),
      getClassTeachersApi(id),
    ]);
    if (!alive || owner !== memberSequence || id !== classroom.activeClassId) return;
    classStudents.value = students;
    classTeachers.value = teachers;
  } catch (error: any) {
    if (alive && owner === memberSequence) {
      classStudents.value = [];
      classTeachers.value = [];
      memberError.value = error?.response?.data?.message ?? '班级成员加载失败，请重试。';
    }
  } finally {
    if (alive && owner === memberSequence) loading.value = false;
  }
}
async function loadCandidates() {
  const id = classroom.activeClassId,
    kind = tab.value,
    owner = ++candidateSequence;
  if (!id) return;
  candidatesLoading.value = true;
  candidateError.value = '';
  candidates.value = [];
  try {
    const data =
      kind === 'students' ? await getAvailableStudentsApi(id) : await getAvailableTeachersApi(id);
    if (
      alive &&
      owner === candidateSequence &&
      id === classroom.activeClassId &&
      kind === tab.value
    )
      candidates.value = data;
  } catch (error: any) {
    if (alive && owner === candidateSequence)
      candidateError.value = error?.response?.data?.message ?? '可添加成员加载失败，请重试。';
  } finally {
    if (alive && owner === candidateSequence) candidatesLoading.value = false;
  }
}
function openAdd() {
  selectedId.value = null;
  candidateSearch.value = '';
  addOpen.value = true;
  void loadCandidates();
}
function askRemove(member: Member) {
  dialogError.value = '';
  removeTarget.value = member;
}
async function refreshAfterWrite() {
  directoryError.value = '';
  try {
    await classroom.loadClasses();
  } catch {
    if (alive) directoryError.value = '成员已更新，班级信息刷新失败，请重新加载班级。';
  }
  if (alive) await loadMembers();
}
async function createClass() {
  if (busy.value || !newName.value.trim()) return;
  busy.value = true;
  dialogError.value = '';
  try {
    const created = await createClassApi({
      name: newName.value.trim(),
      grade: newGrade.value.trim() || undefined,
    });
    if (!alive) return;
    internalClassChange = true;
    classroom.classes = [...classroom.classes.filter((item) => item.id !== created.id), created];
    classroom.setActiveClass(created.id);
    await nextTick();
    internalClassChange = false;
    createOpen.value = false;
    newName.value = '';
    newGrade.value = '';
    show('班级已创建', 'success');
    await refreshAfterWrite();
  } catch (error: any) {
    if (alive) dialogError.value = error?.response?.data?.message ?? '创建班级失败，请重试。';
  } finally {
    internalClassChange = false;
    if (alive) busy.value = false;
  }
}
async function addMember() {
  const id = classroom.activeClassId,
    userId = selectedId.value,
    kind = tab.value;
  if (!id || !userId || busy.value || !canAdd.value) return;
  busy.value = true;
  dialogError.value = '';
  try {
    if (kind === 'students') await addClassStudentApi(id, userId);
    else await addClassTeacherApi(id, userId);
    if (!alive) return;
    addOpen.value = false;
    show('成员已加入班级', 'success');
    await refreshAfterWrite();
  } catch (error: any) {
    if (alive) dialogError.value = error?.response?.data?.message ?? '添加成员失败，请重试。';
  } finally {
    if (alive) busy.value = false;
  }
}
async function removeMember() {
  const id = classroom.activeClassId,
    person = removeTarget.value,
    kind = tab.value;
  if (!id || !person || busy.value || !canRemove(person)) return;
  busy.value = true;
  dialogError.value = '';
  try {
    if (kind === 'students') await removeClassStudentApi(id, person.userId);
    else await removeClassTeacherApi(id, person.userId);
    if (!alive) return;
    removeTarget.value = null;
    show(`${person.realName}已移出班级`, 'success');
    await refreshAfterWrite();
  } catch (error: any) {
    if (alive) dialogError.value = error?.response?.data?.message ?? '移出成员失败，请重试。';
  } finally {
    if (alive) busy.value = false;
  }
}
watch(
  () => classroom.activeClassId,
  (_id, old) => {
    if (internalClassChange) return;
    if (reverting) {
      reverting = false;
      return;
    }
    if (busy.value) {
      reverting = true;
      classroom.setActiveClass(old);
      return;
    }
    addOpen.value = false;
    removeTarget.value = null;
    ++candidateSequence;
    candidatesLoading.value = false;
    search.value = '';
    classStudents.value = [];
    classTeachers.value = [];
    void loadMembers();
  },
);
onBeforeRouteLeave(() => !busy.value);
async function loadClassroom() {
  directoryError.value = '';
  try {
    await classroom.loadClasses();
    if (alive) await loadMembers();
  } catch (error: any) {
    if (alive)
      directoryError.value = error?.response?.data?.message ?? '班级列表加载失败，请稍后重试。';
  }
}
onMounted(loadClassroom);
onBeforeUnmount(() => {
  alive = false;
  ++memberSequence;
  ++candidateSequence;
});
</script>
<style scoped>
.class-workspace {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}
.class-directory-list {
  display: grid;
  gap: 8px;
  padding: 0 12px 14px;
}
.class-directory .tw-section-head {
  padding: 22px 20px;
}
.class-directory-item {
  display: flex;
  gap: 12px;
  align-items: center;
  text-align: left;
  padding: 14px 12px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: white;
  color: #668571;
  cursor: pointer;
  min-width: 0;
}
.class-directory-item:hover {
  background: #f8fbf9;
}
.class-directory-item.selected {
  background: #f0f7f2;
  border-color: #dceadf;
}
.class-directory-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  background: #edf3ee;
  border-radius: 8px;
}
.class-directory-icon .app-icon {
  width: 17px;
  height: 17px;
}
.class-directory-copy {
  min-width: 0;
}
.class-directory-copy strong {
  display: block;
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.class-directory-copy small {
  display: block;
  font-size: 11px;
  color: #8ba18f;
  margin-top: 7px;
  line-height: 1.7;
}
.member-toolbar {
  padding-top: 22px;
}
.class-operation-title {
  text-align: right;
  display: block;
}
.self-label {
  font-size: 10px;
  font-weight: 400;
  color: #7b9a83;
  margin-left: 8px;
  padding: 2px 5px;
  background: #f2f7f3;
  border-radius: 4px;
}
.member-count {
  margin: 0;
  padding: 18px 24px;
  font-size: 11px;
  color: #94a799;
  border-top: 1px solid #edf2ef;
}
.member-error {
  margin: 0 24px 18px;
}
.candidate-list {
  max-height: 340px;
  overflow: auto;
  border: 1px solid #e3ece5;
  border-radius: 9px;
}
.candidate-option {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border-bottom: 1px solid #edf2ef;
  cursor: pointer;
}
.candidate-option:last-child {
  border-bottom: 0;
}
.candidate-option.checked {
  background: #f3f8f4;
}
.candidate-option > input {
  accent-color: #477d55;
  width: 16px;
  height: 16px;
}
.tw-tabs button span {
  margin-left: 6px;
  font-size: 11px;
}
.class-members .tw-table {
  min-width: 460px;
}
@media (max-width: 1000px) {
  .class-workspace {
    grid-template-columns: 1fr;
  }
  .class-directory-list {
    display: flex;
    overflow: auto;
  }
  .class-directory-item {
    flex: 0 0 215px;
  }
  .class-directory .tw-section-head {
    padding-block: 18px 12px;
  }
  .class-directory-item {
    padding: 10px 12px;
  }
  .class-members .tw-table th,
  .class-members .tw-table td {
    padding-inline: 16px;
  }
}
</style>
