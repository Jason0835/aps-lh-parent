package com.zlt.aps.mps.domain;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 硫化排程各班次完成量
 * @TableName T_LH_SHIFT_FINISH_QTY
 */
@Data
public class LhShiftFinishQty implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 工单号，自动生成，批次号+4位定长自增序号
     */
    private String orderNo;

    /**
     * 排程日期
     */
    private Date scheduleDate;

    /**
     * 硫化机台编号
     */
    private String lhMachineCode;

    /**
     * 物料编码
     */
    private String materialCode;

		/**
		 * 一班完成量
		 */
		private Integer class1FinishQty = 0;

		/**
		 * 二班完成量
		 */
		private Integer class2FinishQty = 0;

		/**
		 * 三班完成量
		 */
		private Integer class3FinishQty = 0;

		/**
		 * 四班完成量
		 */
		private Integer class4FinishQty = 0;

		/**
		 * 五班完成量
		 */
		private Integer class5FinishQty = 0;

		/**
		 * 六班完成量
		 */
		private Integer class6FinishQty = 0;

		/**
		 * 七班完成量
		 */
		private Integer class7FinishQty = 0;

		/**
		 * 八班完成量
		 */
		private Integer class8FinishQty = 0;


    /**
     * 备注
     */
    private String remark;

    /**
     * 版本号
     */
    private String dataVersion;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 更新时间
     */
    private Date updateDate;

    /**
     * 删除标识：0--正常，1-删除
     */
    private Integer isDelete;

}
