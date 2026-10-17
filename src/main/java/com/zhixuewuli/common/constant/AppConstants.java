package com.zhixuewuli.common.constant;

/**
 * 应用程序系统常量定义类
 * 
 * 包含整个智学物理系统中使用的绝对不变的常量定义，按功能模块进行分类组织。
 * 所有常量均采用public static final修饰符，确保线程安全和不可变性。
 * 
 * <p>
 * 常量分类说明：
 * </p>
 * <ul>
 * <li><strong>项目标识</strong>: 系统和团队的基本标识信息</li>
 * <li><strong>字符编码</strong>: 系统统一使用的字符编码格式</li>
 * <li><strong>数学/物理常数</strong>: 精确的科学计算常数</li>
 * <li><strong>系统保留关键字</strong>: 系统内部使用的保留字段</li>
 * <li><strong>正则表达式</strong>: 输入验证用的正则表达式模式</li>
 * </ul>
 * 
 * <p>
 * <strong>重要说明：</strong>
 * 本类仅包含绝对不变的系统常量，所有可能随业务需求变化的配置项
 * 已迁移至对应的配置类中，详见各properties配置类。
 * </p>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public final class AppConstants {

    // ==================== 项目标识 ====================
    /** 项目名称 - 系统标识，绝对不变 */
    public static final String PROJECT_NAME = "智学物理";

    /** 开发团队名称 - 组织标识，绝对不变 */
    public static final String TEAM_NAME = "智学物理开发团队";

    // ==================== 字符编码 ====================
    /** 默认字符编码格式 - 系统统一编码标准 */
    public static final String DEFAULT_ENCODING = "UTF-8";

    // ==================== 数学/物理常数 ====================
    /** 圆周率 - 精确的数学常数 */
    public static final double PI = Math.PI;

    /** 地球平均半径（千米） - 精确的物理常数 */
    public static final double EARTH_RADIUS_KM = 6371.0;

    // ==================== 系统保留关键字 ====================
    /** 默认排序字段 - 数据库查询的保留字段名 */
    public static final String DEFAULT_SORT_FIELD = "id";

    // ==================== 分页相关常量 ====================
    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 最大分页大小限制 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 最小分页大小 */
    public static final int MIN_PAGE_SIZE = 1;

    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUMBER = 1;

    /** 最小页码 */
    public static final int MIN_PAGE_NUMBER = 1;

    // ==================== 正则表达式 ====================
    /** 排序字段正则表达式 - 验证允许的排序字段 */
    public static final String REGEX_SORT_FIELD = "^(id|createdAt|updatedAt|title|duration)$";

    /** 会话ID正则表达式 - 验证会话标识格式 */
    public static final String REGEX_SESSION_ID = "^[a-zA-Z0-9_-]+$";

    /** 手机号码正则表达式 - 验证中国大陆手机号格式 */
    public static final String REGEX_PHONE_NUMBER = "^1[3-9]\\d{9}$";

    /** 邮箱地址正则表达式 - 验证邮箱地址格式 */
    public static final String REGEX_EMAIL = "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$";

    /**
     * 私有构造函数，防止外部实例化
     * 
     * 常量类不应该被实例化，通过断言确保不会意外创建实例。
     * 
     * @throws AssertionError 当尝试实例化该类时抛出
     */
    private AppConstants() {
        throw new AssertionError("Constant class cannot be instantiated");
    }
}
