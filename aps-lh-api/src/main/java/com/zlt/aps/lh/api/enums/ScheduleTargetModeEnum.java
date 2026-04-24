package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 排产目标量模式枚举
 *
 * @author APS
 */
@Getter
@AllArgsConstructor
public enum ScheduleTargetModeEnum {

    /** 按产能满排 */
    CAPACITY_FULL("1", "按产能满排"),
    /** 按需求排产 */
    DEMAND_DRIVEN("0", "按需求排产");

    /** 模式编码 */
    private final String code;

    /** 模式描述 */
    private final String description;

    /**
     * 根据编码解析排产目标量模式。
     *
     * @param code 模式编码
     * @return 排产目标量模式，缺失时默认按产能满排
     */
    public static ScheduleTargetModeEnum fromCode(String code) {
        if (StringUtils.isEmpty(code)) {
            return CAPACITY_FULL;
        }
        for (ScheduleTargetModeEnum modeEnum : values()) {
            if (StringUtils.equals(modeEnum.getCode(), code)) {
                return modeEnum;
            }
        }
        return CAPACITY_FULL;
    }
}
