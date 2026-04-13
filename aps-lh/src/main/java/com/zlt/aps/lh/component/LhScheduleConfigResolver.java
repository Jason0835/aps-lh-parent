package com.zlt.aps.lh.component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.constant.LhScheduleParamConstant;
import com.zlt.aps.lh.api.domain.entity.LhParams;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.mapper.LhParamsMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 硫化排程配置解析器
 * <p>统一将 LhParams 与常量默认值解析为排程配置快照</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class LhScheduleConfigResolver {

    private static final int DEFAULT_PARAM_CAPACITY = 48;

    @Resource
    private LhParamsMapper lhParamsMapper;

    /**
     * 解析并挂载排程配置
     *
     * @param context 排程上下文
     */
    public void resolveAndAttach(LhScheduleContext context) {
        Map<String, String> lhParamsMap = loadLhParams(context.getFactoryCode());
        context.setLhParamsMap(new HashMap<>(lhParamsMap));
        context.setScheduleConfig(buildConfig(lhParamsMap));
    }

    private Map<String, String> loadLhParams(String factoryCode) {
        Map<String, String> lhParamsMap = new HashMap<>(DEFAULT_PARAM_CAPACITY);
        List<LhParams> paramsList = lhParamsMapper.selectList(
                new LambdaQueryWrapper<LhParams>()
                        .eq(LhParams::getFactoryCode, factoryCode)
                        .eq(LhParams::getIsDelete, DeleteFlagEnum.NORMAL.getCode()));
        if (paramsList != null) {
            for (LhParams param : paramsList) {
                if (StringUtils.isNotEmpty(param.getParamCode()) && StringUtils.isNotEmpty(param.getParamValue())) {
                    lhParamsMap.put(param.getParamCode(), param.getParamValue());
                }
            }
        }
        log.info("硫化参数加载完成, 工厂: {}, 参数数量: {}", factoryCode, lhParamsMap.size());
        return lhParamsMap;
    }

    private LhScheduleConfig buildConfig(Map<String, String> lhParamsMap) {
        Map<String, String> resolvedParamMap = new HashMap<>(DEFAULT_PARAM_CAPACITY);

        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.NIGHT_START_HOUR, LhScheduleConstant.NIGHT_SHIFT_START_HOUR);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.MORNING_START_HOUR, LhScheduleConstant.MORNING_SHIFT_START_HOUR);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.AFTERNOON_START_HOUR, LhScheduleConstant.AFTERNOON_SHIFT_START_HOUR);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.SHIFT_DURATION_HOURS, LhScheduleConstant.SHIFT_DURATION_HOURS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.NO_MOULD_CHANGE_START_HOUR,
                LhScheduleConstant.NO_MOULD_CHANGE_START_HOUR);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.NO_MOULD_CHANGE_END_HOUR,
                LhScheduleConstant.NO_MOULD_CHANGE_END_HOUR);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.DAILY_MOULD_CHANGE_LIMIT,
                LhScheduleConstant.DEFAULT_DAILY_MOULD_CHANGE_LIMIT);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.MORNING_MOULD_CHANGE_LIMIT,
                LhScheduleConstant.DEFAULT_MORNING_MOULD_CHANGE_LIMIT);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.AFTERNOON_MOULD_CHANGE_LIMIT,
                LhScheduleConstant.DEFAULT_AFTERNOON_MOULD_CHANGE_LIMIT);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.MOULD_CHANGE_PREHEAT_HOURS,
                LhScheduleConstant.MOULD_CHANGE_PREHEAT_HOURS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.MOULD_CHANGE_OTHER_HOURS,
                LhScheduleConstant.MOULD_CHANGE_OTHER_HOURS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.MOULD_CHANGE_TOTAL_HOURS,
                LhScheduleConstant.MOULD_CHANGE_TOTAL_HOURS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.FIRST_INSPECTION_HOURS,
                LhScheduleConstant.FIRST_INSPECTION_HOURS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.MAX_FIRST_INSPECTION_PER_SHIFT,
                LhScheduleConstant.MAX_FIRST_INSPECTION_PER_SHIFT);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.ENDING_DETECT_DAYS, LhScheduleConstant.DEFAULT_ENDING_DAYS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.STRUCTURE_ENDING_DAYS,
                LhScheduleConstant.DEFAULT_STRUCTURE_ENDING_DAYS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.ENDING_TIME_TOLERANCE_MINUTES,
                LhScheduleConstant.DEFAULT_ENDING_TIME_TOLERANCE_MINUTES);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.DRY_ICE_DURATION_HOURS, LhScheduleConstant.DRY_ICE_DURATION_HOURS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.SAND_BLAST_WITH_INSPECTION_HOURS,
                LhScheduleConstant.SAND_BLAST_WITH_INSPECTION_HOURS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.MAINTENANCE_DURATION_HOURS,
                LhScheduleConstant.MAINTENANCE_DURATION_HOURS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.MAINTENANCE_START_HOUR,
                LhScheduleConstant.MAINTENANCE_START_HOUR);
        putDoubleValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.CAPSULE_PREHEAT_HOURS,
                LhScheduleConstant.CAPSULE_PREHEAT_HOURS.doubleValue());
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.SCHEDULE_DAYS, LhScheduleConstant.SCHEDULE_DAYS, 1);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.MACHINE_STOP_TIMEOUT_HOURS,
                LhScheduleConstant.MACHINE_STOP_TIMEOUT_HOURS);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.SHUTDOWN_DAY_MINUS_3_RATE,
                LhScheduleConstant.SHUTDOWN_DAY_MINUS_3_RATE);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.SHUTDOWN_DAY_MINUS_2_RATE,
                LhScheduleConstant.SHUTDOWN_DAY_MINUS_2_RATE);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.SHUTDOWN_DAY_MINUS_1_RATE,
                LhScheduleConstant.SHUTDOWN_DAY_MINUS_1_RATE);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.STARTUP_FIRST_DAY_RATE,
                LhScheduleConstant.STARTUP_FIRST_DAY_RATE);
        putIntValue(resolvedParamMap, lhParamsMap, LhScheduleParamConstant.TRIAL_DAILY_LIMIT, LhScheduleConstant.TRIAL_DAILY_LIMIT);

        return new LhScheduleConfig(resolvedParamMap);
    }

    private void putIntValue(Map<String, String> resolvedParamMap, Map<String, String> lhParamsMap, String paramCode, int defaultValue) {
        putIntValue(resolvedParamMap, lhParamsMap, paramCode, defaultValue, null);
    }

    private void putIntValue(Map<String, String> resolvedParamMap, Map<String, String> lhParamsMap,
            String paramCode, int defaultValue, Integer minValue) {
        int resolvedValue = defaultValue;
        String value = lhParamsMap.get(paramCode);
        if (StringUtils.isNotEmpty(value)) {
            try {
                resolvedValue = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                log.warn("硫化参数解析失败, paramCode={}, value={}, 使用默认值: {}", paramCode, value, defaultValue);
                resolvedValue = defaultValue;
            }
        }
        if (minValue != null && resolvedValue < minValue) {
            resolvedValue = minValue;
        }
        resolvedParamMap.put(paramCode, String.valueOf(resolvedValue));
    }

    private void putDoubleValue(Map<String, String> resolvedParamMap, Map<String, String> lhParamsMap,
            String paramCode, double defaultValue) {
        double resolvedValue = defaultValue;
        String value = lhParamsMap.get(paramCode);
        if (StringUtils.isNotEmpty(value)) {
            try {
                resolvedValue = Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                log.warn("硫化参数解析失败, paramCode={}, value={}, 使用默认值: {}", paramCode, value, defaultValue);
                resolvedValue = defaultValue;
            }
        }
        resolvedParamMap.put(paramCode, BigDecimal.valueOf(resolvedValue).stripTrailingZeros().toPlainString());
    }
}
