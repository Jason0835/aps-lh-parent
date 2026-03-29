package com.zlt.aps.lh.api.domain.dto;

import lombok.Data;

import java.util.Date;

/**
 * 硫化排程请求参数
 *
 * @author APS
 */
@Data
public class LhScheduleRequestDTO {

    /** 分厂编号 */
    private String factoryCode;
    /** 排程目标日期(默认T+2) */
    private Date scheduleDate;
    /** 月计划需求版本 */
    private String monthPlanVersion;
    /** 月计划排产版本 */
    private String productionVersion;
    /** 操作人 */
    private String operator;
}
