package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.dto.MachineCleaningWindowDTO;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;
import com.zlt.aps.lh.api.enums.CleaningTypeEnum;
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
    /** 每小时秒数 */
    private static final int SECONDS_PER_HOUR = 3600;

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
     * 解析忽略清洗扣量时的首个可排产开始时间。
     *
     * @param devicePlanShutList 设备停机列表
     * @param machineCode 机台编号
     * @param productionStartTime 切换完成后的理论开产时间
     * @param shifts 排程班次窗口
     * @param shiftCapacity 班产
     * @param lhTimeSeconds 硫化时长（秒）
     * @param mouldQty 模台数
     * @return 首个可排产开始时间；不存在可排产班次时返回 null
     */
    public static Date resolveFirstSchedulableStartIgnoringCleaning(List<MdmDevicePlanShut> devicePlanShutList,
                                                                    String machineCode,
                                                                    Date productionStartTime,
                                                                    List<LhShiftConfigVO> shifts,
                                                                    int shiftCapacity,
                                                                    int lhTimeSeconds,
                                                                    int mouldQty) {
        if (Objects.isNull(productionStartTime) || CollectionUtils.isEmpty(shifts)) {
            return null;
        }
        Date cursorStartTime = productionStartTime;
        boolean started = false;
        for (LhShiftConfigVO shift : shifts) {
            if (Objects.isNull(shift)
                    || Objects.isNull(shift.getShiftStartDateTime())
                    || Objects.isNull(shift.getShiftEndDateTime())) {
                continue;
            }
            if (!started) {
                if (cursorStartTime.before(shift.getShiftEndDateTime())) {
                    started = true;
                } else {
                    continue;
                }
            }
            Date effectiveStartTime = cursorStartTime.after(shift.getShiftStartDateTime())
                    ? cursorStartTime : shift.getShiftStartDateTime();
            if (!effectiveStartTime.before(shift.getShiftEndDateTime())) {
                cursorStartTime = shift.getShiftEndDateTime();
                continue;
            }
            long netAvailableSeconds = resolveNetAvailableSeconds(
                    devicePlanShutList, machineCode, effectiveStartTime, shift.getShiftEndDateTime());
            if (netAvailableSeconds <= 0) {
                cursorStartTime = shift.getShiftEndDateTime();
                continue;
            }
            int shiftMaxQty = resolveShiftCapacity(
                    shiftCapacity,
                    lhTimeSeconds,
                    mouldQty,
                    resolveShiftDurationSeconds(shift),
                    netAvailableSeconds);
            if (shiftMaxQty > 0) {
                return effectiveStartTime;
            }
            cursorStartTime = shift.getShiftEndDateTime();
        }
        return null;
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
     * 计算机台在指定时间窗内扣减计划停机和清洗后的净生产秒数。
     *
     * @param devicePlanShutList 设备停机计划
     * @param cleaningWindowList 清洗时间窗口
     * @param machineCode 机台编号
     * @param windowStartTime 时间窗开始时间
     * @param windowEndTime 时间窗结束时间
     * @return 净生产秒数
     */
    public static long resolveNetProductiveSeconds(List<MdmDevicePlanShut> devicePlanShutList,
                                                   List<MachineCleaningWindowDTO> cleaningWindowList,
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
        long downtimeSeconds = resolveDowntimeOverlapSeconds(
                devicePlanShutList, cleaningWindowList, machineCode, windowStartTime, windowEndTime);
        return Math.max(availableSeconds - downtimeSeconds, 0L);
    }

    /**
     * 计算扣减停机与清洗后的班次最大计划量。
     *
     * @param devicePlanShutList 设备停机计划
     * @param cleaningWindowList 清洗时间窗口
     * @param machineCode 机台编号
     * @param windowStartTime 时间窗开始时间
     * @param windowEndTime 时间窗结束时间
     * @param shiftCapacity 班产
     * @param lhTimeSeconds 硫化时长（秒）
     * @param mouldQty 模台数
     * @param shiftDurationSeconds 班次总时长（秒）
     * @param dryIceDurationHours 干冰标准清洗时长（小时）
     * @return 扣减后的班次最大计划量
     */
    public static int resolveShiftCapacityWithDowntime(List<MdmDevicePlanShut> devicePlanShutList,
                                                       List<MachineCleaningWindowDTO> cleaningWindowList,
                                                       String machineCode,
                                                       Date windowStartTime,
                                                       Date windowEndTime,
                                                       int shiftCapacity,
                                                       int lhTimeSeconds,
                                                       int mouldQty,
                                                       long shiftDurationSeconds,
                                                       int dryIceLossQty,
                                                       int dryIceDurationHours) {
        long stopAdjustedSeconds = resolveNetAvailableSeconds(
                devicePlanShutList, machineCode, windowStartTime, windowEndTime);
        int stopAdjustedQty = resolveShiftCapacity(
                shiftCapacity, lhTimeSeconds, mouldQty, shiftDurationSeconds, stopAdjustedSeconds);
        if (stopAdjustedQty <= 0) {
            return 0;
        }
        int cleaningLossQty = resolveCleaningLossQty(
                devicePlanShutList, cleaningWindowList, machineCode, windowStartTime, windowEndTime,
                shiftCapacity, lhTimeSeconds, mouldQty, shiftDurationSeconds, dryIceLossQty, dryIceDurationHours);
        return Math.max(stopAdjustedQty - cleaningLossQty, 0);
    }

    /**
     * 计算班次内当前计划量对应的实际结束时间。
     *
     * @param devicePlanShutList 设备停机计划
     * @param cleaningWindowList 清洗时间窗口
     * @param machineCode 机台编号
     * @param effectiveStartTime 班次实际开产时间
     * @param shiftEndTime 班次结束时间
     * @param allocationQty 当前班次分配量
     * @param shiftMaxQty 当前班次最大可排量
     * @return 实际结束时间
     */
    public static Date resolveShiftPlanEndTime(List<MdmDevicePlanShut> devicePlanShutList,
                                               List<MachineCleaningWindowDTO> cleaningWindowList,
                                               String machineCode,
                                               Date effectiveStartTime,
                                               Date shiftEndTime,
                                               int allocationQty,
                                               int shiftMaxQty) {
        if (Objects.isNull(effectiveStartTime) || Objects.isNull(shiftEndTime) || allocationQty <= 0 || shiftMaxQty <= 0) {
            return effectiveStartTime;
        }
        long netProductiveSeconds = resolveNetProductiveSeconds(
                devicePlanShutList, cleaningWindowList, machineCode, effectiveStartTime, shiftEndTime);
        if (netProductiveSeconds <= 0) {
            return effectiveStartTime;
        }
        long requiredProductiveSeconds = BigDecimal.valueOf(allocationQty)
                .multiply(BigDecimal.valueOf(netProductiveSeconds))
                .divide(BigDecimal.valueOf(shiftMaxQty), 0, RoundingMode.UP)
                .longValue();
        return resolveCompletionTimeWithDowntimes(
                devicePlanShutList, cleaningWindowList, machineCode, effectiveStartTime, requiredProductiveSeconds);
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

    /**
     * 推导考虑停机与清洗空档后的完工时间。
     *
     * @param devicePlanShutList 设备停机计划
     * @param cleaningWindowList 清洗时间窗口
     * @param machineCode 机台编号
     * @param productionStartTime 生产开始时间
     * @param productionSeconds 纯生产所需秒数
     * @return 完工时间
     */
    public static Date resolveCompletionTimeWithDowntimes(List<MdmDevicePlanShut> devicePlanShutList,
                                                          List<MachineCleaningWindowDTO> cleaningWindowList,
                                                          String machineCode,
                                                          Date productionStartTime,
                                                          long productionSeconds) {
        if (Objects.isNull(productionStartTime)) {
            return null;
        }
        if (productionSeconds <= 0) {
            return productionStartTime;
        }
        List<Date[]> downtimeIntervals = collectMergedDowntimeIntervals(
                devicePlanShutList, cleaningWindowList, machineCode, productionStartTime, null);
        if (downtimeIntervals.isEmpty()) {
            return new Date(productionStartTime.getTime() + productionSeconds * 1000L);
        }

        long remainingSeconds = productionSeconds;
        Date cursor = productionStartTime;
        for (Date[] downtimeInterval : downtimeIntervals) {
            Date downtimeStartTime = downtimeInterval[0];
            Date downtimeEndTime = downtimeInterval[1];

            if (!cursor.before(downtimeEndTime)) {
                continue;
            }
            if (downtimeStartTime.after(cursor)) {
                long productiveSeconds = (downtimeStartTime.getTime() - cursor.getTime()) / 1000L;
                if (productiveSeconds >= remainingSeconds) {
                    return new Date(cursor.getTime() + remainingSeconds * 1000L);
                }
                if (productiveSeconds > 0) {
                    remainingSeconds -= productiveSeconds;
                    cursor = downtimeStartTime;
                }
            }
            if (cursor.before(downtimeEndTime)) {
                cursor = downtimeEndTime;
            }
        }
        return new Date(cursor.getTime() + remainingSeconds * 1000L);
    }

    /**
     * 计算指定时间窗内的停机与清洗重叠秒数。
     *
     * @param devicePlanShutList 设备停机计划
     * @param cleaningWindowList 清洗时间窗口
     * @param machineCode 机台编号
     * @param windowStartTime 时间窗开始时间
     * @param windowEndTime 时间窗结束时间
     * @return 重叠秒数
     */
    public static long resolveDowntimeOverlapSeconds(List<MdmDevicePlanShut> devicePlanShutList,
                                                     List<MachineCleaningWindowDTO> cleaningWindowList,
                                                     String machineCode,
                                                     Date windowStartTime,
                                                     Date windowEndTime) {
        List<Date[]> downtimeIntervals = collectMergedDowntimeIntervals(
                devicePlanShutList, cleaningWindowList, machineCode, windowStartTime, windowEndTime);
        return resolveIntervalDurationSeconds(downtimeIntervals);
    }

    private static int resolveCleaningLossQty(List<MdmDevicePlanShut> devicePlanShutList,
                                              List<MachineCleaningWindowDTO> cleaningWindowList,
                                              String machineCode,
                                              Date windowStartTime,
                                              Date windowEndTime,
                                              int shiftCapacity,
                                              int lhTimeSeconds,
                                              int mouldQty,
                                              long shiftDurationSeconds,
                                              int dryIceLossQty,
                                              int dryIceDurationHours) {
        if (CollectionUtils.isEmpty(cleaningWindowList)
                || Objects.isNull(windowStartTime)
                || Objects.isNull(windowEndTime)
                || !windowStartTime.before(windowEndTime)) {
            return 0;
        }
        List<Date[]> stopIntervals = collectMergedPlannedStopIntervals(
                devicePlanShutList, machineCode, windowStartTime, windowEndTime);
        int cleaningLossQty = 0;
        cleaningLossQty += resolveDryIceLossQty(
                cleaningWindowList, stopIntervals, windowStartTime, windowEndTime, dryIceLossQty, dryIceDurationHours);
        cleaningLossQty += resolveSandBlastLossQty(
                cleaningWindowList, stopIntervals, windowStartTime, windowEndTime,
                shiftCapacity, lhTimeSeconds, mouldQty, shiftDurationSeconds);
        return cleaningLossQty;
    }

    private static int resolveDryIceLossQty(List<MachineCleaningWindowDTO> cleaningWindowList,
                                            List<Date[]> stopIntervals,
                                            Date windowStartTime,
                                            Date windowEndTime,
                                            int dryIceLossQty,
                                            int dryIceDurationHours) {
        int totalLossQty = 0;
        long dryIceDurationSeconds = (long) Math.max(dryIceDurationHours, 0) * SECONDS_PER_HOUR;
        if (dryIceLossQty <= 0 || dryIceDurationSeconds <= 0) {
            return totalLossQty;
        }
        List<Date[]> dryIceIntervals = collectMergedCleaningIntervals(
                cleaningWindowList, CleaningTypeEnum.DRY_ICE.getCode(), windowStartTime, windowEndTime);
        for (Date[] dryIceInterval : dryIceIntervals) {
            long effectiveOverlapSeconds = resolveEffectiveCleaningOverlapSeconds(
                    dryIceInterval, stopIntervals, windowStartTime, windowEndTime);
            if (effectiveOverlapSeconds <= 0) {
                continue;
            }
            totalLossQty += BigDecimal.valueOf(dryIceLossQty)
                    .multiply(BigDecimal.valueOf(effectiveOverlapSeconds))
                    .divide(BigDecimal.valueOf(dryIceDurationSeconds), 0, RoundingMode.DOWN)
                    .intValue();
        }
        return totalLossQty;
    }

    private static int resolveSandBlastLossQty(List<MachineCleaningWindowDTO> cleaningWindowList,
                                               List<Date[]> stopIntervals,
                                               Date windowStartTime,
                                               Date windowEndTime,
                                               int shiftCapacity,
                                               int lhTimeSeconds,
                                               int mouldQty,
                                               long shiftDurationSeconds) {
        int totalLossQty = 0;
        List<Date[]> sandBlastIntervals = collectMergedCleaningIntervals(
                cleaningWindowList, CleaningTypeEnum.SAND_BLAST.getCode(), windowStartTime, windowEndTime);
        for (Date[] sandBlastInterval : sandBlastIntervals) {
            long effectiveOverlapSeconds = resolveEffectiveCleaningOverlapSeconds(
                    sandBlastInterval, stopIntervals, windowStartTime, windowEndTime);
            if (effectiveOverlapSeconds <= 0) {
                continue;
            }
            totalLossQty += resolveShiftCapacity(
                    shiftCapacity, lhTimeSeconds, mouldQty, shiftDurationSeconds, effectiveOverlapSeconds);
        }
        return totalLossQty;
    }

    private static long resolveEffectiveCleaningOverlapSeconds(Date[] cleaningInterval,
                                                               List<Date[]> stopIntervals,
                                                               Date windowStartTime,
                                                               Date windowEndTime) {
        List<Date[]> scopedCleaningIntervals = new ArrayList<>(1);
        Date overlapStartTime = later(cleaningInterval[0], windowStartTime);
        Date overlapEndTime = earlier(cleaningInterval[1], windowEndTime);
        if (!overlapStartTime.before(overlapEndTime)) {
            return 0L;
        }
        scopedCleaningIntervals.add(new Date[]{overlapStartTime, overlapEndTime});
        long cleaningOverlapSeconds = resolveIntervalDurationSeconds(scopedCleaningIntervals);
        long duplicatedStopSeconds = resolveIntervalIntersectionSeconds(scopedCleaningIntervals, stopIntervals);
        return Math.max(cleaningOverlapSeconds - duplicatedStopSeconds, 0L);
    }

    private static List<Date[]> collectMergedDowntimeIntervals(List<MdmDevicePlanShut> devicePlanShutList,
                                                               List<MachineCleaningWindowDTO> cleaningWindowList,
                                                               String machineCode,
                                                               Date windowStartTime,
                                                               Date windowEndTime) {
        List<Date[]> downtimeIntervals = new ArrayList<>();
        downtimeIntervals.addAll(collectMergedPlannedStopIntervals(
                devicePlanShutList, machineCode, windowStartTime, windowEndTime));
        downtimeIntervals.addAll(collectMergedCleaningIntervals(
                cleaningWindowList, null, windowStartTime, windowEndTime));
        return mergeIntervals(downtimeIntervals);
    }

    private static List<Date[]> collectMergedPlannedStopIntervals(List<MdmDevicePlanShut> devicePlanShutList,
                                                                  String machineCode,
                                                                  Date windowStartTime,
                                                                  Date windowEndTime) {
        List<Date[]> stopIntervals = new ArrayList<>();
        if (CollectionUtils.isEmpty(devicePlanShutList)
                || StringUtils.isEmpty(machineCode)
                || Objects.isNull(windowStartTime)) {
            return stopIntervals;
        }
        for (MdmDevicePlanShut planShut : devicePlanShutList) {
            if (Objects.isNull(planShut)
                    || !StringUtils.equals(machineCode, planShut.getMachineCode())
                    || Objects.isNull(planShut.getBeginDate())
                    || Objects.isNull(planShut.getEndDate())
                    || !planShut.getBeginDate().before(planShut.getEndDate())) {
                continue;
            }
            Date overlapStartTime = later(planShut.getBeginDate(), windowStartTime);
            Date overlapEndTime = windowEndTime == null
                    ? planShut.getEndDate()
                    : earlier(planShut.getEndDate(), windowEndTime);
            if (overlapStartTime.before(overlapEndTime)) {
                stopIntervals.add(new Date[]{overlapStartTime, overlapEndTime});
            }
        }
        return mergeIntervals(stopIntervals);
    }

    private static List<Date[]> collectMergedCleaningIntervals(List<MachineCleaningWindowDTO> cleaningWindowList,
                                                               String cleanType,
                                                               Date windowStartTime,
                                                               Date windowEndTime) {
        List<Date[]> cleaningIntervals = new ArrayList<>();
        if (CollectionUtils.isEmpty(cleaningWindowList) || Objects.isNull(windowStartTime)) {
            return cleaningIntervals;
        }
        for (MachineCleaningWindowDTO cleaningWindow : cleaningWindowList) {
            if (Objects.isNull(cleaningWindow)
                    || Objects.isNull(cleaningWindow.getCleanStartTime())
                    || Objects.isNull(cleaningWindow.getCleanEndTime())
                    || !cleaningWindow.getCleanStartTime().before(cleaningWindow.getCleanEndTime())) {
                continue;
            }
            if (StringUtils.isNotEmpty(cleanType)
                    && !StringUtils.equals(cleanType, cleaningWindow.getCleanType())) {
                continue;
            }
            Date overlapStartTime = later(cleaningWindow.getCleanStartTime(), windowStartTime);
            Date overlapEndTime = windowEndTime == null
                    ? cleaningWindow.getCleanEndTime()
                    : earlier(cleaningWindow.getCleanEndTime(), windowEndTime);
            if (overlapStartTime.before(overlapEndTime)) {
                cleaningIntervals.add(new Date[]{overlapStartTime, overlapEndTime});
            }
        }
        return mergeIntervals(cleaningIntervals);
    }

    private static List<Date[]> mergeIntervals(List<Date[]> intervals) {
        List<Date[]> mergedIntervals = new ArrayList<>();
        if (CollectionUtils.isEmpty(intervals)) {
            return mergedIntervals;
        }
        intervals.sort((left, right) -> {
            int startCmp = left[0].compareTo(right[0]);
            if (startCmp != 0) {
                return startCmp;
            }
            return left[1].compareTo(right[1]);
        });
        Date mergedStartTime = intervals.get(0)[0];
        Date mergedEndTime = intervals.get(0)[1];
        for (int i = 1; i < intervals.size(); i++) {
            Date currentStartTime = intervals.get(i)[0];
            Date currentEndTime = intervals.get(i)[1];
            if (!currentStartTime.after(mergedEndTime)) {
                if (currentEndTime.after(mergedEndTime)) {
                    mergedEndTime = currentEndTime;
                }
                continue;
            }
            mergedIntervals.add(new Date[]{mergedStartTime, mergedEndTime});
            mergedStartTime = currentStartTime;
            mergedEndTime = currentEndTime;
        }
        mergedIntervals.add(new Date[]{mergedStartTime, mergedEndTime});
        return mergedIntervals;
    }

    private static long resolveIntervalDurationSeconds(List<Date[]> intervals) {
        long totalSeconds = 0L;
        for (Date[] interval : intervals) {
            totalSeconds += intervalDurationSeconds(interval[0], interval[1]);
        }
        return Math.max(totalSeconds, 0L);
    }

    private static long resolveIntervalIntersectionSeconds(List<Date[]> leftIntervals, List<Date[]> rightIntervals) {
        if (CollectionUtils.isEmpty(leftIntervals) || CollectionUtils.isEmpty(rightIntervals)) {
            return 0L;
        }
        long overlapSeconds = 0L;
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < leftIntervals.size() && rightIndex < rightIntervals.size()) {
            Date[] leftInterval = leftIntervals.get(leftIndex);
            Date[] rightInterval = rightIntervals.get(rightIndex);
            Date overlapStartTime = later(leftInterval[0], rightInterval[0]);
            Date overlapEndTime = earlier(leftInterval[1], rightInterval[1]);
            if (overlapStartTime.before(overlapEndTime)) {
                overlapSeconds += intervalDurationSeconds(overlapStartTime, overlapEndTime);
            }
            if (leftInterval[1].before(rightInterval[1])) {
                leftIndex++;
            } else {
                rightIndex++;
            }
        }
        return Math.max(overlapSeconds, 0L);
    }

    private static long intervalDurationSeconds(Date startTime, Date endTime) {
        if (Objects.isNull(startTime) || Objects.isNull(endTime) || !startTime.before(endTime)) {
            return 0L;
        }
        return (endTime.getTime() - startTime.getTime()) / 1000L;
    }

    private static Date later(Date left, Date right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.after(right) ? left : right;
    }

    private static Date earlier(Date left, Date right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.before(right) ? left : right;
    }
}
