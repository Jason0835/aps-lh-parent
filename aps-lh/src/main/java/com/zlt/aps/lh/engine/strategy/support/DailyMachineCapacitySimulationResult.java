package com.zlt.aps.lh.engine.strategy.support;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * dayN 机台产能模拟结果。
 */
@Data
public class DailyMachineCapacitySimulationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 最终启用机台数 */
    private int finalActiveMachines;

    /** 累计新增机台数 */
    private int totalAddedMachineCount;

    /** 最终仍未满足数量 */
    private int totalUnmetQty;

    /** 逐日决策明细 */
    private List<DailyMachineCapacityDayDecision> dayDecisionList =
            new ArrayList<DailyMachineCapacityDayDecision>(4);
}
