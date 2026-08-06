package com.campusconnect.agent.service;

import com.campusconnect.agent.dto.CampusRagChunkDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CampusRagService {

    private final EmbeddingClient embeddingClient;
    private final QdrantVectorService qdrantVectorService;

    public List<CampusRagChunkDTO> retrieve(String question, int limit) {
        if (question == null || question.trim().isEmpty()) {
            throw new RuntimeException("问题不能为空");
        }

        // 检索时多取一些，给 MMR 和去重留余地
        int fetchLimit = Math.max(limit * 2, 20);

        List<Double> queryVector = embeddingClient.embed(question);
        List<Map<String, Object>> searchResults = qdrantVectorService.search(queryVector, fetchLimit);

        // 1. 解析结果
        List<CampusRagChunkDTO> chunks = new ArrayList<>();
        for (Map<String, Object> item : searchResults) {
            Map<String, Object> payload = (Map<String, Object>) item.get("payload");
            CampusRagChunkDTO dto = new CampusRagChunkDTO();
            dto.setScore(toDouble(item.get("score")));
            dto.setChunkId(toLong(payload.get("chunkId")));
            dto.setKnowledgeId(toLong(payload.get("knowledgeId")));
            dto.setTitle(toString(payload.get("title")));
            dto.setSourceName(toString(payload.get("sourceName")));
            dto.setSourceType(toString(payload.get("sourceType")));
            dto.setUrl(toString(payload.get("url")));
            dto.setTrustLevel(toString(payload.get("trustLevel")));
            dto.setContent(toString(payload.get("content")));
            chunks.add(dto);
        }

        // 2. MMR 多样性去重：每个文档最多保留 2 个 chunk，避免 LLM 只看到单一来源
        List<CampusRagChunkDTO> diversified = mmrDiversify(chunks, limit, 0.7);

        return diversified;
    }

    /**
     * MMR (Maximal Marginal Relevance) 多样性优化
     *
     * 策略：
     *   1. 按 score 降序排列
     *   2. 依次选取，但同一 knowledgeId 最多取 2 个 chunk
     *   3. 如果当前 chunk 与已选中的 chunk 文本相似度过高（> 80%），跳过
     *
     * λ = 0.7 偏向相关性，0.3 偏向多样性
     */
    private List<CampusRagChunkDTO> mmrDiversify(List<CampusRagChunkDTO> chunks, int limit, double lambda) {
        List<CampusRagChunkDTO> selected = new ArrayList<>();
        java.util.Map<Long, Integer> docCount = new java.util.HashMap<>();

        for (CampusRagChunkDTO candidate : chunks) {
            if (selected.size() >= limit) break;

            // 同一文档最多 2 个 chunk
            Long knowledgeId = candidate.getKnowledgeId();
            int count = docCount.getOrDefault(knowledgeId, 0);
            if (count >= 2) continue;

            // 与已选中的 chunk 文本相似度检查（简单 Jaccard 去重）
            boolean tooSimilar = false;
            for (CampusRagChunkDTO s : selected) {
                if (textSimilarity(s.getContent(), candidate.getContent()) > 0.85) {
                    tooSimilar = true;
                    break;
                }
            }
            if (tooSimilar) continue;

            selected.add(candidate);
            docCount.put(knowledgeId, count + 1);
        }

        return selected;
    }

    /**
     * 简单 Jaccard 文本相似度（基于 2-gram 字符）
     * 返回值 0.0 ~ 1.0
     */
    private double textSimilarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;

        java.util.Set<String> aSet = new java.util.HashSet<>();
        java.util.Set<String> bSet = new java.util.HashSet<>();

        for (int i = 0; i < a.length() - 1; i++) aSet.add(a.substring(i, i + 2));
        for (int i = 0; i < b.length() - 1; i++) bSet.add(b.substring(i, i + 2));

        java.util.Set<String> intersection = new java.util.HashSet<>(aSet);
        intersection.retainAll(bSet);
        java.util.Set<String> union = new java.util.HashSet<>(aSet);
        union.addAll(bSet);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(value.toString());
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return Double.parseDouble(value.toString());
    }

    private String toString(Object value) {
        return value == null ? null : value.toString();
    }
}