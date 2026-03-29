package com.zlt.aps.lh.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@ApiModel(value = "硫化参数信息对象", description = "硫化参数信息对象 ")
@Data
@TableName(value = "T_LH_PARAMS")
public class LhParams extends BaseEntity implements Serializable {


    private static final long serialVersionUID = 413651276300257467L;

    /**
     * 分厂编号
     */
    @Excel(name = "ui.data.column.lhParams.factoryCode")
    @ApiModelProperty(value = "分厂编号", name = "factoryCode")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

    /**
     * 参数代码
     */
    @Excel(name = "ui.data.column.lhParams.paramCode")
    @ApiModelProperty(value = "参数代码", name = "paramCode")
    @TableField(value = "PARAM_CODE")
    private String paramCode;

    /**
     * 参数名称
     */
    @Excel(name = "ui.data.column.lhParams.paramName")
    @ApiModelProperty(value = "参数名称", name = "paramName")
    @TableField(value = "PARAM_NAME")
    private String paramName;

    /**
     * 参数值
     */
    @Excel(name = "ui.data.column.lhParams.paramValue")
    @ApiModelProperty(value = "参数值", name = "paramValue")
    @TableField(value = "PARAM_VALUE")
    private String paramValue;

    /**
     * 备注
     */
    @Excel(name = "ui.data.column.lhParams.remark")
    @ApiModelProperty(value = "备注", name = "remark")
    @TableField(value = "REMARK")
    private String remark;

    /**
     * 参数值根据正则表达式校验是失败后的错误提示
     */
    @ApiModelProperty(value = "参数值根据正则表达式校验是失败后的错误提示", name = "errorTips")
    @TableField(value = "ERROR_TIPS")
    private String errorTips;


}
