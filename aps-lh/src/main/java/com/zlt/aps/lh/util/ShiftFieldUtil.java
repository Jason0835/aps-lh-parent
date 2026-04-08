package com.zlt.aps.lh.util;

import cn.hutool.core.bean.BeanUtil;
import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 通过 Hutool BeanUtil 统一读写 {@link LhScheduleResult} 的 class1～class8 班次字段（与 shiftIndex 对应）。
 *
 * @author APS
 */
@Slf4j
public final class ShiftFieldUtil {

    private ShiftFieldUtil() {
    }

    /**
     * 设置班次计划量及起止时间
     *
     * @param result     排程结果
     * @param shiftIndex 班次索引 1～8
     * @param qty        计划量
     * @param startTime  开始时间
     * @param endTime    结束时间
     */
    public static void setShiftPlanQty(LhScheduleResult result, int shiftIndex, Integer qty,
            Date startTime, Date endTime) {
        if (!isValidIndex(shiftIndex)) {
            log.warn("未知班次索引: {}", shiftIndex);
            return;
        }
        String prefix = propertyPrefix(shiftIndex);
        BeanUtil.setProperty(result, prefix + "PlanQty", qty);
        BeanUtil.setProperty(result, prefix + "StartTime", startTime);
        BeanUtil.setProperty(result, prefix + "EndTime", endTime);
    }

    /**
     * 获取班次计划量
     *
     * @param result     排程结果
     * @param shiftIndex 班次索引
     * @return 计划量，越界返回 null
     */
    public static Integer getShiftPlanQty(LhScheduleResult result, int shiftIndex) {
        if (!isValidIndex(shiftIndex)) {
            return null;
        }
        return toInteger(BeanUtil.getProperty(result, propertyPrefix(shiftIndex) + "PlanQty"));
    }

    /**
     * 获取班次计划开始时间
     *
     * @param result     排程结果
     * @param shiftIndex 班次索引
     * @return 开始时间，越界返回 null
     */
    public static Date getShiftStartTime(LhScheduleResult result, int shiftIndex) {
        if (!isValidIndex(shiftIndex)) {
            return null;
        }
        Object v = BeanUtil.getProperty(result, propertyPrefix(shiftIndex) + "StartTime");
        return v instanceof Date ? (Date) v : null;
    }

    /**
     * 获取班次计划结束时间
     *
     * @param result     排程结果
     * @param shiftIndex 班次索引
     * @return 结束时间，越界返回 null
     */
    public static Date getShiftEndTime(LhScheduleResult result, int shiftIndex) {
        if (!isValidIndex(shiftIndex)) {
            return null;
        }
        Object v = BeanUtil.getProperty(result, propertyPrefix(shiftIndex) + "EndTime");
        return v instanceof Date ? (Date) v : null;
    }

    /**
     * 获取班次完成量
     *
     * @param result     排程结果
     * @param shiftIndex 班次索引
     * @return 完成量，越界返回 null
     */
    public static Integer getShiftFinishQty(LhScheduleResult result, int shiftIndex) {
        if (!isValidIndex(shiftIndex)) {
            return null;
        }
        return toInteger(BeanUtil.getProperty(result, propertyPrefix(shiftIndex) + "FinishQty"));
    }

    private static Integer toInteger(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Integer) {
            return (Integer) v;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return null;
    }

    /**
     * 汇总 1～maxIndex 班次的计划量之和
     *
     * @param result   排程结果
     * @param maxIndex 最大班次索引（含）
     * @return 合计
     */
    public static int sumPlanQty(LhScheduleResult result, int maxIndex) {
        int total = 0;
        int cap = Math.min(maxIndex, LhScheduleConstant.MAX_SHIFT_SLOT_COUNT);
        for (int i = 1; i <= cap; i++) {
            Integer q = getShiftPlanQty(result, i);
            total += (q != null ? q : 0);
        }
        return total;
    }

    private static boolean isValidIndex(int shiftIndex) {
        return shiftIndex >= 1 && shiftIndex <= LhScheduleConstant.MAX_SHIFT_SLOT_COUNT;
    }

    private static String propertyPrefix(int shiftIndex) {
        return "class" + shiftIndex;
    }
}
