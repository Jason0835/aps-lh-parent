package com.zlt.aps.lh.exception;

import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 排程领域异常
 * <p>统一异常处理，携带排程上下文信息</p>
 *
 * @author APS
 */
@Getter
public class ScheduleException extends RuntimeException {

    /** 当前执行步骤 */
    private final ScheduleStepEnum step;

    /** 错误码 */
    private final ScheduleErrorCode errorCode;

    /** 工厂编码 */
    private final String factoryCode;

    /** 批次号 */
    private final String batchNo;

    /**
     * @param errorCode 错误码枚举
     * @param message   业务详情说明（不含步骤/错误码前缀）
     */
    public ScheduleException(ScheduleErrorCode errorCode, String message) {
        super(message);
        this.step = null;
        this.errorCode = errorCode;
        this.factoryCode = null;
        this.batchNo = null;
    }

    /**
     * @param step      排程步骤
     * @param errorCode 错误码枚举
     * @param message   业务详情说明
     */
    public ScheduleException(ScheduleStepEnum step, ScheduleErrorCode errorCode, String message) {
        super(message);
        this.step = step;
        this.errorCode = errorCode;
        this.factoryCode = null;
        this.batchNo = null;
    }

    /**
     * @param step        排程步骤
     * @param errorCode   错误码枚举
     * @param factoryCode 工厂编码
     * @param batchNo     批次号
     * @param message     业务详情说明
     */
    public ScheduleException(ScheduleStepEnum step, ScheduleErrorCode errorCode,
                             String factoryCode, String batchNo, String message) {
        super(message);
        this.step = step;
        this.errorCode = errorCode;
        this.factoryCode = factoryCode;
        this.batchNo = batchNo;
    }

    /**
     * @param step      排程步骤
     * @param errorCode 错误码枚举
     * @param message   业务详情说明
     * @param cause     底层原因
     */
    public ScheduleException(ScheduleStepEnum step, ScheduleErrorCode errorCode,
                             String message, Throwable cause) {
        super(message, cause);
        this.step = step;
        this.errorCode = errorCode;
        this.factoryCode = null;
        this.batchNo = null;
    }

    /**
     * @param step        排程步骤
     * @param errorCode   错误码枚举
     * @param factoryCode 工厂编码
     * @param batchNo     批次号
     * @param message     业务详情说明
     * @param cause       底层原因
     */
    public ScheduleException(ScheduleStepEnum step, ScheduleErrorCode errorCode,
                             String factoryCode, String batchNo, String message, Throwable cause) {
        super(message, cause);
        this.step = step;
        this.errorCode = errorCode;
        this.factoryCode = factoryCode;
        this.batchNo = batchNo;
    }

    /**
     * 返回面向调用方与日志的完整格式化错误文案（含步骤、错误码、详情及工厂/批次后缀）。
     *
     * @return 格式化后的完整消息
     */
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        if (step != null) {
            sb.append("[").append(step.getCode()).append(" ").append(step.getDescription()).append("] ");
        }
        if (errorCode != null) {
            sb.append(errorCode.getCode()).append(": ");
        }
        sb.append(super.getMessage());
        return sb.toString();
    }
}
