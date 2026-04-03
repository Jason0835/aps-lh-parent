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
}
