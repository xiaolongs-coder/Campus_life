package com.campusconnect.agent.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * OCR 编排服务 — Chain of Responsibility + Strategy Pattern
 *
 * 设计原则：
 *   1. 优先用 PaddleOCR（中文识别最高精度）
 *   2. PaddleOCR 不可用时，返回明确错误（不静默降级）
 *   3. 所有 OCR 引擎通过 OcrProvider 接口接入，可插拔
 *
 * 扩展方式：实现 OcrProvider 接口并注入 Spring 容器即可自动注册
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final List<OcrProvider> providers;

    /**
     * 对单个图片文件执行 OCR
     *
     * @param imageFile 图片文件（png/jpg/bmp/tiff）
     * @return OCR 结果
     */
    public OcrResult recognize(File imageFile) {
        if (!imageFile.exists() || imageFile.length() == 0) {
            return OcrResult.fail("OcrService", "图片文件为空或不存在");
        }

        log.info("[OCR] 开始识别: {} ({} bytes)", imageFile.getName(), imageFile.length());

        List<String> tried = new ArrayList<>();

        for (OcrProvider provider : providers) {
            if (!provider.isAvailable()) {
                log.debug("[OCR] {} 不可用，跳过", provider.getName());
                continue;
            }

            tried.add(provider.getName());
            log.info("[OCR] 尝试引擎: {}", provider.getName());

            OcrResult result = provider.recognize(imageFile);

            if (result.isSuccess()) {
                log.info("[OCR] {} 识别成功, {} 字符, {}ms",
                        provider.getName(),
                        result.getText().length(),
                        result.getElapsedMs());
                return result;
            }

            log.warn("[OCR] {} 识别失败: {}", provider.getName(), result.getErrorMessage());
        }

        String msg = tried.isEmpty()
                ? "没有可用的 OCR 引擎，请安装 PaddleOCR（pip install paddleocr paddlepaddle）"
                : "所有 OCR 引擎均识别失败，已尝试: " + String.join(", ", tried);

        return OcrResult.fail("OcrService", msg);
    }

    /**
     * 检查是否有可用的 OCR 引擎
     */
    public boolean isAnyAvailable() {
        return providers.stream().anyMatch(OcrProvider::isAvailable);
    }

    /**
     * 列出所有可用的 OCR 引擎
     */
    public List<String> getAvailableEngines() {
        return providers.stream()
                .filter(OcrProvider::isAvailable)
                .map(OcrProvider::getName)
                .toList();
    }
}
