package com.zhixuewuli.common.util;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.dictionary.CustomDictionary;
import com.zhixuewuli.common.exception.questionErrorAnalysis.SegmentationException;
import com.zhixuewuli.common.exception.questionErrorAnalysis.WordCloudAnalysisException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * HanLP 词云分析工具类
 * 
 * 基于 HanLP 中文分词技术，对错误原因文本进行分词和词频统计，
 * 生成用于词云可视化的数据。针对高中物理实验（原子物理、万有引力与宇宙航天）进行了优化。
 * 
 * <p>
 * 主要功能：
 * </p>
 * <ul>
 * <li>中文文本分词处理</li>
 * <li>高中物理实验停用词过滤</li>
 * <li>专业术语保护（自定义词典）</li>
 * <li>词频统计分析</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.2.0
 * @since 2026
 * @see com.hankcs.hanlp.HanLP HanLP 分词器
 */
@Slf4j
public class HanLPWordCloudUtil {

    // 物理学科通用停用词（扩充了实验分析中常见干扰词）
    private static final Set<String> PHYSICS_STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "那", "里", "就是", "时候", "还是", "因为", "所以", "但是", "然后",
            "可以", "能够", "应该", "可能", "需要", "必须", "已经", "正在", "开始", "结束",
            "如图", "所示", "图中", "根据", "可得", "解得", "代入", "则", "若", "即", "故",
            "因此", "由于", "并且", "或者", "其中", "可知", "忽略", "取", "得",
            "问题", "分析", "解答", "过程", "步骤", "思路", "方法", "技巧", "注意", "关键"));

    // 原子物理与宇宙航天专业术语词典（高中物理实验相关）
    private static final Set<String> PHYSICS_EXPERIMENT_TERMS = new HashSet<>(Arrays.asList(
            // ========== 原子物理相关 ==========
            "卢瑟福散射", "α粒子散射", "α粒子散射实验", "金箔", "金箔实验", "原子核", "核式结构", "核式结构模型",
            "玻尔模型", "玻尔能级", "能级结构", "能级跃迁", "能级跃迁图", "能级差", "基态", "激发态",
            "电子云", "概率密度", "电子轨道", "量子化", "能级图", "光谱", "氢原子", "原子光谱", "谱线",
            "光电效应", "光子", "光电子", "光子能量", "光子频率", "逸出功", "截止频率", "截止电压",
            "遏止电压", "爱因斯坦方程", "爱因斯坦光电效应方程", "光电流", "饱和光电流",
            "粒子性", "波动性", "波粒二象性", "康普顿效应", "德布罗意波", "德布罗意波长", "物质波",
            "干涉", "衍射", "电子显微镜", "电子束", "阴极射线", "汤姆孙实验", "油滴实验", "密立根实验",
            "质子", "中子", "核子", "结合能", "质量亏损",
            
            // ========== 万有引力与宇宙航天 ==========
            "开普勒定律", "开普勒第一定律", "开普勒第二定律", "开普勒第三定律", "椭圆轨道",
            "焦点", "近日点", "远日点", "面积速度", "周期平方", "半长轴", "轨道半径", "轨道半长轴",
            "轨道偏心率", "轨道倾角", "轨道机动", "轨道交会", "对接", "太空行走",
            "万有引力", "引力常量", "引力势能", "引力场", "中心天体", "环绕天体",
            "人造卫星", "地球同步卫星", "近地卫星", "静止轨道", "同步轨道", "极地轨道",
            "变轨", "加速", "减速", "椭圆轨道", "圆轨道", "近地点", "远地点", "轨道速度",
            "宇宙速度", "第一宇宙速度", "第二宇宙速度", "第三宇宙速度", "逃逸速度",
            "卫星发射", "轨道转移", "轨道维持", "航天器", "宇宙飞船", "空间站",
            "引力弹弓效应", "拉格朗日点",
            
            // ========== 相对论与前沿科普 ==========
            "相对论", "狭义相对论", "光速不变", "时间膨胀", "长度收缩", "质能方程",
            "相对论效应", "高速运动", "引力红移", "时空弯曲", "黑洞", "引力波",
            "近光速", "多普勒效应", "红移", "蓝移",
            
            // ========== 实验相关术语 ==========
            "实验装置", "实验原理", "实验步骤", "实验现象", "实验结果", "数据分析",
            "误差分析", "系统误差", "偶然误差", "减小误差", "控制变量", "对比实验",
            "真空管", "荧光屏", "荧光屏闪烁", "显微镜", "显微镜观测", "计数器", "探测器", "云室", "盖革计数器",
            
            // ========== 物理量与单位 ==========
            "电荷量", "电子电荷", "质量数", "质子数", "中子数", "核电荷数",
            "轨道半径", "轨道角动量", "量子数", "主量子数", "角量子数",
            "引力常数", "天体质量", "轨道周期", "轨道高度", "线速度", "角速度",
            "向心加速度", "向心力", "离心力"
    ));
    
    // 保留单字词（需要在 shouldIncludeWord 中特殊处理）
    private static final Set<String> SINGLE_CHAR_TERMS = new HashSet<>(Arrays.asList(
            "α", "β", "γ", "核", "电", "光", "子", "能", "轨", "速", "力", "质", "量"));

    static {
        // 将专业术语动态添加到 HanLP 自定义词典，确保分词时能识别为整体
        for (String term : PHYSICS_EXPERIMENT_TERMS) {
            CustomDictionary.add(term);
        }
        log.info("HanLP 自定义词典已加载 {} 个高中物理实验专业术语", PHYSICS_EXPERIMENT_TERMS.size());
    }

    /**
     * 对错误原因文本进行HanLP分词并统计词频
     * 
     * @param errorReasons 错误原因文本列表
     * @return 词频统计Map，key为词汇，value为出现次数
     * @throws WordCloudAnalysisException 分词处理异常时抛出
     */
    public static Map<String, Integer> analyzeErrorReasons(List<String> errorReasons) {
        if (errorReasons == null || errorReasons.isEmpty()) {
            log.debug("错误原因列表为空，返回空的词频统计");
            return new HashMap<>();
        }

        log.info("开始分析{}条错误原因文本", errorReasons.size());

        Map<String, Integer> wordFrequency = new HashMap<>();

        for (int i = 0; i < errorReasons.size(); i++) {
            String errorReason = errorReasons.get(i);
            try {
                if (StringUtils.isBlank(errorReason)) {
                    continue;
                }

                List<String> words = segmentText(errorReason.trim());
                for (String word : words) {
                    // 过滤停用词和无关词
                    if (shouldIncludeWord(word)) {
                        wordFrequency.merge(word, 1, Integer::sum);
                    }
                }
            } catch (Exception e) {
                log.warn("处理第{}条错误原因文本时出现异常: {}", i + 1, errorReason, e);
                // 局部异常不影响整体处理，记录日志后继续
            }
        }

        log.info("词频分析完成，共统计到{}个有效词汇", wordFrequency.size());
        return wordFrequency;
    }

    /**
     * 单个文本分词处理
     * 
     * @param text 待分词文本
     * @return 分词结果列表
     * @throws SegmentationException 分词异常时抛出
     */
    public static List<String> segmentText(String text) {
        if (StringUtils.isBlank(text)) {
            return new ArrayList<>();
        }

        try {
            log.debug("对文本进行分词处理: {}", text);

            // 使用HanLP标准分词器（已自动加载自定义词典）
            List<Term> terms = HanLP.segment(text);
            List<String> words = terms.stream()
                    .map(term -> term.word)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());

            log.debug("分词结果: {}", words);
            return words;
        } catch (Exception e) {
            log.error("文本分词处理失败: {}", text, e);
            throw new SegmentationException(text, e);
        }
    }

    /**
     * 判断词汇是否应该包含在词频统计中
     * 
     * @param word 待判断的词汇
     * @return true 表示应该包含，false 表示应该过滤
     */
    private static boolean shouldIncludeWord(String word) {
        // 专业术语无条件保留
        if (PHYSICS_EXPERIMENT_TERMS.contains(word)) {
            return true;
        }
    
        // 特殊单字符术语保留（如α粒子中的α）
        if (SINGLE_CHAR_TERMS.contains(word)) {
            return true;
        }
    
        // 长度过滤：排除单字符（除非是上述特殊单字符）
        if (word.length() == 1) {
            return false;
        }
    
        // 停用词过滤
        if (PHYSICS_STOP_WORDS.contains(word)) {
            return false;
        }
    
        // 数字和纯符号过滤
        if (word.matches("[0-9]+") || word.matches("[\\p{Punct}\\s]+")) {
            return false;
        }
    
        // 过滤过短的词汇（少于 2 个字符，再次确保）
        if (word.length() < 2) {
            return false;
        }
    
        return true;
    }

    /**
     * 获取最常见的前N个词汇
     * 
     * @param wordFrequency 词频统计Map
     * @param topN          返回前N个词汇
     * @return 按频率排序的前N个词汇Map
     */
    public static Map<String, Integer> getTopWords(Map<String, Integer> wordFrequency, int topN) {
        if (wordFrequency == null || wordFrequency.isEmpty() || topN <= 0) {
            return new HashMap<>();
        }

        return wordFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    /**
     * 清理和标准化文本
     * 
     * @param text 原始文本
     * @return 清理后的文本
     */
    public static String cleanText(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }

        // 移除多余的空白字符
        String cleaned = text.replaceAll("\\s+", " ").trim();

        // 移除常见的标点符号干扰（保留可能的关键字符，如角度符号°等）
        cleaned = cleaned.replaceAll("[\\.．。,，;；:：!！?？、]", "");

        return cleaned;
    }
}