package com.zlt.aps.lh.engine.rule;

/**
 * 排程规则引擎接口
 * <p>将硬编码的业务规则抽取为可配置项</p>
 *
 * @author APS
 */
public interface IScheduleRuleEngine {

    // ======================== 换模相关规则 ========================

    /**
     * 获取每日换模上限
     *
     * @param factoryCode 工厂编码
     * @return 每日换模上限
     */
    int getDailyMouldChangeLimit(String factoryCode);

    /**
     * 获取早班换模上限
     *
     * @param factoryCode 工厂编码
     * @return 早班换模上限
     */
    int getMorningMouldChangeLimit(String factoryCode);

    /**
     * 获取中班换模上限
     *
     * @param factoryCode 工厂编码
     * @return 中班换模上限
     */
    int getAfternoonMouldChangeLimit(String factoryCode);

    /**
     * 获取换模预热时间（小时）
     *
     * @param factoryCode 工厂编码
     * @return 换模预热时间
     */
    int getMouldChangePreheatHours(String factoryCode);

    /**
     * 获取换模其他作业时间（小时）
     *
     * @param factoryCode 工厂编码
     * @return 换模其他作业时间
     */
    int getMouldChangeOtherHours(String factoryCode);

    /**
     * 获取换模总耗时（小时）
     *
     * @param factoryCode 工厂编码
     * @return 换模总耗时
     */
    int getMouldChangeTotalHours(String factoryCode);

    // ======================== 首检相关规则 ========================

    /**
     * 获取首检时间（小时）
     *
     * @param factoryCode 工厂编码
     * @return 首检时间
     */
    int getFirstInspectionHours(String factoryCode);

    /**
     * 获取每班最大首检次数
     *
     * @param factoryCode 工厂编码
     * @return 每班最大首检次数
     */
    int getMaxFirstInspectionPerShift(String factoryCode);

    // ======================== 产能相关规则 ========================

    /**
     * 获取班次时长（小时）
     *
     * @param factoryCode 工厂编码
     * @return 班次时长
     */
    int getShiftDurationHours(String factoryCode);

    /**
     * 获取班次效率因子
     * <p>不同班次的效率可能不同，如夜班效率可能较低</p>
     *
     * @param factoryCode 工厂编码
     * @param shiftType   班次类型（MORNING/AFTERNOON/NIGHT）
     * @return 效率因子（0.0-1.0）
     */
    double getShiftEfficiencyFactor(String factoryCode, String shiftType);

    // ======================== 时间窗口规则 ========================

    /**
     * 获取禁止换模开始小时
     *
     * @param factoryCode 工厂编码
     * @return 禁止换模开始小时
     */
    int getNoMouldChangeStartHour(String factoryCode);

    /**
     * 获取禁止换模结束小时
     *
     * @param factoryCode 工厂编码
     * @return 禁止换模结束小时
     */
    int getNoMouldChangeEndHour(String factoryCode);

    // ======================== 排程参数 ========================

    /**
     * 获取排程天数
     *
     * @param factoryCode 工厂编码
     * @return 排程天数
     */
    int getScheduleDays(String factoryCode);

    /**
     * 获取收尾判定天数
     *
     * @param factoryCode 工厂编码
     * @return 收尾判定天数
     */
    int getEndingDetectDays(String factoryCode);

    /**
     * 获取机台收尾时间容差（分钟）
     *
     * @param factoryCode 工厂编码
     * @return 时间容差
     */
    int getEndingTimeToleranceMinutes(String factoryCode);

    // ======================== 干冰清洗规则 ========================

    /**
     * 获取干冰清洗间隔天数
     *
     * @param factoryCode 工厂编码
     * @return 干冰清洗间隔天数
     */
    int getDryIceIntervalDays(String factoryCode);

    /**
     * 获取干冰清洗预警天数
     *
     * @param factoryCode 工厂编码
     * @return 干冰清洗预警天数
     */
    int getDryIceWarningDays(String factoryCode);

    /**
     * 获取干冰清洗提前天数
     *
     * @param factoryCode 工厂编码
     * @return 干冰清洗提前天数
     */
    int getDryIceAdvanceDays(String factoryCode);

    /**
     * 获取干冰清洗耗时（小时）
     *
     * @param factoryCode 工厂编码
     * @return 干冰清洗耗时
     */
    int getDryIceDurationHours(String factoryCode);

    /**
     * 获取干冰清洗损失数量
     *
     * @param factoryCode 工厂编码
     * @return 干冰清洗损失数量
     */
    int getDryIceLossQty(String factoryCode);

    /**
     * 获取每日干冰清洗上限
     *
     * @param factoryCode 工厂编码
     * @return 每日干冰清洗上限
     */
    int getDryIceDailyLimit(String factoryCode);

    // ======================== 喷砂清洗规则 ========================

    /**
     * 获取喷砂清洗耗时（小时）
     *
     * @param factoryCode 工厂编码
     * @return 喷砂清洗耗时
     */
    int getSandBlastDurationHours(String factoryCode);

    /**
     * 获取喷砂清洗含首检耗时（小时）
     *
     * @param factoryCode 工厂编码
     * @return 喷砂清洗含首检耗时
     */
    int getSandBlastWithInspectionHours(String factoryCode);

    /**
     * 获取每日喷砂清洗上限
     *
     * @param factoryCode 工厂编码
     * @return 每日喷砂清洗上限
     */
    int getSandBlastDailyLimit(String factoryCode);

    /**
     * 获取喷砂保养日 - 月中
     *
     * @param factoryCode 工厂编码
     * @return 喷砂保养日 - 月中
     */
    int getSandBlastMaintenanceDayMid(String factoryCode);

    /**
     * 获取喷砂保养日 - 月末
     *
     * @param factoryCode 工厂编码
     * @return 喷砂保养日 - 月末
     */
    int getSandBlastMaintenanceDayEnd(String factoryCode);

    // ======================== 设备保养规则 ========================

    /**
     * 获取保养耗时（小时）
     *
     * @param factoryCode 工厂编码
     * @return 保养耗时
     */
    int getMaintenanceDurationHours(String factoryCode);

    /**
     * 获取保养开始小时
     *
     * @param factoryCode 工厂编码
     * @return 保养开始小时
     */
    int getMaintenanceStartHour(String factoryCode);

    /**
     * 获取保养预警天数
     *
     * @param factoryCode 工厂编码
     * @return 保养预警天数
     */
    int getMaintenanceWarningDays(String factoryCode);

    /**
     * 获取胶囊预热时间（小时）
     *
     * @param factoryCode 工厂编码
     * @return 胶囊预热时间
     */
    double getCapsulePreheatHours(String factoryCode);

    // ======================== 停机超时阈值 ========================

    /**
     * 获取停机超时阈值（小时）
     *
     * @param factoryCode 工厂编码
     * @return 停机超时阈值
     */
    int getMachineStopTimeoutHours(String factoryCode);

    // ======================== 胶囊相关规则 ========================

    /**
     * 获取胶囊预警次数
     *
     * @param factoryCode 工厂编码
     * @return 胶囊预警次数
     */
    int getCapsuleWarningCount(String factoryCode);

    /**
     * 获取胶囊强制下机次数
     *
     * @param factoryCode 工厂编码
     * @return 胶囊强制下机次数
     */
    int getCapsuleForceDownCount(String factoryCode);

    /**
     * 获取胶囊更换损失数量
     *
     * @param factoryCode 工厂编码
     * @return 胶囊更换损失数量
     */
    int getCapsuleChangeLossQty(String factoryCode);

    // ======================== 开停产比例 ========================

    /**
     * 获取停产前第 3 天产能比例 (%)
     *
     * @param factoryCode 工厂编码
     * @return 产能比例
     */
    int getShutdownDayMinus3Rate(String factoryCode);

    /**
     * 获取停产前第 2 天产能比例 (%)
     *
     * @param factoryCode 工厂编码
     * @return 产能比例
     */
    int getShutdownDayMinus2Rate(String factoryCode);

    /**
     * 获取停产前第 1 天产能比例 (%)
     *
     * @param factoryCode 工厂编码
     * @return 产能比例
     */
    int getShutdownDayMinus1Rate(String factoryCode);

    /**
     * 获取开产首日产能比例 (%)
     *
     * @param factoryCode 工厂编码
     * @return 产能比例
     */
    int getStartupFirstDayRate(String factoryCode);

    // ======================== 试制量试规则 ========================

    /**
     * 获取试制量试每日上限
     *
     * @param factoryCode 工厂编码
     * @return 试制量试每日上限
     */
    int getTrialDailyLimit(String factoryCode);

    // ======================== 模具交替计划规则 ========================

    /**
     * 获取模具交替计划天数
     *
     * @param factoryCode 工厂编码
     * @return 模具交替计划天数
     */
    int getMouldChangePlanDays(String factoryCode);

    // ======================== 收尾判定规则 ========================

    /**
     * 获取结构收尾判定天数
     *
     * @param factoryCode 工厂编码
     * @return 结构收尾判定天数
     */
    int getStructureEndingDays(String factoryCode);
}
