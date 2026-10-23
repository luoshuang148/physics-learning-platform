package com.zhixuewuli.repository;
public interface KeywordKnowledgeRow {
    Long getChunkId(); Long getDocumentId(); Long getRevisionId(); Long getClassId(); Long getOrdinalNo();
    String getTitle(); Integer getPageNo(); String getSectionTitle(); String getChunkText(); Double getScore();
}
