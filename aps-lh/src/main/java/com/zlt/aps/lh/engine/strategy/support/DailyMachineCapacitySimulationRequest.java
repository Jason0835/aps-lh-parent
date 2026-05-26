package com.zlt.aps.lh.engine.strategy.support;

import com.zlt.aps.lh.api.domain.dto.SkuDailyPlanQuotaDTO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * dayN 机台产能模拟请求。
 */
@Data
public class DailyMachineCapacitySimulationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SKU物料编码 */
    private String materialCode;

    /** 日计划额度账本 */
    private Map<LocalDate, SkuDailyPlanQuotaDTO> dailyPlanQuotaMap;

    /** 候选机台逐日产能列表，顺序即启用优先级 */
    private List<Map<LocalDate, Integer>> machineDailyCapacityList;

    /** 初始启用机台数 */
    private int initialActiveMachines;

    /** 向后观察天数，不含当天 */
    private int shortageLookAheadDays;

    /** 排程窗口结束日期 */
    private LocalDate windowEndDate;

    /** 场景类型 */
    private String sceneType;
}
