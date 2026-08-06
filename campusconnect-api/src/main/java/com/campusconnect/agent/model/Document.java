package com.campusconnect.agent.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档模型 — 对标 LangChain Document
 *
 * content（正文） + metadata（溯源信息）
 * 切分时 metadata 自动继承到每个 chunk
 */
public class Document {

    private String content;
    private Map<String, Object> metadata;

    public Document() {
        this.metadata = new LinkedHashMap<>();
    }

    public Document(String content, Map<String, Object> metadata) {
        this.content = content;
        this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
    }

    public String getContent() { return content; }
    public void setContent(String v) { content = v; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> v) { metadata = v != null ? v : new LinkedHashMap<>(); }

    // === 工厂方法 ===
    public static Document of(String content) {
        Document d = new Document();
        d.content = content;
        return d;
    }

    public static Document of(String content, Map<String, Object> metadata) {
        return new Document(content, metadata);
    }

    // === 元数据操作 ===
    public Document addMeta(String key, Object value) {
        metadata.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T getMeta(String key) {
        return (T) metadata.get(key);
    }

    public String getMetaString(String key) {
        Object v = metadata.get(key);
        return v != null ? v.toString() : "";
    }

    // === 溯源 ===
    public String toCitation() {
        StringBuilder sb = new StringBuilder();
        String source = getMetaString("source");
        if (!source.isEmpty()) sb.append("《").append(source).append("》");
        Object page = metadata.get("page_number");
        if (page != null) sb.append("第").append(page).append("页");
        String sourceType = getMetaString("source_type");
        if (!sourceType.isEmpty()) sb.append(" via ").append(sourceType);
        return sb.toString();
    }

    public Map<String, Object> cloneMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    // === chunk 创建 ===
    public Document createChunk(String chunkContent, int chunkIndex, int chunkCount) {
        Document chunk = new Document(chunkContent, cloneMetadata());
        chunk.addMeta("chunk_index", chunkIndex);
        chunk.addMeta("chunk_count", chunkCount);
        return chunk;
    }
}
