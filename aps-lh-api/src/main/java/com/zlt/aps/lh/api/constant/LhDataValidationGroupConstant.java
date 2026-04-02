package com.zlt.aps.lh.api.constant;

/**
 * 硫化基础数据校验分组编号（数值越小越先执行）
 *
 * @author APS
 */
public final class LhDataValidationGroupConstant {

    private LhDataValidationGroupConstant() {
    }

    /**
     * 基础数据完整性：组内默认使用 {@link com.zlt.aps.lh.api.enums.ValidationPolicyEnum#COLLECT_ALL}
     */
    public static final int BASE_DATA_INTEGRITY = 10;

    /**
     * 强门禁/短路校验组（示例编号，可按业务再分档）
     */
    public static final int CRITICAL_GATE = 20;
}
