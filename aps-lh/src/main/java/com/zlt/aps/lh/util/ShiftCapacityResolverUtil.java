package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.mdm.api.domain.entity.MdmDevicePlanShut;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 班次产能解析工具。
 * <p>统一处理机台模台数、满班班产和残班折算口径。</p>
 *
 * @author APS
 */
public final class ShiftCapacityResolverUtil {

    /** 默认模台数 */
    private static final int DEFAULT_MOULD_QTY = 1;
    /** 每分钟秒数 */
    private static final int SECONDS_PER_MINUTE = 60;

    private ShiftCapacityResolverUtil() {
    }

    /**
     * 解析机台模台数。
     *
     * @param machine 机台
     * @return 模台数，缺失时返回默认值 1
     */
    public static int resolveMachineMouldQty(MachineScheduleDTO machine) {
        if (Objects.isNull(machine)) {
            return DEFAULT_MOULD_QTY;
        }
        return resolveMachineMouldQty(machine.getMaxMoldNum());
    }

    /**
     * 解析机台模台数。
     *
     * @param maxMoldNum 机台最大模台数
     * @return 模台数，缺失时返回默认值 1
     */
    public static int resolveMachineMouldQty(int maxMoldNum) {
        return maxMoldNum > 0 ? maxMoldNum : DEFAULT_MOULD_QTY;
    }

    /**
     * 按班次和实际开产时间解析班次产能。
     *
     * @param shift 班次
     * @param effectiveStartTime 实际开产时间
     * @param shiftCapacity 班产
     * @param lhTimeSeconds 硫化时长（秒）
     * @param mouldQty 模台数
     * @return 班次可排计划量
     */
    public static int resolveShiftCapacity(LhShiftConfigVO shift,
                                           Date effectiveStartTime,
                                           int shiftCapacity,
                                           int lhTimeSeconds,
                                           int mouldQty) {
        if (Objects.isNull(shift) || Objects.isNull(effectiveStartTime)) {
            return 0;
        }
        Date shiftEndTime = shift.getShiftEndDateTime();
        if (Objects.isNull(shiftEndTime) || !effectiveStartTime.before(shiftEndTime)) {
            return 0;
        }
        long availableSeconds = (shiftEndTime.getTime() - effectiveStartTime.getTime()) / 1000L;
        if (availableSeconds <= 0) {
            return 0;
        }
        long shiftDurationSeconds = resolveShiftDurationSeconds(shift);
        return resolveShiftCapacity(shiftCapacity, lhTimeSeconds, mouldQty, shiftDurationSeconds, availableSeconds);
    }

    /**
     * 按统一业务口径解析班次产能。
     *
     * @param shiftCapacity 班产
     * @param lhTimeSeconds 硫化时长（秒）
     * @param mouldQty 模台数
     * @param shiftDurationSeconds 班次总时长（秒）
     * @param availableSeconds 有效可生产时长（秒）
     * @return 班次可排计划量
     */
    public static int resolveShiftCapacity(int shiftCapacity,
                                           int lhTimeSeconds,
                                           int mouldQty,
                                           long shiftDurationSeconds,
                                           long availableSeconds) {
        if (availableSeconds <= 0) {
            return 0;
        }

        long effectiveAvailableSeconds = availableSeconds;
        if (shiftDurationSeconds > 0) {
            effectiveAvailableSeconds = Math.min(availableSeconds, shiftDurationSeconds);
        }

        // 有班产主数据时，按整班班产基准做残班折算。
        if (shiftCapacity > 0) {
            if (shiftDurationSeconds <= 0) {
                return shiftCapacity;
            }
            return BigDecimal.valueOf(shiftCapacity)
                    .multiply(BigDecimal.valueOf(effectiveAvailableSeconds))
                    .divide(BigDecimal.valueOf(shiftDurationSeconds), 0, RoundingMode.DOWN)
                    .intValue();
        }

        // 无班产主数据时，按硫化时长与模台数回退计算。
        int resolvedMouldQty = resolveMachineMouldQty(mouldQty);
        if (lhTimeSeconds <= 0 || resolvedMouldQty <= 0) {
            return 0;
        }
        return (int) (effectiveAvailableSeconds / lhTimeSeconds) * resolvedMouldQty;
    }

    /**
     * 解析班次总时长（秒）。
     *
     * @param shift 班次
     * @return 班次总时长（秒）
     */
    public static long resolveShiftDurationSeconds(LhShiftConfigVO shift) {
        if (Objects.isNull(shift)) {
            return 0L;
        }
        if (shift.getDurationMinutes() > 0) {
            return (long) shift.getDurationMinutes() * SECONDS_PER_MINUTE;
        }
        Date shiftStartTime = shift.getShiftStartDateTime();
        Date shiftEndTime = shift.getShiftEndDateTime();
        if (Objects.isNull(shiftStartTime) || Objects.isNull(shiftEndTime)) {
            return 0L;
        }
        long durationSeconds = (shiftEndTime.getTime() - shiftStartTime.getTime()) / 1000L;
        return Math.max(durationSeconds, 0L);
    }

    /**
     * 计算机台在指定时间窗内被计划停机占用的总秒数。
     *
     * @param devicePlanShutList 设备计划停机列表
     * @param machineCode 机台编号
     * @param windowStartTime 时间窗开始时间
     * @param windowEndTime 时间窗结束时间
     * @return 停机重叠秒数
     */
    public static long resolvePlannedStopOverlapSeconds(List<MdmDevicePlanShut> devicePlanShutList,
                                                        String machineCode,
                                                        Date windowStartTime,
                                                        Date windowEndTime) {
        if (CollectionUtils.isEmpty(devicePlanShutList)
                || StringUtils.isEmpty(machineCode)
                || Objects.isNull(windowStartTime)
                || Objects.isNull(windowEndTime)
                || !windowStartTime.before(windowEndTime)) {
            return 0L;
        }

        List<Date[]> overlapIntervals = new ArrayList<>();
        for (MdmDevicePlanShut planShut : devicePlanShutList) {
            if (Objects.isNull(planShut)
                    || !StringUtils.equals(machineCode, planShut.getMachineCode())
                    || Objects.isNull(planShut.getBeginDate())
                    || Objects.isNull(planShut.getEndDate())
                    || !planShut.getBeginDate().before(planShut.getEndDate())) {
                continue;
            }
            Date overlapStartTime = planShut.getBeginDate().after(windowStartTime) ? planShut.getBeginDate() : windowStartTime;
            Date overlapEndTime = planShut.getEndDate().before(windowEndTime) ? planShut.getEndDate() : windowEndTime;
            if (overlapStartTime.before(overlapEndTime)) {
                overlapIntervals.add(new Date[]{overlapStartTime, overlapEndTime});
            }
        }
        if (overlapIntervals.isEmpty()) {
            return 0L;
        }

        overlapIntervals.sort((left, right) -> {
            int startCmp = left[0].compareTo(right[0]);
            if (startCmp != 0) {
                return startCmp;
            }
            return left[1].compareTo(right[1]);
        });

        long overlapSeconds = 0L;
        Date mergedStart = overlapIntervals.get(0)[0];
        Date mergedEnd = overlapIntervals.get(0)[1];
        for (int i = 1; i < overlapIntervals.size(); i++) {
            Date currentStart = overlapIntervals.get(i)[0];
            Date currentEnd = overlapIntervals.get(i)[1];
            if (!currentStart.after(mergedEnd)) {
                if (currentEnd.after(mergedEnd)) {
                    mergedEnd = currentEnd;
                }
                continue;
            }
            overlapSeconds += (mergedEnd.getTime() - mergedStart.getTime()) / 1000L;
            mergedStart = currentStart;
            mergedEnd = currentEnd;
        }
        overlapSeconds += (mergedEnd.getTime() - mergedStart.getTime()) / 1000L;
        return Math.max(overlapSeconds, 0L);
    }

    /**
     * 计算机台在指定时间窗内扣减计划停机后的净可用秒数。
     *
     * @param devicePlanShutList 设备计划停机列表
     * @param machineCode 机台编号
     * @param windowStartTime 时间窗开始时间
     * @param windowEndTime 时间窗结束时间
     * @return 净可用秒数
     */
    public static long resolveNetAvailableSeconds(List<MdmDevicePlanShut> devicePlanShutList,
                                                  String machineCode,
                                                  Date windowStartTime,
                                                  Date windowEndTime) {
        if (Objects.isNull(windowStartTime) || Objects.isNull(windowEndTime) || !windowStartTime.before(windowEndTime)) {
            return 0L;
        }
        long availableSeconds = (windowEndTime.getTime() - windowStartTime.getTime()) / 1000L;
        if (availableSeconds <= 0) {
            return 0L;
        }
        long overlapSeconds = resolvePlannedStopOverlapSeconds(devicePlanShutList, machineCode, windowStartTime, windowEndTime);
        return Math.max(availableSeconds - overlapSeconds, 0L);
    }

    /**
     * 推导考虑计划停机空档后的完工时间。
     * <p>语义：生产只能在非停机时间推进，遇到停机窗口自动顺延。</p>
     *
     * @param devicePlanShutList 设备计划停机列表
     * @param machineCode 机台编号
     * @param productionStartTime 生产开始时间
     * @param productionSeconds 纯生产所需秒数（不含停机空档）
     * @return 完工时间
     */
    public static Date resolveCompletionTimeWithPlannedStops(List<MdmDevicePlanShut> devicePlanShutList,
                                                             String machineCode,
                                                             Date productionStartTime,
                                                             long productionSeconds) {
        if (Objects.isNull(productionStartTime)) {
            return null;
        }
        if (productionSeconds <= 0) {
            return productionStartTime;
        }
        if (CollectionUtils.isEmpty(devicePlanShutList) || StringUtils.isEmpty(machineCode)) {
            return new Date(productionStartTime.getTime() + productionSeconds * 1000L);
        }

        List<MdmDevicePlanShut> machineStops = new ArrayList<>();
        for (MdmDevicePlanShut planShut : devicePlanShutList) {
            if (Objects.isNull(planShut)
                    || !StringUtils.equals(machineCode, planShut.getMachineCode())
                    || Objects.isNull(planShut.getBeginDate())
                    || Objects.isNull(planShut.getEndDate())
                    || !planShut.getBeginDate().before(planShut.getEndDate())
                    || !planShut.getEndDate().after(productionStartTime)) {
                continue;
            }
            machineStops.add(planShut);
        }
        if (machineStops.isEmpty()) {
            return new Date(productionStartTime.getTime() + productionSeconds * 1000L);
        }
        machineStops.sort(Comparator.comparing(MdmDevicePlanShut::getBeginDate)
                .thenComparing(MdmDevicePlanShut::getEndDate));

        long remainingSeconds = productionSeconds;
        Date cursor = productionStartTime;
        for (MdmDevicePlanShut stop : machineStops) {
            Date stopStartTime = stop.getBeginDate();
            Date stopEndTime = stop.getEndDate();

            if (!cursor.before(stopEndTime)) {
                continue;
            }

            if (stopStartTime.after(cursor)) {
                long productiveSeconds = (stopStartTime.getTime() - cursor.getTime()) / 1000L;
                if (productiveSeconds >= remainingSeconds) {
                    return new Date(cursor.getTime() + remainingSeconds * 1000L);
                }
                if (productiveSeconds > 0) {
                    remainingSeconds -= productiveSeconds;
                    cursor = stopStartTime;
                }
            }

            if (cursor.before(stopEndTime)) {
                cursor = stopEndTime;
            }
        }

        return new Date(cursor.getTime() + remainingSeconds * 1000L);
    }
}
