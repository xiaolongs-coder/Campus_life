package com.campusconnect.agent.ocr;

import java.io.File;

/**
 * OCR 引擎抽象接口 — Strategy Pattern
 *
 * 当前支持：
 *   1. PaddleOCR（百度开源，中文识别最优）— 通过 Python 子进程调用
 *   2. 未来可扩展：Tesseract、Aliyun OCR API、Tencent OCR
 */
public interface OcrProvider {

    /** 引擎名称 */
    String getName();

    /** 是否可用（环境检查） */
    boolean isAvailable();

    /** 对图片文件执行 OCR */
    OcrResult recognize(File imageFile);

    /** 支持的图片格式 */
    default String[] supportedFormats() {
        return new String[]{"png", "jpg", "jpeg", "bmp", "tiff"};
    }
}
