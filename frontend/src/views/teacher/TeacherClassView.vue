<template>
  <AppShell :subtitle="`班级管理 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="class-page">
      <section class="class-list-panel">
        <div class="panel-head">
          <h2>班级</h2>
          <span class="count">{{ classroom.classes.length }}</span>
        </div>
        <button v-for="item in classroom.classes" :key="item.id" class="class-item"
          :class="{ active: classroom.activeClassId === item.id }" @click="selectClass(item.id)">
          <span class="class-name">{{ item.name }}</span>
          <span class="class-meta">{{ item.studentCount }} 学生 · {{ item.teacherCount }} 教师</span>
          <span class="role-badge">{{ item.role === 'OWNER' ? '负责' : '协作' }}</span>
        </button>
        <div v-if="!classroom.classes.length" class="empty">暂无班级</div>

        <form class="create-form" @submit.prevent="createClass">
          <input v-model="newName" placeholder="班级名称" maxlength="80" />
          <input v-model="newGrade" placeholder="年级" maxlength="30" />
          <button class="primary-btn" :disabled="creating || !newName.trim()">创建班级</button>
        </form>
      </section>

      <section class="class-detail-panel">
        <div v-if="!classroom.activeClass" class="empty large">请选择班级</div>
        <template v-else>
          <div class="detail-head">
            <div>
              <h2>{{ classroom.activeClass.name }}</h2>
              <span class="detail-meta">{{ classroom.activeClass.grade || '未填年级' }} · {{ classroom.activeClass.studentCount }} 学生 · {{ classroom.activeClass.teacherCount }} 教师</span>
            </div>
            <div class="tabs">
              <button :class="{ active: tab === 'students' }" @click="tab = 'students'">学生</button>
              <button :class="{ active: tab === 'teachers' }" @click="tab = 'teachers'">教师</button>
            </div>
          </div>

          <div v-if="tab === 'students'" class="member-section">
            <div class="add-row">
              <select v-model="studentToAdd">
                <option value="">选择学生</option>
                <option v-for="item in availableStudents" :key="item.userId" :value="item.userId">
                  {{ item.realName }} · {{ item.username }}
                </option>
              </select>
              <button class="ghost-btn" :disabled="!studentToAdd" @click="addStudent">加入班级</button>
            </div>
            <div class="member-list">
              <div v-for="item in classStudents" :key="item.userId" class="member-row">
                <span class="member-name">{{ item.realName }}</span>
                <span class="member-account">@{{ item.username }}</span>
                <span class="member-status" :class="{ off: !item.active }">{{ item.active ? '正常' : '停用' }}</span>
                <button class="text-danger" @click="removeStudent(item.userId)">移出</button>
              </div>
              <div v-if="!classStudents.length" class="empty">暂无学生</div>
            </div>
          </div>

          <div v-else class="member-section">
            <div class="add-row" v-if="classroom.activeClass.role === 'OWNER'">
              <select v-model="teacherToAdd">
                <option value="">选择教师</option>
                <option v-for="item in availableTeachers" :key="item.userId" :value="item.userId">
                  {{ item.realName }} · {{ item.username }}
                </option>
              </select>
              <button class="ghost-btn" :disabled="!teacherToAdd" @click="addTeacher">加入班级</button>
            </div>
            <div class="member-list">
              <div v-for="item in classTeachers" :key="item.userId" class="member-row">
                <span class="member-name">{{ item.realName }}</span>
                <span class="member-account">@{{ item.username }}</span>
                <span class="role-badge">{{ item.role === 'OWNER' ? '负责教师' : '协作教师' }}</span>
                <button v-if="classroom.activeClass.role === 'OWNER' && item.userId !== auth.user?.id" class="text-danger" @click="removeTeacher(item.userId)">移出</button>
              </div>
            </div>
          </div>
        </template>
      </section>
    </div>
    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import { useAuthStore } from '@/stores/auth';
import { useClassroomStore } from '@/stores/classroom';
import { teacherNavLinks } from '@/constants/navigation';
import { useToast } from '@/composables/useToast';
import {
  addClassStudentApi, addClassTeacherApi, createClassApi, getAvailableStudentsApi,
  getAvailableTeachersApi, getClassStudentsApi, getClassTeachersApi, removeClassStudentApi, removeClassTeacherApi,
} from '@/api/classroom';
import type { ClassStudentRow, ClassTeacherRow } from '@/types/classroom';

const auth = useAuthStore();
const classroom = useClassroomStore();
const links = teacherNavLinks;
const { visible, type, message, show } = useToast();
const tab = ref<'students' | 'teachers'>('students');
const newName = ref('');
const newGrade = ref('');
const creating = ref(false);
const classStudents = ref<ClassStudentRow[]>([]);
const classTeachers = ref<ClassTeacherRow[]>([]);
const availableStudents = ref<ClassStudentRow[]>([]);
const availableTeachers = ref<ClassTeacherRow[]>([]);
const studentToAdd = ref<number | ''>('');
const teacherToAdd = ref<number | ''>('');

const selectClass = (id: number) => classroom.setActiveClass(id);

const loadMembers = async () => {
  const id = classroom.activeClassId;
  if (!id) return;
  const [students, teachers, candidates, teacherCandidates] = await Promise.all([
    getClassStudentsApi(id), getClassTeachersApi(id), getAvailableStudentsApi(id), getAvailableTeachersApi(id),
  ]);
  classStudents.value = students; classTeachers.value = teachers;
  availableStudents.value = candidates; availableTeachers.value = teacherCandidates;
};

const createClass = async () => {
  creating.value = true;
  try {
    const created = await createClassApi({ name: newName.value.trim(), grade: newGrade.value.trim() || undefined });
    await classroom.loadClasses();
    classroom.setActiveClass(created.id);
    newName.value = ''; newGrade.value = '';
    await loadMembers();
    show('班级已创建', 'success');
  } catch (error: any) { show(error?.response?.data?.message ?? '创建班级失败', 'error'); }
  finally { creating.value = false; }
};

const addStudent = async () => {
  if (!classroom.activeClassId || !studentToAdd.value) return;
  try {
    await addClassStudentApi(classroom.activeClassId, Number(studentToAdd.value));
    studentToAdd.value = '';
    await classroom.loadClasses(); await loadMembers();
  } catch (error: any) { show(error?.response?.data?.message ?? '添加学生失败', 'error'); }
};

const removeStudent = async (userId: number) => {
  if (!classroom.activeClassId) return;
  try {
    await removeClassStudentApi(classroom.activeClassId, userId);
    await classroom.loadClasses(); await loadMembers();
  } catch (error: any) { show(error?.response?.data?.message ?? '移出学生失败', 'error'); }
};

const addTeacher = async () => {
  if (!classroom.activeClassId || !teacherToAdd.value) return;
  try {
    await addClassTeacherApi(classroom.activeClassId, Number(teacherToAdd.value));
    teacherToAdd.value = '';
    await classroom.loadClasses(); await loadMembers();
  } catch (error: any) { show(error?.response?.data?.message ?? '添加教师失败', 'error'); }
};

const removeTeacher = async (userId: number) => {
  if (!classroom.activeClassId) return;
  try {
    await removeClassTeacherApi(classroom.activeClassId, userId);
    await classroom.loadClasses(); await loadMembers();
  } catch (error: any) { show(error?.response?.data?.message ?? '移出教师失败', 'error'); }
};

watch(() => classroom.activeClassId, () => { loadMembers().catch(() => {}); });

onMounted(async () => {
  try {
    await classroom.loadClasses();
    await loadMembers();
  } catch (error: any) { show(error?.response?.data?.message ?? '班级加载失败', 'error'); }
});
</script>

<style scoped>
.class-page { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 16px; align-items: start; }
.class-list-panel, .class-detail-panel { padding: 16px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; }
.class-list-panel { display: grid; gap: 8px; }
.panel-head, .detail-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.panel-head h2, .detail-head h2 { margin: 0; color: #172033; font-size: 16px; }
.count { color: #94a3b8; font-size: 11px; }
.class-item { position: relative; display: grid; gap: 3px; padding: 10px; border: 1px solid #eef2f7; border-radius: 8px; background: #fff; text-align: left; cursor: pointer; }
.class-item.active { border-color: #a7f3d0; background: #f0fdf9; }
.class-name { color: #172033; font-size: 13px; font-weight: 600; }
.class-meta, .detail-meta { color: #64748b; font-size: 10px; }
.role-badge { justify-self: start; padding: 1px 6px; border-radius: 4px; background: #ecfdf5; color: #0f766e; font-size: 10px; }
.create-form { display: grid; gap: 6px; margin-top: 10px; padding-top: 12px; border-top: 1px solid #eef2f7; }
.create-form input, .add-row select { width: 100%; padding: 8px 10px; border: 1px solid #dbe3ec; border-radius: 6px; background: #fff; color: #172033; font: inherit; }
.primary-btn, .ghost-btn { padding: 8px 12px; border-radius: 6px; font: inherit; cursor: pointer; }
.primary-btn { border: 1px solid #0f766e; background: #0f766e; color: #fff; }
.ghost-btn { border: 1px solid #dbe3ec; background: #fff; color: #334155; }
.primary-btn:disabled, .ghost-btn:disabled { opacity: .5; cursor: not-allowed; }
.detail-head { margin-bottom: 14px; }
.detail-head > div:first-child { display: grid; gap: 4px; }
.tabs { display: flex; gap: 4px; }
.tabs button { padding: 5px 10px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; color: #64748b; font: inherit; font-size: 11px; cursor: pointer; }
.tabs button.active { border-color: #0f766e; color: #0f766e; background: #f0fdf9; }
.member-section { display: grid; gap: 10px; }
.add-row { display: flex; gap: 8px; }
.add-row select { flex: 1; }
.member-list { display: grid; }
.member-row { display: grid; grid-template-columns: 1fr 1fr 90px 60px; align-items: center; gap: 10px; padding: 9px 0; border-bottom: 1px solid #f1f5f9; font-size: 12px; }
.member-name { color: #172033; font-weight: 500; }
.member-account { color: #64748b; }
.member-status { color: #0f766e; font-size: 10px; }
.member-status.off { color: #b91c1c; }
.text-danger { border: 0; background: transparent; color: #b91c1c; font: inherit; font-size: 11px; cursor: pointer; }
.empty { padding: 12px 0; color: #94a3b8; font-size: 12px; }
.empty.large { padding: 60px 0; text-align: center; }
@media (max-width: 900px) { .class-page { grid-template-columns: 1fr; } .member-row { grid-template-columns: 1fr 1fr 80px; } .member-row .text-danger { grid-column: 1 / -1; justify-self: start; } }
</style>
