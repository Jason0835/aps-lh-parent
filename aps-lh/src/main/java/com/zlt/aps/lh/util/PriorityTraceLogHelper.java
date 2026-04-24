package com.zlt.aps.lh.util;

import com.zlt.aps.lh.api.domain.entity.LhScheduleProcessLog;
import com.zlt.aps.lh.context.LhScheduleConfig;
import com.zlt.aps.lh.context.LhScheduleContext;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Date;

/**
 * 优先级跟踪日志辅助工具。
 *
 * @author APS
 */
public final class PriorityTraceLogHelper {

    /** 候选机台日志默认输出前N名 */
    public static final int MACHINE_TRACE_TOP_N = 5;

    private PriorityTraceLogHelper() {
    }

    /**
     * 判断当前排程是否开启优先级跟踪日志。
     *
     * @param context 排程上下文
     * @return true-开启，false-关闭
     */
    public static boolean isEnabled(LhScheduleContext context) {
        if (context == null) {
            return false;
        }
        LhScheduleConfig scheduleConfig = context.getScheduleConfig();
        return scheduleConfig != null
                && scheduleConfig.isPriorityTraceLogEnabled()
                && !context.isPriorityTraceMuted();
    }

    /**
     * 同时写应用日志和过程日志明细时使用的安全文本。
     *
     * @param value 原始值
     * @return 展示文本
     */
    public static String safeText(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    /**
     * 布尔值转展示文本。
     *
     * @param flag 布尔值
     * @return 是/否
     */
    public static String yesNo(boolean flag) {
        return flag ? "是" : "否";
    }

    /**
     * 格式化日期时间，空值输出“-”。
     *
     * @param date 日期
     * @return 文本
     */
    public static String formatDateTime(Date date) {
        return date == null ? "-" : LhScheduleTimeUtil.formatDateTime(date);
    }

    /**
     * 拼接文本行。
     *
     * @param builder 文本构建器
     * @param line 行内容
     */
    public static void appendLine(StringBuilder builder, String line) {
        if (builder == null || StringUtils.isEmpty(line)) {
            return;
        }
        builder.append(line).append('\n');
    }

    /**
     * 输出集合大小文本。
     *
     * @param collection 集合
     * @return 数量文本
     */
    public static int sizeOf(Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    /**
     * 向排程过程日志列表追加一条日志。
     *
     * @param context 排程上下文
     * @param title 标题
     * @param detail 明细
     */
    public static void appendProcessLog(LhScheduleContext context, String title, String detail) {
        if (context == null || StringUtils.isEmpty(title) || StringUtils.isEmpty(detail)) {
            return;
        }
        LhScheduleProcessLog processLog = new LhScheduleProcessLog();
        processLog.setBatchNo(context.getBatchNo());
        processLog.setTitle(title);
        processLog.setBusiCode(context.getFactoryCode());
        processLog.setLogDetail(detail);
        processLog.setIsDelete(0);
        context.getScheduleLogList().add(processLog);
    }
}
