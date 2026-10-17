package com.zhixuewuli.common.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 流式响应测试工具类
 * 
 * <p>
 * 提供 SSE 响应解析、验证和等待完成的辅助方法
 * </p>
 * 
 * @author 智学物理开发团队
 * @since 2026
 */
public class SseTestUtils {

    /**
     * 提取SSE 响应中的所有事件数据
     * 
     * @param sseResponse SSE 响应字符串
     * @return 包含所有 data: 字段值的列表
     */
    public static List<String> extractSseEvents(String sseResponse) {
        if (sseResponse == null || sseResponse.isEmpty()) {
            return List.of();
        }

        List<String> dataList = new ArrayList<>();
        String[] events = sseResponse.split("\n\n");
        for (String eventBlock : events) {
            if (eventBlock == null || eventBlock.trim().isEmpty())
                continue;
            String[] lines = eventBlock.split("\n");
            StringBuilder data = null;
            for (String line : lines) {
                if (line.startsWith("data:")) {
                    if (data == null)
                        data = new StringBuilder();
                    else
                        data.append("\n");
                    data.append(line.substring(5).trim());
                }
            }
            if (data != null) {
                dataList.add(data.toString());
            }
        }
        return dataList;
    }

    /**
     * 验证SSE 响应包含特定内容
     * 
     * @param result           MockMvc 结果
     * @param expectedContents 期望包含的内容数组
     */
    public static void assertSseContains(MvcResult result, String... expectedContents) {
        try {
            String response = result.getResponse().getContentAsString();
            List<String> events = extractSseEvents(response);

            for (String expected : expectedContents) {
                assertThat(events).anyMatch(event -> event.contains(expected));
            }
        } catch (Exception e) {
            throw new RuntimeException("获取 SSE 响应内容失败", e);
        }
    }

    /**
     * 验证SSE 响应包含特定的事件数量
     * 
     * @param result        MockMvc 结果
     * @param expectedCount 期望的事件数量
     */
    public static void assertSseEventCount(MvcResult result, int expectedCount) {
        try {
            String response = result.getResponse().getContentAsString();
            List<String> events = extractSseEvents(response);
            assertThat(events).hasSize(expectedCount);
        } catch (Exception e) {
            throw new RuntimeException("获取 SSE 响应内容失败", e);
        }
    }

    /**
     * 等待 SSE连接完成（带超时）
     * 
     * @param emitter        SSE 发射器
     * @param timeoutSeconds 超时时间（秒）
     * @throws InterruptedException 中断异常
     */
    public static void awaitSseCompletion(SseEmitter emitter, long timeoutSeconds)
            throws InterruptedException {
        if (emitter == null) {
            return;
        }

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutSeconds * 1000) {
            // 通过尝试发送心跳来检测连接状态
            try {
                emitter.send(SseEmitter.event().name("heartbeat"));
                Thread.sleep(100);
            } catch (Exception e) {
                // 发送失败说明连接已关闭
                return;
            }
        }

        // 如果超时，认为连接已完成
    }

    /**
     * 检查 SSE 响应是否包含错误事件
     * 
     * @param sseResponse SSE 响应字符串
     * @return true 如果包含错误事件
     */
    public static boolean hasErrorEvent(String sseResponse) {
        if (sseResponse == null || sseResponse.isEmpty()) {
            return false;
        }

        return Arrays.stream(sseResponse.split("\n\n"))
                .anyMatch(event -> event.startsWith("event: error"));
    }

    /**
     * 验证SSE 响应格式正确
     * 
     * @param result MockMvc 结果
     */
    public static void assertValidSseFormat(MvcResult result) {
        try {
            String response = result.getResponse().getContentAsString();
            assertThat(response).isNotBlank();
            // 验证至少包含一个 data: 前缀
            assertThat(response).contains("data:");
        } catch (Exception e) {
            throw new RuntimeException("获取 SSE 响应内容失败", e);
        }
    }

    /**
     * 获取最后一个 SSE 事件的内容
     * 
     * @param result MockMvc 结果
     * @return 最后一个事件的 data 内容
     */
    public static String getLastSseEvent(MvcResult result) {
        try {
            String response = result.getResponse().getContentAsString();
            List<String> events = extractSseEvents(response);
            assertThat(events).isNotEmpty();
            return events.get(events.size() - 1);
        } catch (Exception e) {
            throw new RuntimeException("获取 SSE 响应内容失败", e);
        }
    }
}
