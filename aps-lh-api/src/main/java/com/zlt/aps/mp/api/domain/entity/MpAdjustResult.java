package com.zlt.aps.mp.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;



@ApiModel(value = "调整-调整结果记录对象", description = "调整-调整结果记录对象 ")
@Data
@TableName(value = "T_MP_ADJUST_RESULT")
public class MpAdjustResult extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 分厂编号 */
    @Excel(name = "ui.data.column.mpAdjustResult.factoryCode", dictType = "biz_factory_name")
    @ApiModelProperty(value = "分厂编号，字典：biz_factory_name", name = "factoryCode")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

    /** 年份 */
    @Excel(name = "ui.data.column.mpAdjustResult.year")
    @ApiModelProperty(value = "年份", name = "year")
    @TableField(value = "YEAR")
    private Integer year;

    /** 月份 */
    @Excel(name = "ui.data.column.mpAdjustResult.month")
    @ApiModelProperty(value = "月份", name = "month")
    @TableField(value = "MONTH")
    private Integer month;

    /** 版本规则：ADJ+年月日+3位流水号； */
    @Excel(name = "ui.data.column.mpAdjustResult.version")
    @ApiModelProperty(value = "版本规则：ADJ+年月日+3位流水号；", name = "version")
    @TableField(value = "VERSION")
    private String version;

    /**
     * 销售生产需求计划版本
     */
    @Excel(name = "ui.data.column.mpAdjustResult.monthPlanVersion")
    @ApiModelProperty(value = "销售生产需求计划版本", name = "monthPlanVersion")
    @TableField(value = "MONTH_PLAN_VERSION")
    private String monthPlanVersion;

    /**
     * 排产计划版本
     */
    @Excel(name = "ui.data.column.mpAdjustResult.productionVersion")
    @ApiModelProperty(value = "排产计划版本", name = "productionVersion")
    @TableField(value = "PRODUCTION_VERSION")
    private String productionVersion;

     /** 调整类型 01-结构内，02-结构延长，03-结构缩短，04-新增结构 */
    @Excel(name = "ui.data.column.mpAdjustResult.adjustType")
    @ApiModelProperty(value = "调整类型 01-结构内，02-结构延长，03-结构缩短，04-新增结构", name = "adjustType")
    @TableField(value = "ADJUST_TYPE")
    private String adjustType;

    /** 成型机台 */
    @Excel(name = "ui.data.column.mpAdjustResult.cxMachineCode")
    @ApiModelProperty(value = "成型机台", name = "cxMachineCode")
    @TableField(value = "CX_MACHINE_CODE")
    private String cxMachineCode;

    /** 产品结构 */
    @Excel(name = "ui.data.column.mpAdjustResult.structureName")
    @ApiModelProperty(value = "产品结构", name = "structureName")
    @TableField(value = "STRUCTURE_NAME")
    private String structureName;

    /** MES物料编码 */
    @Excel(name = "ui.data.column.mpAdjustResult.mesMaterialCode")
    @ApiModelProperty(value = "MES物料编码", name = "mesMaterialCode")
    @TableField(value = "MES_MATERIAL_CODE")
    private String mesMaterialCode;

    /** 物料编码 */
    @Excel(name = "ui.data.column.mpAdjustResult.materialCode")
    @ApiModelProperty(value = "物料编码", name = "materialCode")
    @TableField(value = "MATERIAL_CODE")
    private String materialCode;

    /** 物料描述 */
    @Excel(name = "ui.data.column.mpAdjustResult.materialDesc")
    @ApiModelProperty(value = "物料描述", name = "materialDesc")
    @TableField(value = "MATERIAL_DESC")
    private String materialDesc;

    /** 是否含特殊物料 */
    @Excel(name = "ui.data.column.mpAdjustResult.hasSpecialMaterial")
    @ApiModelProperty(value = "是否含特殊物料", name = "hasSpecialMaterial")
    @TableField(value = "HAS_SPECIAL_MATERIAL")
    private String hasSpecialMaterial;

    /**
     * 模具使用变化信息如2-4-2,或是2-4或是2
     */
    @ApiModelProperty(value = "模具使用变化信息如2-4-2,或是2-4或是2", name = "mouldChangeInfo")
    @TableField(value = "MOULD_CHANGE_INFO")
    private String mouldChangeInfo;

    /**
     * 调整标识，0-没有调整,1-有调整
     */
    @ApiModelProperty(value = "调整标识", name = "adjustFlag")
    @TableField(value = "ADJUST_FLAG")
    private String adjustFlag;

    /** 总计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.totalPlanQty")
    @ApiModelProperty(value = "总计划量", name = "totalPlanQty")
    @TableField(value = "TOTAL_PLAN_QTY")
    private Integer totalPlanQty;

    /** 主花纹 */
    @Excel(name = "ui.data.column.mpAdjustResult.mainPattern")
    @ApiModelProperty(value = "主花纹", name = "mainPattern")
    @TableField(value = "MAIN_PATTERN")
    private String mainPattern;

    /** 型腔 */
    @Excel(name = "ui.data.column.mpAdjustResult.mouldCavityQty")
    @ApiModelProperty(value = "型腔", name = "mouldCavityQty")
    @TableField(value = "MOULD_CAVITY_QTY")
    private Integer mouldCavityQty;

    /** 活块 */
    @Excel(name = "ui.data.column.mpAdjustResult.typeBlockQty")
    @ApiModelProperty(value = "活块", name = "typeBlockQty")
    @TableField(value = "TYPE_BLOCK_QTY")
    private Integer typeBlockQty;

    /**
     * 开始日期
     */
    @Excel(name = "ui.data.column.FactoryMonthPlanFinalResult.beginDay")
    @ApiModelProperty(value = "开始日期", name = "beginDay")
    @TableField(value = "BEGIN_DAY")
    private Integer beginDay;

    /**
     * 结束日期
     */
    @Excel(name = "ui.data.column.FactoryMonthPlanFinalResult.endDay")
    @ApiModelProperty(value = "结束日期", name = "endDay")
    @TableField(value = "END_DAY")
    private Integer endDay;

    /** 1号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day1")
    @ApiModelProperty(value = "1号计划量", name = "day1")
    @TableField(value = "DAY_1")
    private Integer day1;

    /** 2号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day2")
    @ApiModelProperty(value = "2号计划量", name = "day2")
    @TableField(value = "DAY_2")
    private Integer day2;

    /** 3号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day3")
    @ApiModelProperty(value = "3号计划量", name = "day3")
    @TableField(value = "DAY_3")
    private Integer day3;

    /** 4号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day4")
    @ApiModelProperty(value = "4号计划量", name = "day4")
    @TableField(value = "DAY_4")
    private Integer day4;

    /** 5号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day5")
    @ApiModelProperty(value = "5号计划量", name = "day5")
    @TableField(value = "DAY_5")
    private Integer day5;

    /** 6号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day6")
    @ApiModelProperty(value = "6号计划量", name = "day6")
    @TableField(value = "DAY_6")
    private Integer day6;

    /** 7号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day7")
    @ApiModelProperty(value = "7号计划量", name = "day7")
    @TableField(value = "DAY_7")
    private Integer day7;

    /** 8号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day8")
    @ApiModelProperty(value = "8号计划量", name = "day8")
    @TableField(value = "DAY_8")
    private Integer day8;

    /** 9号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day9")
    @ApiModelProperty(value = "9号计划量", name = "day9")
    @TableField(value = "DAY_9")
    private Integer day9;

    /** 10号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day10")
    @ApiModelProperty(value = "10号计划量", name = "day10")
    @TableField(value = "DAY_10")
    private Integer day10;

    /** 11号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day11")
    @ApiModelProperty(value = "11号计划量", name = "day11")
    @TableField(value = "DAY_11")
    private Integer day11;

    /** 12号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day12")
    @ApiModelProperty(value = "12号计划量", name = "day12")
    @TableField(value = "DAY_12")
    private Integer day12;

    /** 13号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day13")
    @ApiModelProperty(value = "13号计划量", name = "day13")
    @TableField(value = "DAY_13")
    private Integer day13;

    /** 14号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day14")
    @ApiModelProperty(value = "14号计划量", name = "day14")
    @TableField(value = "DAY_14")
    private Integer day14;

    /** 15号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day15")
    @ApiModelProperty(value = "15号计划量", name = "day15")
    @TableField(value = "DAY_15")
    private Integer day15;

    /** 16号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day16")
    @ApiModelProperty(value = "16号计划量", name = "day16")
    @TableField(value = "DAY_16")
    private Integer day16;

    /** 17号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day17")
    @ApiModelProperty(value = "17号计划量", name = "day17")
    @TableField(value = "DAY_17")
    private Integer day17;

    /** 18号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day18")
    @ApiModelProperty(value = "18号计划量", name = "day18")
    @TableField(value = "DAY_18")
    private Integer day18;

    /** 19号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day19")
    @ApiModelProperty(value = "19号计划量", name = "day19")
    @TableField(value = "DAY_19")
    private Integer day19;

    /** 20号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day20")
    @ApiModelProperty(value = "20号计划量", name = "day20")
    @TableField(value = "DAY_20")
    private Integer day20;

    /** 21号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day21")
    @ApiModelProperty(value = "21号计划量", name = "day21")
    @TableField(value = "DAY_21")
    private Integer day21;

    /** 22号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day22")
    @ApiModelProperty(value = "22号计划量", name = "day22")
    @TableField(value = "DAY_22")
    private Integer day22;

    /** 23号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day23")
    @ApiModelProperty(value = "23号计划量", name = "day23")
    @TableField(value = "DAY_23")
    private Integer day23;

    /** 24号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day24")
    @ApiModelProperty(value = "24号计划量", name = "day24")
    @TableField(value = "DAY_24")
    private Integer day24;

    /** 25号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day25")
    @ApiModelProperty(value = "25号计划量", name = "day25")
    @TableField(value = "DAY_25")
    private Integer day25;

    /** 26号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day26")
    @ApiModelProperty(value = "26号计划量", name = "day26")
    @TableField(value = "DAY_26")
    private Integer day26;

    /** 27号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day27")
    @ApiModelProperty(value = "27号计划量", name = "day27")
    @TableField(value = "DAY_27")
    private Integer day27;

    /** 28号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day28")
    @ApiModelProperty(value = "28号计划量", name = "day28")
    @TableField(value = "DAY_28")
    private Integer day28;

    /** 29号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day29")
    @ApiModelProperty(value = "29号计划量", name = "day29")
    @TableField(value = "DAY_29")
    private Integer day29;

    /** 30号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day30")
    @ApiModelProperty(value = "30号计划量", name = "day30")
    @TableField(value = "DAY_30")
    private Integer day30;

    /** 31号计划量 */
    @Excel(name = "ui.data.column.mpAdjustResult.day31")
    @ApiModelProperty(value = "31号计划量", name = "day31")
    @TableField(value = "DAY_31")
    private Integer day31;

    /** 是否锁定上机日期：0-否，1-是 */
    @Excel(name = "ui.data.column.mpAdjustResult.isLockSchedule", dictType = "biz_yes_no")
    @ApiModelProperty(value = "是否锁定上机日期：0-否，1-是", name = "isLockSchedule")
    @TableField(value = "IS_LOCK_SCHEDULE")
    private String isLockSchedule;

    /**
     * 生产实际排产量
     */
    @Excel(name = "ui.data.column.mpAdjustResult.totalQty")
    @ApiModelProperty(value = "生产实际排产量", name = "totalQty")
    @TableField(value = "TOTAL_QTY")
    private Integer totalQty;

    /**
     * 高优先级排产数量
     */
    @Excel(name = "ui.data.column.mpAdjustResult.heightProductionQty")
    @ApiModelProperty(value = "高优先级排产数量", name = "heightProductionQty")
    @TableField(value = "HEIGHT_PRODUCTION_QTY")
    private Integer heightProductionQty;

    /**
     * 中优先级排产数量
     */
    @Excel(name = "ui.data.column.mpAdjustResult.midProductionQty")
    @ApiModelProperty(value = "中优先级排产数量", name = "midProductionQty")
    @TableField(value = "MID_PRODUCTION_QTY")
    private Integer midProductionQty;

    /**
     * 周期排产储备排产数量
     */
    @Excel(name = "ui.data.column.mpAdjustResult.cycleProductionQty")
    @ApiModelProperty(value = "周期排产储备排产数量", name = "cycleProductionQty")
    @TableField(value = "CYCLE_PRODUCTION_QTY")
    private Integer cycleProductionQty;

    /**
     * 常规储备排产数量
     */
    @Excel(name = "ui.data.column.mpAdjustResult.conventionProductionQty")
    @ApiModelProperty(value = "常规储备排产数量", name = "conventionProductionQty")
    @TableField(value = "CONVENTION_PRODUCTION_QTY")
    private Integer conventionProductionQty;

    /**
     * 暂缓订单排产数量
     */
    @Excel(name = "ui.data.column.mpAdjustResult.postponeProductionQty")
    @ApiModelProperty(value = "暂缓订单排产数量", name = "postponeProductionQty")
    @TableField(value = "POSTPONE_PRODUCTION_QTY")
    private Integer postponeProductionQty;

    /**
     * 试制量试排产量
     */
    @Excel(name = "ui.data.column.mpAdjustResult.trialProductionQty")
    @ApiModelProperty(value = "试制量试排产量", name = "trialProductionQty")
    @TableField(value = "TRIAL_PRODUCTION_QTY")
    private Integer trialProductionQty;

    /**
     * 差异量(未排产数量)
     */
    @Excel(name = "ui.data.column.mpAdjustResult.differenceQty")
    @ApiModelProperty(value = "差异量(未排产数量)", name = "differenceQty")
    @TableField(value = "DIFFERENCE_QTY")
    private Integer differenceQty;



}