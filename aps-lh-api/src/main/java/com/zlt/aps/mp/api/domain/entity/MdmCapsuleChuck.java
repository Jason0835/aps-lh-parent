package com.zlt.aps.mp.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import com.zlt.common.annotation.ImportExcelValidated;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;


@ApiModel(value = "胶囊卡盘台账对象", description = "胶囊卡盘台账对象 ")
@Data
@TableName(value = "T_MDM_CAPSULE_CHUCK")
public class MdmCapsuleChuck extends BaseEntity {

    private static final long serialVersionUID = 1L;

     /** 工厂编号 */
    @Excel(name = "ui.data.column.mdmCapsuleChuck.factoryCode", dictType = "biz_factory_name", sort = 1)
    @ApiModelProperty(value = "工厂编号", name = "factoryCode")
    @ImportExcelValidated(required = true)
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

    /** TBR卡盘英寸 多个以,分隔 */
    @ImportExcelValidated(maxLength = 1024)
    @Excel(name = "ui.data.column.mdmCapsuleChuck.proSize", width = 25, sort = 3)
    @ApiModelProperty(value = "TBR卡盘英寸 多个以,分隔", name = "proSize")
    @TableField(value = "PRO_SIZE")
    private String proSize;

    /** TBR卡盘 多个以,分隔 */
    @ImportExcelValidated(maxLength = 1024)
    @Excel(name = "ui.data.column.mdmCapsuleChuck.specifications", width = 25, sort = 2)
    @ApiModelProperty(value = "TBR卡盘 多个以,分隔", name = "specifications")
    @TableField(value = "SPECIFICATIONS")
    private String specifications;

    /** 国内转移 */
    @ImportExcelValidated(required = true,digits = true,min = 0,max = 9999999)
    @Excel(name = "ui.data.column.mdmCapsuleChuck.internalQty", sort = 4)
    @ApiModelProperty(value = "国内转移", name = "internalQty")
    @TableField(value = "INTERNAL_QTY")
    private Integer internalQty;

    /** 新卡盘 */
    @ImportExcelValidated(required = true,digits = true,min = 0,max = 9999999)
    @Excel(name = "ui.data.column.mdmCapsuleChuck.newChuckQty", sort = 5)
    @ApiModelProperty(value = "新卡盘", name = "newChuckQty")
    @TableField(value = "NEW_CHUCK_QTY")
    private Integer newChuckQty;

    @ImportExcelValidated(maxLength = 500)
    @Excel(name = "ui.common.column.remark")
    @ApiModelProperty(value = "备注", name = "remark")
    @TableField(value = "REMARK")
    private String remark;


}