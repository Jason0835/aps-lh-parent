package com.zlt.aps.lh.context;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 硫化排程配置快照
 * <p>统一承载本次排程已解析的配置值，优先级：LhParams > 常量默认值</p>
 *
 * @author APS
 */
public class LhScheduleConfig {

    /** 已解析的参数快照（值均为字符串，按需转换） */
    private final Map<String, String> resolvedParamMap;

    /**
     * 构造配置快照
     *
     * @param resolvedParamMap 已解析参数
     */
    public LhScheduleConfig(Map<String, String> resolvedParamMap) {
        this.resolvedParamMap = new HashMap<>(resolvedParamMap);
    }

    /**
     * 获取已解析参数快照
     *
     * @return 参数快照
     */
    public Map<String, String> getResolvedParamMap() {
        return Collections.unmodifiableMap(resolvedParamMap);
    }

    /**
     * 获取字符串参数值
     *
     * @param paramCode    参数编码
     * @param defaultValue 默认值
     * @return 参数值
     */
    public String getParamValue(String paramCode, String defaultValue) {
        String value = resolvedParamMap.get(paramCode);
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }

    /**
     * 获取整数参数值
     *
     * @param paramCode    参数编码
     * @param defaultValue 默认值
     * @return 参数值
     */
    public int getParamIntValue(String paramCode, int defaultValue) {
        String value = resolvedParamMap.get(paramCode);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取浮点参数值
     *
     * @param paramCode    参数编码
     * @param defaultValue 默认值
     * @return 参数值
     */
    public double getParamDoubleValue(String paramCode, double defaultValue) {
        String value = resolvedParamMap.get(paramCode);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 以下为常用业务参数的强类型快捷读取，避免调用方重复传参数编码与默认值。
     */
    public int getScheduleDays() {
        return Math.max(1, getParamIntValue(LhScheduleParamConstant.SCHEDULE_DAYS, LhScheduleConstant.SCHEDULE_DAYS));
    }

    public int getNightStartHour() {
        return getParamIntValue(LhScheduleParamConstant.NIGHT_START_HOUR, LhScheduleConstant.NIGHT_SHIFT_START_HOUR);
    }

    public int getMorningStartHour() {
        return getParamIntValue(LhScheduleParamConstant.MORNING_START_HOUR, LhScheduleConstant.MORNING_SHIFT_START_HOUR);
    }

    public int getAfternoonStartHour() {
        return getParamIntValue(LhScheduleParamConstant.AFTERNOON_START_HOUR, LhScheduleConstant.AFTERNOON_SHIFT_START_HOUR);
    }

    public int getShiftDurationHours() {
        return getParamIntValue(LhScheduleParamConstant.SHIFT_DURATION_HOURS, LhScheduleConstant.SHIFT_DURATION_HOURS);
    }

    public int getNoMouldChangeStartHour() {
        return getParamIntValue(LhScheduleParamConstant.NO_MOULD_CHANGE_START_HOUR, LhScheduleConstant.NO_MOULD_CHANGE_START_HOUR);
    }

    public int getNoMouldChangeEndHour() {
        return getParamIntValue(LhScheduleParamConstant.NO_MOULD_CHANGE_END_HOUR, LhScheduleConstant.NO_MOULD_CHANGE_END_HOUR);
    }

    public int getDailyMouldChangeLimit() {
        return getParamIntValue(LhScheduleParamConstant.DAILY_MOULD_CHANGE_LIMIT, LhScheduleConstant.DEFAULT_DAILY_MOULD_CHANGE_LIMIT);
    }

    public int getMorningMouldChangeLimit() {
        return getParamIntValue(LhScheduleParamConstant.MORNING_MOULD_CHANGE_LIMIT, LhScheduleConstant.DEFAULT_MORNING_MOULD_CHANGE_LIMIT);
    }

    public int getAfternoonMouldChangeLimit() {
        return getParamIntValue(LhScheduleParamConstant.AFTERNOON_MOULD_CHANGE_LIMIT, LhScheduleConstant.DEFAULT_AFTERNOON_MOULD_CHANGE_LIMIT);
    }

    public int getMouldChangePreheatHours() {
        return getParamIntValue(LhScheduleParamConstant.MOULD_CHANGE_PREHEAT_HOURS, LhScheduleConstant.MOULD_CHANGE_PREHEAT_HOURS);
    }

    public int getMouldChangeOtherHours() {
        return getParamIntValue(LhScheduleParamConstant.MOULD_CHANGE_OTHER_HOURS, LhScheduleConstant.MOULD_CHANGE_OTHER_HOURS);
    }

    public int getMouldChangeTotalHours() {
        return getParamIntValue(LhScheduleParamConstant.MOULD_CHANGE_TOTAL_HOURS, LhScheduleConstant.MOULD_CHANGE_TOTAL_HOURS);
    }

    public int getFirstInspectionHours() {
        return getParamIntValue(LhScheduleParamConstant.FIRST_INSPECTION_HOURS, LhScheduleConstant.FIRST_INSPECTION_HOURS);
    }

    public int getMaxFirstInspectionPerShift() {
        return getParamIntValue(LhScheduleParamConstant.MAX_FIRST_INSPECTION_PER_SHIFT, LhScheduleConstant.MAX_FIRST_INSPECTION_PER_SHIFT);
    }

    public int getEndingDetectDays() {
        return getParamIntValue(LhScheduleParamConstant.ENDING_DETECT_DAYS, LhScheduleConstant.DEFAULT_ENDING_DAYS);
    }

    public int getStructureEndingDays() {
        return getParamIntValue(LhScheduleParamConstant.STRUCTURE_ENDING_DAYS, LhScheduleConstant.DEFAULT_STRUCTURE_ENDING_DAYS);
    }

    public int getEndingTimeToleranceMinutes() {
        return getParamIntValue(LhScheduleParamConstant.ENDING_TIME_TOLERANCE_MINUTES,
                LhScheduleConstant.DEFAULT_ENDING_TIME_TOLERANCE_MINUTES);
    }

    public int getDryIceDurationHours() {
        return getParamIntValue(LhScheduleParamConstant.DRY_ICE_DURATION_HOURS, LhScheduleConstant.DRY_ICE_DURATION_HOURS);
    }

    public int getSandBlastWithInspectionHours() {
        return getParamIntValue(LhScheduleParamConstant.SAND_BLAST_WITH_INSPECTION_HOURS,
                LhScheduleConstant.SAND_BLAST_WITH_INSPECTION_HOURS);
    }

    public int getMaintenanceDurationHours() {
        return getParamIntValue(LhScheduleParamConstant.MAINTENANCE_DURATION_HOURS, LhScheduleConstant.MAINTENANCE_DURATION_HOURS);
    }

    public int getMaintenanceStartHour() {
        return getParamIntValue(LhScheduleParamConstant.MAINTENANCE_START_HOUR, LhScheduleConstant.MAINTENANCE_START_HOUR);
    }

    public double getCapsulePreheatHours() {
        return getParamDoubleValue(LhScheduleParamConstant.CAPSULE_PREHEAT_HOURS,
                LhScheduleConstant.CAPSULE_PREHEAT_HOURS.doubleValue());
    }

    public int getMachineStopTimeoutHours() {
        return getParamIntValue(LhScheduleParamConstant.MACHINE_STOP_TIMEOUT_HOURS, LhScheduleConstant.MACHINE_STOP_TIMEOUT_HOURS);
    }

    public int getShutdownDayMinus3Rate() {
        return getParamIntValue(LhScheduleParamConstant.SHUTDOWN_DAY_MINUS_3_RATE, LhScheduleConstant.SHUTDOWN_DAY_MINUS_3_RATE);
    }

    public int getShutdownDayMinus2Rate() {
        return getParamIntValue(LhScheduleParamConstant.SHUTDOWN_DAY_MINUS_2_RATE, LhScheduleConstant.SHUTDOWN_DAY_MINUS_2_RATE);
    }

    public int getShutdownDayMinus1Rate() {
        return getParamIntValue(LhScheduleParamConstant.SHUTDOWN_DAY_MINUS_1_RATE, LhScheduleConstant.SHUTDOWN_DAY_MINUS_1_RATE);
    }

    public int getStartupFirstDayRate() {
        return getParamIntValue(LhScheduleParamConstant.STARTUP_FIRST_DAY_RATE, LhScheduleConstant.STARTUP_FIRST_DAY_RATE);
    }

    public int getTrialDailyLimit() {
        return getParamIntValue(LhScheduleParamConstant.TRIAL_DAILY_LIMIT, LhScheduleConstant.TRIAL_DAILY_LIMIT);
    }

    public boolean isLocalSearchEnabled() {
        return getParamIntValue(LhScheduleParamConstant.ENABLE_LOCAL_SEARCH, LhScheduleConstant.ENABLE_LOCAL_SEARCH) == 1;
    }

    public int getLocalSearchMachineThreshold() {
        return Math.max(1, getParamIntValue(LhScheduleParamConstant.LOCAL_SEARCH_MACHINE_THRESHOLD,
                LhScheduleConstant.LOCAL_SEARCH_MACHINE_THRESHOLD));
    }

    public int getLocalSearchDepth() {
        return Math.max(1, getParamIntValue(LhScheduleParamConstant.LOCAL_SEARCH_DEPTH,
                LhScheduleConstant.LOCAL_SEARCH_DEPTH));
    }

    public int getLocalSearchTimeBudgetMs() {
        return Math.max(1, getParamIntValue(LhScheduleParamConstant.LOCAL_SEARCH_TIME_BUDGET_MS,
                LhScheduleConstant.LOCAL_SEARCH_TIME_BUDGET_MS));
    }
}
