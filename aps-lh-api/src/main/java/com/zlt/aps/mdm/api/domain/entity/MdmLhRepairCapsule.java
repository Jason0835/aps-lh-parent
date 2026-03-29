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
 * APS胶囊已使用次数
 *
 * @author zlt
 * @since 2025/12/25
 */
@ApiModel(value = "APS胶囊已使用次数", description = "APS胶囊已使用次数")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("T_MDM_LH_REPAIR_CAPSULE")
public class MdmLhRepairCapsule extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "获取日期")
    @TableField(value = "OBTAIN_TIME")
    private String obtainTime;

    @ApiModelProperty(value = "硫化机台")
    @TableField(value = "LH_CODE")
    private String lhCode;

    @ApiModelProperty(value = "当前生产的物料编码")
    @TableField(value = "MATERIAL_CODE")
    private String materialCode;

    @ApiModelProperty(value = "已使用次数")
    @TableField(value = "REPLACE_CAPSULE_COUNT")
    private Integer replaceCapsuleCount;

    @ApiModelProperty(value = "已使用次数2")
    @TableField(value = "REPLACE_CAPSULE_COUNT2")
    private Integer replaceCapsuleCount2;

    @ApiModelProperty(value = "品牌")
    @TableField(value = "BRAND")
    private String brand;

    @ApiModelProperty(value = "备注")
    @TableField(value = "REMARK")
    private String remark;

    @ApiModelProperty(value = "版本号")
    @TableField(value = "DATA_VERSION")
    private String dataVersion;

    @ApiModelProperty(value = "分公司编码")
    @TableField(value = "COMPANY_CODE")
    private String companyCode;

    @ApiModelProperty(value = "分厂")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

}
