package com.zlt.aps.lh.mapper;

import com.zlt.aps.lh.api.domain.entity.*;
import com.zlt.aps.mdm.api.domain.entity.*;
import com.zlt.aps.mp.api.domain.entity.*;
import com.zlt.aps.mps.domain.LhShiftFinishQty;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 硫化排程基础数据综合查询Mapper
 * <p>集中查询排程所需的各类基础数据</p>
 *
 * @author APS
 */
@Mapper
public interface LhBaseDataMapper {

    /**
     * 查询月生产计划
     *
     * @param factoryCode       分厂编号
     * @param yearMonth         年月(如202603)
     * @param productionVersion 排产版本号
     * @return 月生产计划列表
     */
    List<FactoryMonthPlanProductionFinalResult> selectMonthPlan(@Param("factoryCode") String factoryCode,
                                                                @Param("yearMonth") Integer yearMonth,
                                                                @Param("productionVersion") String productionVersion);

    /**
     * 查询工作日历
     *
     * @param factoryCode 分厂编号
     * @param procCode    工序代码
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @return 工作日历列表
     */
    List<MdmWorkCalendar> selectWorkCalendar(@Param("factoryCode") String factoryCode,
                                             @Param("procCode") String procCode,
                                             @Param("startDate") Date startDate,
                                             @Param("endDate") Date endDate);

    /**
     * 查询SKU日硫化产能
     *
     * @param factoryCode 分厂编号
     * @return SKU日硫化产能列表
     */
    List<MdmSkuLhCapacity> selectSkuLhCapacity(@Param("factoryCode") String factoryCode);

    /**
     * 查询设备停机计划
     *
     * @param factoryCode 分厂编号
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @return 设备停机计划列表
     */
    List<MdmDevicePlanShut> selectDevicePlanShut(@Param("factoryCode") String factoryCode,
                                                 @Param("startDate") Date startDate,
                                                 @Param("endDate") Date endDate);

    /**
     * 查询SKU与模具关系
     *
     * @param factoryCode 分厂编号
     * @return SKU与模具关系列表
     */
    List<MdmSkuMouldRel> selectSkuMouldRel(@Param("factoryCode") String factoryCode);

    /**
     * 查询模具清洗计划
     *
     * @param factoryCode 分厂编号
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @return 模具清洗计划列表
     */
    List<LhCleaningPlan> selectCleaningPlan(@Param("factoryCode") String factoryCode,
                                            @Param("startDate") Date startDate,
                                            @Param("endDate") Date endDate);

    /**
     * 查询月底计划余量
     *
     * @param factoryCode 分厂编号
     * @param year        年份
     * @param month       月份
     * @return 月底计划余量列表
     */
    List<MdmMonthSurplus> selectMonthSurplus(@Param("factoryCode") String factoryCode,
                                             @Param("year") Integer year,
                                             @Param("month") Integer month);

    /**
     * 查询各班次完成量
     *
     * @param factoryCode  分厂编号
     * @param scheduleDate 排程日期
     * @return 各班次完成量列表
     */
    List<LhShiftFinishQty> selectShiftFinishQty(@Param("factoryCode") String factoryCode,
                                                @Param("scheduleDate") Date scheduleDate);

    /**
     * 查询物料信息
     *
     * @param factoryCode 分厂编号
     * @return 物料信息列表
     */
    List<MdmMaterialInfo> selectMaterialInfo(@Param("factoryCode") String factoryCode);

    /**
     * 查询MES硫化在机信息
     *
     * @param factoryCode 分厂编号
     * @param onlineDate  在机日期
     * @return MES硫化在机信息列表
     */
    List<MdmLhMachineOnlineInfo> selectMachineOnlineInfo(@Param("factoryCode") String factoryCode,
                                                         @Param("onlineDate") Date onlineDate);

    /**
     * 查询硫化定点机台
     *
     * @param factoryCode 分厂编号
     * @return 硫化定点机台列表
     */
    List<LhSpecifyMachine> selectSpecifyMachine(@Param("factoryCode") String factoryCode);

    /**
     * 查询硫化机胶囊已使用次数
     *
     * @param factoryCode 分厂编号
     * @return 胶囊使用次数列表
     */
    List<MdmLhRepairCapsule> selectCapsuleUsage(@Param("factoryCode") String factoryCode);

    /**
     * 查询设备保养计划
     *
     * @param factoryCode 分厂编号
     * @return 设备保养计划列表
     */
    List<MdmDevMaintenancePlan> selectMaintenancePlan(@Param("factoryCode") String factoryCode);
}
