package com.zlt.aps.lh.util;

import java.util.Objects;

/**
 * 模具状态工具类
 * <p>统一处理模具状态码判定，避免硬编码导致语义不一致。</p>
 */
public final class MouldStatusUtil {

    /** 启用状态码（字典：biz_available_status） */
    public static final Integer STATUS_ENABLED = 1;

    private MouldStatusUtil() {
    }

    /**
     * 判断模具状态是否启用
     *
     * @param mouldStatus 模具状态码
     * @return true 表示启用，false 表示未启用
     */
    public static boolean isEnabled(Integer mouldStatus) {
        return Objects.nonNull(mouldStatus) && STATUS_ENABLED.equals(mouldStatus);
    }
}
