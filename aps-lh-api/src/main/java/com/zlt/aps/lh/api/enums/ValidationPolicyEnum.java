package com.zlt.aps.lh.api.enums;

/**
 * 数据校验组内执行策略
 * <p>同一校验组（group）下所有校验器须返回相同策略。</p>
 *
 * @author APS
 */
public enum ValidationPolicyEnum {

    /**
     * 组内校验器全部执行，错误写入上下文的校验错误列表后再统一判定
     */
    COLLECT_ALL,

    /**
     * 组内按顺序执行，首次校验失败即停止本组及后续所有组
     */
    FAIL_FAST
}
