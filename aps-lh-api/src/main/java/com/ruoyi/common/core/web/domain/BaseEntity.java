package com.ruoyi.common.core.web.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
public class BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    @TableField(exist = false)
    private String searchValue;

    @ApiModelProperty("创建者")
    @TableField(value = "CREATE_BY", fill = FieldFill.INSERT)
    private String createBy;

    @ApiModelProperty("创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "CREATE_TIME", fill = FieldFill.INSERT)
    private Date createTime;

    @ApiModelProperty("更新者")
    @TableField(value = "UPDATE_BY", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @ApiModelProperty("更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "UPDATE_TIME", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @ApiModelProperty("备注")
    @TableField("REMARK")
    private String remark;

    @ApiModelProperty("删除标识：0-正常，1-删除")
    @TableField("IS_DELETE")
    private Integer isDelete = 0;

    @TableField(exist = false)
    private Map<String, Object> params;

    public Map<String, Object> getParams() {
        if (this.params == null) {
            this.params = new HashMap<>();
        }
        return this.params;
    }
}
