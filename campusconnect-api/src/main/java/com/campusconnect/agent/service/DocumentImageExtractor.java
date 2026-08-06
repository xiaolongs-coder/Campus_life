package com.campusconnect.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文档嵌入图片提取器
 *
 * PDF:  使用 PDFBox 将每页渲染为图片（不提取嵌入图—渲染整页更可靠）
 * Word: 使用 ZIP 解压提取 media/ 目录下的图片
 */
@Slf4j
@Component
public class DocumentImageExtractor {

    /**
     * 从 PDF 中提取页面图片（每页渲染为一张图）
     *
     * 为什么渲染整页而不是提取嵌入图？
     *   - 嵌入图可能被缩放/裁剪/旋转，直接提出来可能不完整
     *   - 渲染整页保留了排版信息，OCR 可以按原始布局识别
     *
     * @return 临时图片文件列表（调用方负责清理）
     */
    public List<File> extractFromPdf(File pdfFile, int maxPages) {
        List<File> images = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), maxPages);

            for (int i = 0; i < pages; i++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(i, 200); // 200 DPI 平衡清晰度和速度
                Path imgPath = Files.createTempFile("pdf_page_" + i + "_", ".png");
                ImageIO.write(pageImage, "png", imgPath.toFile());
                images.add(imgPath.toFile());
                log.debug("[图片提取] PDF 第{}页渲染完成: {}", i + 1, imgPath);
            }
        } catch (Exception e) {
            log.error("[图片提取] PDF 页面渲染失败", e);
        }
        return images;
    }

    /**
     * 从 Word (.docx) 中提取嵌入图片
     *
     * .docx 本质是 ZIP 包，图片在 word/media/ 目录下
     */
    public List<File> extractFromDocx(File docxFile, int maxImages) {
        List<File> images = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(docxFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null && images.size() < maxImages) {
                String name = entry.getName().toLowerCase();
                if (name.startsWith("word/media/") && isImageFile(name)) {
                    Path imgPath = Files.createTempFile("docx_img_", "_" + getFileName(name));
                    Files.copy(zis, imgPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    images.add(imgPath.toFile());
                    log.debug("[图片提取] DOCX 提取图片: {}", imgPath);
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            log.error("[图片提取] DOCX 图片提取失败", e);
        }
        return images;
    }

    private boolean isImageFile(String name) {
        return name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".bmp")
                || name.endsWith(".gif") || name.endsWith(".tiff");
    }

    private String getFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /** 清理临时图片文件 */
    public void cleanup(List<File> files) {
        for (File f : files) {
            try { Files.deleteIfExists(f.toPath()); } catch (Exception ignored) {}
        }
    }
}
