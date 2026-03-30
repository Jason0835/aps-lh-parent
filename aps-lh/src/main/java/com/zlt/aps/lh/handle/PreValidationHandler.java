package com.zlt.aps.lh.handle;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.mapper.LhMouldChangePlanMapper;
import com.zlt.aps.lh.mapper.LhScheduleProcessLogMapper;
import com.zlt.aps.lh.mapper.LhScheduleResultMapper;
import com.zlt.aps.lh.mapper.LhUnscheduledResultMapper;
import com.zlt.aps.lh.service.ILhScheduleResultService;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

/**
 * S4.1 前置校验与数据清理处理器
 * <p>校验MES下发状态、是否重复排程，清理历史数据，生成批次号</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class PreValidationHandler extends AbsScheduleStepHandler {

    @Resource
    private LhScheduleResultMapper scheduleResultMapper;

    @Resource
    private ILhScheduleResultService scheduleResultService;

    @Resource
    private LhUnscheduledResultMapper unscheduledResultMapper;

    @Resource
    private LhMouldChangePlanMapper mouldChangePlanMapper;

    @Resource
    private LhScheduleProcessLogMapper scheduleProcessLogMapper;

    @Override
    protected void doHandle(LhScheduleContext context) {
        // S4.1.1 校验MES下发状态
        checkMesReleaseStatus(context);
        if (context.isInterrupted()) {
            return;
        }

        // S4.1.2 校验是否正在排程
        checkScheduleInProgress(context);
        if (context.isInterrupted()) {
            return;
        }

        // S4.1.3 删除旧排程数据
        cleanHistoryData(context);

        // S4.1.4 生成批次号
        generateBatchNo(context);
    }

    /**
     * 校验MES下发状态
     * <p>依据 {@code LhScheduleResult.isRelease}：仅当为 {@code 1}（已发布）时视为已下发 MES，
     * 此时禁止重新排程，需先撤销发布再排程（统计逻辑见 {@link ILhScheduleResultService#countReleasedByDate}）。</p>
     *
     * @param context 排程上下文
     */
    private void checkMesReleaseStatus(LhScheduleContext context) {
        Date scheduleDate = context.getScheduleDate();
        String factoryCode = context.getFactoryCode();
        int releasedCount = scheduleResultService.countReleasedByDate(scheduleDate, factoryCode);
        if (releasedCount > 0) {
            context.interruptSchedule("该日期排程已下发MES，请先撤销发布后再重新排程。排程日期: "
                    + LhScheduleTimeUtil.getDateStr(scheduleDate));
            log.warn("排程被拒绝: 日期[{}]已有已发布排程, 数量: {}", LhScheduleTimeUtil.getDateStr(scheduleDate), releasedCount);
        }
    }

    /**
     * 校验是否有排程正在执行中，防止重复排程
     * <p>当前通过检查上下文的batchNo是否已初始化来判断，实际项目中可引入分布式锁</p>
     *
     * @param context 排程上下文
     */
    private void checkScheduleInProgress(LhScheduleContext context) {
        // 若传入的batchNo不为空，说明已有排程在执行（或前端重复调用），需防重
        if (context.getBatchNo() != null && !context.getBatchNo().isEmpty()) {
            context.interruptSchedule("当前已有排程任务正在执行中，请勿重复提交。批次号: " + context.getBatchNo());
            log.warn("排程被拒绝: 排程任务已在执行, 批次号: {}", context.getBatchNo());
        }
    }

    /**
     * 清理历史排程数据
     * <p>删除该日期旧的排程结果、未排结果、模具交替计划及对应日志，以便重新生成</p>
     *
     * @param context 排程上下文
     */
    private void cleanHistoryData(LhScheduleContext context) {
        Date scheduleDate = context.getScheduleDate();
        String factoryCode = context.getFactoryCode();

        // 删除旧的排程结果（T_LH_SCHEDULE_RESULT）
        int deleteResultCount = scheduleResultMapper.deleteByDateAndFactory(scheduleDate, factoryCode);

        // 删除旧的未排产结果（T_LH_UNSCHEDULED_RESULT）
        int deleteUnscheduledCount = unscheduledResultMapper.deleteByDateAndFactory(scheduleDate, factoryCode);

        // 删除旧的模具交替计划（T_LH_MOULD_CHANGE_PLAN）
        int deleteMouldChangeCount = mouldChangePlanMapper.deleteByDateAndFactory(scheduleDate, factoryCode);

        log.info("清理历史排程数据完成, 工厂: {}, 日期: {}, 删除排程结果: {}条, 删除未排结果: {}条, 删除换模计划: {}条",
                factoryCode, LhScheduleTimeUtil.getDateStr(scheduleDate),
                deleteResultCount, deleteUnscheduledCount, deleteMouldChangeCount);
    }

    /**
     * 生成排程批次号
     * <p>规则：LHPC+年月日+3位流水号，如 LHPC20260327001</p>
     *
     * @param context 排程上下文
     */
    private void generateBatchNo(LhScheduleContext context) {
        String dateStr = LhScheduleTimeUtil.getDateStr(context.getScheduleDate());
        // 生成3位流水号（简单实现：基于时间戳取模，实际项目中应使用数据库序列或分布式ID）
        String sequence = String.format("%03d", (System.currentTimeMillis() % 1000));
        String batchNo = LhScheduleConstant.BATCH_NO_PREFIX + dateStr + sequence;
        context.setBatchNo(batchNo);
        log.info("生成排程批次号: {}", batchNo);
    }

    @Override
    protected String getStepName() {
        return ScheduleStepEnum.S4_1_PRE_VALIDATION.getDescription();
    }
}
