/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 施工阶段枚举
 *
 * @author zlt
 */
@Getter
@AllArgsConstructor
public enum ConstructionStageEnum {

    NO_PROCESS("00", "无工艺"),
    TRIAL("01", "试制"),
    MASS_TRIAL("02", "量试"),
    FORMAL("03", "正式");

    /** 阶段编码 */
    private final String code;

    /** 阶段描述 */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 阶段编码
     * @return 施工阶段枚举，未找到返回null
     */
    public static ConstructionStageEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ConstructionStageEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
