package com.zlt.aps.lh.api.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 硫化排程响应结果
 *
 * @author APS
 */
@Data
public class LhScheduleResponseDTO {

    /** 是否成功 */
    private boolean success;
    /** 响应消息 */
    private String message;
    /** 批次号 */
    private String batchNo;
    /** 排程结果数量 */
    private int scheduleResultCount;
    /** 未排产SKU数量 */
    private int unscheduledCount;
    /** 模具交替计划数量 */
    private int mouldChangePlanCount;
    /** 排程日志列表 */
    private List<String> logMessages = new ArrayList<>();

    /**
     * 构建成功响应
     *
     * @param batchNo 批次号
     * @param message 响应消息
     * @return 成功响应DTO
     */
    public static LhScheduleResponseDTO success(String batchNo, String message) {
        LhScheduleResponseDTO dto = new LhScheduleResponseDTO();
        dto.setSuccess(true);
        dto.setBatchNo(batchNo);
        dto.setMessage(message);
        return dto;
    }

    /**
     * 构建失败响应
     *
     * @param message 失败原因
     * @return 失败响应DTO
     */
    public static LhScheduleResponseDTO fail(String message) {
        return fail(null, message);
    }

    /**
     * 构建失败响应
     *
     * @param message 失败原因
     * @return 失败响应DTO
     */
    public static LhScheduleResponseDTO fail(String batchNo, String message) {
        LhScheduleResponseDTO dto = new LhScheduleResponseDTO();
        dto.setSuccess(false);
        dto.setBatchNo(batchNo);
        dto.setMessage(message);
        return dto;
    }



}
