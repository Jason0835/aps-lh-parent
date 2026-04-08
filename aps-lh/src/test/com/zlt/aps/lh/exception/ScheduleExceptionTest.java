package com.zlt.aps.lh.exception;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScheduleException} 与 {@link ScheduleDomainExceptionHelper} 消息格式测试
 */
class ScheduleExceptionTest {

    @Test
    void getMessage_包含步骤错误码详情与工厂批次后缀() {
        ScheduleException ex = new ScheduleException(
                ScheduleStepEnum.S4_5_NEW_PRODUCTION,
                ScheduleErrorCode.NO_MACHINE_AVAILABLE,
                "F001",
                "LHPC20260402001",
                "无可用硫化机台");
        String msg = ex.getMessage();
        assertTrue(msg.contains("[S4.5 "));
        assertTrue(msg.contains("新增规格排产"));
        assertTrue(msg.contains("S4501:"));
        assertTrue(msg.contains("无可用硫化机台"));
        assertTrue(msg.contains("factory=F001"));
        assertTrue(msg.contains("batchNo=LHPC20260402001"));
    }

    @Test
    void getMessage_无步骤时仅错误码与详情() {
        ScheduleException ex = new ScheduleException(ScheduleErrorCode.PRODUCTION_STRATEGY_NOT_REGISTERED, "未找到策略");
        String msg = ex.getMessage();
        assertTrue(msg.startsWith("S0002:"));
        assertTrue(msg.contains("未找到策略"));
    }

    @Test
    void 全参构造含Cause时_getMessage仍格式化() {
        RuntimeException cause = new RuntimeException("root");
        ScheduleException ex = new ScheduleException(
                ScheduleStepEnum.S4_1_PRE_VALIDATION,
                ScheduleErrorCode.BATCH_NO_GENERATE_FAILED,
                "FC",
                null,
                "Redis 不可用",
                cause);
        assertTrue(ex.getMessage().contains("S4103:"));
        assertEquals(cause, ex.getCause());
    }

    @Test
    void helper_interrupt_写入上下文的中断原因与异常消息一致() {
        LhScheduleContext ctx = new LhScheduleContext();
        ctx.setFactoryCode("F001");
        ctx.setBatchNo("BATCH-1");
        ScheduleDomainExceptionHelper.interrupt(ctx, ScheduleStepEnum.S4_2_DATA_INIT,
                ScheduleErrorCode.DATA_INCOMPLETE, "月计划缺失");
        assertTrue(ctx.isInterrupted());
        assertEquals(
                new ScheduleException(ScheduleStepEnum.S4_2_DATA_INIT, ScheduleErrorCode.DATA_INCOMPLETE,
                        "F001", "BATCH-1", "月计划缺失").getMessage(),
                ctx.getInterruptReason());
    }
}
