package com.zlt.aps.mp.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;


@Data
@TableName(value = "T_MP_PROC_VERSION")
@ApiModel(value = "排产版本对象", description = "排产版本对象 ")
public class MpFactoryProductionVersion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 工厂编号
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.factoryCode")
    @ApiModelProperty(value = "工厂编号", name = "factoryCode")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

    /**
     * 产品分类 数据字典：biz_product_type TBR 全钢 PCR 半钢
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.productTypeCode")
    @ApiModelProperty(value = "产品分类 数据字典：biz_product_type TBR 全钢 PCR 半钢", name = "productTypeCode")
    @TableField(value = "PRODUCT_TYPE_CODE")
    private String productTypeCode;

    /**
     * 年份
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.year")
    @ApiModelProperty(value = "年份", name = "year")
    @TableField(value = "YEAR")
    private Integer year;

    /**
     * 月份
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.month")
    @ApiModelProperty(value = "月份", name = "month")
    @TableField(value = "MONTH")
    private Integer month;

    /**
     * 需求计划版本
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.monthPlanVersion")
    @ApiModelProperty(value = "需求计划版本", name = "monthPlanVersion")
    @TableField(value = "MONTH_PLAN_VERSION")
    private String monthPlanVersion;

    /**
     * 计划类型：biz_plan_type 01 正常 02 订单预测 03 实单模拟
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.planType")
    @ApiModelProperty(value = "计划类型：biz_plan_type 01 正常 02 订单预测 03 实单模拟", name = "planType")
    @TableField(value = "PLAN_TYPE")
    private String planType;

    /**
     * 是否进行需求排产--选择需求版本
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.isSelectedDemand")
    @ApiModelProperty(value = "是否进行需求排产--选择需求版本", name = "isSelectedDemand")
    @TableField(value = "IS_SELECTED_DEMAND")
    private String isSelectedDemand;

    /**
     * 初始化版本号
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.productionInitVersion")
    @ApiModelProperty(value = "初始化版本号", name = "productionInitVersion")
    @TableField(value = "PRODUCTION_INIT_VERSION")
    private String productionInitVersion;

    /**
     * 排结构版本号
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.productionStVersion")
    @ApiModelProperty(value = "排结构版本号", name = "productionStVersion")
    @TableField(value = "PRODUCTION_ST_VERSION")
    private String productionStVersion;

    /**
     * 排产版本号
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.productionVersion")
    @ApiModelProperty(value = "排产版本号", name = "productionVersion")
    @TableField(value = "PRODUCTION_VERSION")
    private String productionVersion;

    /**
     * 是否自然月排产 0 不是 1 是
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.isNaturalMonth")
    @ApiModelProperty(value = "是否自然月排产 0 不是 1 是", name = "isNaturalMonth")
    @TableField(value = "IS_NATURAL_MONTH")
    private String isNaturalMonth;

    /**
     * 排产周期起始日
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.productionStartDate", width = 30, dateFormat = "yyyy-MM-dd")
    @ApiModelProperty(value = "排产周期起始日", name = "productionStartDate")
    @TableField(value = "PRODUCTION_START_DATE")
    private Date productionStartDate;

    /**
     * 排产周期结束日
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.productionEndDate", width = 30, dateFormat = "yyyy-MM-dd")
    @ApiModelProperty(value = "排产周期结束日", name = "productionEndDate")
    @TableField(value = "PRODUCTION_END_DATE")
    private Date productionEndDate;

    /**
     * 0 不是定稿 1 是定稿
     */
    @Excel(name = "ui.data.column.MpFactoryProductionVersion.isFinal")
    @ApiModelProperty(value = "0 不是定稿 1 是定稿", name = "isFinal")
    @TableField(value = "IS_FINAL")
    private String isFinal;

}