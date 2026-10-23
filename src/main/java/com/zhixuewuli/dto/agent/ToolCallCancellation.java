package com.zhixuewuli.dto.agent;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface ToolCallCancellation {
    boolean isCancelled();
    void onCancel(Runnable action);

    static ToolCallCancellation from(BooleanSupplier cancelled, Consumer<Runnable> registration) {
        return new ToolCallCancellation() {
            @Override public boolean isCancelled() { return cancelled.getAsBoolean(); }
            @Override public void onCancel(Runnable action) { registration.accept(action); }
        };
    }

    static ToolCallCancellation none() {
        return from(() -> false, ignored -> {});
    }

    default void throwIfCancelled() {
        if (isCancelled()) throw new CancellationException("工具模型调用已取消");
    }
}
