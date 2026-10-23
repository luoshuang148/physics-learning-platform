export interface KnowledgeDocumentStatus {
  documentId: number;
  revisionId: number;
  classId: number;
  title: string;
  status: 'UPLOADED' | 'INDEXING' | 'READY' | 'FAILED' | 'WITHDRAWN' | string;
  errorCode: string | null;
}

export interface KnowledgePage {
  total: number;
  pages: number;
  currentPage: number;
  pageSize: number;
  records: KnowledgeDocumentStatus[];
}
