/**
 * Copyright (c) 2008, 智立通（厦门）科技有限公司 All rights reserved。
 */
package com.zlt.aps.lh.engine.strategy.impl;

import com.zlt.aps.lh.api.constant.LhScheduleConstant;
import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.api.domain.entity.LhScheduleResult;
import com.zlt.aps.lh.api.domain.entity.LhSpecifyMachine;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuMouldRel;
import com.zlt.aps.lh.engine.strategy.IMachineMatchStrategy;
import com.zlt.aps.lh.util.LhScheduleTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认机台匹配策略实现
 * <p>基于收尾时间、规格、英寸、胶囊共用性和胎胚共用性进行多层级匹配排序</p>
 *
 * @author APS
 */
@Slf4j
@Component
public class DefaultMachineMatchStrategy implements IMachineMatchStrategy {

    /** 机台启用状态 */
    private static final String STATUS_ENABLED = "0";

    /** 定点机台不可作业标记 */
    private static final String JOB_TYPE_FORBIDDEN = "1";

    @Override
    public List<MachineScheduleDTO> matchMachines(LhScheduleContext context, SkuScheduleDTO sku) {
        log.debug("匹配可用硫化机台, SKU: {}", sku.getMaterialCode());

        // 1. 从硫化定点机台获取SKU可用机台编号集合（排除不可作业的）
        Set<String> allowedMachineCodes = getAllowedMachineCodes(context, sku);

        // 2. 获取SKU的模具号列表
        List<String> skuMouldCodes = getSkuMouldCodes(context, sku.getMaterialCode());

        // 3. 获取已被其他计划占用的模具集合
        Set<String> occupiedMouldCodes = getOccupiedMouldCodes(context);

        // 4. 过滤候选机台：状态启用 + 寸口范围匹配 + 模具兼容 + 模具未被占用
        BigDecimal skuInch = parseInch(sku.getProSize());
        List<MachineScheduleDTO> candidates = new ArrayList<>();

        for (MachineScheduleDTO machine : context.getMachineScheduleMap().values()) {
            // 检查定点机台限制
            if (!allowedMachineCodes.isEmpty() && !allowedMachineCodes.contains(machine.getMachineCode())) {
                continue;
            }
            // 过滤禁用状态
            if (!STATUS_ENABLED.equals(machine.getStatus())) {
                continue;
            }
            // 寸口范围匹配
            if (!isInchInRange(skuInch, machine.getDimensionMinimum(), machine.getDimensionMaximum())) {
                continue;
            }
            // 检查模具与机台兼容性（模数不超过机台最大模台数）
            if (!isMouldCompatible(skuMouldCodes, machine, occupiedMouldCodes)) {
                continue;
            }
            candidates.add(machine);
        }

        // 5. 按多维度排序
        sortCandidates(context, candidates, sku);

        log.debug("SKU: {} 匹配到 {} 台候选机台", sku.getMaterialCode(), candidates.size());
        return candidates;
    }

    @Override
    public MachineScheduleDTO selectBestMachine(List<MachineScheduleDTO> candidates, SkuScheduleDTO sku) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.get(0);
    }

    /**
     * 从硫化定点机台Map中获取该SKU可用的机台编号集合
     * <p>若specifyMachineMap中无该规格记录，则不限制机台（返回空集合表示不限制）</p>
     */
    private Set<String> getAllowedMachineCodes(LhScheduleContext context, SkuScheduleDTO sku) {
        Set<String> allowed = new HashSet<>();
        List<LhSpecifyMachine> specifyList = context.getSpecifyMachineMap().get(sku.getSpecCode());
        if (specifyList == null || specifyList.isEmpty()) {
            return allowed;
        }
        for (LhSpecifyMachine specify : specifyList) {
            // 排除"不可作业"的机台
            if (!JOB_TYPE_FORBIDDEN.equals(specify.getJobType())) {
                allowed.add(specify.getMachineCode());
            }
        }
        return allowed;
    }

    /**
     * 获取SKU对应的模具号列表
     */
    private List<String> getSkuMouldCodes(LhScheduleContext context, String materialCode) {
        List<MdmSkuMouldRel> mouldRels = context.getSkuMouldRelMap().get(materialCode);
        if (mouldRels == null || mouldRels.isEmpty()) {
            return new ArrayList<>();
        }
        return mouldRels.stream()
                .map(MdmSkuMouldRel::getMouldCode)
                .collect(Collectors.toList());
    }

    /**
     * 获取当前所有已分配排程中正在使用的模具号集合（共用模保护）
     */
    private Set<String> getOccupiedMouldCodes(LhScheduleContext context) {
        Set<String> occupied = new HashSet<>();
        for (Map.Entry<String, List<LhScheduleResult>> entry : context.getMachineAssignmentMap().entrySet()) {
            for (LhScheduleResult result : entry.getValue()) {
                if (result.getMouldCode() != null) {
                    occupied.add(result.getMouldCode());
                }
            }
        }
        return occupied;
    }

    /**
     * 检查模具是否与机台兼容（模数不超限且模具未被占用）
     */
    private boolean isMouldCompatible(List<String> skuMouldCodes, MachineScheduleDTO machine, Set<String> occupiedMouldCodes) {
        if (skuMouldCodes.isEmpty()) {
            return true;
        }
        // 模数不能超过机台最大模台数
        if (skuMouldCodes.size() > machine.getMaxMoldNum()) {
            return false;
        }
        // 检查模具是否已被占用（共用模冲突）
        for (String mouldCode : skuMouldCodes) {
            if (occupiedMouldCodes.contains(mouldCode)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断英寸值是否在机台寸口范围内
     */
    private boolean isInchInRange(BigDecimal skuInch, BigDecimal minInch, BigDecimal maxInch) {
        if (skuInch == null || minInch == null || maxInch == null) {
            return true;
        }
        return skuInch.compareTo(minInch) >= 0 && skuInch.compareTo(maxInch) <= 0;
    }

    /**
     * 从规格寸口字符串中提取英寸数值
     * <p>如 "225/65R17" 提取17.0，"17.5" 直接解析为17.5</p>
     */
    private BigDecimal parseInch(String proSize) {
        if (proSize == null || proSize.trim().isEmpty()) {
            return null;
        }
        try {
            // 尝试直接解析为数字
            return new BigDecimal(proSize.trim());
        } catch (NumberFormatException e) {
            // 从轮胎规格字符串中提取英寸（如"225/65R17"中的"17"）
            String upper = proSize.toUpperCase();
            int rIdx = upper.lastIndexOf('R');
            if (rIdx >= 0 && rIdx < upper.length() - 1) {
                String inchStr = upper.substring(rIdx + 1).replaceAll("[^0-9.]", "");
                if (!inchStr.isEmpty()) {
                    try {
                        return new BigDecimal(inchStr);
                    } catch (NumberFormatException ignored) {
                        // fall through
                    }
                }
            }
        }
        return null;
    }

    /**
     * 对候选机台进行多维度排序
     * <p>
     * 排序规则（优先级由高到低）：
     * <ol>
     *   <li>相同规格优先（前规格与SKU规格相同）</li>
     *   <li>收尾时间升序（越早收尾越优先）</li>
     *   <li>收尾时间±20分钟内：相同英寸 > 相近英寸 > 胶囊共用性好的优先</li>
     * </ol>
     * </p>
     */
    private void sortCandidates(LhScheduleContext context, List<MachineScheduleDTO> candidates, SkuScheduleDTO sku) {
        BigDecimal skuInch = parseInch(sku.getProSize());

        // 计算基准收尾时间（取所有候选机台中最早的收尾时间）
        Date baseEndTime = candidates.stream()
                .map(MachineScheduleDTO::getEstimatedEndTime)
                .filter(t -> t != null)
                .min(Comparator.naturalOrder())
                .orElse(null);

        int toleranceMinutes = LhScheduleConstant.DEFAULT_ENDING_TIME_TOLERANCE_MINUTES;

        candidates.sort(
                // 同规格优先
                Comparator.comparingInt((MachineScheduleDTO m) -> sku.getSpecCode() != null && sku.getSpecCode().equals(m.getPreviousSpecCode()) ? 0 : 1)
                        // 收尾时间升序（null排最后）
                        .thenComparing((m1, m2) -> {
                            Date t1 = m1.getEstimatedEndTime();
                            Date t2 = m2.getEstimatedEndTime();
                            if (t1 == null && t2 == null) {
                                return 0;
                            }
                            if (t1 == null) {
                                return 1;
                            }
                            if (t2 == null) {
                                return -1;
                            }
                            // 收尾时间在容差范围内视为相同，否则按时间升序
                            if (LhScheduleTimeUtil.withinTolerance(t1, t2, toleranceMinutes)) {
                                return 0;
                            }
                            return t1.compareTo(t2);
                        })
                        // 相同英寸优先
                        .thenComparingInt(m -> isSameInch(skuInch, parseInch(m.getPreviousProSize())) ? 0 : 1)
                        // 英寸差距最小的优先（相近英寸）
                        .thenComparingDouble(m -> calcInchDistance(skuInch, parseInch(m.getPreviousProSize())))
                        // 胶囊使用次数少的优先（胶囊共用性好）
                        .thenComparingInt(MachineScheduleDTO::getCapsuleUsageCount)
        );
    }

    private boolean isSameInch(BigDecimal skuInch, BigDecimal machineInch) {
        if (skuInch == null || machineInch == null) {
            return false;
        }
        return skuInch.compareTo(machineInch) == 0;
    }

    private double calcInchDistance(BigDecimal skuInch, BigDecimal machineInch) {
        if (skuInch == null || machineInch == null) {
            return Double.MAX_VALUE;
        }
        return skuInch.subtract(machineInch).abs().doubleValue();
    }
}
