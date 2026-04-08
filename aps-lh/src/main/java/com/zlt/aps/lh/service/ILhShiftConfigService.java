package com.zlt.aps.lh.service;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.vo.LhShiftConfigVO;

import java.util.List;

/**
 * 硫化班次配置：加载并解析为排程用 {@link LhShiftConfigVO} 列表
 */
public interface ILhShiftConfigService {

    /**
     * 按工厂加载班次配置并写入上下文；表无有效数据时使用与现网一致的默认 8 班模板。
     *
     * @param context 排程上下文（需已设置 factoryCode、scheduleDate，且建议已加载 lhParamsMap）
     * @return 班次列表（1≤N≤8）
     * @throws IllegalArgumentException 配置非法（班次数、序号、偏移等）
     */
    List<LhShiftConfigVO> resolveAndAttachScheduleShifts(LhScheduleContext context);
}
