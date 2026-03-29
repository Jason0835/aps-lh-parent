package com.zlt.aps.lh.engine.template;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.LhScheduleResponseDTO;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import lombok.extern.slf4j.Slf4j;

/**
 * 硫化排程模板方法抽象类
 * <p>定义排程的标准六步流程骨架，子类实现各步骤的具体逻辑</p>
 *
 * <pre>
 * 流程: S4.1前置校验 -> S4.2数据初始化 -> S4.3排程调整与SKU归集
 *       -> S4.4续作规格排产 -> S4.5新增规格排产 -> S4.6结果校验与发布保存
 * </pre>
 *
 * @author APS
 */
@Slf4j
public abstract class AbsLhScheduleTemplate {

    /**
     * 执行排程(模板方法) - 定义不可变的算法骨架
     *
     * @param context 排程上下文
     * @return 排程响应结果
     */
    public final LhScheduleResponseDTO execute(LhScheduleContext context) {
        long startTime = System.currentTimeMillis();
        log.info("========== 硫化排程开始, 工厂:{}, 排程日期:{} ==========",
                context.getFactoryCode(), context.getScheduleDate());
        try {
            // S4.1 前置校验与数据清理
            context.setCurrentStep(ScheduleStepEnum.S4_1_PRE_VALIDATION.getCode());
            log.info(">>> 步骤 S4.1: {}", ScheduleStepEnum.S4_1_PRE_VALIDATION.getDescription());
            doPreValidation(context);
            if (context.isInterrupted()) {
                return buildInterruptResponse(context);
            }

            // S4.2 基础数据初始化
            context.setCurrentStep(ScheduleStepEnum.S4_2_DATA_INIT.getCode());
            log.info(">>> 步骤 S4.2: {}", ScheduleStepEnum.S4_2_DATA_INIT.getDescription());
            doDataInitialization(context);
            if (context.isInterrupted()) {
                return buildInterruptResponse(context);
            }

            // S4.3 排程调整与SKU归集
            context.setCurrentStep(ScheduleStepEnum.S4_3_ADJUST_AND_GATHER.getCode());
            log.info(">>> 步骤 S4.3: {}", ScheduleStepEnum.S4_3_ADJUST_AND_GATHER.getDescription());
            doAdjustAndGather(context);
            if (context.isInterrupted()) {
                return buildInterruptResponse(context);
            }

            // S4.4 续作规格排产
            context.setCurrentStep(ScheduleStepEnum.S4_4_CONTINUOUS_PRODUCTION.getCode());
            log.info(">>> 步骤 S4.4: {}", ScheduleStepEnum.S4_4_CONTINUOUS_PRODUCTION.getDescription());
            doContinuousProduction(context);
            if (context.isInterrupted()) {
                return buildInterruptResponse(context);
            }

            // S4.5 新增规格排产
            context.setCurrentStep(ScheduleStepEnum.S4_5_NEW_PRODUCTION.getCode());
            log.info(">>> 步骤 S4.5: {}", ScheduleStepEnum.S4_5_NEW_PRODUCTION.getDescription());
            doNewSpecProduction(context);
            if (context.isInterrupted()) {
                return buildInterruptResponse(context);
            }

            // S4.6 结果校验与发布保存
            context.setCurrentStep(ScheduleStepEnum.S4_6_RESULT_VALIDATION.getCode());
            log.info(">>> 步骤 S4.6: {}", ScheduleStepEnum.S4_6_RESULT_VALIDATION.getDescription());
            doResultValidationAndSave(context);

            return buildSuccessResponse(context);
        } catch (Exception e) {
            log.error("硫化排程执行异常, 当前步骤:{}", context.getCurrentStep(), e);
            return LhScheduleResponseDTO.fail("排程执行异常: " + e.getMessage());
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("========== 硫化排程结束, 耗时: {}ms ==========", elapsed);
        }
    }

    /** S4.1 前置校验与数据清理 */
    protected abstract void doPreValidation(LhScheduleContext context);

    /** S4.2 基础数据初始化 */
    protected abstract void doDataInitialization(LhScheduleContext context);

    /** S4.3 排程调整与SKU归集 */
    protected abstract void doAdjustAndGather(LhScheduleContext context);

    /** S4.4 续作规格排产 */
    protected abstract void doContinuousProduction(LhScheduleContext context);

    /** S4.5 新增规格排产 */
    protected abstract void doNewSpecProduction(LhScheduleContext context);

    /** S4.6 结果校验与发布保存 */
    protected abstract void doResultValidationAndSave(LhScheduleContext context);

    /**
     * 构建中断响应
     *
     * @param context 排程上下文
     * @return 中断响应DTO
     */
    private LhScheduleResponseDTO buildInterruptResponse(LhScheduleContext context) {
        log.warn("排程在步骤[{}]被中断, 原因: {}", context.getCurrentStep(), context.getInterruptReason());
        return LhScheduleResponseDTO.fail("排程中断[" + context.getCurrentStep() + "]: " + context.getInterruptReason());
    }

    /**
     * 构建成功响应
     *
     * @param context 排程上下文
     * @return 成功响应DTO
     */
    private LhScheduleResponseDTO buildSuccessResponse(LhScheduleContext context) {
        LhScheduleResponseDTO response = LhScheduleResponseDTO.success(context.getBatchNo(), "排程完成");
        response.setScheduleResultCount(context.getScheduleResultList().size());
        response.setUnscheduledCount(context.getUnscheduledResultList().size());
        response.setMouldChangePlanCount(context.getMouldChangePlanList().size());
        return response;
    }
}
