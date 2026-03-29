package com.zlt.aps.mdm.api.domain.entity;

import com.ruoyi.common.core.web.domain.BaseEntity;
import com.zlt.common.annotation.ImportExcelValidated;
import lombok.Data;
import com.ruoyi.common.core.annotation.Excel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;


@ApiModel(value = "SKU日硫化产能对象", description = "SKU日硫化产能对象 ")
@Data
@TableName(value = "T_MDM_SKU_LH_CAPACITY")
public class MdmSkuLhCapacity extends BaseEntity {

    private static final long serialVersionUID = 1L;

     /** 工厂编号 */
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.factoryCode", dictType = "biz_factory_name")
    @ImportExcelValidated(required = true, isCode = true, maxLength = 20)
    @ApiModelProperty(value = "工厂编号", name = "factoryCode")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

    /** MES物料编码 */
//    @Excel(name = "ui.data.column.mdmSkuLhCapacity.mesMaterialCode")
    @ApiModelProperty(value = "MES物料编码", name = "mesMaterialCode")
    @TableField(value = "MES_MATERIAL_CODE")
    private String mesMaterialCode;

    /** 物料编码 */
    @ImportExcelValidated(required = true, maxLength = 30)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.materialCode")
    @ApiModelProperty(value = "物料编码", name = "materialCode")
    @TableField(value = "MATERIAL_CODE")
    private String materialCode;

    /** 物料描述 */
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.materialDesc")
    @ApiModelProperty(value = "物料描述", name = "materialDesc")
    @TableField(value = "MATERIAL_DESC")
    private String materialDesc;

    /** 班产 */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.classCapacity")
    @ApiModelProperty(value = "班产", name = "classCapacity")
    @TableField(value = "CLASS_CAPACITY")
    private Integer classCapacity;

    /** MES实际产量 */
//    @ImportExcelValidated(digits = true, min = 0, max = 999999)
//    @Excel(name = "ui.data.column.mdmSkuLhCapacity.mesCapacity")
    @ApiModelProperty(value = "MES实际产量", name = "mesCapacity")
    @TableField(value = "MES_CAPACITY")
    private Integer mesCapacity;

    /** 标准产能 */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.standardCapacity")
    @ApiModelProperty(value = "标准产能", name = "standardCapacity")
    @TableField(value = "STANDARD_CAPACITY")
    private Integer standardCapacity;

    /** APS计算日硫化量 */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.apsCapacity")
    @ApiModelProperty(value = "APS计算日硫化量", name = "apsCapacity")
    @TableField(value = "APS_CAPACITY")
    private Integer apsCapacity;

    /** 硫化总时间(min) */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.sumVulcanization")
    @ApiModelProperty(value = "硫化总时间(min)", name = "sumVulcanization")
    @TableField(value = "SUM_VULCANIZATION")
    private Integer sumVulcanization;

    /** 硫化总时间(s) */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.vulcanizationTime")
    @ApiModelProperty(value = "硫化总时间(s)", name = "vulcanizationTime")
    @TableField(value = "VULCANIZATION_TIME")
    private Integer vulcanizationTime;

    /** 机械动作时(s) */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.mechanicalTime")
    @ApiModelProperty(value = "机械动作时(s)", name = "mechanicalTime")
    @TableField(value = "MECHANICAL_TIME")
    private Integer mechanicalTime;

    /** 点检时间 */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.checkTime")
    @ApiModelProperty(value = "点检时间", name = "checkTime")
    @TableField(value = "CHECK_TIME")
    private Integer checkTime;

    /** 检查胶囊、喷胶膜 模具清理2次 */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.clearTime", width = 30)
    @ApiModelProperty(value = "检查胶囊、喷胶膜 模具清理2次", name = "clearTime")
    @TableField(value = "CLEAR_TIME")
    private Integer clearTime;

    /** 生产作业时间(s) */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.productionTime")
    @ApiModelProperty(value = "生产作业时间(s)", name = "productionTime")
    @TableField(value = "PRODUCTION_TIME")
    private Integer productionTime;

    /** 标准作业时间(s) */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.standardTime")
    @ApiModelProperty(value = "标准作业时间(s)", name = "standardTime")
    @TableField(value = "STANDARD_TIME")
    private Integer standardTime;

    /** 就餐时间 */
    @ImportExcelValidated(digits = true, min = 0, max = 999999)
    @Excel(name = "ui.data.column.mdmSkuLhCapacity.dineTime")
    @ApiModelProperty(value = "就餐时间", name = "dineTime")
    @TableField(value = "DINE_TIME")
    private Integer dineTime;

    @ImportExcelValidated(maxLength = 500)
    @Excel(name = "ui.common.column.remark")
    @ApiModelProperty(value = "备注", name = "remark")
    @TableField(value = "REMARK")
    private String remark;


}
