package com.campusconnect.agent.ocr;

import lombok.Builder;
import lombok.Data;

/**
 * OCR 识别结果
 */
@Data
@Builder
public class OcrResult {
    /** 识别的完整文本 */
    private String text;

    /** 使用的 OCR 引擎 */
    private String engine;

    /** 置信度 0.0 ~ 1.0（部分引擎不支持则为 null） */
    private Double confidence;

    /** 耗时（毫秒） */
    private long elapsedMs;

    /** 是否成功 */
    private boolean success;

    /** 失败原因 */
    private String errorMessage;

    public static OcrResult success(String text, String engine, long elapsedMs) {
        return OcrResult.builder()
                .text(text).engine(engine).elapsedMs(elapsedMs)
                .success(true).build();
    }

    public static OcrResult fail(String engine, String error) {
        return OcrResult.builder()
                .engine(engine).success(false).errorMessage(error).build();
    }
}
