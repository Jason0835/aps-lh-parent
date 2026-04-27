package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.dto.MachineCleaningWindowDTO;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 模具清洗窗口与换模/换活字块窗口重叠判定工具。
 *
 * @author APS
 */
public final class MachineCleaningOverlapUtil {

    private MachineCleaningOverlapUtil() {
    }

    /**
     * 判断切换窗口是否与任一清洗窗口严格相交。
     *
     * @param cleaningWindowList 清洗窗口列表
     * @param switchStartTime 切换开始时间
     * @param switchEndTime 切换结束时间
     * @return true-存在重叠；false-不存在重叠
     */
    public static boolean hasOverlap(List<MachineCleaningWindowDTO> cleaningWindowList,
                                     Date switchStartTime,
                                     Date switchEndTime) {
        if (CollectionUtils.isEmpty(cleaningWindowList)
                || Objects.isNull(switchStartTime)
                || Objects.isNull(switchEndTime)
                || !switchStartTime.before(switchEndTime)) {
            return false;
        }
        for (MachineCleaningWindowDTO cleaningWindow : cleaningWindowList) {
            if (isOverlap(cleaningWindow, switchStartTime, switchEndTime)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断单个清洗窗口是否与切换窗口严格相交。
     *
     * @param cleaningWindow 清洗窗口
     * @param switchStartTime 切换开始时间
     * @param switchEndTime 切换结束时间
     * @return true-存在重叠；false-不存在重叠
     */
    public static boolean isOverlap(MachineCleaningWindowDTO cleaningWindow,
                                    Date switchStartTime,
                                    Date switchEndTime) {
        if (Objects.isNull(cleaningWindow)
                || Objects.isNull(cleaningWindow.getCleanStartTime())
                || Objects.isNull(switchStartTime)
                || Objects.isNull(switchEndTime)
                || !switchStartTime.before(switchEndTime)) {
            return false;
        }
        Date cleanEndTime = resolveEffectiveCleanEndTime(cleaningWindow);
        if (Objects.isNull(cleanEndTime)
                || !cleaningWindow.getCleanStartTime().before(cleanEndTime)) {
            return false;
        }
        // 严格相交才算重叠：仅端点相接不视为命中。
        return switchStartTime.before(cleanEndTime)
                && switchEndTime.after(cleaningWindow.getCleanStartTime());
    }

    /**
     * 获取清洗窗口的有效结束时间。
     *
     * @param cleaningWindow 清洗窗口
     * @return 优先取 readyTime，否则取 cleanEndTime
     */
    public static Date resolveEffectiveCleanEndTime(MachineCleaningWindowDTO cleaningWindow) {
        if (Objects.isNull(cleaningWindow)) {
            return null;
        }
        return Objects.nonNull(cleaningWindow.getReadyTime())
                ? cleaningWindow.getReadyTime() : cleaningWindow.getCleanEndTime();
    }

    /**
     * 过滤掉与切换窗口严格相交的清洗窗口。
     *
     * @param cleaningWindowList 清洗窗口列表
     * @param switchStartTime 切换开始时间
     * @param switchEndTime 切换结束时间
     * @return 过滤后的清洗窗口列表
     */
    public static List<MachineCleaningWindowDTO> excludeOverlapWindows(List<MachineCleaningWindowDTO> cleaningWindowList,
                                                                       Date switchStartTime,
                                                                       Date switchEndTime) {
        if (CollectionUtils.isEmpty(cleaningWindowList)) {
            return java.util.Collections.emptyList();
        }
        if (Objects.isNull(switchStartTime)
                || Objects.isNull(switchEndTime)
                || !switchStartTime.before(switchEndTime)) {
            return cleaningWindowList;
        }
        return cleaningWindowList.stream()
                .filter(cleaningWindow -> !isOverlap(cleaningWindow, switchStartTime, switchEndTime))
                .collect(Collectors.toList());
    }
}
