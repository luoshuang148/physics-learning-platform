-- V20: 下线班级资料库（文档 RAG）
-- 题库 RAG（question_search_units / question_index_jobs / question_index_publications）保留。
-- 历史 AI 来源记录保留在 ai_message_sources，但不再回源文档内容。

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS knowledge_chunks;
DROP TABLE IF EXISTS knowledge_revisions;
DROP TABLE IF EXISTS knowledge_documents;
SET FOREIGN_KEY_CHECKS = 1;
