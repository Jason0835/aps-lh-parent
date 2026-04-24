package com.zlt.aps.lh.util;

import com.zlt.aps.mp.api.domain.entity.FactoryMonthPlanProductionFinalResult;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 月计划日计划量工具。
 * <p>统一按排程窗口汇总月计划 DAY_1～DAY_31 字段。</p>
 *
 * @author APS
 */
public final class MonthPlanDayQtyUtil {

    /** 月最小日序 */
    private static final int MIN_DAY_OF_MONTH = 1;
    /** 月最大日序 */
    private static final int MAX_DAY_OF_MONTH = 31;
    /** 跨月排产提示文案 */
    public static final String CROSS_MONTH_UNSUPPORTED_MESSAGE = "当前暂未开放跨月排产能力，技术团队正在紧急处理";
    /** 日计划量读取器 */
    private static final List<Function<FactoryMonthPlanProductionFinalResult, Integer>> DAY_QTY_GETTERS = Arrays.asList(
            FactoryMonthPlanProductionFinalResult::getDay1,
            FactoryMonthPlanProductionFinalResult::getDay2,
            FactoryMonthPlanProductionFinalResult::getDay3,
            FactoryMonthPlanProductionFinalResult::getDay4,
            FactoryMonthPlanProductionFinalResult::getDay5,
            FactoryMonthPlanProductionFinalResult::getDay6,
            FactoryMonthPlanProductionFinalResult::getDay7,
            FactoryMonthPlanProductionFinalResult::getDay8,
            FactoryMonthPlanProductionFinalResult::getDay9,
            FactoryMonthPlanProductionFinalResult::getDay10,
            FactoryMonthPlanProductionFinalResult::getDay11,
            FactoryMonthPlanProductionFinalResult::getDay12,
            FactoryMonthPlanProductionFinalResult::getDay13,
            FactoryMonthPlanProductionFinalResult::getDay14,
            FactoryMonthPlanProductionFinalResult::getDay15,
            FactoryMonthPlanProductionFinalResult::getDay16,
            FactoryMonthPlanProductionFinalResult::getDay17,
            FactoryMonthPlanProductionFinalResult::getDay18,
            FactoryMonthPlanProductionFinalResult::getDay19,
            FactoryMonthPlanProductionFinalResult::getDay20,
            FactoryMonthPlanProductionFinalResult::getDay21,
            FactoryMonthPlanProductionFinalResult::getDay22,
            FactoryMonthPlanProductionFinalResult::getDay23,
            FactoryMonthPlanProductionFinalResult::getDay24,
            FactoryMonthPlanProductionFinalResult::getDay25,
            FactoryMonthPlanProductionFinalResult::getDay26,
            FactoryMonthPlanProductionFinalResult::getDay27,
            FactoryMonthPlanProductionFinalResult::getDay28,
            FactoryMonthPlanProductionFinalResult::getDay29,
            FactoryMonthPlanProductionFinalResult::getDay30,
            FactoryMonthPlanProductionFinalResult::getDay31
    );

    private MonthPlanDayQtyUtil() {
    }

    /**
     * 判断排程窗口是否跨月。
     *
     * @param scheduleDate 排程窗口起点
     * @param scheduleTargetDate 排程窗口终点
     * @return true-跨月，false-同月
     */
    public static boolean isCrossMonthWindow(Date scheduleDate, Date scheduleTargetDate) {
        if (Objects.isNull(scheduleDate) || Objects.isNull(scheduleTargetDate)) {
            return false;
        }
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.setTime(scheduleDate);
        Calendar targetCalendar = Calendar.getInstance();
        targetCalendar.setTime(scheduleTargetDate);
        return startCalendar.get(Calendar.YEAR) != targetCalendar.get(Calendar.YEAR)
                || startCalendar.get(Calendar.MONTH) != targetCalendar.get(Calendar.MONTH);
    }

    /**
     * 汇总排程窗口内的日计划量。
     *
     * @param plan 月计划记录
     * @param scheduleDate 排程窗口起点
     * @param scheduleTargetDate 排程窗口终点
     * @return 窗口内日计划量之和
     */
    public static int resolveWindowPlanQty(FactoryMonthPlanProductionFinalResult plan,
                                           Date scheduleDate,
                                           Date scheduleTargetDate) {
        if (Objects.isNull(plan) || Objects.isNull(scheduleDate) || Objects.isNull(scheduleTargetDate)) {
            return 0;
        }
        if (isCrossMonthWindow(scheduleDate, scheduleTargetDate)) {
            throw new IllegalArgumentException(CROSS_MONTH_UNSUPPORTED_MESSAGE);
        }
        Date startDate = LhScheduleTimeUtil.clearTime(scheduleDate);
        Date endDate = LhScheduleTimeUtil.clearTime(scheduleTargetDate);
        if (startDate.after(endDate)) {
            return 0;
        }

        int totalQty = 0;
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(startDate);
        Calendar endCalendar = Calendar.getInstance();
        endCalendar.setTime(endDate);
        while (!cursor.after(endCalendar)) {
            totalQty += resolveDayQty(plan, cursor.get(Calendar.DAY_OF_MONTH));
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        return totalQty;
    }

    /**
     * 获取指定自然日的计划量。
     *
     * @param plan 月计划记录
     * @param dayOfMonth 日序（1～31）
     * @return 计划量，缺失按 0
     */
    public static int resolveDayQty(FactoryMonthPlanProductionFinalResult plan, int dayOfMonth) {
        if (Objects.isNull(plan) || dayOfMonth < MIN_DAY_OF_MONTH || dayOfMonth > MAX_DAY_OF_MONTH) {
            return 0;
        }
        Integer qty = DAY_QTY_GETTERS.get(dayOfMonth - 1).apply(plan);
        return qty != null ? qty : 0;
    }

    /**
     * 获取月计划中最早存在计划量的自然日。
     *
     * @param plan 月计划记录
     * @return 最早有量日；不存在时返回 -1
     */
    public static int resolveFirstPlannedDay(FactoryMonthPlanProductionFinalResult plan) {
        if (Objects.isNull(plan)) {
            return -1;
        }
        for (int dayOfMonth = MIN_DAY_OF_MONTH; dayOfMonth <= MAX_DAY_OF_MONTH; dayOfMonth++) {
            if (resolveDayQty(plan, dayOfMonth) > 0) {
                return dayOfMonth;
            }
        }
        return -1;
    }
}
