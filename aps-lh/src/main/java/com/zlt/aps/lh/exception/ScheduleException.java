package com.zlt.aps.lh.exception;

import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import lombok.Getter;

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

    public ScheduleException(ScheduleErrorCode errorCode, String message) {
        super(message);
        this.step = null;
        this.errorCode = errorCode;
        this.factoryCode = null;
        this.batchNo = null;
    }

    public ScheduleException(ScheduleStepEnum step, ScheduleErrorCode errorCode, String message) {
        super(message);
        this.step = step;
        this.errorCode = errorCode;
        this.factoryCode = null;
        this.batchNo = null;
    }

    public ScheduleException(ScheduleStepEnum step, ScheduleErrorCode errorCode, 
                             String factoryCode, String batchNo, String message) {
        super(message);
        this.step = step;
        this.errorCode = errorCode;
        this.factoryCode = factoryCode;
        this.batchNo = batchNo;
    }

    public ScheduleException(ScheduleStepEnum step, ScheduleErrorCode errorCode, 
                             String message, Throwable cause) {
        super(message, cause);
        this.step = step;
        this.errorCode = errorCode;
        this.factoryCode = null;
        this.batchNo = null;
    }

    /**
     * 构建格式化的错误消息
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
        if (factoryCode != null) {
            sb.append(", factory=").append(factoryCode);
        }
        if (batchNo != null) {
            sb.append(", batchNo=").append(batchNo);
        }
        return sb.toString();
    }
}
