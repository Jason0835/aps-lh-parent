/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.enums.SchedulePriorityEnum;
import com.zlt.aps.lh.engine.strategy.IEndingJudgmentStrategy;
import com.zlt.aps.lh.engine.strategy.ISkuPriorityStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 默认SKU排产优先级策略实现
 * <p>基于发货要求、延误天数、结构收尾日和供应链优先级进行多维度排序</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class DefaultSkuPriorityStrategy implements ISkuPriorityStrategy {

    @Resource
    private IEndingJudgmentStrategy endingJudgmentStrategy;

    /**
     * 供应链优先级权重：高优先级(04) < 周期排产(05) < 中优先级(06) < 搭配排产(07)，数字越小越优先
     * 未知优先级排最后
     */
    private static final int UNKNOWN_PRIORITY_ORDER = 99;

    @Override
    public void sortByPriority(LhScheduleContext context) {
        log.info("执行SKU优先级排序, 新增SKU数: {}", context.getNewSpecSkuList().size());

        Comparator<SkuScheduleDTO> comparator = buildSkuComparator(context);

        // 对新增SKU列表排序
        context.getNewSpecSkuList().sort(comparator);

        // 同时对每个结构下的SKU列表排序（保持结构内部顺序一致）
        for (Map.Entry<String, List<SkuScheduleDTO>> entry : context.getStructureSkuMap().entrySet()) {
            entry.getValue().sort(comparator);
        }

        // 更新scheduleOrder
        int order = 1;
        for (SkuScheduleDTO sku : context.getNewSpecSkuList()) {
            sku.setScheduleOrder(order++);
        }

        log.debug("SKU优先级排序完成, 排序后第一位: {}",
                context.getNewSpecSkuList().isEmpty() ? "空" : context.getNewSpecSkuList().get(0).getMaterialCode());
    }

    /**
     * 构建SKU多维度比较器
     * <p>
     * 排序规则（优先级从高到低）：
     * <ol>
     *   <li>有发货要求优先（deliveryLocked=true 排前）</li>
     *   <li>延误天数越多越优先（delayDays 降序）</li>
     *   <li>结构收尾SKU优先；收尾标记相同时，收尾日越晚（endingDaysRemaining 越大）的越优先上机</li>
     *   <li>供应链优先级：高优先级(04) → 周期排产(05) → 中优先级(06) → 搭配排产(07)</li>
     * </ol>
     * </p>
     *
     * @param context 排程上下文
     * @return SKU比较器
     */
    private Comparator<SkuScheduleDTO> buildSkuComparator(LhScheduleContext context) {
        return Comparator
                // 顺序1：有发货要求的优先（true=1，false=0，降序）
                .comparingInt((SkuScheduleDTO s) -> s.isDeliveryLocked() ? 0 : 1)
                // 顺序2：延误天数越多越优先（降序）
                .thenComparingInt((SkuScheduleDTO s) -> -s.getDelayDays())
                // 顺序3：收尾SKU优先；收尾日越晚（剩余天数越多）的越先上机
                .thenComparingInt((SkuScheduleDTO s) -> endingJudgmentStrategy.isEnding(context, s) ? 0 : 1)
                .thenComparingInt((SkuScheduleDTO s) -> -s.getEndingDaysRemaining())
                // 顺序4：供应链优先级权重（数字越小越优先）
                .thenComparingInt(this::getSupplyChainPriorityOrder);
    }

    /**
     * 获取供应链优先级排序权重
     * <p>高优先级(04)=1, 周期排产(05)=2, 中优先级(06)=3, 搭配排产(07)=4, 未知=99</p>
     */
    private int getSupplyChainPriorityOrder(SkuScheduleDTO sku) {
        String priority = sku.getSupplyChainPriority();
        if (priority == null) {
            return UNKNOWN_PRIORITY_ORDER;
        }
        if (SchedulePriorityEnum.HIGH_PRIORITY.getCode().equals(priority)) {
            return 1;
        }
        if (SchedulePriorityEnum.CYCLE_PRODUCTION.getCode().equals(priority)) {
            return 2;
        }
        if (SchedulePriorityEnum.MID_PRIORITY.getCode().equals(priority)) {
            return 3;
        }
        if (SchedulePriorityEnum.MATCH_PRODUCTION.getCode().equals(priority)) {
            return 4;
        }
        return UNKNOWN_PRIORITY_ORDER;
    }
}
