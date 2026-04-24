package com.zlt.aps.lh.api.domain.dto;

import lombok.Data;

import java.util.Date;

/**
 * 机台清洗时间窗口。
 *
 * @author APS
 */
@Data
public class MachineCleaningWindowDTO {

    /** 清洗类型 */
    private String cleanType;
    /** 左右模标识 */
    private String leftRightMould;
    /** 清洗开始时间 */
    private Date cleanStartTime;
    /** 清洗结束时间 */
    private Date cleanEndTime;
    /** 清洗结束后机台再次可开产时间 */
    private Date readyTime;
}
