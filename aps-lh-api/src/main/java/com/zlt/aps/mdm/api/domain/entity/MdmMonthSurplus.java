package com.zlt.aps.mdm.api.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ruoyi.common.core.annotation.Excel;
import com.ruoyi.common.core.web.domain.BaseEntity;
import com.zlt.common.annotation.ImportExcelValidated;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

@ApiModel(value = "0140基础数据_月底计划余量对象", description = "0140基础数据_月底计划余量对象")
@Data
@TableName(value = "T_MDM_MONTH_SURPLUS")
public class MdmMonthSurplus extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 工厂编号，字典：biz_factory_name
     */
    @ImportExcelValidated(required = true)
    @Excel(name = "ui.data.column.mdmMonthSurplus.factoryCode", dictType = "biz_factory_name")
    @ApiModelProperty(value = "工厂编号，字典：biz_factory_name", name = "factoryCode")
    @TableField(value = "FACTORY_CODE")
    private String factoryCode;

    /**
     * 产品品类，字典：biz_product_type
     */
    @Excel(name = "ui.data.column.mdmMonthSurplus.productTypeCode", dictType = "biz_product_type")
    @ApiModelProperty(value = "产品品类，字典：biz_product_type", name = "productTypeCode")
    @TableField(value = "PRODUCT_TYPE_CODE")
    private String productTypeCode;

    /**
     * 年份
     */
    @ImportExcelValidated(required = true, min = 0, max = 9999)
    @Excel(name = "ui.data.column.mdmMonthSurplus.year", cellType = Excel.ColumnType.NUMERIC)
    @ApiModelProperty(value = "年份", name = "year")
    @TableField(value = "YEAR")
    private Integer year;

    /**
     * 月份
     */
    @ImportExcelValidated(required = true, min = 1, max = 12)
    @Excel(name = "ui.data.column.mdmMonthSurplus.month", cellType = Excel.ColumnType.NUMERIC)
    @ApiModelProperty(value = "月份", name = "month")
    @TableField(value = "MONTH")
    private Integer month;

    /**
     * 需求版本号
     */
    @ImportExcelValidated(required = true, maxLength = 32)
    @Excel(name = "ui.data.column.mdmMonthSurplus.requireVersion")
    @ApiModelProperty(value = "需求版本号", name = "requireVersion")
    @TableField(value = "REQUIRE_VERSION")
    private String requireVersion;

    /**
     * 品牌(物料信息.品牌)
     */
    @Excel(name = "ui.data.column.mdmMonthSurplus.brand", dictType = "biz_brand_type")
    @ApiModelProperty(value = "品牌(物料信息.品牌)", name = "brand")
    @TableField(value = "BRAND")
    private String brand;

    /**
     * 产品结构(物料信息.结构)
     */
    @ImportExcelValidated(maxLength = 64)
    @Excel(name = "ui.data.column.mdmMonthSurplus.structureName")
    @ApiModelProperty(value = "产品结构(物料信息.结构)", name = "structureName")
    @TableField(value = "STRUCTURE_NAME")
    private String structureName;

    /**
     * 物料编码
     */
    @ImportExcelValidated(required = true, maxLength = 30)
    @Excel(name = "ui.data.column.mdmMonthSurplus.materialCode")
    @ApiModelProperty(value = "物料编码", name = "materialCode")
    @TableField(value = "MATERIAL_CODE")
    private String materialCode;

    /**
     * 物料描述
     */
    @ImportExcelValidated(maxLength = 256)
    @Excel(name = "ui.data.column.mdmMonthSurplus.materialDesc")
    @ApiModelProperty(value = "物料描述", name = "materialDesc")
    @TableField(value = "MATERIAL_DESC")
    private String materialDesc;

    /**
     * 计划余量
     */
    @ImportExcelValidated(required = true, digits = true, min = 0, max = 99999999)
    @Excel(name = "ui.data.column.mdmMonthSurplus.planSurplusQty")
    @ApiModelProperty(value = "计划余量", name = "planSurplusQty")
    @TableField(value = "PLAN_SURPLUS_QTY")
    private BigDecimal planSurplusQty;

    /**
     * 以分厂+物料为维度，转换月底计划余量
     */
    public String getGroupKey() {
        String keyFormat = "%s|*|%s";
        return String.format(keyFormat, factoryCode, materialCode);
    }

    /**
     * 获取分厂销售需求版本计划分组Key
     *
     * @return
     */
    public String getMonthPlanVersionKey() {
        String keyFormat = "%d|*|%d|*|%s|*|%s|*|%s|*|%s";
        return String.format(keyFormat, this.year, this.month, this.factoryCode, this.productTypeCode, this.requireVersion,this.materialDesc);
    }

}
