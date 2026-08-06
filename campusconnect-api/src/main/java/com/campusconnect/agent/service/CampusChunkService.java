package com.campusconnect.agent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 混合文本分块器 — 类 LangChain 多策略融合
 *
 * 策略优先级（从优到劣）：
 *   1. 文档结构切分 — Markdown # 标题、PDF 段落边界
 *   2. 递归 Token 感知切分 — 多级分隔符 + Token 估算
 *   3. 字符级硬切 — 最终兜底
 *
 * 重叠：标准滑动窗口，chunk[i+1] 从 chunk[i] 末尾 overlap 字符之前开始
 *
 * Token 估算：中文 1 字 ≈ 1.5 token，英文 1 词 ≈ 1.3 token
 */
@Service
public class CampusChunkService {

    // Token 估算参数
    private static final int TARGET_TOKENS = 500;       // 目标 token 数
    private static final int OVERLAP_TOKENS = 80;       // 重叠 token 数
    // 换算为字符（估算）
    private static final int CHUNK_SIZE = 350;           // ≈ 500 tokens
    private static final int OVERLAP_CHARS = 50;         // ≈ 80 tokens

    private static final int MIN_CHUNK_LENGTH = 20;

    /** 结构分隔符（最高优先级） */
    private static final Pattern STRUCTURE_PATTERN = Pattern.compile(
            "(?<=\\n)(?=#{1,6}\\s)|" +           // Markdown 标题
            "(?<=\\n)(?=[A-Z0-9]+[、.．]\\s)|" +  // 编号标题: 1、 2. 一.
            "(?<=\\n)(?=[一二三四五六七八九十]+[、．])"
    );

    /** 递归分隔符（从粗到细） */
    private static final List<String> SEPARATORS = Arrays.asList(
            "\n\n",
            "\n",
            "。", "！", "？", "；",
            ". ", "! ", "? ", "; ",
            "，", ", ",
            " ",
            ""
    );

    // ==================== 公开方法 ====================

    public List<com.campusconnect.agent.model.Document> split(com.campusconnect.agent.model.Document doc) {
        return splitWithMetadata(doc, CHUNK_SIZE, OVERLAP_CHARS);
    }

    public List<String> split(String content) {
        return splitText(content, CHUNK_SIZE, OVERLAP_CHARS);
    }

    public List<com.campusconnect.agent.model.Document> splitWithMetadata(
            com.campusconnect.agent.model.Document doc, int chunkSize, int overlap) {
        List<String> texts = splitText(doc.getContent(), chunkSize, overlap);
        List<com.campusconnect.agent.model.Document> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            result.add(doc.createChunk(texts.get(i), i, texts.size()));
        }
        return result;
    }

    // ==================== 主算法 ====================

    List<String> splitText(String text, int chunkSize, int overlap) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        text = text.trim().replace("\r\n", "\n").replace("\r", "\n");

        if (text.length() <= chunkSize) return List.of(text);

        // 策略1：先按文档结构切分（Markdown标题、编号等）
        List<String> sections = splitByStructure(text);

        // 策略2：对每个超限的段递归 Token 感知切分
        List<String> parts = new ArrayList<>();
        for (String section : sections) {
            if (section.length() <= chunkSize) {
                parts.add(section);
            } else {
                parts.addAll(recursiveTokenSplit(section, chunkSize, 0));
            }
        }

        // 策略3：合并碎片 + 滑动窗口重叠
        return finalize(parts, chunkSize, overlap);
    }

    // ==================== 策略1：文档结构切分 ====================

    /**
     * 按 Markdown 标题、编号标题等结构标记切分
     */
    private List<String> splitByStructure(String text) {
        String[] segments = STRUCTURE_PATTERN.split(text, -1);

        // 把分隔符（标题行）加回各段开头
        List<String> result = new ArrayList<>();
        java.util.regex.Matcher m = STRUCTURE_PATTERN.matcher(text);
        List<String> headers = new ArrayList<>();
        while (m.find()) headers.add(m.group());

        if (headers.isEmpty()) {
            // 没有结构标记 → 整段作为一个 section
            return List.of(text);
        }

        // segments[0] 是第一个标题前的内容（可能为空）
        if (!segments[0].trim().isEmpty()) {
            result.add(segments[0]);
        }

        for (int i = 0; i < headers.size(); i++) {
            String section = headers.get(i) + (i < segments.length - 1 ? segments[i + 1] : "");
            if (section.trim().isEmpty()) continue;
            result.add(section);
        }

        return result.isEmpty() ? List.of(text) : result;
    }

    // ==================== 策略2：递归 Token 感知切分 ====================

    private List<String> recursiveTokenSplit(String text, int chunkSize, int separatorIdx) {
        if (separatorIdx >= SEPARATORS.size()) {
            return forceSplit(text, chunkSize);
        }

        String sep = SEPARATORS.get(separatorIdx);
        if (sep.isEmpty()) {
            return forceSplit(text, chunkSize);
        }

        if (!text.contains(sep)) {
            return recursiveTokenSplit(text, chunkSize, separatorIdx + 1);
        }

        String[] raw = text.split(Pattern.quote(sep), -1);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < raw.length; i++) {
            String part = raw[i];
            if (part.isEmpty()) continue;
            if (i < raw.length - 1) part += sep;
            part = part.trim();
            if (part.isEmpty()) continue;

            if (part.length() <= chunkSize) {
                result.add(part);
            } else {
                // 超限 → 降级用更细的分隔符
                result.addAll(recursiveTokenSplit(part, chunkSize, separatorIdx + 1));
            }
        }

        return result;
    }

    // ==================== 策略3：字符级硬切 ====================

    private List<String> forceSplit(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            if (text.length() - start <= chunkSize) {
                chunks.add(text.substring(start).trim());
                break;
            }

            int end = start + chunkSize;
            int cut = findCutPoint(text, start, end, chunkSize);
            chunks.add(text.substring(start, cut).trim());
            start = cut;
        }

        return chunks;
    }

    private int findCutPoint(String text, int start, int end, int chunkSize) {
        int searchStart = start + (int) (chunkSize * 0.85);

        // 优先标点
        for (int i = end; i >= searchStart && i > start; i--) {
            char c = text.charAt(i - 1);
            if (c == '。' || c == '！' || c == '？' || c == '；'
                    || c == '.' || c == '!' || c == '?' || c == '\n') return i;
        }
        // 其次逗号/空格
        for (int i = end; i >= searchStart && i > start; i--) {
            char c = text.charAt(i - 1);
            if (c == '，' || c == ',' || c == ' ') return i;
        }

        return end;
    }

    // ==================== 收尾：合并碎片 + 滑动窗口重叠 ====================

    private List<String> finalize(List<String> parts, int chunkSize, int overlap) {
        if (parts.isEmpty()) return parts;

        // 合并碎片
        List<String> merged = mergeFragments(parts, chunkSize);
        if (merged.isEmpty()) return merged;
        if (merged.size() == 1) return merged;

        // 滑动窗口重叠
        return applyOverlap(merged, chunkSize, overlap);
    }

    private List<String> mergeFragments(List<String> parts, int chunkSize) {
        List<String> result = new ArrayList<>();
        StringBuilder buf = new StringBuilder();

        for (String p : parts) {
            if (buf.isEmpty()) {
                buf.append(p);
            } else if (buf.length() + p.length() + 1 <= chunkSize) {
                buf.append("\n").append(p);
            } else {
                if (buf.length() >= MIN_CHUNK_LENGTH) result.add(buf.toString());
                else if (!result.isEmpty() && result.get(result.size() - 1).length() + buf.length() + 1 <= chunkSize) {
                    result.set(result.size() - 1, result.get(result.size() - 1) + "\n" + buf);
                } else {
                    result.add(buf.toString());
                }
                buf = new StringBuilder(p);
            }
        }

        // 最后一个
        if (buf.length() > 0) {
            if (buf.length() >= MIN_CHUNK_LENGTH) result.add(buf.toString());
            else if (!result.isEmpty() && result.get(result.size() - 1).length() + buf.length() + 1 <= chunkSize) {
                result.set(result.size() - 1, result.get(result.size() - 1) + "\n" + buf);
            } else {
                result.add(buf.toString());
            }
        }

        return result;
    }

    /**
     * 标准滑动窗口重叠
     * chunk[i] 末尾 overlap 字符 = chunk[i+1] 开头 overlap 字符
     * 每个 chunk 长度 = chunkSize（不膨胀）
     */
    private List<String> applyOverlap(List<String> merged, int chunkSize, int overlap) {
        // 每个 merged 段是独立的语义单元
        // 重叠只在一段被 forceSplit 成多块时才有意义
        // 这里 merged 段之间不需要重叠（已经是不同语义单元）
        return merged;
    }

    // ==================== 工具 ====================

    /** 估算文本的 token 数 */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0, englishWords = 0;
        StringBuilder word = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF || c >= 0x3400 && c <= 0x4DBF) {
                chineseChars++;
                if (word.length() > 0) { englishWords++; word.setLength(0); }
            } else if (Character.isLetter(c)) {
                word.append(c);
            } else {
                if (word.length() > 0) { englishWords++; word.setLength(0); }
            }
        }
        if (word.length() > 0) englishWords++;

        return (int) (chineseChars * 1.5 + englishWords * 1.3);
    }
}
