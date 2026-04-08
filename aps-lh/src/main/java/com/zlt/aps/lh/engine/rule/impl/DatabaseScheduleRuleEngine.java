package com.zlt.aps.lh.engine.rule.impl;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.entity.LhParams;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.engine.rule.IScheduleRuleEngine;
import com.zlt.aps.lh.mapper.LhParamsMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于数据库的排程规则引擎实现
 * <p>从LhParams表读取规则配置，支持动态更新</p>
 *
 * @author APS
 */
@Slf4j
@Component
@Deprecated
public class DatabaseScheduleRuleEngine implements IScheduleRuleEngine {

    @Resource
    private LhParamsMapper paramsMapper;

    /** 参数缓存（工厂级别） */
    private final Map<String, Map<String, String>> paramsCache = new ConcurrentHashMap<>();

    /** 缓存过期时间（毫秒） */
    private static final long CACHE_EXPIRE_MS = 5 * 60 * 1000; // 5分钟

    /** 缓存时间戳 */
    private final Map<String, Long> cacheTimestamp = new ConcurrentHashMap<>();

    // ======================== 换模相关规则 ========================

    @Override
    public int getDailyMouldChangeLimit(String factoryCode) {
        return getIntValue(factoryCode, "DAILY_MOULD_CHANGE_LIMIT",
                LhScheduleConstant.DEFAULT_DAILY_MOULD_CHANGE_LIMIT);
    }

    @Override
    public int getMorningMouldChangeLimit(String factoryCode) {
        return getIntValue(factoryCode, "MORNING_MOULD_CHANGE_LIMIT",
                LhScheduleConstant.DEFAULT_MORNING_MOULD_CHANGE_LIMIT);
    }

    @Override
    public int getAfternoonMouldChangeLimit(String factoryCode) {
        return getIntValue(factoryCode, "AFTERNOON_MOULD_CHANGE_LIMIT",
                LhScheduleConstant.DEFAULT_AFTERNOON_MOULD_CHANGE_LIMIT);
    }

    @Override
    public int getMouldChangePreheatHours(String factoryCode) {
        return getIntValue(factoryCode, "MOULD_CHANGE_PREHEAT_HOURS",
                LhScheduleConstant.MOULD_CHANGE_PREHEAT_HOURS);
    }

    @Override
    public int getMouldChangeOtherHours(String factoryCode) {
        return getIntValue(factoryCode, "MOULD_CHANGE_OTHER_HOURS",
                LhScheduleConstant.MOULD_CHANGE_OTHER_HOURS);
    }

    @Override
    public int getMouldChangeTotalHours(String factoryCode) {
        return getIntValue(factoryCode, "MOULD_CHANGE_TOTAL_HOURS",
                LhScheduleConstant.MOULD_CHANGE_TOTAL_HOURS);
    }

    // ======================== 首检相关规则 ========================

    @Override
    public int getFirstInspectionHours(String factoryCode) {
        return getIntValue(factoryCode, "FIRST_INSPECTION_HOURS",
                LhScheduleConstant.FIRST_INSPECTION_HOURS);
    }

    @Override
    public int getMaxFirstInspectionPerShift(String factoryCode) {
        return getIntValue(factoryCode, "MAX_FIRST_INSPECTION_PER_SHIFT",
                LhScheduleConstant.MAX_FIRST_INSPECTION_PER_SHIFT);
    }

    // ======================== 产能相关规则 ========================

    @Override
    public int getShiftDurationHours(String factoryCode) {
        return getIntValue(factoryCode, "SHIFT_DURATION_HOURS",
                LhScheduleConstant.SHIFT_DURATION_HOURS);
    }

    @Override
    public double getShiftEfficiencyFactor(String factoryCode, String shiftType) {
        String key = "SHIFT_EFFICIENCY_" + shiftType.toUpperCase();
        return getDoubleValue(factoryCode, key, 1.0);
    }

    // ======================== 时间窗口规则 ========================

    @Override
    public int getNoMouldChangeStartHour(String factoryCode) {
        return getIntValue(factoryCode, "NO_MOULD_CHANGE_START_HOUR",
                LhScheduleConstant.NO_MOULD_CHANGE_START_HOUR);
    }

    @Override
    public int getNoMouldChangeEndHour(String factoryCode) {
        return getIntValue(factoryCode, "NO_MOULD_CHANGE_END_HOUR",
                LhScheduleConstant.NO_MOULD_CHANGE_END_HOUR);
    }

    // ======================== 排程参数 ========================

    @Override
    public int getScheduleDays(String factoryCode) {
        return getIntValue(factoryCode, "SCHEDULE_DAYS",
                LhScheduleConstant.SCHEDULE_DAYS);
    }

    @Override
    public int getEndingDetectDays(String factoryCode) {
        return getIntValue(factoryCode, "ENDING_DETECT_DAYS",
                LhScheduleConstant.DEFAULT_ENDING_DAYS);
    }

    @Override
    public int getEndingTimeToleranceMinutes(String factoryCode) {
        return getIntValue(factoryCode, "ENDING_TIME_TOLERANCE_MINUTES",
                LhScheduleConstant.DEFAULT_ENDING_TIME_TOLERANCE_MINUTES);
    }

    // ======================== 干冰清洗规则 ========================

    @Override
    public int getDryIceIntervalDays(String factoryCode) {
        return getIntValue(factoryCode, "DRY_ICE_INTERVAL_DAYS",
                LhScheduleConstant.DRY_ICE_INTERVAL_DAYS);
    }

    @Override
    public int getDryIceWarningDays(String factoryCode) {
        return getIntValue(factoryCode, "DRY_ICE_WARNING_DAYS",
                LhScheduleConstant.DRY_ICE_WARNING_DAYS);
    }

    @Override
    public int getDryIceAdvanceDays(String factoryCode) {
        return getIntValue(factoryCode, "DRY_ICE_ADVANCE_DAYS",
                LhScheduleConstant.DRY_ICE_ADVANCE_DAYS);
    }

    @Override
    public int getDryIceDurationHours(String factoryCode) {
        return getIntValue(factoryCode, "DRY_ICE_DURATION_HOURS",
                LhScheduleConstant.DRY_ICE_DURATION_HOURS);
    }

    @Override
    public int getDryIceLossQty(String factoryCode) {
        return getIntValue(factoryCode, "DRY_ICE_LOSS_QTY",
                LhScheduleConstant.DRY_ICE_LOSS_QTY);
    }

    @Override
    public int getDryIceDailyLimit(String factoryCode) {
        return getIntValue(factoryCode, "DRY_ICE_DAILY_LIMIT",
                LhScheduleConstant.DRY_ICE_DAILY_LIMIT);
    }

    // ======================== 喷砂清洗规则 ========================

    @Override
    public int getSandBlastDurationHours(String factoryCode) {
        return getIntValue(factoryCode, "SAND_BLAST_DURATION_HOURS",
                LhScheduleConstant.SAND_BLAST_DURATION_HOURS);
    }

    @Override
    public int getSandBlastWithInspectionHours(String factoryCode) {
        return getIntValue(factoryCode, "SAND_BLAST_WITH_INSPECTION_HOURS",
                LhScheduleConstant.SAND_BLAST_WITH_INSPECTION_HOURS);
    }

    @Override
    public int getSandBlastDailyLimit(String factoryCode) {
        return getIntValue(factoryCode, "SAND_BLAST_DAILY_LIMIT",
                LhScheduleConstant.SAND_BLAST_DAILY_LIMIT);
    }

    @Override
    public int getSandBlastMaintenanceDayMid(String factoryCode) {
        return getIntValue(factoryCode, "SAND_BLAST_MAINTENANCE_DAY_MID",
                LhScheduleConstant.SAND_BLAST_MAINTENANCE_DAY_MID);
    }

    @Override
    public int getSandBlastMaintenanceDayEnd(String factoryCode) {
        return getIntValue(factoryCode, "SAND_BLAST_MAINTENANCE_DAY_END",
                LhScheduleConstant.SAND_BLAST_MAINTENANCE_DAY_END);
    }

    // ======================== 设备保养规则 ========================

    @Override
    public int getMaintenanceDurationHours(String factoryCode) {
        return getIntValue(factoryCode, "MAINTENANCE_DURATION_HOURS",
                LhScheduleConstant.MAINTENANCE_DURATION_HOURS);
    }

    @Override
    public int getMaintenanceStartHour(String factoryCode) {
        return getIntValue(factoryCode, "MAINTENANCE_START_HOUR",
                LhScheduleConstant.MAINTENANCE_START_HOUR);
    }

    @Override
    public int getMaintenanceWarningDays(String factoryCode) {
        return getIntValue(factoryCode, "MAINTENANCE_WARNING_DAYS",
                LhScheduleConstant.MAINTENANCE_WARNING_DAYS);
    }

    @Override
    public double getCapsulePreheatHours(String factoryCode) {
        String value = getParamValue(factoryCode, "CAPSULE_PREHEAT_HOURS");
        if (value == null || value.trim().isEmpty()) {
            return LhScheduleConstant.CAPSULE_PREHEAT_HOURS.doubleValue();
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("参数解析失败，factoryCode={}, paramCode={}, value={}, 使用默认值：{}",
                    factoryCode, "CAPSULE_PREHEAT_HOURS", value, LhScheduleConstant.CAPSULE_PREHEAT_HOURS);
            return LhScheduleConstant.CAPSULE_PREHEAT_HOURS.doubleValue();
        }
    }

    // ======================== 停机超时阈值 ========================

    @Override
    public int getMachineStopTimeoutHours(String factoryCode) {
        return getIntValue(factoryCode, "MACHINE_STOP_TIMEOUT_HOURS",
                LhScheduleConstant.MACHINE_STOP_TIMEOUT_HOURS);
    }

    // ======================== 胶囊相关规则 ========================

    @Override
    public int getCapsuleWarningCount(String factoryCode) {
        return getIntValue(factoryCode, "CAPSULE_WARNING_COUNT",
                LhScheduleConstant.CAPSULE_WARNING_COUNT);
    }

    @Override
    public int getCapsuleForceDownCount(String factoryCode) {
        return getIntValue(factoryCode, "CAPSULE_FORCE_DOWN_COUNT",
                LhScheduleConstant.CAPSULE_FORCE_DOWN_COUNT);
    }

    @Override
    public int getCapsuleChangeLossQty(String factoryCode) {
        return getIntValue(factoryCode, "CAPSULE_CHANGE_LOSS_QTY",
                LhScheduleConstant.CAPSULE_CHANGE_LOSS_QTY);
    }

    // ======================== 开停产比例 ========================

    @Override
    public int getShutdownDayMinus3Rate(String factoryCode) {
        return getIntValue(factoryCode, "SHUTDOWN_DAY_MINUS_3_RATE",
                LhScheduleConstant.SHUTDOWN_DAY_MINUS_3_RATE);
    }

    @Override
    public int getShutdownDayMinus2Rate(String factoryCode) {
        return getIntValue(factoryCode, "SHUTDOWN_DAY_MINUS_2_RATE",
                LhScheduleConstant.SHUTDOWN_DAY_MINUS_2_RATE);
    }

    @Override
    public int getShutdownDayMinus1Rate(String factoryCode) {
        return getIntValue(factoryCode, "SHUTDOWN_DAY_MINUS_1_RATE",
                LhScheduleConstant.SHUTDOWN_DAY_MINUS_1_RATE);
    }

    @Override
    public int getStartupFirstDayRate(String factoryCode) {
        return getIntValue(factoryCode, "STARTUP_FIRST_DAY_RATE",
                LhScheduleConstant.STARTUP_FIRST_DAY_RATE);
    }

    // ======================== 试制量试规则 ========================

    @Override
    public int getTrialDailyLimit(String factoryCode) {
        return getIntValue(factoryCode, "TRIAL_DAILY_LIMIT",
                LhScheduleConstant.TRIAL_DAILY_LIMIT);
    }

    // ======================== 模具交替计划规则 ========================

    @Override
    public int getMouldChangePlanDays(String factoryCode) {
        return getIntValue(factoryCode, "MOULD_CHANGE_PLAN_DAYS",
                LhScheduleConstant.MOULD_CHANGE_PLAN_DAYS);
    }

    // ======================== 收尾判定规则 ========================

    @Override
    public int getStructureEndingDays(String factoryCode) {
        return getIntValue(factoryCode, "STRUCTURE_ENDING_DAYS",
                LhScheduleConstant.DEFAULT_STRUCTURE_ENDING_DAYS);
    }

    // ======================== 私有辅助方法 ========================

    private int getIntValue(String factoryCode, String paramCode, int defaultValue) {
        String value = getParamValue(factoryCode, paramCode);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("参数解析失败, factoryCode={}, paramCode={}, value={}, 使用默认值: {}",
                    factoryCode, paramCode, value, defaultValue);
            return defaultValue;
        }
    }

    private double getDoubleValue(String factoryCode, String paramCode, double defaultValue) {
        String value = getParamValue(factoryCode, paramCode);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("参数解析失败, factoryCode={}, paramCode={}, value={}, 使用默认值: {}",
                    factoryCode, paramCode, value, defaultValue);
            return defaultValue;
        }
    }

    private String getParamValue(String factoryCode, String paramCode) {
        Long lastUpdate = cacheTimestamp.get(factoryCode);
        if (lastUpdate == null || System.currentTimeMillis() - lastUpdate > CACHE_EXPIRE_MS) {
            refreshCache(factoryCode);
        }
        Map<String, String> factoryParams = paramsCache.get(factoryCode);
        return factoryParams != null ? factoryParams.get(paramCode) : null;
    }

    private void refreshCache(String factoryCode) {
        try {
            // 从数据库加载参数列表
            List<LhParams> paramsList = paramsMapper.selectList(
                    new LambdaQueryWrapper<LhParams>()
                            .eq(LhParams::getFactoryCode, factoryCode)
                            .eq(LhParams::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));

            // 转换为Map
            Map<String, String> params = new HashMap<>();
            if (paramsList != null) {
                for (LhParams param : paramsList) {
                    if (param.getParamCode() != null && param.getParamValue() != null) {
                        params.put(param.getParamCode(), param.getParamValue());
                    }
                }
            }

            paramsCache.put(factoryCode, params);
            cacheTimestamp.put(factoryCode, System.currentTimeMillis());
            log.debug("刷新参数缓存, factoryCode={}, 参数数量: {}", factoryCode, params.size());
        } catch (Exception e) {
            log.error("刷新参数缓存失败, factoryCode={}", factoryCode, e);
        }
    }

    public void clearCache(String factoryCode) {
        if (factoryCode == null) {
            paramsCache.clear();
            cacheTimestamp.clear();
        } else {
            paramsCache.remove(factoryCode);
            cacheTimestamp.remove(factoryCode);
        }
        log.info("清除参数缓存, factoryCode={}", factoryCode);
    }
}
