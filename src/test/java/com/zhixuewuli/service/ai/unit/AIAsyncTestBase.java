package com.zhixuewuli.service.ai.unit;

import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AI服务异步测试基类
 * 
 * 基于AIServiceSyncTestBase扩展，专门用于测试AI服务的异步流程。
 * 复用所有同步测试的Mock对象，只新增异步执行器相关的Mock和验证方法。
 * 
 * <p>
 * 核心职责：
 * </p>
 * <ul>
 * <li>继承同步测试基类的所有Mock对象和测试数据</li>
 * <li>提供异步任务执行器Mock</li>
 * <li>提供异步任务捕获和同步执行的核心方法</li>
 * <li>验证异步任务的提交和执行</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Tag("ai-async")
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class AIAsyncTestBase extends AIServiceSyncTestBase {

    // ========== 异步测试专用Mock对象 ==========

    @Mock
    protected Executor aiTaskExecutor;

    // ========== 异步任务捕获器 ==========

    @Captor
    protected ArgumentCaptor<Runnable> runnableCaptor;

    // ========== 核心异步测试方法 ==========

    /**
     * 异步测试核心方法：捕获并同步执行异步任务
     * 
     * 解决"异步任务是否成功执行"的验证问题。
     * 通过捕获提交给aiTaskExecutor的Runnable任务，并在当前线程同步执行，
     * 使得可以验证异步任务内部的业务逻辑调用。
     * 
     * @param taskStarter 触发异步任务提交的回调函数
     */
    protected void captureAndExecuteAsyncTask(Runnable taskStarter) {
        // 1. 配置Mock：捕获提交的Runnable
        doNothing().when(aiTaskExecutor).execute(runnableCaptor.capture());

        // 2. 触发异步任务提交（调用被测试方法）
        taskStarter.run();

        // 3. 同步执行捕获的异步任务
        Runnable capturedTask = runnableCaptor.getValue();
        System.out.println("DEBUG: Captured task is " + (capturedTask == null ? "null" : "not null"));
        if (capturedTask != null) {
            System.out.println("DEBUG: Running captured task...");
            try {
                capturedTask.run(); // 关键：同步执行！
                System.out.println("DEBUG: Captured task completed successfully");
            } catch (Exception e) {
                System.out.println("DEBUG: Captured task threw exception: " + e);
                e.printStackTrace();
                throw e;
            }
        }
    }

    /**
     * 简化验证：只验证任务提交（适用于简单场景）
     * 
     * 当只需要确认异步任务被正确提交，而不需要验证任务内部逻辑时使用。
     */
    protected void verifyAsyncTaskSubmitted() {
        verify(aiTaskExecutor).execute(any(Runnable.class));
    }

    /**
     * 获取捕获的异步任务（用于自定义验证）
     * 
     * 当需要对捕获的异步任务进行更复杂的验证时使用。
     * 
     * @param taskStarter 触发异步任务提交的回调函数
     * @return 捕获的Runnable任务
     */
    protected Runnable getCapturedAsyncTask(Runnable taskStarter) {
        doNothing().when(aiTaskExecutor).execute(runnableCaptor.capture());
        taskStarter.run();
        return runnableCaptor.getValue();
    }

    /**
     * 验证异步任务未被提交
     * 
     * 用于测试异常情况下的验证。
     */
    protected void verifyAsyncTaskNotSubmitted() {
        verify(aiTaskExecutor, never()).execute(any(Runnable.class));
    }
}