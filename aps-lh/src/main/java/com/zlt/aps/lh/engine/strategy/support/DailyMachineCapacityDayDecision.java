package com.zlt.aps.lh.engine.strategy.support;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * dayN 机台产能模拟逐日决策。
 */
@Data
public class DailyMachineCapacityDayDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前生产日期 */
    private LocalDate productionDate;

    /** 允许追补的截止日期 */
    private LocalDate lookAheadEndDate;

    /** 当前日期到追补截止日的需求量 */
    private int demandQty;

    /** 当前启用机台在追补窗口内的产能 */
    private int capacityQty;

    /** 当天模拟后的启用机台数 */
    private int activeMachineCount;

    /** 当天新增机台数 */
    private int addedMachineCount;

    /** 仍未满足的数量 */
    private int unmetQty;

    /** 是否执行扩机或保留 */
    private boolean changed;

    /** 决策原因 */
    private String reason;
}
