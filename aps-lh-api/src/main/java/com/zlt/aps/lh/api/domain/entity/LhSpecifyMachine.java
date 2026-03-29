package com.zlt.aps.lh.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@ApiModel(value = "硫化定点机台信息对象", description = "硫化定点机台信息对象 ")
@Data
@TableName(value = "T_LH_SPECIFY_MACHINE")
public class LhSpecifyMachine  extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1149060431772573064L;

    /** 分厂编号 */
    @ApiModelProperty(value = "分厂编号", name = "factoryCode")
    @TableField(value = "FACTORY_CODE")
    @Excel(name = "ui.data.column.result.factoryCode", dictType = "biz_factory_name")
    private String factoryCode;

     /** 规格代码 */
    @Excel(name = "ui.data.column.lhSpecifyMachine.specCode")
    @ApiModelProperty(value = "规格代码", name = "specCode")
    @TableField(value = "SPEC_CODE")
    private String specCode;

    /** 机台编号 */
    @Excel(name = "ui.data.column.lhSpecifyMachine.machineCode")
    @ApiModelProperty(value = "机台编号", name = "machineCode")
    @TableField(value = "MACHINE_CODE")
    private String machineCode;

    /** 线路，数据维护在数据字典：0-生产线、1-备用线 */
    @Excel(name = "ui.data.column.lhSpecifyMachine.lineType", dictType = "LINE_TYPE")
    @ApiModelProperty(value = "线路，数据维护在数据字典：0-生产线、1-备用线", name = "lineType")
    @TableField(value = "LINE_TYPE")
    private String lineType;

    /** 作业类型，数据维护在数据字典：0-限制作业；1-不可作业 */
    @Excel(name = "ui.data.column.lhSpecifyMachine.jobType", dictType = "JOB_TYPE")
    @ApiModelProperty(value = "作业类型，数据维护在数据字典：0-限制作业；1-不可作业", name = "jobType")
    @TableField(value = "JOB_TYPE")
    private String jobType;


}
