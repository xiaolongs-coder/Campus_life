/**
 * 业务常量 — 前端所有硬编码字符串的唯一定义点
 */

// ==================== 来源名称 ====================
export const SOURCE_NAME = {
  ZCST: '珠海科技学院官网',
  UPLOAD: '文件上传',
  MANUAL: '手动补充',
  UNKNOWN: '未知来源',
}

// ==================== 来源类型 ====================
export const SOURCE_TYPE = {
  NOTICE: '通知公告',
  ACADEMIC: '教务处',
  EMPLOYMENT: '就业网',
  DEPT: '学院通知',
  FILE: '文件上传',
  MANUAL: '手动补充',
  CAMPUS: '校园通知',
  GRADUATE: '研究生事务',
}

// ==================== 可信度 ====================
export const TRUST_LEVEL = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

// ==================== 默认配置 ====================
export const DEFAULTS = {
  PAGE_SIZE: 10,
  MAX_PAGE_SIZE: 50,
  IMAGE_MAX_COUNT: 9,
  MAX_FILE_SIZE: 20 * 1024 * 1024, // 20MB
  CHUNK_SIZE: 350,
  CHUNK_OVERLAP: 50,
}
