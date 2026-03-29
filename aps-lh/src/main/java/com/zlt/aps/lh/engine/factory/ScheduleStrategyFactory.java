/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.factory;

import com.zlt.aps.lh.api.enums.ScheduleTypeEnum;
import com.zlt.aps.lh.engine.strategy.ICapacityCalculateStrategy;
import com.zlt.aps.lh.engine.strategy.IFirstInspectionBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IInsertOrderStrategy;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.engine.strategy.IMouldChangeBalanceStrategy;
import com.zlt.aps.lh.engine.strategy.IProductionShutdownStrategy;
import com.zlt.aps.lh.engine.strategy.IProductionStrategy;
import com.zlt.aps.lh.engine.strategy.ISkuPriorityStrategy;
import com.zlt.aps.lh.engine.strategy.ITrialProductionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 排程策略工厂
 * <p>负责创建和管理所有策略对象, 基于Spring容器实现</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class ScheduleStrategyFactory {

    @Resource
    private Map<String, IProductionStrategy> productionStrategyMap;

    @Resource
    private IMachineMatchStrategy machineMatchStrategy;

    @Resource
    private ISkuPriorityStrategy skuPriorityStrategy;

    @Resource
    private IFirstInspectionBalanceStrategy firstInspectionBalanceStrategy;

    @Resource
    private IMouldChangeBalanceStrategy mouldChangeBalanceStrategy;

    @Resource
    private ICapacityCalculateStrategy capacityCalculateStrategy;

    @Resource
    private IProductionShutdownStrategy productionShutdownStrategy;

    @Resource
    private ITrialProductionStrategy trialProductionStrategy;

    @Resource
    private IInsertOrderStrategy insertOrderStrategy;

    private final Map<String, IProductionStrategy> strategyCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        strategyCache.put(ScheduleTypeEnum.CONTINUOUS.getCode(),
                productionStrategyMap.get("continuousProductionStrategy"));
        strategyCache.put(ScheduleTypeEnum.NEW_SPEC.getCode(),
                productionStrategyMap.get("newSpecProductionStrategy"));
        log.info("排程策略工厂初始化完成, 已注册策略数: {}", strategyCache.size());
    }

    /**
     * 获取排产策略
     *
     * @param scheduleType 排程类型代码(01-续作, 02-新增)
     * @return 对应的排产策略实现
     * @throws IllegalArgumentException 未找到对应策略时抛出
     */
    public IProductionStrategy getProductionStrategy(String scheduleType) {
        IProductionStrategy strategy = strategyCache.get(scheduleType);
        if (strategy == null) {
            throw new IllegalArgumentException("未找到排程类型[" + scheduleType + "]对应的排产策略");
        }
        return strategy;
    }

    /**
     * 获取机台匹配策略
     *
     * @return 机台匹配策略
     */
    public IMachineMatchStrategy getMachineMatchStrategy() {
        return machineMatchStrategy;
    }

    /**
     * 获取SKU优先级策略
     *
     * @return SKU优先级策略
     */
    public ISkuPriorityStrategy getSkuPriorityStrategy() {
        return skuPriorityStrategy;
    }

    /**
     * 获取首检均衡策略
     *
     * @return 首检均衡策略
     */
    public IFirstInspectionBalanceStrategy getFirstInspectionBalanceStrategy() {
        return firstInspectionBalanceStrategy;
    }

    /**
     * 获取换模均衡策略
     *
     * @return 换模均衡策略
     */
    public IMouldChangeBalanceStrategy getMouldChangeBalanceStrategy() {
        return mouldChangeBalanceStrategy;
    }

    /**
     * 获取产能计算策略
     *
     * @return 产能计算策略
     */
    public ICapacityCalculateStrategy getCapacityCalculateStrategy() {
        return capacityCalculateStrategy;
    }

    /**
     * 获取开停产处理策略
     *
     * @return 开停产处理策略
     */
    public IProductionShutdownStrategy getProductionShutdownStrategy() {
        return productionShutdownStrategy;
    }

    /**
     * 获取试制/量试排产策略
     *
     * @return 试制量试策略
     */
    public ITrialProductionStrategy getTrialProductionStrategy() {
        return trialProductionStrategy;
    }

    /**
     * 获取插单处理策略
     *
     * @return 插单处理策略
     */
    public IInsertOrderStrategy getInsertOrderStrategy() {
        return insertOrderStrategy;
    }
}
