/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;

import java.util.Date;

/**
 * 换模均衡策略接口
 * <p>控制每日换模总数和早/中班换模均衡</p>
 *
 * @author APS
 */
public interface IMouldChangeBalanceStrategy {

    /**
     * 检查指定日期的换模能力是否充足
     * <p>约束: 每日最多15台, 早班最多8台, 中班最多7台, 夜班不换模</p>
     *
     * @param context    排程上下文
     * @param targetDate 目标日期
     * @return true-有换模能力, false-已满
     */
    boolean hasCapacity(LhScheduleContext context, Date targetDate);

    /**
     * 分配换模到均衡的班次
     *
     * @param context    排程上下文
     * @param endingTime 前SKU收尾时间
     * @return 换模分配的班次和时间, null表示无可用换模能力
     */
    Date allocateMouldChange(LhScheduleContext context, Date endingTime);

    /**
     * 获取指定日期剩余换模能力
     *
     * @param context    排程上下文
     * @param targetDate 目标日期
     * @return 剩余换模次数
     */
    int getRemainingCapacity(LhScheduleContext context, Date targetDate);
}
