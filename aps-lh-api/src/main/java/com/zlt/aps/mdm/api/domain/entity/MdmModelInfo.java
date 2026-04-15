package com.zlt.aps.mdm.api.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import com.zlt.common.annotation.ImportExcelValidated;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel(value = "模具信息对象", description = "模具信息对象")
@Data
@TableName(value = "T_MDM_MOULD_INFO")
public class MdmModelInfo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    /**
     * 分厂编号
     */
    @Excel(name = "ui.data.column.mdmModelInfo.factoryCode", dictType = "biz_factory_name")
    @ApiModelProperty(value = "分厂编号", name = "factoryCode")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

    /**
     * 型腔模号
     */
    @Excel(name = "ui.data.column.mdmModelInfo.mouldCode")
    @ApiModelProperty(value = "型腔模号", name = "mouldCode")
    @TableField(value = "MOULD_CODE")
    private String mouldCode;

    /**
     * 模具类型，字典：biz_mould_Type
     */
    @Excel(name = "ui.data.column.mdmModelInfo.mouldType", dictType = "biz_mould_Type")
    @ApiModelProperty(value = "模具类型", name = "mouldType")
    @TableField(value = "MOULD_TYPE")
    private String mouldType;

    /**
     * 模具状态，字典：biz_available_status
     */
    @Excel(name = "ui.data.column.mdmModelInfo.mouldStatus", dictType = "biz_available_status")
    @ApiModelProperty(value = "模具状态", name = "mouldStatus")
    @TableField(value = "MOULD_STATUS")
    private Integer mouldStatus;

    /**
     * 物流状态,字典：	logistics_status
     */
    @Excel(name = "ui.data.column.mdmModelInfo.logisticsStatus", dictType = "logistics_status")
    @ApiModelProperty(value = "物流状态,字典：	logistics_status", name = "logisticsStatus")
    @TableField(value = "LOGISTICS_STATUS")
    private String logisticsStatus;

    /**
     * 模具
     */
//    @Excel(name = "ui.data.column.mdmModelInfo.mouldNo")
    @ApiModelProperty(value = "模具", name = "mouldNo")
    @TableField(value = "MOULD_NO")
    private String mouldNo;

    /**
     * 规格
     */
    @ImportExcelValidated(maxLength = 256)
    @Excel(name = "ui.data.column.mdmModelInfo.specifications")
    @ApiModelProperty(value = "规格", name = "specifications")
    @TableField(value = "SPECIFICATIONS")
    private String specifications;

    /**
     * 主花纹
     */
    @ImportExcelValidated(maxLength = 64)
    @Excel(name = "ui.data.column.mdmMaterialInfo.mainPattern")
    @ApiModelProperty(value = "主花纹", name = "mainPattern")
    @TableField(value = "MAIN_PATTERN")
    private String mainPattern;

    /**
     * 品牌
     */
//    @Excel(name = "ui.data.column.mdmModelInfo.brand")
    @ApiModelProperty(value = "品牌", name = "brand")
    @TableField(value = "BRAND")
    private String brand;

    /**
     * 花纹
     */
    @ImportExcelValidated(maxLength = 64)
    @Excel(name = "ui.data.column.mdmModelInfo.pattern")
    @ApiModelProperty(value = "花纹", name = "pattern")
    @TableField(value = "PATTERN")
    private String pattern;

    /**
     * 主商标
     */
//    @Excel(name = "ui.data.column.mdmModelInfo.mainTrademark")
    @ApiModelProperty(value = "主商标", name = "mainTrademark")
    @TableField(value = "MAIN_TRADEMARK")
    private String mainTrademark;

    /**
     * 模具备注
     */
//    @Excel(name = "ui.data.column.mdmModelInfo.mouldRemark")
//    @ApiModelProperty(value = "模具备注", name = "mouldRemark")
//    @TableField(value = "MOULD_REMARK")
//    private String mouldRemark;

    /**
     * 模套
     */
//    @Excel(name = "ui.data.column.mdmModelInfo.mouldSleeve")
    @ApiModelProperty(value = "模套", name = "mouldSleeve")
    @TableField(value = "MOULD_SLEEVE")
    private String mouldSleeve;

    /**
     * 模具气套类型
     */
//    @Excel(name = "ui.data.column.mdmModelInfo.mouldAirType", dictType = "biz_mould_air_type")
    @ApiModelProperty(value = "模具气套类型", name = "mouldAirType")
    @TableField(value = "MOULD_AIR_TYPE")
    private String mouldAirType;

    /**
     * 模壳标准
     */
    @ImportExcelValidated(maxLength = 64)
    @Excel(name = "ui.data.column.mdmModelInfo.shellStandard")
    @ApiModelProperty(value = "模壳标准", name = "shellStandard")
    @TableField(value = "SHELL_STANDARD")
    private String shellStandard;

    @ImportExcelValidated(maxLength = 500)
    @Excel(name = "ui.data.column.mdmMaterialInfo.remark")
    @TableField(value = "REMARK")
    private String remark;

    /**
     * 寸口
     */
    @ApiModelProperty(value = "寸口", name = "proSize")
    @TableField(exist = false)
    private String proSize;

    @ApiModelProperty(value = "数据版本", name = "dataVersion")
    @TableField(exist = false)
    private String dataVersion;
}
