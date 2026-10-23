package com.zhixuewuli.service.knowledge;
import java.util.List;
public record CitationValidationResult(String text,List<String> validSourceIds) { public CitationValidationResult { validSourceIds=List.copyOf(validSourceIds); } }
