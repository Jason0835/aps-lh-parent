package com.zlt.aps.mdm.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * APS设备保养计划实体
 * 对应表 T_MDM_DEV_MAINTENANCE_PLAN
 */
@ApiModel(value = "APS设备保养计划", description = "APS设备保养计划")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("T_MDM_DEV_MAINTENANCE_PLAN")
public class MdmDevMaintenancePlan extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "设备机台")
    @TableField(value = "DEV_CODE")
    private String devCode;

    @ApiModelProperty(value = "精度类型")
    @TableField(value = "PRECISION_TYPE")
    private String precisionType;

    @ApiModelProperty(value = "计划时间")
    @TableField(value = "OPER_TIME")
    private String operTime;

    @ApiModelProperty(value = "实际时间")
    @TableField(value = "FIRST_WASH_TIME")
    private String firstWashTime;

    @ApiModelProperty(value = "删除标识：0-正常，1-已删除")
    @TableField(value = "DEL_FLAG")
    private Integer delFlag;

    @ApiModelProperty(value = "版本号")
    @TableField(value = "DATA_VERSION")
    private String dataVersion;

    @ApiModelProperty(value = "分公司编码")
    @TableField(value = "COMPANY_CODE")
    private String companyCode;

    @ApiModelProperty(value = "厂别")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;
}
