package com.zhixuewuli.common.util.test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射测试工具类
 * 
 * 提供便捷的反射操作方法，用于测试私有方法和字段。
 * 主要用于测试复杂的内部逻辑和私有方法。
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>测试私有方法的逻辑正确性</li>
 * <li>验证私有字段的状态</li>
 * <li>测试复杂的内部计算逻辑</li>
 * <li>绕过访问修饰符限制进行测试</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public class ReflectionTestUtil {

    /**
     * 获取私有字段的值
     * 
     * @param target    目标对象
     * @param fieldName 字段名
     * @return 字段值
     * @throws RuntimeException 当反射操作失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static <T> T getPrivateFieldValue(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("无法获取私有字段值: " + fieldName, e);
        }
    }

    /**
     * 设置私有字段的值
     * 
     * @param target    目标对象
     * @param fieldName 字段名
     * @param value     要设置的值
     * @throws RuntimeException 当反射操作失败时抛出
     */
    public static void setPrivateFieldValue(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("无法设置私有字段值: " + fieldName, e);
        }
    }

    /**
     * 调用私有方法
     * 
     * @param target     目标对象
     * @param methodName 方法名
     * @param paramTypes 参数类型数组
     * @param args       参数值数组
     * @return 方法返回值
     * @throws RuntimeException 当反射操作失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokePrivateMethod(Object target, String methodName,
            Class<?>[] paramTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return (T) method.invoke(target, args);
        } catch (Exception e) {
            throw new RuntimeException("无法调用私有方法: " + methodName, e);
        }
    }

    /**
     * 调用无参私有方法
     * 
     * @param target     目标对象
     * @param methodName 方法名
     * @return 方法返回值
     * @throws RuntimeException 当反射操作失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokePrivateMethod(Object target, String methodName) {
        return invokePrivateMethod(target, methodName, new Class<?>[0]);
    }

    /**
     * 获取类的私有静态字段值
     * 
     * @param clazz     目标类
     * @param fieldName 字段名
     * @return 字段值
     * @throws RuntimeException 当反射操作失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static <T> T getPrivateStaticFieldValue(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("无法获取私有静态字段值: " + fieldName, e);
        }
    }

    /**
     * 设置类的私有静态字段值
     * 
     * @param clazz     目标类
     * @param fieldName 字段名
     * @param value     要设置的值
     * @throws RuntimeException 当反射操作失败时抛出
     */
    public static void setPrivateStaticFieldValue(Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Exception e) {
            throw new RuntimeException("无法设置私有静态字段值: " + fieldName, e);
        }
    }

    /**
     * 调用私有静态方法
     * 
     * @param clazz      目标类
     * @param methodName 方法名
     * @param paramTypes 参数类型数组
     * @param args       参数值数组
     * @return 方法返回值
     * @throws RuntimeException 当反射操作失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokePrivateStaticMethod(Class<?> clazz, String methodName,
            Class<?>[] paramTypes, Object... args) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return (T) method.invoke(null, args);
        } catch (Exception e) {
            throw new RuntimeException("无法调用私有静态方法: " + methodName, e);
        }
    }

    /**
     * 创建类的实例（包括私有构造函数）
     * 
     * @param clazz      目标类
     * @param paramTypes 构造函数参数类型数组
     * @param args       构造函数参数值数组
     * @return 类实例
     * @throws RuntimeException 当反射操作失败时抛出
     */
    public static <T> T createInstance(Class<T> clazz, Class<?>[] paramTypes, Object... args) {
        try {
            var constructor = clazz.getDeclaredConstructor(paramTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("无法创建类实例: " + clazz.getSimpleName(), e);
        }
    }

    /**
     * 创建无参类实例
     * 
     * @param clazz 目标类
     * @return 类实例
     * @throws RuntimeException 当反射操作失败时抛出
     */
    public static <T> T createInstance(Class<T> clazz) {
        return createInstance(clazz, new Class<?>[0]);
    }
}