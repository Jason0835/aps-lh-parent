package com.zlt.aps.lh.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * APS胶囊已使用次数
 *
 * @author zlt
 * @since 2026/04/09
 */
@ApiModel(value = "APS胶囊已使用次数", description = "APS胶囊已使用次数")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("T_LH_REPAIR_CAPSULE")
public class LhRepairCapsule extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;
    @ApiModelProperty(value = "分厂")
    @TableField(value = "FACTORY_CODE")
    @Excel(name = "ui.data.column.lhRepairCapsule.factoryCode", dictType = "biz_factory_name")
    private String factoryCode;

    @ApiModelProperty(value = "获取日期")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @TableField(value = "OBTAIN_TIME")
    @Excel(name = "ui.data.column.lhRepairCapsule.obtainTime", dateFormat = "yyyy-MM-dd")
    private Date obtainTime;

    @ApiModelProperty(value = "硫化机台")
    @TableField(value = "LH_CODE")
    @Excel(name = "ui.data.column.lhRepairCapsule.lhCode")
    private String lhCode;

    @ApiModelProperty(value = "物料编码")
    @TableField(value = "MATERIAL_CODE")
    @Excel(name = "ui.data.column.lhRepairCapsule.materialCode")
    private String materialCode;

    @ApiModelProperty(value = "胶囊已使用次数")
    @TableField(value = "REPLACE_CAPSULE_COUNT")
    @Excel(name = "ui.data.column.lhRepairCapsule.replaceCapsuleCount")
    private Integer replaceCapsuleCount;

    @ApiModelProperty(value = "胶囊已使用次数2")
    @TableField(value = "REPLACE_CAPSULE_COUNT2")
    @Excel(name = "ui.data.column.lhRepairCapsule.replaceCapsuleCount2")
    private Integer replaceCapsuleCount2;

    @ApiModelProperty(value = "品牌")
    @TableField(value = "BRAND")
    @Excel(name = "ui.data.column.lhRepairCapsule.brand", dictType = "biz_brand_type")
    private String brand;

    @ApiModelProperty(value = "版本号")
    @TableField(value = "DATA_VERSION")
    private String dataVersion;

    @ApiModelProperty(value = "分公司编码")
    @TableField(value = "COMPANY_CODE")
    private String companyCode;

    @ApiModelProperty(value = "获取日期开始")
    @TableField(exist = false)
    private String obtainTimeBegin;

    @ApiModelProperty(value = "获取日期结束")
    @TableField(exist = false)
    private String obtainTimeEnd;
}

