package com.zlt.aps.lh.handler;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhMouldChangePlan;
import com.zlt.aps.lh.api.domain.entity.LhScheduleProcessLog;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.component.IncrSerialGenerator;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.engine.observer.ScheduleEvent;
import com.zlt.aps.lh.engine.observer.ScheduleEventPublisher;
import com.zlt.aps.lh.exception.ScheduleErrorCode;
import com.zlt.aps.lh.exception.ScheduleException;
import com.zlt.aps.lh.service.impl.SchedulePersistenceService;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import com.zlt.aps.lh.util.LeftRightMouldUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * S4.6 结果校验与发布保存处理器
 * <p>最终校验排程结果，生成模具交替计划，保存数据</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class ResultValidationHandler extends AbsScheduleStepHandler {

    @Resource
    private ScheduleEventPublisher scheduleEventPublisher;

    @Resource
    private SchedulePersistenceService schedulePersistenceService;

    private static final AtomicInteger ORDER_SEQ = new AtomicInteger(0);
    private static final AtomicInteger CHG_SEQ = new AtomicInteger(0);

    @Override
    protected void doHandle(LhScheduleContext context) {
        String scheduleOrderBusinessKey = buildScheduleOrderBusinessKey(context);
        try {
            // S4.6.1 排程后置校验
            postValidation(context);

            // S4.6.2 生成模具交替计划
            generateMouldChangePlan(context);

            // S4.6.3 补全工单号和发布状态
            assignOrderNumbers(context);

            // S4.6.4 赋值排程顺序
            assignScheduleOrder(context, scheduleOrderBusinessKey);

            // S4.6.5 添加排程汇总日志
            addSummaryLog(context);

            // S4.6.6 保存排程结果到数据库
            schedulePersistenceService.replaceScheduleAtomically(context);

            // S4.6.7 发布排程完成事件（观察者模式）
            scheduleEventPublisher.publish(ScheduleEvent.completed(context));
        } finally {
            clearScheduleOrderCounter(scheduleOrderBusinessKey);
        }
    }

    /**
     * 排程后置校验：检查结果完整性
     */
    private void postValidation(LhScheduleContext context) {
        log.info("执行排程后置校验, 排程结果数: {}, 未排产数: {}",
                context.getScheduleResultList().size(), context.getUnscheduledResultList().size());

        // 校验1：排程结果不能为空（允许全部未排的情况，但记录警告）
        if (context.getScheduleResultList().isEmpty()) {
            log.warn("排程结果为空，可能所有SKU均未成功排产");
        }

        if (StringUtils.isBlank(context.getBatchNo()) || StringUtils.isBlank(context.getFactoryCode())) {
            throw new ScheduleException(ScheduleStepEnum.S4_6_RESULT_VALIDATION,
                    ScheduleErrorCode.RESULT_VALIDATION_FAILED,
                    context.getFactoryCode(), context.getBatchNo(),
                    "批次号或工厂编码为空，无法执行结果保存");
        }

        // 校验2：检查每个排程结果必填字段
        for (LhScheduleResult result : context.getScheduleResultList()) {
            if (result.getBatchNo() == null) {
                result.setBatchNo(context.getBatchNo());
            }
            if (result.getFactoryCode() == null) {
                result.setFactoryCode(context.getFactoryCode());
            }
            if (result.getScheduleDate() == null) {
                result.setScheduleDate(context.getScheduleTargetDate());
            }
            if (result.getIsRelease() == null) {
                result.setIsRelease("0");
            }
            requireField(result.getBatchNo(), "batchNo", context, result);
            requireField(result.getFactoryCode(), "factoryCode", context, result);
            requireField(result.getLhMachineCode(), "lhMachineCode", context, result);
            result.setLeftRightMould(LeftRightMouldUtil.resolveLeftRightMould(
                    result.getLeftRightMould(), result.getLhMachineCode()));
            requireField(result.getMaterialCode(), "materialCode", context, result);
            requireField(result.getScheduleType(), "scheduleType", context, result);
            if (result.getSpecEndTime() == null) {
                throwValidationFailure(context, result, "specEndTime 缺失");
            }
            if ("1".equals(result.getIsChangeMould()) && StringUtils.isBlank(result.getMouldCode())) {
                throwValidationFailure(context, result, "换模结果 mouldCode 缺失");
            }
        }

        log.info("排程后置校验完成");
    }

    /**
     * 生成模具交替计划
     * <p>
     * 收集排程结果中换模的机台，生成对应的模具交替计划记录。<br/>
     * 计划天数为2天（T日和T+1日），均衡早中班换模次数。
     * </p>
     */
    private void generateMouldChangePlan(LhScheduleContext context) {
        List<LhScheduleResult> changeResults = context.getScheduleResultList().stream()
                .filter(r -> "1".equals(r.getIsChangeMould())
                        && r.getDailyPlanQty() != null
                        && r.getDailyPlanQty() > 0)
                .sorted(Comparator.comparing(LhScheduleResult::getLhMachineCode, Comparator.nullsLast(String::compareTo))
                        .thenComparing(this::resolvePlannedMouldChangeStartTime, Comparator.nullsLast(Date::compareTo))
                        .thenComparing(LhScheduleResult::getSpecEndTime, Comparator.nullsLast(Date::compareTo)))
                .collect(Collectors.toList());
        log.info("生成模具交替计划, 换模排程结果数: {}", changeResults.size());

        List<LhMouldChangePlan> plans = context.getMouldChangePlanList();
        plans.clear();

        Map<String, RollingMachineState> rollingStateMap = new HashMap<>();
        int planOrder = 1;

        for (LhScheduleResult result : changeResults) {
            RollingMachineState state = rollingStateMap.computeIfAbsent(result.getLhMachineCode(),
                    machineCode -> buildInitialState(context, machineCode));
            LhMouldChangePlan plan = new LhMouldChangePlan();
            plan.setFactoryCode(context.getFactoryCode());
            plan.setLhResultBatchNo(context.getBatchNo());
            plan.setOrderNo(generateChangePlanOrderNo(context));
            plan.setScheduleDate(context.getScheduleTargetDate());
            // 换模计划优先对齐结果里的真实换模开始时间；没有时再回退旧口径。
            Date plannedMouldChangeStartTime = resolvePlannedMouldChangeStartTime(result);
            plan.setPlanDate(plannedMouldChangeStartTime);
            plan.setPlanOrder(planOrder++);
            plan.setLhMachineCode(result.getLhMachineCode());
            plan.setLhMachineName(result.getLhMachineName());
            plan.setLeftRightMould(LeftRightMouldUtil.resolveLeftRightMould(
                    result.getLeftRightMould(), result.getLhMachineCode()));
            // 前规格取换模前机台当前在产规格，后规格取本次换模上机规格。
            plan.setBeforeMaterialCode(state.getCurrentMaterialCode());
            plan.setBeforeMaterialDesc(state.getCurrentMaterialDesc());
            plan.setAfterMaterialCode(result.getMaterialCode());
            plan.setAfterMaterialDesc(result.getMaterialDesc());
            plan.setMouldCode(result.getMouldCode());
            plan.setIsRelease("0");
            plan.setMouldStatus("0");
            plan.setIsDelete(0);
            plan.setChangeTime(resolvePlanChangeTime(result, state));

            // 判断交替类型
            plan.setChangeMouldType(determineChangeMouldType(result));
            plans.add(plan);

            updateRollingState(state, result);
        }

        log.info("生成模具交替计划完成, 共 {} 条", plans.size());
    }

    /**
     * 确定模具交替类型
     * <p>01-正规换模, 02-更换活字块, 03-模具喷砂清洗, 04-模具干冰清洗</p>
     */
    private String determineChangeMouldType(LhScheduleResult result) {
        // 换活字块：通过 isTypeBlock 精确识别
        if ("1".equals(result.getIsTypeBlock())) {
            return "02";
        }
        // 新增排产（换模）
        if ("02".equals(result.getScheduleType())) {
            return "01";
        }
        return "01";
    }

    /**
     * 为排程结果补全工单号（确保每条记录都有工单号）
     */
    private void assignOrderNumbers(LhScheduleContext context) {
        log.info("补全工单号, 排程结果数: {}", context.getScheduleResultList().size());
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(context.getScheduleTargetDate());

        for (LhScheduleResult result : context.getScheduleResultList()) {
            if (result.getOrderNo() == null || result.getOrderNo().isEmpty()) {
                int seq = ORDER_SEQ.incrementAndGet() % 1000;
                result.setOrderNo(String.format("%s%s%03d", LhScheduleConstant.ORDER_NO_PREFIX, dateStr, seq));
            }
            // 确保发布状态已设置
            if (result.getIsRelease() == null) {
                result.setIsRelease("0");
            }
        }

        // 为模具交替计划补全工单号
        for (LhMouldChangePlan plan : context.getMouldChangePlanList()) {
            if (plan.getOrderNo() == null || plan.getOrderNo().isEmpty()) {
                int seq = CHG_SEQ.incrementAndGet() % 1000;
                plan.setOrderNo(String.format("%s%s%03d", LhScheduleConstant.MOULD_CHANGE_ORDER_PREFIX, dateStr, seq));
            }
        }
    }

    /**
     * 添加排程汇总日志
     */
    private void addSummaryLog(LhScheduleContext context) {
        LhScheduleProcessLog summaryLog = new LhScheduleProcessLog();
        summaryLog.setBatchNo(context.getBatchNo());
        summaryLog.setTitle(ScheduleStepEnum.S4_6_RESULT_VALIDATION.getDescription());
        summaryLog.setBusiCode(context.getFactoryCode());
        summaryLog.setLogDetail(String.format(
                "排程完成: 排程结果%d条, 未排产%d条, 换模计划%d条",
                context.getScheduleResultList().size(),
                context.getUnscheduledResultList().size(),
                context.getMouldChangePlanList().size()
        ));
        summaryLog.setIsDelete(0);
        context.getScheduleLogList().add(summaryLog);
    }

    /**
     * 为排程结果赋值排程顺序。
     *
     * @param context 排程上下文
     * @param businessKey 自增序列业务键
     */
    private void assignScheduleOrder(LhScheduleContext context, String businessKey) {
        if (StringUtils.isEmpty(businessKey)) {
            log.warn("排程顺序业务键为空，跳过排程顺序赋值");
            return;
        }
        if (CollectionUtils.isEmpty(context.getScheduleResultList())) {
            return;
        }
        // 按实际排产结果列表顺序依次生成排程顺序，保证落库顺序与业务执行顺序一致。
        for (LhScheduleResult result : context.getScheduleResultList()) {
            result.setScheduleOrder(IncrSerialGenerator.generateSerial(businessKey));
        }
    }

    /**
     * 构建排程顺序自增序列业务键（工厂编码_目标日yyyyMMdd）。
     *
     * @param context 排程上下文
     * @return 业务键
     */
    private String buildScheduleOrderBusinessKey(LhScheduleContext context) {
        if (context == null || StringUtils.isEmpty(context.getFactoryCode()) || context.getScheduleTargetDate() == null) {
            return null;
        }
        return context.getFactoryCode() + "_" + LhScheduleTimeUtil.getDateStr(context.getScheduleTargetDate());
    }

    /**
     * 清理排程顺序业务计数器。
     *
     * @param businessKey 自增序列业务键
     */
    private void clearScheduleOrderCounter(String businessKey) {
        if (StringUtils.isNotEmpty(businessKey)) {
            IncrSerialGenerator.clearBusinessCounter(businessKey);
        }
    }

    /**
     * 生成模具交替计划工单号：CHG+yyyyMMdd+3位流水号
     */
    private String generateChangePlanOrderNo(LhScheduleContext context) {
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(context.getScheduleTargetDate());
        int seq = CHG_SEQ.incrementAndGet() % 1000;
        return String.format("%s%s%03d", LhScheduleConstant.MOULD_CHANGE_ORDER_PREFIX, dateStr, seq);
    }

    @Override
    protected String getStepName() {
        return ScheduleStepEnum.S4_6_RESULT_VALIDATION.getDescription();
    }

    @Override
    protected boolean shouldPropagateException() {
        return true;
    }

    private void requireField(String value, String fieldName, LhScheduleContext context, LhScheduleResult result) {
        if (StringUtils.isBlank(value)) {
            throwValidationFailure(context, result, fieldName + " 缺失");
        }
    }

    private void throwValidationFailure(LhScheduleContext context, LhScheduleResult result, String detail) {
        throw new ScheduleException(ScheduleStepEnum.S4_6_RESULT_VALIDATION,
                ScheduleErrorCode.RESULT_VALIDATION_FAILED,
                context.getFactoryCode(), context.getBatchNo(),
                String.format("排程结果校验失败，机台[%s] 物料[%s]：%s",
                        result.getLhMachineCode(), result.getMaterialCode(), detail));
    }

    private Date resolveProductionStartTime(LhScheduleResult result) {
        List<Date> startTimes = new ArrayList<>();
        if (result.getClass1StartTime() != null) {
            startTimes.add(result.getClass1StartTime());
        }
        if (result.getClass2StartTime() != null) {
            startTimes.add(result.getClass2StartTime());
        }
        if (result.getClass3StartTime() != null) {
            startTimes.add(result.getClass3StartTime());
        }
        if (result.getClass4StartTime() != null) {
            startTimes.add(result.getClass4StartTime());
        }
        if (result.getClass5StartTime() != null) {
            startTimes.add(result.getClass5StartTime());
        }
        if (result.getClass6StartTime() != null) {
            startTimes.add(result.getClass6StartTime());
        }
        if (result.getClass7StartTime() != null) {
            startTimes.add(result.getClass7StartTime());
        }
        if (result.getClass8StartTime() != null) {
            startTimes.add(result.getClass8StartTime());
        }
        if (startTimes.isEmpty()) {
            return result.getSpecEndTime();
        }
        return startTimes.stream().min(Date::compareTo).orElse(result.getSpecEndTime());
    }

    private Date resolvePlannedMouldChangeStartTime(LhScheduleResult result) {
        if (result == null) {
            return null;
        }
        if (result.getMouldChangeStartTime() != null) {
            return result.getMouldChangeStartTime();
        }
        return resolveProductionStartTime(result);
    }

    private Date resolvePlanChangeTime(LhScheduleResult result, RollingMachineState state) {
        if (result != null && result.getMouldChangeStartTime() != null) {
            return result.getMouldChangeStartTime();
        }
        return state != null ? state.getEstimatedEndTime() : null;
    }

    private RollingMachineState buildInitialState(LhScheduleContext context, String machineCode) {
        MachineScheduleDTO machine = context.getInitialMachineScheduleMap().get(machineCode);
        if (machine == null) {
            machine = context.getMachineScheduleMap().get(machineCode);
        }
        RollingMachineState state = new RollingMachineState();
        if (machine != null) {
            state.setCurrentMaterialCode(machine.getCurrentMaterialCode());
            state.setCurrentMaterialDesc(machine.getCurrentMaterialDesc());
            state.setPreviousMaterialCode(machine.getPreviousMaterialCode());
            state.setPreviousMaterialDesc(machine.getPreviousMaterialDesc());
            state.setEstimatedEndTime(machine.getEstimatedEndTime());
        }
        return state;
    }

    private void updateRollingState(RollingMachineState state, LhScheduleResult result) {
        state.setPreviousMaterialCode(state.getCurrentMaterialCode());
        state.setPreviousMaterialDesc(state.getCurrentMaterialDesc());
        state.setCurrentMaterialCode(result.getMaterialCode());
        state.setCurrentMaterialDesc(result.getMaterialDesc());
        state.setEstimatedEndTime(result.getSpecEndTime());
    }

    /**
     * 换模计划滚动前规格状态。
     */
    private static class RollingMachineState {

        private String currentMaterialCode;
        private String currentMaterialDesc;
        private String previousMaterialCode;
        private String previousMaterialDesc;
        private Date estimatedEndTime;

        public String getCurrentMaterialCode() {
            return currentMaterialCode;
        }

        public void setCurrentMaterialCode(String currentMaterialCode) {
            this.currentMaterialCode = currentMaterialCode;
        }

        public String getCurrentMaterialDesc() {
            return currentMaterialDesc;
        }

        public void setCurrentMaterialDesc(String currentMaterialDesc) {
            this.currentMaterialDesc = currentMaterialDesc;
        }

        public String getPreviousMaterialCode() {
            return previousMaterialCode;
        }

        public void setPreviousMaterialCode(String previousMaterialCode) {
            this.previousMaterialCode = previousMaterialCode;
        }

        public String getPreviousMaterialDesc() {
            return previousMaterialDesc;
        }

        public void setPreviousMaterialDesc(String previousMaterialDesc) {
            this.previousMaterialDesc = previousMaterialDesc;
        }

        public Date getEstimatedEndTime() {
            return estimatedEndTime;
        }

        public void setEstimatedEndTime(Date estimatedEndTime) {
            this.estimatedEndTime = estimatedEndTime;
        }
    }
}
