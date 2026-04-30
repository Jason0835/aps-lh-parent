package com.zlt.aps.cx.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * APS生胎库存
 *
 * @author zlt
 * @since 2026/04/09
 */
@ApiModel(value = "APS生胎库存", description = "APS生胎库存")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("T_CX_MES_STOCK")
public class CxMesStock extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "库存日期")
    @TableField(value = "STOCK_DATE")
    private Date stockDate;

    @ApiModelProperty(value = "胎胚物料编码")
    @TableField(value = "EMBRYO_CODE")
    private String embryoCode;

    @ApiModelProperty(value = "胎胚库存")
    @TableField(value = "STOCK_NUM")
    private BigDecimal stockNum;

    @ApiModelProperty(value = "立体库")
    @TableField(value = "LITI_STOCK")
    private BigDecimal litiStock;

    @ApiModelProperty(value = "胎胚车")
    @TableField(value = "EMBRYO_CAR")
    private BigDecimal embryoCar;

    @ApiModelProperty(value = "硫化库存")
    @TableField(value = "LH_STOCK")
    private BigDecimal lhStock;

    @ApiModelProperty(value = "可用库存")
    @TableField(value = "AVAILABLE_STOCK")
    private BigDecimal availableStock;

    @ApiModelProperty(value = "胎胚版本")
    @TableField(value = "BOM_DATA_VERSION")
    private String bomDataVersion;

    @ApiModelProperty(value = "示方类型")
    @TableField(value = "EXAMPLE_TYPE")
    private String exampleType;

    @ApiModelProperty(value = "不可用库存")
    @TableField(value = "UNAVAILABLE_STOCK")
    private BigDecimal unavailableStock;

    @ApiModelProperty(value = "版本号")
    @TableField(value = "DATA_VERSION")
    private String dataVersion;

    @ApiModelProperty(value = "分公司编码")
    @TableField(value = "COMPANY_CODE")
    private String companyCode;

    @ApiModelProperty(value = "厂别")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

}
