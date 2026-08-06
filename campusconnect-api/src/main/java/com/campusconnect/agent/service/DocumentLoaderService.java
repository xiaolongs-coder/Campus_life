package com.campusconnect.agent.service;

import com.campusconnect.agent.model.Document;
import com.campusconnect.agent.ocr.OcrResult;
import com.campusconnect.agent.ocr.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 多格式文档加载器 — 对标 LangChain DocumentLoader
 *
 * 返回统一的 Document 对象（content + metadata），切分时 metadata 自动继承到每个 chunk。
 *
 * 提取链路：
 *   纯文本（MD/TXT） → 直接读取
 *   二进制（PDF/Word）→ Tika → 为空？→ OCR 兜底
 *   图片（PNG/JPG）  → OCR
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLoaderService {

    private final Tika tika = new Tika();
    private final OcrService ocrService;
    private final DocumentImageExtractor imageExtractor;

    private static final Set<String> BINARY_DOCS = Set.of("pdf", "docx", "doc", "pptx", "xlsx", "html", "htm");
    private static final Set<String> PLAINTEXT_DOCS = Set.of("md", "markdown", "txt", "csv", "json", "xml", "log");
    private static final Set<String> IMAGE_DOCS = Set.of("png", "jpg", "jpeg", "bmp", "tiff", "tif");

    private static final Set<String> ALL;
    static {
        Set<String> s = new HashSet<>();
        s.addAll(BINARY_DOCS); s.addAll(PLAINTEXT_DOCS); s.addAll(IMAGE_DOCS);
        ALL = Set.copyOf(s);
    }

    // ==================== 主入口 ====================

    public Document load(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String ext = getExtension(fileName);

        if (!ALL.contains(ext)) {
            throw new RuntimeException("不支持的文件格式: ." + ext + "，支持: " + String.join(", ", ALL));
        }

        Document doc;
        if (PLAINTEXT_DOCS.contains(ext)) {
            doc = loadPlaintext(file);
        } else if (IMAGE_DOCS.contains(ext)) {
            doc = loadImage(file);
        } else {
            doc = loadBinaryWithOcrFallback(file);  // PDF/Word 等
        }

        // 统一补充元数据
        doc.addMeta("source", fileName);
        doc.addMeta("file_type", ext);
        doc.addMeta("text_length", doc.getContent().length());

        log.info("[文档加载] {} → {} 字符, 方法: {}", fileName, doc.getContent().length(), doc.getMetaString("extract_method"));
        return doc;
    }

    // ==================== 策略1：纯文本 ====================

    private Document loadPlaintext(MultipartFile file) {
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            text = cleanText(text);
            if (text.isBlank()) throw new RuntimeException("文件内容为空");

            return Document.of(text)
                    .addMeta("extract_method", "Direct(" + getExtension(file.getOriginalFilename()).toUpperCase() + ")");
        } catch (RuntimeException e) { throw e;
        } catch (Exception e) { throw new RuntimeException("读取文本文件失败: " + e.getMessage()); }
    }

    // ==================== 策略2：图片 → OCR ====================

    private Document loadImage(MultipartFile file) {
        File temp = null;
        try {
            temp = toTempFile(file);
            OcrResult r = ocrService.recognize(temp);
            if (!r.isSuccess()) throw new RuntimeException("OCR 失败: " + r.getErrorMessage());

            return Document.of(r.getText())
                    .addMeta("extract_method", "OCR(" + r.getEngine() + ")");
        } finally { deleteTemp(temp); }
    }

    // ==================== 策略3：二进制文档 → Tika → OCR 兜底 ====================

    private Document loadBinaryWithOcrFallback(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String ext = getExtension(fileName);
        File temp = null;
        List<File> images = new ArrayList<>();

        try {
            temp = toTempFile(file);

            // 第1步：Tika 提取文字
            String tikaText = null;
            try {
                tikaText = cleanText(tika.parseToString(temp));
            } catch (Exception e) {
                log.warn("[文档加载] Tika 失败: {}", e.getMessage());
            }

            StringBuilder ocrText = new StringBuilder();
            String method = "Tika";
            int pageCount = 0;

            // 第2步：提取/渲染图片并 OCR
            if (ocrService.isAnyAvailable()) {
                if ("pdf".equals(ext)) {
                    // PDF：渲染页面为图片 → 逐页 OCR → 保留页码信息
                    images = imageExtractor.extractFromPdf(temp, 30);
                    pageCount = images.size();

                    for (int i = 0; i < images.size(); i++) {
                        OcrResult r = ocrService.recognize(images.get(i));
                        if (r.isSuccess() && !r.getText().isBlank()) {
                            ocrText.append("\n\n[第").append(i + 1).append("页]\n").append(r.getText());
                        }
                    }

                    if (!ocrText.isEmpty()) {
                        method = tikaText != null && !tikaText.isBlank()
                                ? "Tika+OCR(PaddleOCR)" : "OCR(PaddleOCR)";
                    }
                } else if ("docx".equals(ext) || "pptx".equals(ext)) {
                    images = imageExtractor.extractFromDocx(temp, 10);
                    for (File img : images) {
                        OcrResult r = ocrService.recognize(img);
                        if (r.isSuccess() && !r.getText().isBlank()) {
                            ocrText.append("\n\n[嵌入图片]\n").append(r.getText());
                        }
                    }
                    if (!ocrText.isEmpty()) method = "Tika+OCR(PaddleOCR)";
                }
            }

            // 第3步：合并
            String finalText = "";
            if (tikaText != null && !tikaText.isBlank()) finalText = tikaText;
            if (!ocrText.isEmpty()) {
                finalText += finalText.isEmpty() ? ocrText.toString() : "\n\n[OCR图片内容]\n" + ocrText;
            }

            if (finalText.isBlank()) {
                throw new RuntimeException("文档无法提取文字，可能为扫描图片型PDF且OCR引擎不可用");
            }

            return Document.of(finalText)
                    .addMeta("extract_method", method)
                    .addMeta("page_count", pageCount > 0 ? pageCount : null);

        } finally {
            deleteTemp(temp);
            imageExtractor.cleanup(images);
        }
    }

    // ==================== 工具方法 ====================

    private File toTempFile(MultipartFile file) {
        try {
            Path p = Files.createTempFile("docload_", "_" + file.getOriginalFilename());
            file.transferTo(p.toFile());
            return p.toFile();
        } catch (IOException e) { throw new RuntimeException("创建临时文件失败", e); }
    }

    private void deleteTemp(File f) {
        if (f != null && f.exists()) { try { Files.deleteIfExists(f.toPath()); } catch (IOException ignored) {} }
    }

    private String getExtension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }

    private String cleanText(String t) {
        return t.replace("\r\n", "\n").replace("\r", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("(?m)^[ \t]+$", "")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .trim();
    }

    public Map<String, String> getSupportedFormats() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String ext : BINARY_DOCS) m.put(ext, "二进制文档(Tika解析)");
        for (String ext : PLAINTEXT_DOCS) m.put(ext, "纯文本(直接读取)");
        for (String ext : IMAGE_DOCS) m.put(ext, "图片(OCR识别)");
        return m;
    }
}
