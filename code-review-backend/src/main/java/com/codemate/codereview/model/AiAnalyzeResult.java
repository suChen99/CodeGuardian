package com.codemate.codereview.model;

import lombok.Data;

/**
 * AI 服务一次调用的结果
 * 不在用【返回一段JSON字符串，里面可能是error】方式
 * 调度器必须能明确知道：这次调用成功没有
 */
@Data
public class AiAnalyzeResult {
    // true = HTTP 2xx 且拿到了响应体
    private boolean success;

    // 成功时：Python 返回的原始JSON
    private String rawJson;

    // 失败时：给人看的原因（超时、连接拒绝、4xx等）
    private String errorMessage;

    // 成功工厂方法：调用方只关心[成功+正文]
    public static AiAnalyzeResult ok(String rawJson) {
        AiAnalyzeResult result =new AiAnalyzeResult();
        result.setSuccess(true);
        result.setRawJson(rawJson);
        return result;
    }
    // 失败工厂方法：不要把异常信息拼进假JSON里
    public static AiAnalyzeResult fail(String errorMessage) {
        AiAnalyzeResult result = new AiAnalyzeResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

}