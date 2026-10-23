package com.zhixuewuli.service.knowledge;
import com.zhixuewuli.common.exception.BusinessException;
public class KnowledgeExtractionException extends BusinessException {
    public enum Reason { OCR_REQUIRED, ENCRYPTED_PDF, TOO_MANY_PAGES, INPUT_TOO_LARGE, INVALID_DOCUMENT, FORMULA_TOO_LONG }
    private final Reason reason;
    public KnowledgeExtractionException(Reason reason){super("资料提取失败："+reason.name());this.reason=reason;}
    public Reason reason(){return reason;}
}
