package com.zlt.aps.lh.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 硫化排程日志实体类
 *
 * @author 自动生成
 * @date 2026-03-23
 */
@ApiModel(value = "硫化排程日志对象", description = "硫化排程日志表实体对象")
@Data
@TableName(value = "T_LH_SCHEDULE_PROCESS_LOG")
public class LhScheduleProcessLog extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;


    /**
     * 批次号
     */
    @Excel(name = "ui.data.column.lhScheduleProcessLog.batchNo")
    @ApiModelProperty(value = "批次号", name = "batchNo")
    @TableField(value = "BATCH_NO")
    private String batchNo;

    /**
     * 工单号
     */
    @Excel(name = "ui.data.column.lhScheduleProcessLog.orderNo")
    @ApiModelProperty(value = "工单号", name = "orderNo")
    @TableField(value = "ORDER_NO")
    private String orderNo;

    /**
     * 标题
     */
    @Excel(name = "ui.data.column.lhScheduleProcessLog.title")
    @ApiModelProperty(value = "标题", name = "title")
    @TableField(value = "TITLE")
    private String title;

    /**
     * 业务编码
     */
    @Excel(name = "ui.data.column.lhScheduleProcessLog.busiCode")
    @ApiModelProperty(value = "业务编码", name = "busiCode")
    @TableField(value = "BUSI_CODE")
    private String busiCode;

    /**
     * 日志明细
     */
    @Excel(name = "ui.data.column.lhScheduleProcessLog.logDetail")
    @ApiModelProperty(value = "日志明细", name = "logDetail")
    @TableField(value = "LOG_DETAIL")
    private String logDetail;

    /**
     * 删除标识（0未删除；1已删除）
     */
    @Excel(name = "ui.data.column.lhScheduleProcessLog.isDelete")
    @ApiModelProperty(value = "删除标识（0未删除；1已删除）", name = "isDelete")
    @TableField(value = "IS_DELETE")
    private Integer isDelete;
}
