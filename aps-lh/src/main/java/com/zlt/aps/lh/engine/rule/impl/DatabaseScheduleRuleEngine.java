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
