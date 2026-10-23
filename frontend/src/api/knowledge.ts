import request from '@/utils/request';
import type {ApiResponse} from '@/types/common';
import type {KnowledgeDocumentStatus, KnowledgePage} from '@/types/knowledge';

export const listKnowledgeDocumentsApi = async (classId: number) => {
  const {data} = await request.get<ApiResponse<KnowledgePage>>('/knowledge/documents',{params:{classId,page:1,size:100}});
  return data.data.records;
};
export const uploadKnowledgeDocumentApi = async (classId: number,title: string,file: File) => {
  const form=new FormData();form.append('classId',String(classId));form.append('title',title);form.append('file',file);
  const {data}=await request.post<ApiResponse<KnowledgeDocumentStatus>>('/knowledge/documents',form);
  return data.data;
};
export const withdrawKnowledgeDocumentApi = (documentId: number) => request.delete(`/knowledge/documents/${documentId}`);
export const getKnowledgeChunkApi = async (chunkId: number) => {
  const {data}=await request.get<ApiResponse<import('@/types/ai').AISource>>(`/knowledge/chunks/${chunkId}`);
  return data.data;
};
