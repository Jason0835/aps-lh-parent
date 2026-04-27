package com.zlt.aps.lh.handler;

import com.zlt.aps.lh.api.domain.dto.MachineCleaningWindowDTO;
import com.zlt.aps.lh.api.domain.entity.LhMouldCleanPlan;
import com.zlt.aps.lh.api.enums.CleaningTypeEnum;
import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.mdm.api.domain.entity.MdmDevicePlanShut;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;

/**
 * DataInitHandler 清洗窗口构建测试。
 *
 * @author APS
 */
public class DataInitHandlerTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 用例说明：干冰清洗时间命中计划停机时，清洗开始时间应顺延到停机结束时刻。
     *
     * @throws Exception 反射调用异常
     */
    @Test
    public void shouldShiftDryIceCleaningWindowToStopEndWhenOverlap() throws Exception {
        // 准备停机窗口：2026-04-22 06:00:00 ~ 2026-04-22 23:59:59
        Date stopStart = toDate(2026, 4, 22, 6, 0, 0);
        Date stopEnd = toDate(2026, 4, 22, 23, 59, 59);
        LhScheduleContext context = buildContextWithStop("K1313", stopStart, stopEnd);

        // 准备清洗计划：干冰，发生在停机窗口内
        LhMouldCleanPlan plan = new LhMouldCleanPlan();
        plan.setLhCode("K1313");
        plan.setCleanType(CleaningTypeEnum.DRY_ICE.getCode());
        plan.setCleanTime(toDate(2026, 4, 22, 6, 30, 55));

        MachineCleaningWindowDTO window = invokeBuildCleaningWindow(context, plan);

        Assertions.assertNotNull(window);
        Assertions.assertEquals(stopEnd, window.getCleanStartTime());
        Assertions.assertEquals(toDate(2026, 4, 23, 2, 59, 59), window.getCleanEndTime());
    }

    /**
     * 用例说明：喷砂清洗命中计划停机时，停机扣减按 12 小时口径，机台就绪仍保持喷砂清洗原时长口径。
     *
     * @throws Exception 反射调用异常
     */
    @Test
    public void shouldShiftSandBlastCleaningWindowToStopEndAndSeparateDowntimeAndReadyTime() throws Exception {
        // 准备停机窗口：2026-04-22 06:00:00 ~ 2026-04-22 23:59:59
        Date stopStart = toDate(2026, 4, 22, 6, 0, 0);
        Date stopEnd = toDate(2026, 4, 22, 23, 59, 59);
        LhScheduleContext context = buildContextWithStop("K1111", stopStart, stopEnd);

        // 准备清洗计划：喷砂，发生在停机窗口内
        LhMouldCleanPlan plan = new LhMouldCleanPlan();
        plan.setLhCode("K1111");
        plan.setCleanType(CleaningTypeEnum.SAND_BLAST.getCode());
        plan.setCleanTime(toDate(2026, 4, 22, 9, 44, 51));

        MachineCleaningWindowDTO window = invokeBuildCleaningWindow(context, plan);

        Assertions.assertNotNull(window);
        Assertions.assertEquals(stopEnd, window.getCleanStartTime());
        Assertions.assertEquals(toDate(2026, 4, 23, 11, 59, 59), window.getCleanEndTime());
        Assertions.assertEquals(toDate(2026, 4, 23, 9, 59, 59), window.getReadyTime());
    }

    /**
     * 反射调用私有方法 buildCleaningWindow。
     *
     * @param context 排程上下文
     * @param plan 清洗计划
     * @return 清洗窗口
     * @throws Exception 反射异常
     */
    private MachineCleaningWindowDTO invokeBuildCleaningWindow(LhScheduleContext context, LhMouldCleanPlan plan)
            throws Exception {
        DataInitHandler handler = new DataInitHandler();
        Method method = DataInitHandler.class.getDeclaredMethod(
                "buildCleaningWindow", LhScheduleContext.class, LhMouldCleanPlan.class);
        method.setAccessible(true);
        return (MachineCleaningWindowDTO) method.invoke(handler, context, plan);
    }

    /**
     * 构建包含单条停机记录的上下文。
     *
     * @param machineCode 机台编号
     * @param stopStart 停机开始
     * @param stopEnd 停机结束
     * @return 排程上下文
     */
    private LhScheduleContext buildContextWithStop(String machineCode, Date stopStart, Date stopEnd) {
        LhScheduleContext context = new LhScheduleContext();
        MdmDevicePlanShut stop = new MdmDevicePlanShut();
        stop.setMachineCode(machineCode);
        stop.setBeginDate(stopStart);
        stop.setEndDate(stopEnd);
        context.setDevicePlanShutList(Arrays.asList(stop));
        return context;
    }

    /**
     * 生成指定时刻的 Date。
     *
     * @param year 年
     * @param month 月
     * @param day 日
     * @param hour 时
     * @param minute 分
     * @param second 秒
     * @return Date 实例
     */
    private Date toDate(int year, int month, int day, int hour, int minute, int second) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute, second)
                .atZone(ZONE_ID)
                .toInstant());
    }
}
