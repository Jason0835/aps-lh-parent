package com.zlt.aps.lh.mapper;

import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 硫化机台信息Mapper
 *
 * @author APS
 */
@Mapper
public interface LhMachineInfoMapper {

    /**
     * 查询工厂下启用的机台信息
     *
     * @param factoryCode 分厂编号
     * @return 启用的机台信息列表
     */
    List<LhMachineInfo> selectEnabledByFactory(@Param("factoryCode") String factoryCode);
}
