package com.zlt.aps.lh.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 机台状态工具类
 * <p>统一处理机台状态码判定，避免各处硬编码导致语义不一致。</p>
 *
 * @author APS
 */
public final class MachineStatusUtil {

    /** 启用状态码（字典：sys_enable_disable，1-启用，0-停用） */
    public static final String STATUS_ENABLED = "1";

    private MachineStatusUtil() {
    }

    /**
     * 判断机台状态是否启用
     * <p>数据库字段为 char(1) 时可能出现补空格，比较前统一 trim。</p>
     *
     * @param status 机台状态码
     * @return true 表示启用，false 表示未启用
     */
    public static boolean isEnabled(String status) {
        if (StringUtils.isEmpty(status)) {
            return false;
        }
        return STATUS_ENABLED.equals(StringUtils.trim(status));
    }
}
