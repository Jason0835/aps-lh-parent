package com.zlt.aps.lh.api.constant;

/**
 * 硫化排程参数编码常量
 *
 * @author APS
 */
public final class LhScheduleParamConstant {

    private LhScheduleParamConstant() {
    }

    /** 夜班开始小时 */
    public static final String NIGHT_START_HOUR = "NIGHT_START_HOUR";
    /** 早班开始小时 */
    public static final String MORNING_START_HOUR = "MORNING_START_HOUR";
    /** 中班开始小时 */
    public static final String AFTERNOON_START_HOUR = "AFTERNOON_START_HOUR";
    /** 每班时长（小时） */
    public static final String SHIFT_DURATION_HOURS = "SHIFT_DURATION_HOURS";
    /** 禁止换模开始小时 */
    public static final String NO_MOULD_CHANGE_START_HOUR = "NO_MOULD_CHANGE_START_HOUR";
    /** 禁止换模结束小时 */
    public static final String NO_MOULD_CHANGE_END_HOUR = "NO_MOULD_CHANGE_END_HOUR";
    /** 每日换模总上限 */
    public static final String DAILY_MOULD_CHANGE_LIMIT = "DAILY_MOULD_CHANGE_LIMIT";
    /** 早班换模上限 */
    public static final String MORNING_MOULD_CHANGE_LIMIT = "MORNING_MOULD_CHANGE_LIMIT";
    /** 中班换模上限 */
    public static final String AFTERNOON_MOULD_CHANGE_LIMIT = "AFTERNOON_MOULD_CHANGE_LIMIT";
    /** 夜班换模上限 */
    public static final String NIGHT_MOULD_CHANGE_LIMIT = "NIGHT_MOULD_CHANGE_LIMIT";
    /** 换模预热时间（小时） */
    public static final String MOULD_CHANGE_PREHEAT_HOURS = "MOULD_CHANGE_PREHEAT_HOURS";
    /** 换模其他作业时间（小时） */
    public static final String MOULD_CHANGE_OTHER_HOURS = "MOULD_CHANGE_OTHER_HOURS";
    /** 换模总耗时（小时） */
    public static final String MOULD_CHANGE_TOTAL_HOURS = "MOULD_CHANGE_TOTAL_HOURS";
    /** 换活字块总耗时（小时） */
    public static final String TYPE_BLOCK_CHANGE_TOTAL_HOURS = "TYPE_BLOCK_CHANGE_TOTAL_HOURS";
    /** 首检时间（小时） */
    public static final String FIRST_INSPECTION_HOURS = "FIRST_INSPECTION_HOURS";
    /** 每班最大首检次数 */
    public static final String MAX_FIRST_INSPECTION_PER_SHIFT = "MAX_FIRST_INSPECTION_PER_SHIFT";
    /** 收尾判定天数 */
    public static final String ENDING_DETECT_DAYS = "ENDING_DETECT_DAYS";
    /** 结构收尾判定天数 */
    public static final String STRUCTURE_ENDING_DAYS = "STRUCTURE_ENDING_DAYS";
    /** 机台收尾时间容差（分钟） */
    public static final String ENDING_TIME_TOLERANCE_MINUTES = "ENDING_TIME_TOLERANCE_MINUTES";
    /** 干冰清洗间隔天数 */
    public static final String DRY_ICE_INTERVAL_DAYS = "DRY_ICE_INTERVAL_DAYS";
    /** 干冰清洗预警天数 */
    public static final String DRY_ICE_WARNING_DAYS = "DRY_ICE_WARNING_DAYS";
    /** 干冰清洗提前天数 */
    public static final String DRY_ICE_ADVANCE_DAYS = "DRY_ICE_ADVANCE_DAYS";
    /** 干冰清洗耗时（小时） */
    public static final String DRY_ICE_DURATION_HOURS = "DRY_ICE_DURATION_HOURS";
    /** 干冰清洗损失数量 */
    public static final String DRY_ICE_LOSS_QTY = "DRY_ICE_LOSS_QTY";
    /** 每日干冰清洗上限 */
    public static final String DRY_ICE_DAILY_LIMIT = "DRY_ICE_DAILY_LIMIT";
    /** 喷砂清洗耗时（小时） */
    public static final String SAND_BLAST_DURATION_HOURS = "SAND_BLAST_DURATION_HOURS";
    /** 喷砂清洗含首检耗时（小时） */
    public static final String SAND_BLAST_WITH_INSPECTION_HOURS = "SAND_BLAST_WITH_INSPECTION_HOURS";
    /** 每日喷砂清洗上限 */
    public static final String SAND_BLAST_DAILY_LIMIT = "SAND_BLAST_DAILY_LIMIT";
    /** 喷砂保养月中日期 */
    public static final String SAND_BLAST_MAINTENANCE_DAY_MID = "SAND_BLAST_MAINTENANCE_DAY_MID";
    /** 喷砂保养月末日期 */
    public static final String SAND_BLAST_MAINTENANCE_DAY_END = "SAND_BLAST_MAINTENANCE_DAY_END";
    /** 保养耗时（小时） */
    public static final String MAINTENANCE_DURATION_HOURS = "MAINTENANCE_DURATION_HOURS";
    /** 保养开始小时 */
    public static final String MAINTENANCE_START_HOUR = "MAINTENANCE_START_HOUR";
    /** 保养预警天数 */
    public static final String MAINTENANCE_WARNING_DAYS = "MAINTENANCE_WARNING_DAYS";
    /** 胶囊预热时间（小时） */
    public static final String CAPSULE_PREHEAT_HOURS = "CAPSULE_PREHEAT_HOURS";
    /** 排程天数 */
    public static final String SCHEDULE_DAYS = "SCHEDULE_DAYS";
    /** 是否按产能满排 */
    public static final String ENABLE_FULL_CAPACITY_SCHEDULING = "ENABLE_FULL_CAPACITY_SCHEDULING";
    /** 满排模式是否按余量命中收尾规则2 */
    public static final String ENABLE_ENDING_BY_SURPLUS_IN_FULL_MODE = "ENABLE_ENDING_BY_SURPLUS_IN_FULL_MODE";
    /** 是否强制重排（0-否，1-是） */
    public static final String FORCE_RESCHEDULE = "FORCE_RESCHEDULE";
    /** MES在机信息往前追溯天数 */
    public static final String MACHINE_ONLINE_LOOKBACK_DAYS = "MACHINE_ONLINE_LOOKBACK_DAYS";
    /** 停机超时阈值（小时） */
    public static final String MACHINE_STOP_TIMEOUT_HOURS = "MACHINE_STOP_TIMEOUT_HOURS";
    /** 胶囊预警次数 */
    public static final String CAPSULE_WARNING_COUNT = "CAPSULE_WARNING_COUNT";
    /** 胶囊强制下机次数 */
    public static final String CAPSULE_FORCE_DOWN_COUNT = "CAPSULE_FORCE_DOWN_COUNT";
    /** 胶囊更换损失数量 */
    public static final String CAPSULE_CHANGE_LOSS_QTY = "CAPSULE_CHANGE_LOSS_QTY";
    /** 停产前第3天产能比例 */
    public static final String SHUTDOWN_DAY_MINUS_3_RATE = "SHUTDOWN_DAY_MINUS_3_RATE";
    /** 停产前第2天产能比例 */
    public static final String SHUTDOWN_DAY_MINUS_2_RATE = "SHUTDOWN_DAY_MINUS_2_RATE";
    /** 停产前第1天产能比例 */
    public static final String SHUTDOWN_DAY_MINUS_1_RATE = "SHUTDOWN_DAY_MINUS_1_RATE";
    /** 开产首日产能比例 */
    public static final String STARTUP_FIRST_DAY_RATE = "STARTUP_FIRST_DAY_RATE";
    /** 试制量试每日上限 */
    public static final String TRIAL_DAILY_LIMIT = "TRIAL_DAILY_LIMIT";
    /** 模具交替计划天数 */
    public static final String MOULD_CHANGE_PLAN_DAYS = "MOULD_CHANGE_PLAN_DAYS";
    /** 局部搜索开关（0-关闭，1-开启） */
    public static final String ENABLE_LOCAL_SEARCH = "ENABLE_LOCAL_SEARCH";
    /** 局部搜索候选机台阈值（小于该值时启用） */
    public static final String LOCAL_SEARCH_MACHINE_THRESHOLD = "LOCAL_SEARCH_MACHINE_THRESHOLD";
    /** 局部搜索深度（包含当前SKU） */
    public static final String LOCAL_SEARCH_DEPTH = "LOCAL_SEARCH_DEPTH";
    /** 局部搜索单次耗时预算（毫秒） */
    public static final String LOCAL_SEARCH_TIME_BUDGET_MS = "LOCAL_SEARCH_TIME_BUDGET_MS";
    /** 优先级跟踪日志开关（0-关闭，1-开启） */
    public static final String ENABLE_PRIORITY_TRACE_LOG = "ENABLE_PRIORITY_TRACE_LOG";
}
