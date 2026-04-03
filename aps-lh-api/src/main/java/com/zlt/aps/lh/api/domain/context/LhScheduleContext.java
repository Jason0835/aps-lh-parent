package com.zlt.aps.lh.api.domain.context;

import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhCleaningPlan;
import com.zlt.aps.lh.api.domain.entity.LhMachineInfo;
import com.zlt.aps.lh.api.domain.entity.LhMouldChangePlan;
import com.zlt.aps.lh.api.domain.entity.LhScheduleProcessLog;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.domain.entity.LhSpecifyMachine;
import com.zlt.aps.lh.api.domain.entity.LhUnscheduledResult;
import com.zlt.aps.mdm.api.domain.entity.MdmDevMaintenancePlan;
import com.zlt.aps.mdm.api.domain.entity.MdmDevicePlanShut;
import com.zlt.aps.mdm.api.domain.entity.MdmLhMachineOnlineInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmLhRepairCapsule;
import com.zlt.aps.mdm.api.domain.entity.MdmMaterialInfo;
import com.zlt.aps.mdm.api.domain.entity.MdmMonthSurplus;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuLhCapacity;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import com.zlt.aps.mdm.api.domain.entity.MdmWorkCalendar;
import com.zlt.aps.mp.api.domain.entity.FactoryMonthPlanProductionFinalResult;
import com.zlt.aps.lh.api.domain.entity.LhShiftFinishQty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * 硫化排程上下文
 * <p>贯穿整个排程流程的数据总线，持有排程过程中所有需要的基础数据和中间结果</p>
 *
 * @author APS
 */
@Data
public class LhScheduleContext {

    // ========== 排程基本参数 ==========

    /** 分厂编号 */
    private String factoryCode;
    /** 排程目标日（与请求体日期一致，如业务上的 T+2） */
    private Date scheduleTargetDate;
    /**
     * 排程窗口起点 T 日：由 {@link #scheduleTargetDate} 减去 ({@link com.zlt.aps.lh.api.constant.LhScheduleConstant#SCHEDULE_DAYS} - 1) 得到，
     * 供班次计算、基础数据加载等引擎时间轴使用
     */
    private Date scheduleDate;
    /** 批次号 */
    private String batchNo;
    /** 月计划需求版本 */
    private String monthPlanVersion;
    /** 月计划排产版本 */
    private String productionVersion;

    // ========== 硫化参数(从LhParams加载) ==========

    /** 硫化参数Map, key=paramCode, value=paramValue */
    private Map<String, String> lhParamsMap = new HashMap<>();

    // ========== 基础数据(S4.2加载) ==========

    /** 月生产计划列表 */
    private List<FactoryMonthPlanProductionFinalResult> monthPlanList = new ArrayList<>();
    /** 工作日历列表 */
    private List<MdmWorkCalendar> workCalendarList = new ArrayList<>();
    /** SKU日硫化产能Map, key=materialCode */
    private Map<String, MdmSkuLhCapacity> skuLhCapacityMap = new HashMap<>();
    /** 设备停机计划列表 */
    private List<MdmDevicePlanShut> devicePlanShutList = new ArrayList<>();
    /** SKU与模具关系Map, key=materialCode */
    private Map<String, List<MdmSkuMouldRel>> skuMouldRelMap = new HashMap<>();
    /** 硫化机台信息Map, key=machineCode */
    private Map<String, LhMachineInfo> machineInfoMap = new LinkedHashMap<>();
    /** 模具清洗计划列表 */
    private List<LhCleaningPlan> cleaningPlanList = new ArrayList<>();
    /** 月底计划余量Map, key=groupKey(factoryCode|*|materialCode) */
    private Map<String, MdmMonthSurplus> monthSurplusMap = new HashMap<>();
    /** 各班次完成量Map, key=machineCode+materialCode */
    private Map<String, LhShiftFinishQty> shiftFinishQtyMap = new HashMap<>();
    /** 物料信息Map, key=materialCode */
    private Map<String, MdmMaterialInfo> materialInfoMap = new HashMap<>();
    /** MES硫化在机信息Map, key=machineCode */
    private Map<String, MdmLhMachineOnlineInfo> machineOnlineInfoMap = new HashMap<>();
    /** 硫化定点机台Map, key=specCode */
    private Map<String, List<LhSpecifyMachine>> specifyMachineMap = new HashMap<>();
    /** 硫化机胶囊已使用次数Map, key=machineCode */
    private Map<String, MdmLhRepairCapsule> capsuleUsageMap = new HashMap<>();
    /** 设备保养计划Map, key=devCode */
    private Map<String, MdmDevMaintenancePlan> maintenancePlanMap = new HashMap<>();

    // ========== 中间计算结果(S4.3) ==========

    /** 前日排程结果列表(修正后) */
    private List<LhScheduleResult> previousScheduleResultList = new ArrayList<>();
    /** SKU按结构归集, key=structureName, value=SKU排程DTO列表 */
    private Map<String, List<SkuScheduleDTO>> structureSkuMap = new LinkedHashMap<>();
    /** 续作SKU列表 */
    private List<SkuScheduleDTO> continuousSkuList = new ArrayList<>();
    /** 新增SKU列表 */
    private List<SkuScheduleDTO> newSpecSkuList = new ArrayList<>();

    // ========== 机台分配状态 ==========

    /** 机台排程DTO Map, key=machineCode */
    private Map<String, MachineScheduleDTO> machineScheduleMap = new LinkedHashMap<>();
    /** 机台剩余产能Map, key=machineCode, value=各班次剩余产能 */
    private Map<String, int[]> machineShiftCapacityMap = new LinkedHashMap<>();
    /** 机台已分配SKU Map, key=machineCode, value=已分配的排程结果 */
    private Map<String, List<LhScheduleResult>> machineAssignmentMap = new LinkedHashMap<>();
    /** 每日换模计数, key=dateString, value=[早班换模数, 中班换模数] */
    private Map<String, int[]> dailyMouldChangeCountMap = new LinkedHashMap<>();
    /** 每日首检计数, key=dateString, value=[早班首检数, 中班首检数] */
    private Map<String, int[]> dailyFirstInspectionCountMap = new LinkedHashMap<>();

    // ========== 排程输出结果 ==========

    /** 硫化排程结果列表 */
    private List<LhScheduleResult> scheduleResultList = new ArrayList<>();
    /** 硫化未排结果列表 */
    private List<LhUnscheduledResult> unscheduledResultList = new ArrayList<>();
    /** 模具交替计划列表 */
    private List<LhMouldChangePlan> mouldChangePlanList = new ArrayList<>();
    /** 排程日志列表 */
    private List<LhScheduleProcessLog> scheduleLogList = new ArrayList<>();

    // ========== 流程控制 ==========

    /** 是否中断排程 */
    private boolean interrupted = false;
    /** 中断原因 */
    private String interruptReason;
    /** 当前执行步骤 */
    private String currentStep;
    /** 校验错误信息集合 */
    private List<String> validationErrorList = new ArrayList<>();

    /**
     * 追加一条校验错误信息（空串或 null 将被忽略）
     *
     * @param message 错误描述
     */
    public void addValidationError(String message) {
        if (StringUtils.isEmpty(message)) {
            return;
        }
        this.validationErrorList.add(message);
    }

    /**
     * 获取硫化参数值
     *
     * @param paramCode    参数代码
     * @param defaultValue 默认值
     * @return 参数值
     */
    public String getParamValue(String paramCode, String defaultValue) {
        return lhParamsMap.getOrDefault(paramCode, defaultValue);
    }

    /**
     * 获取硫化参数值(整数)
     *
     * @param paramCode    参数代码
     * @param defaultValue 默认值
     * @return 参数值(整数)
     */
    public int getParamIntValue(String paramCode, int defaultValue) {
        String value = lhParamsMap.get(paramCode);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 中断排程流程
     *
     * @param reason 中断原因
     */
    public void interruptSchedule(String reason) {
        this.interrupted = true;
        this.interruptReason = reason;
    }

}
