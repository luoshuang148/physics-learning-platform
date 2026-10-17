export interface VideoUploadResponse {
  url: string;
  originalFilename: string;
  fileSize: number;
  duration?: number;
}

export interface VideoCreatePayload {
  title: string;
  url: string;
  knowledgePoint?: string;
  coverUrl?: string;
  duration?: number;
  description?: string;
}

export interface VideoUpdatePayload extends VideoCreatePayload {
  id?: number;
}

export interface VideoResponse {
  id: number;
  title: string;
  url: string;
  knowledgePoint: string | null;
  coverUrl: string | null;
  duration: number | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

