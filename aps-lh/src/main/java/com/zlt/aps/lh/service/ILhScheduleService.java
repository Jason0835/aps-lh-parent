package com.zlt.aps.lh.service;

import com.zlt.aps.lh.api.domain.dto.LhScheduleRequestDTO;
import com.zlt.aps.lh.api.domain.dto.LhScheduleResponseDTO;

/**
 * 硫化排程主服务接口
 * <p>排程入口，负责编排排程全流程</p>
 *
 * @author APS
 */
public interface ILhScheduleService {

    /**
     * 执行自动排程
     *
     * @param request 排程请求参数
     * @return 排程响应结果
     */
    LhScheduleResponseDTO executeSchedule(LhScheduleRequestDTO request);

    /**
     * 发布排程结果到MES
     *
     * @param batchNo 批次号
     * @return 发布响应结果
     */
    LhScheduleResponseDTO publishSchedule(String batchNo);
}
