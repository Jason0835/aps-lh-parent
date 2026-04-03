package com.zlt.aps.lh.component;

import com.zlt.aps.lh.api.domain.context.LhScheduleContext;
import com.zlt.aps.lh.api.domain.dto.MachineScheduleDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机台索引管理器
 * <p>
 * 为机台匹配提供高效的多维索引，优化匹配性能：
 * <ul>
 *   <li>英寸范围索引：基于TreeMap快速查找寸口匹配的机台</li>
 *   <li>状态索引：按机台状态快速过滤</li>
 *   <li>模台数索引：按最大模台数分组</li>
 * </ul>
 * </p>
 *
 * @author APS
 */
@Slf4j
@Component
public class MachineIndexManager {

    /** 英寸范围索引：key=英寸值，value=机台列表 */
    private NavigableMap<BigDecimal, List<MachineScheduleDTO>> inchRangeIndex;

    /** 状态索引：key=状态码，value=机台列表 */
    private Map<String, List<MachineScheduleDTO>> statusIndex;

    /** 模台数索引：key=最大模台数，value=机台列表 */
    private Map<Integer, List<MachineScheduleDTO>> mouldNumIndex;

    /** 全量机台缓存 */
    private final Map<String, MachineScheduleDTO> machineCache = new ConcurrentHashMap<>();

    /** 索引版本号，用于判断是否需要重建 */
    private volatile long indexVersion = 0;

    /**
     * 初始化索引
     */
    @PostConstruct
    public void init() {
        this.inchRangeIndex = new TreeMap<>();
        this.statusIndex = new HashMap<>();
        this.mouldNumIndex = new HashMap<>();
        log.info("机台索引管理器初始化完成");
    }

    /**
     * 构建索引
     *
     * @param context 排程上下文
     */
    public synchronized void buildIndex(LhScheduleContext context) {
        long startTime = System.currentTimeMillis();
        long newVersion = System.currentTimeMillis();

        // 清空旧索引
        inchRangeIndex.clear();
        statusIndex.clear();
        mouldNumIndex.clear();
        machineCache.clear();

        // 构建新索引
        for (MachineScheduleDTO machine : context.getMachineScheduleMap().values()) {
            indexByInchRange(machine);
            indexByStatus(machine);
            indexByMouldNum(machine);
            machineCache.put(machine.getMachineCode(), machine);
        }

        this.indexVersion = newVersion;
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("机台索引构建完成, 机台数: {}, 耗时: {}ms", machineCache.size(), elapsed);
    }

    /**
     * 按英寸范围索引
     */
    private void indexByInchRange(MachineScheduleDTO machine) {
        // 使用机台的最小寸口作为索引键
        BigDecimal minInch = machine.getDimensionMinimum();
        if (minInch != null) {
            inchRangeIndex.computeIfAbsent(minInch, k -> new ArrayList<>()).add(machine);
        }
    }

    /**
     * 按状态索引
     */
    private void indexByStatus(MachineScheduleDTO machine) {
        String status = machine.getStatus() != null ? machine.getStatus() : "unknown";
        statusIndex.computeIfAbsent(status, k -> new ArrayList<>()).add(machine);
    }

    /**
     * 按模台数索引
     */
    private void indexByMouldNum(MachineScheduleDTO machine) {
        int mouldNum = machine.getMaxMoldNum();
        mouldNumIndex.computeIfAbsent(mouldNum, k -> new ArrayList<>()).add(machine);
    }

    /**
     * 按英寸范围查找候选机台
     * <p>使用TreeMap的subMap高效查找</p>
     *
     * @param minInch 最小英寸
     * @param maxInch 最大英寸
     * @return 候选机台列表
     */
    public List<MachineScheduleDTO> findByInchRange(BigDecimal minInch, BigDecimal maxInch) {
        List<MachineScheduleDTO> result = new ArrayList<>();

        if (minInch == null || maxInch == null) {
            return result;
        }

        // 获取所有在范围内的机台（寸口最小值小于等于maxInch的）
        Map.Entry<BigDecimal, List<MachineScheduleDTO>> entry = inchRangeIndex.firstEntry();
        while (entry != null && entry.getKey().compareTo(maxInch) <= 0) {
            result.addAll(entry.getValue());
            entry = inchRangeIndex.higherEntry(entry.getKey());
        }

        return result;
    }

    /**
     * 按状态查找机台
     *
     * @param status 状态码
     * @return 机台列表
     */
    public List<MachineScheduleDTO> findByStatus(String status) {
        return statusIndex.getOrDefault(status, new ArrayList<>());
    }

    /**
     * 按模台数查找机台（大于等于指定模台数）
     *
     * @param minMouldNum 最小模台数
     * @return 机台列表
     */
    public List<MachineScheduleDTO> findByMinMouldNum(int minMouldNum) {
        List<MachineScheduleDTO> result = new ArrayList<>();
        for (Map.Entry<Integer, List<MachineScheduleDTO>> entry : mouldNumIndex.entrySet()) {
            if (entry.getKey() >= minMouldNum) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    /**
     * 多条件组合查询
     *
     * @param minInch 最小英寸（SKU英寸）
     * @param maxInch 最大英寸（SKU英寸）
     * @param status 状态码
     * @param minMouldNum 最小模台数
     * @return 候选机台列表
     */
    public List<MachineScheduleDTO> findCandidates(BigDecimal minInch, BigDecimal maxInch,
                                                    String status, int minMouldNum) {
        // 先按最严格的条件过滤
        List<MachineScheduleDTO> candidates = findByStatus(status);

        // 按英寸范围过滤
        if (minInch != null && maxInch != null) {
            candidates.removeIf(m -> {
                BigDecimal machineMin = m.getDimensionMinimum();
                BigDecimal machineMax = m.getDimensionMaximum();
                if (machineMin == null || machineMax == null) {
                    return false;
                }
                // 检查英寸是否在机台寸口范围内
                return minInch.compareTo(machineMin) < 0 || maxInch.compareTo(machineMax) > 0;
            });
        }

        // 按模台数过滤
        if (minMouldNum > 0) {
            candidates.removeIf(m -> {
                int machineMouldNum = m.getMaxMoldNum();
                return machineMouldNum < minMouldNum;
            });
        }

        return candidates;
    }

    /**
     * 获取机台
     *
     * @param machineCode 机台编码
     * @return 机台DTO
     */
    public MachineScheduleDTO getMachine(String machineCode) {
        return machineCache.get(machineCode);
    }

    /**
     * 获取索引版本号
     */
    public long getIndexVersion() {
        return indexVersion;
    }

    /**
     * 判断索引是否需要重建
     *
     * @param contextVersion 上下文版本号
     */
    public boolean needRebuild(long contextVersion) {
        return contextVersion != this.indexVersion;
    }
}
