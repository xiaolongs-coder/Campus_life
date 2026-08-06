package com.campusconnect.agent.dto;

import java.util.Map;



public class CampusKnowledgeImportRequest {

    private String title;
    private String sourceName;
    private String sourceType;
    private String url;
    /** 提取后的纯文本（存 MySQL 用于将来重切 + 分块向量化入 Qdrant） */
    private String content;
    /** 文档元数据（溯源信息：source/页码/提取方式等，切分时继承到每个chunk） */
    private Map<String, Object> metadata;
    private String trustLevel;

    // === 手写 getter/setter（Lombok 在 agent 包不可用） ===
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String v) { sourceName = v; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String v) { sourceType = v; }
    public String getUrl() { return url; }
    public void setUrl(String v) { url = v; }
    public String getContent() { return content; }
    public void setContent(String v) { content = v; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> v) { metadata = v; }
    public String getTrustLevel() { return trustLevel; }
    public void setTrustLevel(String v) { trustLevel = v; }
}