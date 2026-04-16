package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.dto.SkuScheduleDTO;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.mdm.api.domain.entity.MdmSkuLhCapacity;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 单班硫化量解析工具。
 *
 * @author APS
 */
public final class SingleMouldShiftQtyUtil {

    private SingleMouldShiftQtyUtil() {
    }

    /**
     * 解析排程结果的单班硫化量。
     * <p>优先取 SKU 硫化产能主数据班产，缺失时按班次时长、硫化时间和机台模台数回退计算。</p>
     *
     * @param context 排程上下文
     * @param sku SKU排程信息
     * @return 单班硫化量，无法计算时返回 null
     */
    public static Integer resolveSingleMouldShiftQty(LhScheduleContext context, SkuScheduleDTO sku) {
        return resolveSingleMouldShiftQty(context, sku, Objects.nonNull(sku) ? sku.getMouldQty() : 0);
    }

    /**
     * 解析排程结果的单班硫化量。
     * <p>优先取 SKU 硫化产能主数据班产，缺失时按班次时长、硫化时间和机台模台数回退计算。</p>
     *
     * @param context 排程上下文
     * @param sku SKU排程信息
     * @param mouldQty 机台模台数
     * @return 单班硫化量，无法计算时返回 null
     */
    public static Integer resolveSingleMouldShiftQty(LhScheduleContext context, SkuScheduleDTO sku, int mouldQty) {
        if (Objects.isNull(context) || Objects.isNull(sku)) {
            return null;
        }
        MdmSkuLhCapacity capacity = context.getSkuLhCapacityMap().get(sku.getMaterialCode());
        if (Objects.nonNull(capacity)
                && Objects.nonNull(capacity.getClassCapacity())
                && capacity.getClassCapacity() > 0) {
            return capacity.getClassCapacity();
        }
        if (sku.getShiftCapacity() > 0) {
            return sku.getShiftCapacity();
        }
        if (sku.getLhTimeSeconds() <= 0) {
            return null;
        }
        long shiftSeconds = TimeUnit.HOURS.toSeconds(LhScheduleTimeUtil.getShiftDurationHours(context));
        int singleMouldShiftQty = ShiftCapacityResolverUtil.resolveShiftCapacity(
                sku.getShiftCapacity(), sku.getLhTimeSeconds(), mouldQty, shiftSeconds, shiftSeconds);
        if (singleMouldShiftQty <= 0) {
            return null;
        }
        return singleMouldShiftQty;
    }
}
