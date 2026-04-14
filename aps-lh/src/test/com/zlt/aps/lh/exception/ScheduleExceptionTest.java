package com.zlt.aps.lh.exception;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScheduleException} 与 {@link ScheduleDomainExceptionHelper} 消息格式测试
 */
class ScheduleExceptionTest {

    @Test
    void getMessage_包含步骤错误码与详情() {
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
    }

    @Test
    void getMessage_无步骤时仅错误码与详情() {
        ScheduleException ex = new ScheduleException(ScheduleErrorCode.PRODUCTION_STRATEGY_NOT_REGISTERED, "未找到策略");
        String msg = ex.getMessage();
        assertTrue(msg.startsWith(ScheduleErrorCode.PRODUCTION_STRATEGY_NOT_REGISTERED.getCode() + ":"));
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

    @Test
    void helper_interrupt_无明细时_message不追加明细块() {
        LhScheduleContext ctx = new LhScheduleContext();
        ctx.setFactoryCode("F001");
        ctx.setBatchNo("BATCH-2");

        ScheduleDomainExceptionHelper.interrupt(ctx, ScheduleStepEnum.S4_2_DATA_INIT,
                ScheduleErrorCode.DATA_INCOMPLETE, "校验未通过", Collections.emptyList());

        assertTrue(ctx.isInterrupted());
        assertTrue(ctx.getInterruptReason().contains("校验未通过"));
        assertFalse(ctx.getInterruptReason().contains("错误明细（共"));
    }

    @Test
    void helper_interrupt_明细在10条内时_按序号完整展示() {
        LhScheduleContext ctx = new LhScheduleContext();
        ctx.setFactoryCode("F001");
        ctx.setBatchNo("BATCH-3");
        List<String> details = Arrays.asList("缺少月计划", "缺少工作日历", "缺少机台信息");

        ScheduleDomainExceptionHelper.interrupt(ctx, ScheduleStepEnum.S4_2_DATA_INIT,
                ScheduleErrorCode.DATA_INCOMPLETE, "校验未通过，共3条错误", details);

        String message = ctx.getInterruptReason();
        assertTrue(message.contains("校验未通过，共3条错误"));
        assertTrue(message.contains("错误明细（共3条）："));
        assertTrue(message.contains("1. 缺少月计划"));
        assertTrue(message.contains("2. 缺少工作日历"));
        assertTrue(message.contains("3. 缺少机台信息"));
    }

    @Test
    void helper_interrupt_明细超过10条时_仅展示前10条并提示剩余() {
        LhScheduleContext ctx = new LhScheduleContext();
        ctx.setFactoryCode("F001");
        ctx.setBatchNo("BATCH-4");
        List<String> details = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            details.add("错误项-" + index);
        }

        ScheduleDomainExceptionHelper.interrupt(ctx, ScheduleStepEnum.S4_2_DATA_INIT,
                ScheduleErrorCode.DATA_INCOMPLETE, "校验未通过，共12条错误", details);

        String message = ctx.getInterruptReason();
        assertTrue(message.contains("错误明细（共12条）："));
        assertTrue(message.contains("10. 错误项-10"));
        assertFalse(message.contains("11. 错误项-11"));
        assertTrue(message.contains("... 其余2条请查看明细列表"));
    }

    @Test
    void helper_interrupt_明细含空白时_过滤后再展示() {
        LhScheduleContext ctx = new LhScheduleContext();
        ctx.setFactoryCode("F001");
        ctx.setBatchNo("BATCH-5");
        List<String> details = Arrays.asList(null, "", "   ", "  月计划缺失  ");

        ScheduleDomainExceptionHelper.interrupt(ctx, ScheduleStepEnum.S4_2_DATA_INIT,
                ScheduleErrorCode.DATA_INCOMPLETE, "校验未通过", details);

        String message = ctx.getInterruptReason();
        assertTrue(message.contains("错误明细（共1条）："));
        assertTrue(message.contains("1. 月计划缺失"));
        assertFalse(message.contains("2."));
    }
}
