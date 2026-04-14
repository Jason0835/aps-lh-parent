package com.zlt.aps.lh.api.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 工厂编码枚举
 *
 * @author APS
 */
public enum FactoryCodeEnum {

    /** 越南工厂 */
    VIETNAM("116", "越南"),
    /** 泰国工厂 */
    THAILAND("117", "泰国");

    /** 工厂编码 */
    private final String code;

    /** 工厂名称 */
    private final String name;

    FactoryCodeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /**
     * 按工厂编码获取工厂名称
     *
     * @param factoryCode 工厂编码
     * @return 工厂名称，未匹配时返回原始工厂编码
     */
    public static String getFactoryNameByCode(String factoryCode) {
        if (StringUtils.isEmpty(factoryCode)) {
            return factoryCode;
        }
        for (FactoryCodeEnum e : values()) {
            if (e.getCode().equals(factoryCode)) {
                return e.getName();
            }
        }
        return factoryCode;
    }
}
