import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { ClassStudentRow, ClassTeacherRow, TeachingClass } from '@/types/classroom';

export const getMyClassesApi = async (): Promise<TeachingClass[]> => {
  const { data } = await request.get<ApiResponse<TeachingClass[]>>('/teacher/classes');
  return data.data;
};
export const createClassApi = async (payload: { name: string; grade?: string }): Promise<TeachingClass> => {
  const { data } = await request.post<ApiResponse<TeachingClass>>('/teacher/classes', payload);
  return data.data;
};
export const getClassStudentsApi = async (classId: number): Promise<ClassStudentRow[]> => {
  const { data } = await request.get<ApiResponse<ClassStudentRow[]>>(`/teacher/classes/${classId}/students`);
  return data.data;
};
export const getAvailableStudentsApi = async (classId: number): Promise<ClassStudentRow[]> => {
  const { data } = await request.get<ApiResponse<ClassStudentRow[]>>(`/teacher/classes/${classId}/students/available`);
  return data.data;
};
export const addClassStudentApi = async (classId: number, userId: number): Promise<void> => {
  await request.post(`/teacher/classes/${classId}/students`, { userId });
};
export const removeClassStudentApi = async (classId: number, userId: number): Promise<void> => {
  await request.delete(`/teacher/classes/${classId}/students/${userId}`);
};
export const getClassTeachersApi = async (classId: number): Promise<ClassTeacherRow[]> => {
  const { data } = await request.get<ApiResponse<ClassTeacherRow[]>>(`/teacher/classes/${classId}/teachers`);
  return data.data;
};
export const getAvailableTeachersApi = async (classId: number): Promise<ClassTeacherRow[]> => {
  const { data } = await request.get<ApiResponse<ClassTeacherRow[]>>(`/teacher/classes/${classId}/teachers/available`);
  return data.data;
};
export const addClassTeacherApi = async (classId: number, userId: number): Promise<void> => {
  await request.post(`/teacher/classes/${classId}/teachers`, { userId });
};
export const removeClassTeacherApi = async (classId: number, userId: number): Promise<void> => {
  await request.delete(`/teacher/classes/${classId}/teachers/${userId}`);
};
