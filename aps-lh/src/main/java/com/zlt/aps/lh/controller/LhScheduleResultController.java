package com.zlt.aps.lh.controller;

import com.zlt.aps.lh.api.domain.dto.LhScheduleRequestDTO;
import com.zlt.aps.lh.api.domain.dto.LhScheduleResponseDTO;
import com.zlt.aps.lh.service.ILhScheduleService;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 硫化排程控制器
 *
 * @author APS
 */
@Api(tags = "硫化排程接口")
@Slf4j
@RestController
@RequestMapping("/lhScheduleResult")
public class LhScheduleResultController {

    @Resource
    private ILhScheduleService lhScheduleService;

    /**
     * 执行自动排程
     *
     * @param request 排程请求参数
     * @return 排程响应结果
     */
    @PostMapping("/execute")
    @ApiOperation("执行自动排程")
    public LhScheduleResponseDTO executeSchedule(@RequestBody LhScheduleRequestDTO request) {
        log.info("收到排程请求, 工厂: {}, 日期: {}",
                request.getFactoryCode(), LhScheduleTimeUtil.formatDate(request.getScheduleDate()));
        return lhScheduleService.executeSchedule(request);
    }

    /**
     * 发布排程结果到MES
     *
     * @param batchNo 批次号
     * @return 发布响应结果
     */
    @PostMapping("/publish/{batchNo}")
    @ApiOperation("发布排程结果到MES")
    public LhScheduleResponseDTO publishSchedule(@PathVariable("batchNo") String batchNo) {
        log.info("收到发布请求, 批次号: {}", batchNo);
        return lhScheduleService.publishSchedule(batchNo);
    }
}
