package com.zlt.aps.lh.api.domain.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数据校验结果值对象
 * <p>封装校验是否通过、错误列表及摘要信息，统一校验结果的语义</p>
 *
 * @author APS
 */
public class ValidationResult {

    /** 校验是否通过 */
    private final boolean passed;

    /** 错误信息列表（不可变） */
    private final List<String> errors;

    /** 摘要信息 */
    private final String summaryMessage;

    private ValidationResult(boolean passed, List<String> errors, String summaryMessage) {
        this.passed = passed;
        this.errors = errors != null ? Collections.unmodifiableList(new ArrayList<>(errors)) : Collections.emptyList();
        this.summaryMessage = summaryMessage;
    }

    /**
     * 创建校验通过的结果
     */
    public static ValidationResult pass() {
        return new ValidationResult(true, Collections.emptyList(), null);
    }

    /**
     * 创建校验失败的结果
     *
     * @param errors 错误信息列表
     * @return 校验结果
     */
    public static ValidationResult fail(List<String> errors) {
        List<String> errorList = errors != null ? errors : Collections.emptyList();
        String summary = errorList.isEmpty()
                ? "校验未通过"
                : "校验未通过，共 " + errorList.size() + " 条错误";
        return new ValidationResult(false, errorList, summary);
    }

    /**
     * 创建校验失败的结果（单条错误）
     *
     * @param error 错误信息
     * @return 校验结果
     */
    public static ValidationResult fail(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return fail(errors);
    }

    public boolean isPassed() {
        return passed;
    }

    public boolean isFailed() {
        return !passed;
    }

    public List<String> getErrors() {
        return errors;
    }

    public String getSummaryMessage() {
        return summaryMessage;
    }

    /**
     * 获取格式化的错误明细（用于日志输出）
     */
    public String getFormattedErrors() {
        if (errors.isEmpty()) {
            return "无错误明细";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(errors.get(i));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "passed=" + passed +
                ", errorCount=" + errors.size() +
                ", summaryMessage='" + summaryMessage + '\'' +
                '}';
    }
}
