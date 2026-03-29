package com.zlt.aps.mdm.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import com.zlt.common.annotation.ImportExcelValidated;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;


@ApiModel(value = "工作日历对象", description = "工作日历对象 ")
@Data
@TableName(value = "T_MDM_WORK_CALENDAR")
public class MdmWorkCalendar extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 工厂编号
     */
    @Excel(name = "ui.data.column.mdmWorkCalendar.factoryCode", dictType = "biz_factory_name")
    @ApiModelProperty(value = "工厂编号", name = "factoryCode")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

    /**
     * 01-月计划,02-硫化,03-成型,04-胎面,05-胎侧,06--内衬,07-垫胶,08-胎圈,09-钢丝圈,10-斜裁,11-直裁,12-压延,15-零度,16-密炼
     */
    @Excel(name = "ui.data.column.mdmWorkCalendar.procCode", dictType = "work_calendar_proc")
    @ApiModelProperty(value = "01-月计划,02-硫化,03-成型,04-胎面,05-胎侧,06--内衬,07-垫胶,08-胎圈,09-钢丝圈,10-斜裁,11-直裁,12-压延,15-零度,16-密炼", name = "procCode")
    @TableField(value = "PROC_CODE")
    private String procCode;

    /**
     * 年份
     */
    @ImportExcelValidated(required = true, min = 0, max = 9999)
    @Excel(name = "ui.data.column.mdmWorkCalendar.year")
    @ApiModelProperty(value = "年份", name = "year")
    @TableField(value = "YEAR")
    private Integer year;

    /**
     * 月份
     */
    @ImportExcelValidated(required = true, min = 1, max = 12)
    @Excel(name = "ui.data.column.mdmWorkCalendar.month")
    @ApiModelProperty(value = "月份", name = "month")
    @TableField(value = "MONTH")
    private Integer month;

    /**
     * 日期
     */
    @Excel(name = "ui.data.column.mdmWorkCalendar.day")
    @ApiModelProperty(value = "日期", name = "day")
    @TableField(value = "DAY")
    private Integer day;

    /**
     * 日期
     */
    @ImportExcelValidated(required = true, date = true)
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "ui.data.column.mdmWorkCalendar.productionDate", width = 30, dateFormat = "yyyy-MM-dd")
    @ApiModelProperty(value = "日期", name = "productionDate")
    @TableField(value = "PRODUCTION_DATE")
    private Date productionDate;

    /**
     * 一班开停产标志，0-停,1-开
     */
    @Excel(name = "ui.data.column.mdmWorkCalendar.oneShiftFlag", dictType = "sys_enable_disable")
    @ApiModelProperty(value = "一班开停产标志，0-停,1-开", name = "oneShiftFlag")
    @TableField(value = "ONE_SHIFT_FLAG")
    private String oneShiftFlag;

    /**
     * 二班开停产标志，0-停,1-开
     */
    @Excel(name = "ui.data.column.mdmWorkCalendar.twoShiftFlag", dictType = "sys_enable_disable")
    @ApiModelProperty(value = "二班开停产标志，0-停,1-开", name = "twoShiftFlag")
    @TableField(value = "TWO_SHIFT_FLAG")
    private String twoShiftFlag;

    /**
     * 三班开停产标志，0-停,1-开
     */
    @Excel(name = "ui.data.column.mdmWorkCalendar.threeShiftFlag", dictType = "sys_enable_disable")
    @ApiModelProperty(value = "三班开停产标志，0-停,1-开", name = "threeShiftFlag")
    @TableField(value = "THREE_SHIFT_FLAG")
    private String threeShiftFlag;

    /**
     * 日期开停产标志，0-停,1-开
     */
    @Excel(name = "ui.data.column.mdmWorkCalendar.dayFlag", dictType = "sys_enable_disable")
    @ApiModelProperty(value = "日期开停产标志，0-停,1-开", name = "dayFlag")
    @TableField(value = "DAY_FLAG")
    private String dayFlag;

    /**
     * 比例
     */
    @ImportExcelValidated(digits = true, min = 0, max = 100)
    @Excel(name = "ui.data.column.mdmWorkCalendar.rate")
    @ApiModelProperty(value = "比例", name = "rate")
    @TableField(value = "RATE")
    private Integer rate;

    /**
     * 日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "日期", name = "calendarTime")
    @TableField(exist = false)
    private Date calendarTime;

    /**
     * 节假日名称
     */
    @ApiModelProperty(value = "节假日名称", name = "holidayNames")
    @TableField(exist = false)
    private String holidayNames;

    /**
     * 源工序
     */
    @ApiModelProperty(value = "源工序", name = "sourceProcCode")
    @TableField(exist = false)
    private String sourceProcCode;

    /**
     * 目标工序
     */
    @ApiModelProperty(value = "目标工序", name = "targetProcCode")
    @TableField(exist = false)
    private String targetProcCode;

    /**
     * 源年份
     */
    @ApiModelProperty(value = "源年份", name = "sourceYear")
    @TableField(exist = false)
    private Integer sourceYear;

    /**
     * 源月份
     */
    @ApiModelProperty(value = "源月份", name = "sourceMonth")
    @TableField(exist = false)
    private Integer sourceMonth;

    /**
     * 目标年份
     */
    @ApiModelProperty(value = "目标年份", name = "targetYear")
    @TableField(exist = false)
    private Integer targetYear;

    /**
     * 目标月份
     */
    @ApiModelProperty(value = "目标月份", name = "targetMonth")
    @TableField(exist = false)
    private Integer targetMonth;

    /**
     * 复制时源工厂编号，字典：biz_factory_name
     */
    @ApiModelProperty(value = "复制时源工厂编号，字典：biz_factory_name", name = "sourceFactoryCode")
    @TableField(exist = false)
    private String sourceFactoryCode;

    /**
     * 复制时目标工厂编号，字典：biz_factory_name
     */
    @ApiModelProperty(value = "复制时目标工厂编号，字典：biz_factory_name", name = "targetFactoryCode")
    @TableField(exist = false)
    private String targetFactoryCode;
}
