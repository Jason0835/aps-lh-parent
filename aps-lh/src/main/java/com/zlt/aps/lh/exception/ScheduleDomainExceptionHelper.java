package com.zlt.aps.lh.exception;

import com.zlt.aps.lh.context.LhScheduleContext;
import com.zlt.aps.lh.api.enums.ScheduleStepEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 领域异常与排程中断的桥接：将 {@link ScheduleException} 的标准消息写入上下文中断原因。
 *
 * @author APS
 */
public final class ScheduleDomainExceptionHelper {

    /** 错误明细在 message 中的最大展示条数 */
    private static final int MAX_DETAIL_DISPLAY_COUNT = 10;
    /** 展示文案换行符（避免在前端纯文本组件中显示 HTML 标签） */
    private static final String MESSAGE_LINE_BREAK = "\n";

    private ScheduleDomainExceptionHelper() {
    }

    /**
     * 使用领域错误码中断排程（消息格式与 {@link ScheduleException#getMessage()} 一致）。
     *
     * @param context       排程上下文
     * @param step          当前步骤
     * @param errorCode     错误码
     * @param detailMessage 业务详情（不含步骤与错误码前缀）
     */
    public static void interrupt(LhScheduleContext context, ScheduleStepEnum step,
                                 ScheduleErrorCode errorCode, String detailMessage) {
        interrupt(context, step, errorCode, detailMessage, Collections.emptyList());
    }

    /**
     * 使用领域错误码中断排程（支持摘要 + 多条明细的展示文案）。
     *
     * @param context        排程上下文
     * @param step           当前步骤
     * @param errorCode      错误码
     * @param summaryMessage 摘要消息
     * @param detailList     明细列表
     */
    public static void interrupt(LhScheduleContext context, ScheduleStepEnum step,
                                 ScheduleErrorCode errorCode, String summaryMessage, List<String> detailList) {
        String displayMessage = buildDisplayMessage(summaryMessage, detailList);
        String msg = new ScheduleException(step, errorCode,
                context.getFactoryCode(), context.getBatchNo(), displayMessage).getMessage();
        context.interruptSchedule(msg);
    }

    /**
     * 构建展示文案：摘要 + 分条明细。
     *
     * @param summaryMessage 摘要消息
     * @param detailList     明细列表
     * @return 展示文案
     */
    private static String buildDisplayMessage(String summaryMessage, List<String> detailList) {
        String summary = StringUtils.isNotEmpty(summaryMessage) ? summaryMessage : "排程异常";
        List<String> cleanedDetails = cleanDetails(detailList);
        if (CollectionUtils.isEmpty(cleanedDetails)) {
            return summary;
        }

        int totalCount = cleanedDetails.size();
        int displayCount = Math.min(totalCount, MAX_DETAIL_DISPLAY_COUNT);
        StringBuilder messageBuilder = new StringBuilder(summary.length() + 128 + totalCount * 16);
        messageBuilder.append(summary)
                .append(MESSAGE_LINE_BREAK)
                .append(MESSAGE_LINE_BREAK)
                .append("错误明细（共")
                .append(totalCount)
                .append("条）：");

        for (int index = 0; index < displayCount; index++) {
            messageBuilder.append(MESSAGE_LINE_BREAK)
                    .append(index + 1)
                    .append(". ")
                    .append(cleanedDetails.get(index));
        }
        if (totalCount > displayCount) {
            messageBuilder.append(MESSAGE_LINE_BREAK)
                    .append("... 其余")
                    .append(totalCount - displayCount)
                    .append("条请查看明细列表");
        }
        return messageBuilder.toString();
    }

    /**
     * 清理明细列表：过滤空值并去除前后空白。
     *
     * @param detailList 原始明细
     * @return 清理后的明细列表
     */
    private static List<String> cleanDetails(List<String> detailList) {
        if (CollectionUtils.isEmpty(detailList)) {
            return Collections.emptyList();
        }
        List<String> cleanedList = new ArrayList<>(detailList.size());
        for (String detail : detailList) {
            if (StringUtils.isEmpty(detail)) {
                continue;
            }
            String trimmedDetail = detail.trim();
            if (StringUtils.isEmpty(trimmedDetail)) {
                continue;
            }
            cleanedList.add(trimmedDetail);
        }
        return cleanedList;
    }
}
