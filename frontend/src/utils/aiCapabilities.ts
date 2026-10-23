export type AICapability = 'reports' | 'tutoring' | 'workflowReady';
/** Aliyun powers both reports and teacher question analysis. */
export const requiredAICapability = (endpoint: string): AICapability =>
  ['/ai/student/report', '/ai/teacher/report']
    .includes(endpoint.split('?')[0] ?? '') ? 'reports'
    : endpoint.startsWith('/ai/student/learning-plan/') ? 'workflowReady' : 'tutoring';
