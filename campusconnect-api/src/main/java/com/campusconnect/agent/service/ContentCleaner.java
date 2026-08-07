package com.campusconnect.agent.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 正文清洗器 — 去除网页爬取的噪音内容
 *
 * 清洗目标：
 *   1. JS 模板代码          {{xxx}}, {{{xxx}}}
 *   2. 网站导航栏文字        办事大厅/学生/校友/校长信箱/ENGLISH/首页/概况
 *   3. 网站页脚             地址/电话/扫码关注/友情链接/技术支持
 *   4. 空白行和冗余换行
 */
@Component
public class ContentCleaner {

    /** 需要移除的整行（匹配即删除） */
    private static final List<Pattern> JUNK_LINES = List.of(
            // JS 模板
            Pattern.compile("\\{\\{.*\\}\\}"),
            // 纯导航词组合
            Pattern.compile("^(办事大厅|教师|学生|考生|校友|校长信箱|ENGLISH|旧版|怀念旧版)$"),
            Pattern.compile("^(首页|学校概况|机构设置|师资队伍|教育教学|科学研究|招生就业|校园文化|信息服务|信息公开)$"),
            // 统计信息行
            Pattern.compile("^\\{.*\\}$"),
            // 空链接残留
            Pattern.compile("^\\[.*\\]$")
    );

    /** 需要删除的段落（包含这些关键词的整段） */
    private static final List<String> JUNK_PARAGRAPHS = List.of(
            "扫码关注", "友情链接", "技术支持", "党委宣传部",
            "松山校区", "滨海校区", "金山大街", "科技路",
            "中华人民共和国教育部", "国家科学技术", "辽宁省教育厅",
            "浏览量：", "下一篇：", "上一篇：",
            "{{loadLanguage", "{{languageData"
    );

    private static final Pattern MULTI_BLANK_LINE = Pattern.compile("\n{3,}");

    public String clean(String text) {
        if (text == null || text.isBlank()) return text;

        StringBuilder result = new StringBuilder();
        boolean inJunkParagraph = false;

        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                result.append("\n");
                continue;
            }

            // 1. 检查是否为垃圾单行
            if (isJunkLine(trimmed)) continue;

            // 2. 检查是否进入垃圾段落
            if (isJunkParagraphStart(trimmed)) {
                inJunkParagraph = true;
                continue;
            }

            // 3. 在垃圾段落中，遇到空行或新标题则退出
            if (inJunkParagraph) {
                if (trimmed.startsWith("#") || trimmed.length() < 5) {
                    inJunkParagraph = false;
                    // fall through to add this line
                } else {
                    continue; // 仍在垃圾段落中，跳过
                }
            }

            result.append(trimmed).append("\n");
        }

        return MULTI_BLANK_LINE.matcher(result.toString())
                .replaceAll("\n\n").trim();
    }

    private boolean isJunkLine(String line) {
        for (Pattern p : JUNK_LINES) {
            if (p.matcher(line).matches()) return true;
        }
        return false;
    }

    private boolean isJunkParagraphStart(String line) {
        for (String keyword : JUNK_PARAGRAPHS) {
            if (line.contains(keyword)) return true;
        }
        return false;
    }
}
