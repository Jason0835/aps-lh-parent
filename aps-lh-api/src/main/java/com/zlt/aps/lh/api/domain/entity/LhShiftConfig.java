package com.zlt.aps.lh.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 硫化班次配置实体（表 T_LH_SHIFT_CONFIG）
 *
 * @author APS
 */
@Getter
@Setter
@ApiModel(value = "硫化班次配置")
@TableName(value = "T_LH_SHIFT_CONFIG")
public class LhShiftConfig extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("工厂编号")
    @TableField("FACTORY_CODE")
    private String factoryCode;

    @ApiModelProperty("班次编号")
    @TableField("SHIFT_CODE")
    private String shiftCode;

    @ApiModelProperty("班次名称（如：T日早班、T+1日夜班等）")
    @TableField("SHIFT_NAME")
    private String shiftName;

    @ApiModelProperty("班次序号/索引（1~N）")
    @TableField("SHIFT_INDEX")
    private Integer shiftIndex;

    @ApiModelProperty("班次类型（早班/中班/夜班）")
    @TableField("SHIFT_TYPE")
    private String shiftType;

    @ApiModelProperty("开始时刻，格式 HH:mm:ss")
    @TableField("START_TIME")
    private String startTime;

    @ApiModelProperty("结束时刻，格式 HH:mm:ss")
    @TableField("END_TIME")
    private String endTime;

    @ApiModelProperty("日期偏移（0表示T日，1表示T+1，2表示T+2）")
    @TableField("DATE_OFFSET")
    private Integer dateOffset;

    @ApiModelProperty("班次时长（小时）")
    @TableField("SHIFT_DURATION")
    private Integer shiftDuration;
}
