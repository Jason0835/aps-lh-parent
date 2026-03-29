package com.zlt.aps.lh.mapper;

import com.zlt.aps.lh.api.domain.entity.LhParams;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 硫化参数Mapper
 *
 * @author APS
 */
@Mapper
public interface LhParamsMapper {

    /**
     * 根据分厂编号查询硫化参数
     *
     * @param factoryCode 分厂编号
     * @return 参数列表
     */
    List<LhParams> selectByFactoryCode(@Param("factoryCode") String factoryCode);
}
