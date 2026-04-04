package com.zlt.aps.lh.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 排程错误码枚举
 *
 * @author APS
 */
@Getter
@AllArgsConstructor
public enum ScheduleErrorCode {

    // ==================== 通用错误 ====================
    UNKNOWN("S9999", "未知错误"),
    SYSTEM_ERROR("S0001", "系统异常"),
    /** 排程类型对应的排产策略未注册 */
    PRODUCTION_STRATEGY_NOT_REGISTERED("S0002", "排产策略未注册"),
    /** 数据校验链配置不一致（如校验组内策略冲突） */
    VALIDATION_CHAIN_CONFIG_ERROR("S0003", "数据校验链配置错误"),

    // ==================== S4.1 前置校验错误 ====================
    MES_RELEASED("S4101", "排程已下发MES，禁止重新排程"),
    SCHEDULE_IN_PROGRESS("S4102", "排程任务正在执行中"),
    BATCH_NO_GENERATE_FAILED("S4103", "批次号生成失败"),

    // ==================== S4.2 数据初始化错误 ====================
    DATA_INCOMPLETE("S4201", "基础数据不完整"),
    MACHINE_INFO_MISSING("S4202", "硫化机台信息缺失"),
    MONTH_PLAN_MISSING("S4203", "月生产计划数据缺失"),
    WORK_CALENDAR_MISSING("S4204", "工作日历数据缺失"),
    SKU_CAPACITY_MISSING("S4205", "SKU产能数据缺失"),
    MOULD_REL_MISSING("S4206", "SKU模具关系数据缺失"),

    // ==================== S4.3 SKU归集错误 ====================
    NO_SKU_TO_SCHEDULE("S4301", "无可排产SKU"),
    SURPLUS_CALCULATION_ERROR("S4302", "硫化余量计算异常"),

    // ==================== S4.4 续作排产错误 ====================
    CONTINUOUS_MACHINE_NOT_FOUND("S4401", "续作机台未找到"),
    SHIFT_ALLOCATION_FAILED("S4402", "班次分配失败"),
    EMBRYO_STOCK_INSUFFICIENT("S4403", "胎胚库存不足"),

    // ==================== S4.5 新增排产错误 ====================
    NO_MACHINE_AVAILABLE("S4501", "无可用硫化机台"),
    MACHINE_SELECTION_FAILED("S4502", "机台选择失败"),
    MOULD_CHANGE_CAPACITY_EXCEEDED("S4503", "换模能力不足"),
    MOULD_CHANGE_ALLOCATION_FAILED("S4504", "换模班次分配失败"),
    INSPECTION_ALLOCATION_FAILED("S4505", "首检班次分配失败"),
    NO_PRODUCTION_CAPACITY("S4506", "排程窗口内无可用产能"),

    // ==================== S4.6 结果校验错误 ====================
    RESULT_VALIDATION_FAILED("S4601", "排程结果校验失败"),
    RESULT_SAVE_FAILED("S4602", "排程结果保存失败"),
    MOULD_CHANGE_PLAN_FAILED("S4603", "模具交替计划生成失败");

    /** 错误码 */
    private final String code;

    /** 错误描述 */
    private final String description;

    /**
     * 根据错误码获取枚举
     *
     * @param code 错误码
     * @return 错误码枚举，未找到返回null
     */
    public static ScheduleErrorCode getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ScheduleErrorCode e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
