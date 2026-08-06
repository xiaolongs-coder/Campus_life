package com.campusconnect.agent.controller;

import com.campusconnect.agent.dto.CampusAgentRequest;
import com.campusconnect.agent.dto.CampusCrawlRequest;
import com.campusconnect.agent.dto.CampusKnowledgeImportRequest;
import com.campusconnect.agent.service.*;
import com.campusconnect.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.campusconnect.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController
@RequestMapping("/agent/campus")
@RequiredArgsConstructor
public class CampusAgentController {

    private final EmbeddingClient embeddingClient;

    private final QdrantVectorService qdrantVectorService;

    private final CampusKnowledgeImportService campusKnowledgeImportService;
    private final CampusRagService campusRagService;

    private final CampusAgentService campusAgentService;

    private final CampusCrawlerService campusCrawlerService;

    private final DocumentLoaderService documentLoaderService;

    @PostMapping("/embedding-test")
    public Result<?> embeddingTest(@RequestBody Map<String, String> body) {
        String text = body.get("text");

        List<Double> vector = embeddingClient.embed(text);

        Map<String, Object> result = new HashMap<>();
        result.put("text", text);
        result.put("dimension", vector.size());
        result.put("preview", vector.subList(0, Math.min(5, vector.size())));

        return Result.success(result);
    }

    @PostMapping("/qdrant-test")
    public Result<?> qdrantTest(@RequestBody Map<String, String> body) {
        String text = body.get("text");

        List<Double> vector = embeddingClient.embed(text);

        // 你的 embedding 维度现在是 1024
        qdrantVectorService.ensureCollection(vector.size());

        Long pointId = System.currentTimeMillis();

        Map<String, Object> payload = new HashMap<>();
        payload.put("chunkId", pointId);
        payload.put("knowledgeId", 1L);
        payload.put("title", "缓考申请流程测试");
        payload.put("sourceName", "渤海大学教务处");
        payload.put("sourceType", "教务处");
        payload.put("trustLevel", "高");
        payload.put("content", text);

        qdrantVectorService.upsertPoint(pointId, vector, payload);

        List<Map<String, Object>> results = qdrantVectorService.search(vector, 3);

        Map<String, Object> result = new HashMap<>();
        result.put("text", text);
        result.put("dimension", vector.size());
        result.put("pointId", pointId);
        result.put("searchResults", results);

        return Result.success(result);
    }
    @PostMapping("/knowledge/import")
    public Result<?> importKnowledge(@RequestBody CampusKnowledgeImportRequest request) {
        return Result.success(campusKnowledgeImportService.importKnowledge(request));
    }
    @PostMapping("/rag-test")
    public Result<?> ragTest(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        return Result.success(campusRagService.retrieve(question, 5));
    }
    @PostMapping("/chat")
    public Result<?> chat(
            @RequestBody CampusAgentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal == null ? 0L : principal.getId();
        return Result.success(campusAgentService.chat(request, userId));
    }
    @DeleteMapping("/knowledge/reset-vector")
    public Result<?> resetVectorCollection() {
        qdrantVectorService.deleteCollection();
        return Result.success("Qdrant 校园知识库向量集合已清空");
    }
    @PostMapping("/crawler/import")
    public Result<?> crawlAndImport(@RequestBody CampusCrawlRequest request) {
        return Result.success(campusCrawlerService.crawlAndImport(request));
    }

    /**
     * 文档文件上传导入（PDF/Word/Markdown/TXT 等）
     *
     * 前端：FormData 上传文件 + 元数据字段
     * 后端：Tika 自动解析 → 智能分块 → Embedding → Qdrant
     */
    @PostMapping("/knowledge/import-file")
    public Result<?> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "sourceName", defaultValue = "文件上传") String sourceName,
            @RequestParam(value = "sourceType", defaultValue = "文件上传") String sourceType,
            @RequestParam(value = "trustLevel", defaultValue = "高") String trustLevel
    ) {
        if (file.isEmpty()) {
            return Result.error(400, "请选择文件");
        }

        // 文件大小限制：20MB
        long maxSize = 20 * 1024 * 1024L;
        if (file.getSize() > maxSize) {
            return Result.error(400, "文件过大，最大支持 20MB");
        }

        // 文件名校验：防止路径穿越攻击
        String fileName = file.getOriginalFilename();
        if (fileName != null && (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\"))) {
            return Result.error(400, "文件名包含非法字符");
        }

        // 1. 文档加载：Tika + OCR 提取文本 + metadata（溯源信息）
        com.campusconnect.agent.model.Document doc;
        try {
            doc = documentLoaderService.load(file);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }

        // 2. 补充来源元数据
        doc.addMeta("source_name", sourceName);
        doc.addMeta("source_type", sourceType);

        // 3. 导入知识库：
        //    - 提取后的纯文本 → MySQL campus_knowledge.content（便于将来重切）
        //    - 切片 + metadata → Qdrant（检索主力 + 溯源）
        CampusKnowledgeImportRequest importRequest = new CampusKnowledgeImportRequest();
        importRequest.setTitle(
                title != null && !title.isBlank() ? title : fileName
        );
        importRequest.setSourceName(sourceName);
        importRequest.setSourceType(sourceType);
        importRequest.setTrustLevel(trustLevel);
        importRequest.setUrl("file://" + fileName);
        importRequest.setContent(doc.getContent());
        importRequest.setMetadata(doc.getMetadata());

        Map<String, Object> result = campusKnowledgeImportService.importKnowledge(importRequest);

        result.put("fileName", fileName);
        result.put("extractMethod", doc.getMetaString("extract_method"));
        result.put("textLength", doc.getContent().length());
        result.put("pageCount", doc.getMeta("page_count"));
        return Result.success(result);
    }

    /**
     * 获取支持的文件格式列表
     */
    @GetMapping("/knowledge/supported-formats")
    public Result<?> supportedFormats() {
        return Result.success(documentLoaderService.getSupportedFormats());
    }
}