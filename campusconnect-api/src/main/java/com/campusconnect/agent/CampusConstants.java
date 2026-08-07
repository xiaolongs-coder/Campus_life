package com.campusconnect.agent;

/**
 * 业务常量 — 所有硬编码字符串的唯一定义点
 * 修改一处，全局生效
 */
public final class CampusConstants {

    private CampusConstants() {}

    // ==================== 来源信息 ====================
    public static final String SOURCE_NAME_ZCST = "珠海科技学院官网";
    public static final String SOURCE_NAME_UPLOAD = "文件上传";
    public static final String SOURCE_NAME_MANUAL = "手动补充";
    public static final String SOURCE_NAME_UNKNOWN = "未知来源";

    // ==================== 来源类型 ====================
    public static final String SOURCE_TYPE_NOTICE = "通知公告";
    public static final String SOURCE_TYPE_ACADEMIC = "教务处";
    public static final String SOURCE_TYPE_EMPLOYMENT = "就业网";
    public static final String SOURCE_TYPE_DEPT = "学院通知";
    public static final String SOURCE_TYPE_FILE = "文件上传";
    public static final String SOURCE_TYPE_MANUAL = "手动补充";
    public static final String SOURCE_TYPE_CAMPUS = "校园通知";
    public static final String SOURCE_TYPE_GRADUATE = "研究生事务";

    // ==================== 可信度 ====================
    public static final String TRUST_HIGH = "高";
    public static final String TRUST_MEDIUM = "中";
    public static final String TRUST_LOW = "低";

    // ==================== 状态 ====================
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    // ==================== 爬虫 ====================
    public static final int CRAWL_MAX_ITEMS = 5;
    public static final int CRAWL_TIMEOUT_MS = 10_000;
    public static final String CRAWL_USER_AGENT = "Mozilla/5.0 ZcstCampusBot/1.0";

    // ==================== AI / LLM ====================
    public static final String LLM_SYSTEM_ROLE = "system";
    public static final String LLM_USER_ROLE = "user";
    public static final String LLM_ASSISTANT_ROLE = "assistant";

    // ==================== Embedding ====================
    public static final String EMBED_MODEL = "text-embedding-v4";
    public static final int EMBED_DIMENSION = 1024;

    // ==================== RAG ====================
    public static final int RAG_RETRIEVE_LIMIT = 5;
    public static final int RAG_FETCH_MULTIPLIER = 2;
    public static final double MMR_LAMBDA = 0.7;
    public static final double SIMILARITY_THRESHOLD = 0.85;
    public static final int MAX_CHUNKS_PER_DOC = 2;

    // ==================== 知识库 ====================
    public static final String KNOWLEDGE_COLLECTION = "campus_knowledge";
}
