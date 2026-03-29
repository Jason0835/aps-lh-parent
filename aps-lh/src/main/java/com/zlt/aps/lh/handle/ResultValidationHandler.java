package com.zlt.aps.lh.handle;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhMouldChangePlan;
import com.zlt.aps.lh.api.domain.entity.LhScheduleProcessLog;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.engine.observer.ScheduleEvent;
import com.zlt.aps.lh.engine.observer.ScheduleEventPublisher;
import com.zlt.aps.lh.mapper.LhMouldChangePlanMapper;
import com.zlt.aps.lh.mapper.LhScheduleProcessLogMapper;
import com.zlt.aps.lh.mapper.LhScheduleResultMapper;
import com.zlt.aps.lh.mapper.LhUnscheduledResultMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
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
    private ScheduleEventPublisher eventPublisher;

    @Resource
    private LhScheduleResultMapper scheduleResultMapper;

    @Resource
    private LhUnscheduledResultMapper unscheduledResultMapper;

    @Resource
    private LhMouldChangePlanMapper mouldChangePlanMapper;

    @Resource
    private LhScheduleProcessLogMapper processLogMapper;

    private static final AtomicInteger ORDER_SEQ = new AtomicInteger(0);
    private static final AtomicInteger CHG_SEQ = new AtomicInteger(0);

    @Override
    protected void doHandle(LhScheduleContext context) {
        // S4.6.1 排程后置校验
        postValidation(context);
        if (context.isInterrupted()) {
            return;
        }

        // S4.6.2 生成模具交替计划
        generateMouldChangePlan(context);

        // S4.6.3 补全工单号和发布状态
        assignOrderNumbers(context);

        // S4.6.4 保存排程结果到数据库
        saveScheduleResults(context);

        // S4.6.5 发布排程完成事件（观察者模式）
        eventPublisher.publish(ScheduleEvent.completed(context));
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

        // 校验2：检查每个排程结果必填字段
        for (LhScheduleResult result : context.getScheduleResultList()) {
            if (result.getLhMachineCode() == null || result.getMaterialCode() == null) {
                log.warn("排程结果存在缺失必填字段的记录, materialCode: {}", result.getMaterialCode());
            }
            // 确保批次号和工厂编号已填充
            if (result.getBatchNo() == null) {
                result.setBatchNo(context.getBatchNo());
            }
            if (result.getFactoryCode() == null) {
                result.setFactoryCode(context.getFactoryCode());
            }
        }

        // 校验3：检查模具交替计划完整性
        for (LhMouldChangePlan plan : context.getMouldChangePlanList()) {
            if (plan.getAfterMaterialCode() == null) {
                log.warn("模具交替计划存在后规格为空的记录, 机台: {}", plan.getLhMachineCode());
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
        log.info("生成模具交替计划, 换模排程结果数: {}",
                context.getScheduleResultList().stream().filter(r -> "1".equals(r.getIsChangeMould())).count());

        List<LhMouldChangePlan> plans = context.getMouldChangePlanList();
        int planOrder = 1;

        for (LhScheduleResult result : context.getScheduleResultList()) {
            if (!"1".equals(result.getIsChangeMould())) {
                continue;
            }

            LhMouldChangePlan plan = new LhMouldChangePlan();
            plan.setFactoryCode(context.getFactoryCode());
            plan.setLhResultBatchNo(context.getBatchNo());
            plan.setOrderNo(generateChangePlanOrderNo(context));
            plan.setScheduleDate(context.getScheduleDate());
            plan.setPlanDate(result.getClass1StartTime() != null ? result.getClass1StartTime() : context.getScheduleDate());
            plan.setPlanOrder(planOrder++);
            plan.setLhMachineCode(result.getLhMachineCode());
            plan.setLhMachineName(result.getLhMachineName());
            plan.setAfterMaterialCode(result.getMaterialCode());
            plan.setAfterMaterialDesc(result.getMaterialDesc());
            plan.setMouldCode(result.getMouldCode());
            plan.setIsRelease("0");
            plan.setMouldStatus("0");
            plan.setIsDelete(0);

            // 记录前规格信息（从机台排程信息中获取）
            MachineScheduleDTO machine = context.getMachineScheduleMap().get(result.getLhMachineCode());
            if (machine != null) {
                plan.setBeforeMaterialCode(machine.getCurrentMaterialCode());
                plan.setBeforeMaterialDesc(machine.getCurrentMaterialDesc());
                plan.setChangeTime(machine.getEstimatedEndTime());
            }

            // 判断交替类型
            plan.setChangeMouldType(determineChangeMouldType(result));
            plans.add(plan);
        }

        log.info("生成模具交替计划完成, 共 {} 条", plans.size());
    }

    /**
     * 确定模具交替类型
     * <p>01-正规换模, 02-更换活字块, 03-模具喷砂清洗, 04-模具干冰清洗</p>
     */
    private String determineChangeMouldType(LhScheduleResult result) {
        // 新增排产（换模）
        if ("02".equals(result.getScheduleType())) {
            return "01";
        }
        // 续作且有换模标记的为换活字块
        if ("01".equals(result.getScheduleType()) && "1".equals(result.getIsChangeMould())) {
            return "02";
        }
        return "01";
    }

    /**
     * 为排程结果补全工单号（确保每条记录都有工单号）
     */
    private void assignOrderNumbers(LhScheduleContext context) {
        log.info("补全工单号, 排程结果数: {}", context.getScheduleResultList().size());
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(context.getScheduleDate());

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
     * 批量保存所有排程输出结果到数据库
     */
    private void saveScheduleResults(LhScheduleContext context) {
        log.info("保存排程结果, 排程结果: {}, 未排产: {}, 换模计划: {}, 日志: {}",
                context.getScheduleResultList().size(),
                context.getUnscheduledResultList().size(),
                context.getMouldChangePlanList().size(),
                context.getScheduleLogList().size());

        // 1. 保存排程结果
        if (!context.getScheduleResultList().isEmpty()) {
            scheduleResultMapper.insertBatch(context.getScheduleResultList());
        }

        // 2. 保存未排产结果
        if (!context.getUnscheduledResultList().isEmpty()) {
            unscheduledResultMapper.insertBatch(context.getUnscheduledResultList());
        }

        // 3. 保存模具交替计划
        if (!context.getMouldChangePlanList().isEmpty()) {
            mouldChangePlanMapper.insertBatch(context.getMouldChangePlanList());
        }

        // 4. 添加排程汇总日志并保存
        addSummaryLog(context);
        if (!context.getScheduleLogList().isEmpty()) {
            processLogMapper.insertBatch(context.getScheduleLogList());
        }

        log.info("排程结果保存完成");
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
     * 生成模具交替计划工单号：CHG+yyyyMMdd+3位流水号
     */
    private String generateChangePlanOrderNo(LhScheduleContext context) {
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(context.getScheduleDate());
        int seq = CHG_SEQ.incrementAndGet() % 1000;
        return String.format("%s%s%03d", LhScheduleConstant.MOULD_CHANGE_ORDER_PREFIX, dateStr, seq);
    }

    @Override
    protected String getStepName() {
        return ScheduleStepEnum.S4_6_RESULT_VALIDATION.getDescription();
    }
}
