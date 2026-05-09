package com.zlt.aps.lh.api.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * SKU 日维度排产需求 DTO
 * <p>按排程窗口内每个自然日生成一条记录，用于日计划桶控制和多机台拆量</p>
 *
 * @author APS
 */
@Data
public class SkuDailyScheduleDemandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 物料编码 */
    private String materialCode;

    /** 排程日期（该日 0 点） */
    private Date scheduleDate;

    /** 月计划当天 dayN 计划量 */
    private int dayPlanQty;

    /** 当天已继承/已锁定/已排产量 */
    private int inheritedQty;

    /** 当天欠产结转量 */
    private int carryForwardQty;

    /** 当天目标排产量（dayPlanQty - inheritedQty + carryForwardQty） */
    private int targetQty;

    /** 当天剩余未排量 */
    private int remainingQty;
}
