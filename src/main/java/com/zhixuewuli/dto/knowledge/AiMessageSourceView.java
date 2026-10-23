package com.zhixuewuli.dto.knowledge;
public record AiMessageSourceView(String citationId,Long documentId,Long revisionId,String title,Integer page,
                                  String section,String text,boolean available) {}
