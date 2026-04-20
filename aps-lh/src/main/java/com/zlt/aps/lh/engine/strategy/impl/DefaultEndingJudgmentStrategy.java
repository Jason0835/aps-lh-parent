package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.enums.SkuTagEnum;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import org.springframework.util.CollectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认收尾判定策略实现
 * <p>统一收尾判定逻辑，确保一致性</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class DefaultEndingJudgmentStrategy implements IEndingJudgmentStrategy {

    @Override
    public boolean isEnding(LhScheduleContext context, SkuScheduleDTO sku) {
        // 规则1：已明确标记为收尾
        if (SkuTagEnum.ENDING.getCode().equals(sku.getSkuTag())) {
            return true;
        }

        int targetScheduleQty = sku.resolveTargetScheduleQty();

        // 规则2：排产目标量 <= 排程期内可生产总产能
        int totalScheduleShifts = getTotalScheduleShifts(context);
        int shiftCapacity = sku.getShiftCapacity();
        if (shiftCapacity > 0) {
            int totalCapacity = shiftCapacity * totalScheduleShifts;
            if (targetScheduleQty <= totalCapacity && targetScheduleQty > 0) {
                log.debug("SKU[{}]判定为收尾(规则2): 目标量{} <= 总产能{}",
                        sku.getMaterialCode(), targetScheduleQty, totalCapacity);
                return true;
            }
        }

        // 规则3：待排量 < 日产能（非满产运行）
        int dailyCapacity = sku.getDailyCapacity();
        if (dailyCapacity > 0 && targetScheduleQty < dailyCapacity && targetScheduleQty > 0) {
            log.debug("SKU[{}]判定为收尾(规则3): 目标量{} < 日产能{}",
                    sku.getMaterialCode(), targetScheduleQty, dailyCapacity);
            return true;
        }

        return false;
    }

    @Override
    public int calculateEndingShifts(LhScheduleContext context, SkuScheduleDTO sku) {
        int shiftCapacity = sku.getShiftCapacity();
        if (shiftCapacity <= 0) {
            return -1;
        }

        int targetScheduleQty = sku.resolveTargetScheduleQty();
        if (targetScheduleQty <= 0) {
            return 0;
        }

        // 向上取整计算所需班次
        return (int) Math.ceil((double) targetScheduleQty / shiftCapacity);
    }

    @Override
    public int calculateEndingDays(LhScheduleContext context, SkuScheduleDTO sku) {
        int shifts = calculateEndingShifts(context, sku);
        if (shifts < 0) {
            return -1;
        }
        if (shifts == 0) {
            return 0;
        }
        return (int) Math.ceil((double) shifts / LhScheduleConstant.DEFAULT_SHIFTS_PER_DAY);
    }

    /**
     * 获取排程期总班次数
     * <p>可配置，默认8班（T日2班 + T+1日3班 + T+2日3班）</p>
     *
     * @param context 排程上下文
     * @return 排程窗口内总班次数
     */
    private int getTotalScheduleShifts(LhScheduleContext context) {
        if (!CollectionUtils.isEmpty(context.getScheduleWindowShifts())) {
            return context.getScheduleWindowShifts().size();
        }
        return LhScheduleTimeUtil.getScheduleShifts(context, context.getScheduleDate()).size();
    }
}
