package com.campusconnect.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("campus_knowledge_chunk")
public class CampusKnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long knowledgeId;

    private Integer chunkIndex;

    private String content;

    private String contentHash;

    private String qdrantPointId;

    private Integer tokenCount;

    private LocalDateTime createdAt;

    // === getter/setter（agent包Lombok不可用） ===
    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public Long getKnowledgeId() { return knowledgeId; }
    public void setKnowledgeId(Long v) { knowledgeId = v; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer v) { chunkIndex = v; }
    public String getContent() { return content; }
    public void setContent(String v) { content = v; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String v) { contentHash = v; }
    public String getQdrantPointId() { return qdrantPointId; }
    public void setQdrantPointId(String v) { qdrantPointId = v; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer v) { tokenCount = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}