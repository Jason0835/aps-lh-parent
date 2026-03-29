package com.zlt.aps.lh.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 模具清洗计划实体类
 *
 * @author 自动生成
 * @date 2026-03-23
 */
@ApiModel(value = "模具清洗计划对象", description = "模具清洗计划表实体对象")
@Data
@TableName(value = "T_LH_CLEANING_PLAN")
public class LhCleaningPlan extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;


    /**
     * 模具编码
     */
    @Excel(name = "ui.data.column.lhCleaningPlan.moldCode")
    @ApiModelProperty(value = "模具编码", name = "moldCode")
    @TableField(value = "MOLD_CODE")
    private String moldCode;

    /**
     * 硫化机编码
     */
    @Excel(name = "ui.data.column.lhCleaningPlan.lhMachineCode")
    @ApiModelProperty(value = "硫化机编码", name = "lhMachineCode")
    @TableField(value = "LH_MACHINE_CODE")
    private String lhMachineCode;

    /**
     * 计划类型：干冰清洗/喷砂清洗
     */
    @Excel(name = "ui.data.column.lhCleaningPlan.planType")
    @ApiModelProperty(value = "计划类型", name = "planType", notes = "干冰清洗/喷砂清洗")
    @TableField(value = "PLAN_TYPE")
    private String planType;

    /**
     * 计划清洗时间
     */
    @Excel(name = "ui.data.column.lhCleaningPlan.planTime")
    @ApiModelProperty(value = "计划清洗时间", name = "planTime")
    @TableField(value = "PLAN_TIME")
    private Date planTime;

    /**
     * 实际清洗时间
     */
    @Excel(name = "ui.data.column.lhCleaningPlan.actualTime")
    @ApiModelProperty(value = "实际清洗时间", name = "actualTime")
    @TableField(value = "ACTUAL_TIME")
    private Date actualTime;

    /**
     * 计划状态：待清洗/清洗中/已清洗/已取消
     */
    @Excel(name = "ui.data.column.lhCleaningPlan.planStatus")
    @ApiModelProperty(value = "计划状态", name = "planStatus", notes = "待清洗/清洗中/已清洗/已取消")
    @TableField(value = "PLAN_STATUS")
    private String planStatus;

    /**
     * 删除标识
     */
    @Excel(name = "ui.data.column.lhCleaningPlan.isDelete")
    @ApiModelProperty(value = "删除标识", name = "isDelete")
    @TableField(value = "IS_DELETE")
    private Integer isDelete;
}
