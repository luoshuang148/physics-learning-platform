package com.zhixuewuli.common.client.base;

import com.zhixuewuli.common.client.callback.AIStreamCallback;

/** Per-request splitter for inline model tags, including tags split across SSE chunks. */
public final class ThinkingContentSplitter {
    private static final String[] TAGS = {"<think>", "</think>", "<answer>", "</answer>"};
    private static final String[] THINKING_TAGS = {"</think>"};
    private final AIStreamCallback callback;
    private final StringBuilder pending = new StringBuilder();
    private boolean thinking;

    public ThinkingContentSplitter(AIStreamCallback callback) {
        this.callback = callback;
    }

    public void accept(String content, boolean isLast) {
        pending.append(content);
        while (!pending.isEmpty()) {
            String[] activeTags = thinking ? THINKING_TAGS : TAGS;
            String tag = null;
            int position = -1;
            for (String candidate : activeTags) {
                int found = pending.indexOf(candidate);
                if (found >= 0 && (position < 0 || found < position)) {
                    position = found;
                    tag = candidate;
                }
            }
            if (tag != null) {
                emit(pending.substring(0, position), false);
                pending.delete(0, position + tag.length());
                thinking = "<think>".equals(tag);
                continue;
            }

            // Keep only a possible partial tag; all other text can be streamed immediately.
            int retained = 0;
            for (String candidate : activeTags) {
                for (int length = 1; length < candidate.length() && length <= pending.length(); length++) {
                    if (pending.substring(pending.length() - length).equals(candidate.substring(0, length))) {
                        retained = Math.max(retained, length);
                    }
                }
            }
            int ready = pending.length() - retained;
            emit(pending.substring(0, ready), isLast && retained == 0);
            pending.delete(0, ready);
            return;
        }
    }

    public void finish() {
        emit(pending.toString(), true);
        pending.setLength(0);
    }

    private void emit(String value, boolean isLast) {
        if (value.isEmpty()) return;
        if (thinking) callback.onReasoningContent(value);
        else callback.onContent(value, isLast);
    }
}
