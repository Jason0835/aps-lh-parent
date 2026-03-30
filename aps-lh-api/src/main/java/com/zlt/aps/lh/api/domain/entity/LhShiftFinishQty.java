package com.zlt.aps.mps.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.web.domain.BaseEntity;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 硫化排程各班次完成量
 */
@Data
@TableName("T_LH_SHIFT_FINISH_QTY")
public class LhShiftFinishQty extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分厂编号
     */
    @TableField("FACTORY_CODE")
    private String factoryCode;

    /**
     * 工单号，自动生成，批次号+4位定长自增序号
     */
    @TableField("ORDER_NO")
    private String orderNo;

    /**
     * 排程日期
     */
    @TableField("SCHEDULE_DATE")
    private Date scheduleDate;

    /**
     * 硫化机台编号
     */
    @TableField("LH_MACHINE_CODE")
    private String lhMachineCode;

    /**
     * 物料编码
     */
    @TableField("MATERIAL_CODE")
    private String materialCode;

    /**
     * 一班完成量
     */
    @TableField("CLASS1_FINISH_QTY")
    private Integer class1FinishQty = 0;

    /**
     * 二班完成量
     */
    @TableField("CLASS2_FINISH_QTY")
    private Integer class2FinishQty = 0;

    /**
     * 三班完成量
     */
    @TableField("CLASS3_FINISH_QTY")
    private Integer class3FinishQty = 0;

    /**
     * 四班完成量
     */
    @TableField("CLASS4_FINISH_QTY")
    private Integer class4FinishQty = 0;

    /**
     * 五班完成量
     */
    @TableField("CLASS5_FINISH_QTY")
    private Integer class5FinishQty = 0;

    /**
     * 六班完成量
     */
    @TableField("CLASS6_FINISH_QTY")
    private Integer class6FinishQty = 0;

    /**
     * 七班完成量
     */
    @TableField("CLASS7_FINISH_QTY")
    private Integer class7FinishQty = 0;

    /**
     * 八班完成量
     */
    @TableField("CLASS8_FINISH_QTY")
    private Integer class8FinishQty = 0;

    /**
     * 版本号
     */
    @TableField("DATA_VERSION")
    private String dataVersion;

}
