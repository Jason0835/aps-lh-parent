/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy;

import com.zlt.aps.lh.context.LhScheduleContext;

/**
 * SKU排产优先级策略接口
 * <p>对结构下的SKU进行排产顺序排列</p>
 *
 * @author APS
 */
public interface ISkuPriorityStrategy {

    /**
     * 对context中的newSpecSkuList按优先级排序
     * <p>
     * 排序规则:
     * <ol>
     *   <li>有发货要求的优先(锁定上机日期)</li>
     *   <li>延误上机的优先(延误越久越优先)</li>
     *   <li>未来5天结构要收尾的, 该结构下的SKU收尾日越晚的优先上机</li>
     *   <li>供应链优先级(高优先级->周期排产->中优先级->搭配排产)</li>
     * </ol>
     * </p>
     *
     * @param context 排程上下文
     */
    void sortByPriority(LhScheduleContext context);
}
