/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;

/**
 * 排产策略接口
 * <p>定义续作排产和新增排产的标准操作</p>
 *
 * @author APS
 */
public interface IProductionStrategy {

    /**
     * 换活字块排产(同胎胚同模具的SKU切换)
     *
     * @param context 排程上下文
     */
    void scheduleTypeBlockChange(LhScheduleContext context);

    /**
     * 续作收尾判定与排产
     *
     * @param context 排程上下文
     */
    void scheduleContinuousEnding(LhScheduleContext context);

    /**
     * 班次计划量分配
     *
     * @param context 排程上下文
     */
    void allocateShiftPlanQty(LhScheduleContext context);

    /**
     * 胎胚库存调整
     *
     * @param context 排程上下文
     */
    void adjustEmbryoStock(LhScheduleContext context);

    /**
     * 降模排产
     *
     * @param context 排程上下文
     */
    void scheduleReduceMould(LhScheduleContext context);

    /**
     * 新增规格排产(新增策略使用)
     *
     * @param context            排程上下文
     * @param machineMatch       机台匹配策略
     * @param mouldChangeBalance 换模均衡策略
     * @param inspectionBalance  首检均衡策略
     * @param capacityCalculate  产能计算策略
     */
    void scheduleNewSpecs(LhScheduleContext context,
                          IMachineMatchStrategy machineMatch,
                          IMouldChangeBalanceStrategy mouldChangeBalance,
                          IFirstInspectionBalanceStrategy inspectionBalance,
                          ICapacityCalculateStrategy capacityCalculate);
}
