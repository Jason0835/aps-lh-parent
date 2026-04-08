package com.zlt.aps.lh.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.entity.LhMouldChangePlan;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.domain.entity.LhUnscheduledResult;
import com.zlt.aps.lh.api.enums.DeleteFlagEnum;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import com.zlt.aps.lh.exception.ScheduleErrorCode;
import com.zlt.aps.lh.exception.ScheduleException;
import com.zlt.aps.lh.mapper.LhMouldChangePlanMapper;
import com.zlt.aps.lh.mapper.LhScheduleProcessLogMapper;
import com.zlt.aps.lh.mapper.LhScheduleResultMapper;
import com.zlt.aps.lh.mapper.LhUnscheduledResultMapper;
import com.zlt.aps.lh.service.ILhScheduleResultService;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

        // S4.1.2 校验是否正在排程
        checkScheduleInProgress(context);

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
        Date targetDate = context.getScheduleTargetDate();
        String factoryCode = context.getFactoryCode();
        int releasedCount = scheduleResultService.countReleasedByDate(targetDate, factoryCode);
        if (releasedCount > 0) {
            log.warn("排程被拒绝: 日期[{}]已有已发布排程, 数量: {}", LhScheduleTimeUtil.getDateStr(targetDate), releasedCount);
            throw new ScheduleException(ScheduleStepEnum.S4_1_PRE_VALIDATION, ScheduleErrorCode.MES_RELEASED,
                    factoryCode, context.getBatchNo(),
                    "该日期排程已下发MES，请先撤销发布后再重新排程。排程日期: "
                            + LhScheduleTimeUtil.getDateStr(targetDate));
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
        if (!StringUtils.isEmpty(context.getBatchNo())) {
            log.warn("排程被拒绝: 排程任务已在执行, 批次号: {}", context.getBatchNo());
            throw new ScheduleException(ScheduleStepEnum.S4_1_PRE_VALIDATION, ScheduleErrorCode.SCHEDULE_IN_PROGRESS,
                    context.getFactoryCode(), context.getBatchNo(),
                    "当前已有排程任务正在执行中，请勿重复提交。批次号: " + context.getBatchNo());
        }
    }

    /**
     * 清理历史排程数据
     * <p>删除该日期旧的排程结果、未排结果、模具交替计划（仅 {@code isDelete = 0} 未删除数据），以便重新生成</p>
     *
     * @param context 排程上下文
     */
    private void cleanHistoryData(LhScheduleContext context) {
        Date targetDate = context.getScheduleTargetDate();
        String factoryCode = context.getFactoryCode();

        LambdaQueryWrapper<LhScheduleResult> resultWrapper = new LambdaQueryWrapper<>();
        resultWrapper.eq(LhScheduleResult::getFactoryCode, factoryCode)
                .eq(LhScheduleResult::getScheduleDate, targetDate)
                .eq(LhScheduleResult::getIsDelete, DeleteFlagEnum.NORMAL.getCode());
        int deleteResultCount = scheduleResultMapper.delete(resultWrapper);

        LambdaQueryWrapper<LhUnscheduledResult> unscheduledWrapper = new LambdaQueryWrapper<>();
        unscheduledWrapper.eq(LhUnscheduledResult::getFactoryCode, factoryCode)
                .eq(LhUnscheduledResult::getScheduleDate, targetDate)
                .eq(LhUnscheduledResult::getIsDelete, DeleteFlagEnum.NORMAL.getCode());
        int deleteUnscheduledCount = unscheduledResultMapper.delete(unscheduledWrapper);

        LambdaQueryWrapper<LhMouldChangePlan> mouldWrapper = new LambdaQueryWrapper<>();
        mouldWrapper.eq(LhMouldChangePlan::getFactoryCode, factoryCode)
                .eq(LhMouldChangePlan::getScheduleDate, targetDate)
                .eq(LhMouldChangePlan::getIsDelete, DeleteFlagEnum.NORMAL.getCode());
        int deleteMouldChangeCount = mouldChangePlanMapper.delete(mouldWrapper);

        log.info("清理历史排程数据完成, 工厂: {}, 日期: {}, 删除排程结果: {}条, 删除未排结果: {}条, 删除换模计划: {}条",
                factoryCode, LhScheduleTimeUtil.getDateStr(targetDate),
                deleteResultCount, deleteUnscheduledCount, deleteMouldChangeCount);
    }

    /**
     * 生成排程批次号（规则见 {@link ILhScheduleResultService#generateNextBatchNo}，由 Redis 自增分配流水）
     *
     * @param context 排程上下文
     */
    private void generateBatchNo(LhScheduleContext context) {
        String batchNo = scheduleResultService.generateNextBatchNo(context.getScheduleTargetDate(), context.getFactoryCode());
        context.setBatchNo(batchNo);
        log.info("生成排程批次号: {}", batchNo);
    }

    @Override
    protected String getStepName() {
        return ScheduleStepEnum.S4_1_PRE_VALIDATION.getDescription();
    }
}
