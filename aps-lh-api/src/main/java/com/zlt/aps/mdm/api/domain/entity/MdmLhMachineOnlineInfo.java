package com.zlt.aps.mdm.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 硫化在机信息实体类
 *
 * @author 自动生成
 * @date 2026-03-23
 */
@ApiModel(value = "硫化在机信息对象", description = "硫化在机信息表实体对象")
@Data
@TableName(value = "T_MDM_LH_MACHINE_ONLINE_INFO")
public class MdmLhMachineOnlineInfo extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;


    /**
     * 在机日期
     */
    @Excel(name = "ui.data.column.mdmLhMachineOnlineInfo.onlineDate")
    @ApiModelProperty(value = "在机日期", name = "onlineDate")
    @TableField(value = "ONLINE_DATE")
    private Date onlineDate;

    /**
     * 硫化机台
     */
    @Excel(name = "ui.data.column.mdmLhMachineOnlineInfo.lhCode")
    @ApiModelProperty(value = "硫化机台", name = "lhCode")
    @TableField(value = "LH_CODE")
    private String lhCode;

    /**
     * 在机物料编码（NC）
     */
    @Excel(name = "ui.data.column.mdmLhMachineOnlineInfo.materialCode")
    @ApiModelProperty(value = "在机物料编码（NC）", name = "materialCode")
    @TableField(value = "MATERIAL_CODE")
    private String materialCode;

    /**
     * 在机物料编码（MES）
     */
    @Excel(name = "ui.data.column.mdmLhMachineOnlineInfo.mesMaterialCode")
    @ApiModelProperty(value = "在机物料编码（MES）", name = "mesMaterialCode")
    @TableField(value = "MES_MATERIAL_CODE")
    private String mesMaterialCode;

    /**
     * 在机物料描述
     */
    @Excel(name = "ui.data.column.mdmLhMachineOnlineInfo.specDesc")
    @ApiModelProperty(value = "在机物料描述", name = "specDesc")
    @TableField(value = "SPEC_DESC")
    private String specDesc;

    /**
     * 左右模
     */
    @Excel(name = "ui.data.column.mdmLhMachineOnlineInfo.lrMolds")
    @ApiModelProperty(value = "左右模", name = "lrMolds")
    @TableField(value = "LR_MOLDS")
    private String lrMolds;

    /**
     * 版本号
     */
    @Excel(name = "ui.data.column.mdmLhMachineOnlineInfo.dataVersion")
    @ApiModelProperty(value = "版本号", name = "dataVersion")
    @TableField(value = "DATA_VERSION")
    private String dataVersion;

    /**
     * 分公司编码
     */
    @Excel(name = "ui.data.column.mdmLhMachineOnlineInfo.companyCode")
    @ApiModelProperty(value = "分公司编码", name = "companyCode")
    @TableField(value = "COMPANY_CODE")
    private String companyCode;

    /**
     * 厂别
     */
    @Excel(name = "ui.data.column.mdmLhMachineOnlineInfo.factoryCode")
    @ApiModelProperty(value = "厂别", name = "factoryCode")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

    /**
     * 删除标识
     */
    @Excel(name = "ui.data.column.mdmLhMachineOnlineInfo.isDelete")
    @ApiModelProperty(value = "删除标识", name = "isDelete")
    @TableField(value = "IS_DELETE")
    private Integer isDelete;
}
