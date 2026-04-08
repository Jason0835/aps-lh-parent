/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy;

import com.zlt.aps.lh.context.LhScheduleContext;

import java.util.Date;

/**
 * 产能计算策略接口
 * <p>计算硫化班产、开产时间和班次计划量</p>
 *
 * @author APS
 */
public interface ICapacityCalculateStrategy {

    /**
     * 计算硫化班产
     * <p>公式: (班次时间-非生产作业时间) / 硫化时间 向下取整 * 模数</p>
     *
     * @param lhTimeSeconds 硫化时间(秒)
     * @param mouldQty      模数
     * @return 班产量
     */
    int calculateShiftCapacity(int lhTimeSeconds, int mouldQty);

    /**
     * 计算开产时间
     * <p>
     * 考虑: 前SKU收尾时间 + 换模含预热(4h) + 其他时间(4h, 首检+等待交替)
     * 以及与保养/维修/清洗计划的重叠
     * </p>
     *
     * @param context     排程上下文
     * @param machineCode 机台编号
     * @param endingTime  前SKU收尾时间
     * @return 开产时间
     */
    Date calculateStartTime(LhScheduleContext context, String machineCode, Date endingTime);

    /**
     * 计算首班计划量
     * <p>公式: (首班次结束时间 - 首班次上机时间) / 硫化时间 向下取整 * 模数</p>
     *
     * @param startTime     开产时间
     * @param shiftEndTime  首班次结束时间
     * @param lhTimeSeconds 硫化时间(秒)
     * @param mouldQty      模数
     * @return 首班计划量
     */
    int calculateFirstShiftQty(Date startTime, Date shiftEndTime, int lhTimeSeconds, int mouldQty);

    /**
     * 计算日硫化量
     * <p>日单模产能 = 24*3600 / 硫化时间; 日硫化量 = 日单模产能 * 模数</p>
     *
     * @param lhTimeSeconds 硫化时间(秒)
     * @param mouldQty      模数
     * @return 日硫化量
     */
    int calculateDailyCapacity(int lhTimeSeconds, int mouldQty);
}
