export type TeachingClassStatus = 'ACTIVE' | 'INACTIVE';
export interface TeachingClass {
  id: number;
  name: string;
  grade: string | null;
  status: TeachingClassStatus;
  studentCount: number;
  teacherCount: number;
  role: 'OWNER' | 'CO_TEACHER' | null;
}
export interface ClassStudentRow {
  userId: number;
  username: string;
  realName: string;
  className: string | null;
  active: boolean;
}
export interface ClassTeacherRow {
  userId: number;
  username: string;
  realName: string;
  role: 'OWNER' | 'CO_TEACHER';
}
