package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
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
}
