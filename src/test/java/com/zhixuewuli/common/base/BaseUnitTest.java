package com.zhixuewuli.common.base;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 基础单元测试类
 * 提供Mockito支持和单元测试标记
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
public abstract class BaseUnitTest {
    // 单元测试基类，提供Mockito支持
}