# 珠科校园动脉 — ZCST Life Hub

面向**珠海科技学院**的校园综合服务平台，基于 **Spring Boot 3 + Vue 3 + MySQL 8.0 + Redis + RabbitMQ + WebSocket + Qdrant** 构建。

覆盖校园动态、活动报名、学生拼团、实时聊天、失物招领、AI 校园事务 Agent、后台管理等场景。

---

## 在线体验

| 入口 | 地址 |
|------|------|
| 前台 | http://localhost:3000 |
| 后台 | http://localhost:3000/admin |

> 测试账号：`admin` / `admin123`（管理员），`user1` / `admin123`（普通用户）

---

## 技术栈

### 后端
| 技术 | 用途 |
|------|------|
| Spring Boot 3.5 + Java 21 | 核心框架 |
| Spring Security + JWT | 认证鉴权 |
| MyBatis-Plus 3.5 | ORM |
| MySQL 8.0 | 业务数据 + 知识库元数据 |
| Redis 7 + Redisson | 缓存 / 分布式锁 / Bitmap / 限流 |
| RabbitMQ 3 | 拼团事件 + TTL+DLX 过期补偿 |
| WebSocket | 实时聊天 + 拼团推送 |
| Qdrant | AI Agent 向量检索（RAG） |
| Apache Tika 2.9 | 多格式文档解析（PDF/Word/MD 等） |
| PaddleOCR 3.7 | 中文 OCR / 扫描件识别 |
| Resilience4j | 熔断降级（规划中） |

### 前端
| 技术 | 用途 |
|------|------|
| Vue 3 (Composition API) | 核心框架 |
| Vite 6 | 构建工具 |
| Pinia | 状态管理 |
| Vue Router 4 | 路由 |
| Tailwind CSS 3 | 样式 |
| Axios | HTTP 请求 + 全局去重 |

### AI / RAG
| 组件 | 用途 |
|------|------|
| Qdrant | 向量数据库（Cosine 相似度检索） |
| 阿里云百炼 / SiliconFlow | LLM（qwen3.7-plus）+ Embedding（text-embedding-v4） |
| PaddleOCR | 中文图片/扫描件 OCR 识别 |
| Apache Tika | PDF/Word/PPT/Excel/Markdown 文档解析 |

---

## 项目亮点

### 1. 活动报名高并发控制（Redis Lua + MySQL 条件更新 + 唯一索引）

- Redis Lua 脚本**原子**完成库存判断 + 重复报名检查 + 库存预扣，**单次 Redis 往返**
- MySQL `UPDATE ... WHERE participant_count < max_participants` 条件防超卖
- `activity_id + user_id` 唯一索引最终兜底

### 2. 热门动态缓存击穿优化（Redisson 分布式锁 + 双重检查）

- Redisson tryLock 自适应等待 + 看门狗防死锁
- 双重检查避免锁竞争后重复查库
- 帖子变更时主动删除缓存

### 3. 学生拼团异步事件驱动（RabbitMQ TTL + DLX + WebSocket）

- RabbitMQ TTL + DLX 实现拼团过期延迟检查
- `afterCommit` 保证事务提交后才发送 MQ / WebSocket
- CompletableFuture + 自定义线程池并行聚合首页数据
- WebSocket 实时推送拼团状态变化

### 4. 实时聊天室与已读统计（Redis Bitmap）

- WebSocket 消息实时推送，ConcurrentHashMap 管理多窗口连接
- Redis Bitmap 实现消息已读统计：`SETBIT` 标记 + `BITCOUNT` 统计
- 阅后即焚消息支持

### 5. 校园事务 AI Agent（多阶段 RAG 检索增强生成）

```
用户问题 → Query Rewrite → Hybrid Search → RRF Fusion → Cross-Encoder Rerank
  → Context Compression → LLM Generate → Self-Reflection → 返回
```

- **智能文本分块**：类 LangChain RecursiveCharacterTextSplitter，多级分隔符递归切分
- **多格式文档加载**：Apache Tika 自动识别 PDF/Word/PPT/Excel/MD → 提取纯文本
- **OCR 降级链**：Tika 文本提取失败 → PaddleOCR 兜底（扫描件/图片识别）
- **Chain of Responsibility + Strategy 双模式**：可插拔 OCR 引擎
- MySQL 存纯文本（用于将来重切）+ Qdrant 存向量（检索主力）

### 6. 系统流控与可观测性

- **滑动窗口限流**：Redis Sorted Set 替代固定窗口，消除边界泄漏
- **动态配置**：Redis Hash + Pub/Sub，运营后台修改 QPS 实时生效
- **全站降级开关**：一键熔断
- VisitMetricsFilter：小时级 PV 埋点

### 7. 全局限流去重

- Axios 拦截器：同请求 pending 期间自动拦截重复提交
- 前端按钮 loading 置灰

---

## 项目结构

```
social_school
├── campusconnect-api          # Spring Boot 后端
│   └── src/main/java/com/campusconnect/
│       ├── agent/             # AI Agent（RAG/OCR/知识库/分块）
│       │   ├── ocr/           # OCR 引擎（Strategy Pattern）
│       │   ├── expert/        # 多专家协作
│       │   └── service/       # DocumentLoader/Tika/Embedding
│       ├── chat/              # 新版聊天（WebSocket + Bitmap）
│       ├── common/            # 通用组件（动态配置/幂等/流控）
│       ├── config/            # Spring 配置
│       ├── controller/        # API 控制器（17 个）
│       ├── entity/            # 实体类
│       ├── mq/                # RabbitMQ 生产者/消费者
│       ├── security/          # JWT 认证
│       ├── service/           # 业务服务
│       └── system/            # 流控/埋点
├── campusconnect-web          # Vue 3 前端
│   └── src/
│       ├── api/               # Axios 封装 + 全局去重
│       ├── components/        # 通用组件
│       ├── composables/       # 组合式函数
│       ├── router/            # 路由
│       ├── stores/            # Pinia 状态管理
│       └── views/             # 页面（含 admin 后台）
├── docs/sql/                  # 数据库脚本
└── screenshots/               # 项目截图
```

---

## 本地启动

### 环境要求

| 组件 | 版本 | 端口 |
|------|------|:--:|
| JDK | 21+ | — |
| Maven | 3.8+ | — |
| Node.js | 20+ | — |
| MySQL | 8.0 | 3307 |
| Redis | 7 | 6379 |
| RabbitMQ | 3 | 5672 |
| Qdrant | — | 6333 |
| Python | 3.10+ | — |

### 1. 启动中间件（Docker）

```bash
# MySQL 8.0
docker run -d --name mysql8-campus -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 -e MYSQL_DATABASE=campusconnect \
  --restart=always mysql:8.0

# Redis
docker run -d --name redis-campus -p 6379:6379 --restart=always redis:7-alpine

# RabbitMQ（管理后台: http://localhost:15672 guest/guest）
docker run -d --name rabbitmq-campus -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest -e RABBITMQ_DEFAULT_PASS=guest \
  --restart=always rabbitmq:3-management-alpine

# Qdrant
docker run -d --name qdrant-campus -p 6333:6333 --restart=always qdrant/qdrant:latest
```

### 2. 初始化数据库

```bash
# 导入表结构和初始数据
mysql -u root -p123456 -P 3307 --protocol=tcp --default-character-set=utf8mb4 < docs/sql/campusconnect_schema.sql
```

### 3. 安装 PaddleOCR（用于图片/扫描件识别）

```bash
pip install paddleocr paddlepaddle
```

### 4. 后端启动

```bash
cd campusconnect-api
set JAVA_HOME=D:\Develop\java\jdk-24   # Windows
mvn clean package -DskipTests
java -jar target/campusconnect-api-1.0.0.jar
```

后端默认配置：`application.yml`（端口 8080，context-path /api）

### 5. 前端启动

```bash
cd campusconnect-web
npm install
npm run dev
```

访问 http://localhost:3000

### 6. 启动顺序

```
MySQL 8.0 → 导入 SQL → Redis → RabbitMQ → Qdrant → 后端 → 前端
```

---

## RAG 知识库使用

### 手动导入
后台 → 知识库导入 → 粘贴文本 → 导入知识库

### 文件上传导入
后台 → 知识库导入 → 上传 PDF/Word/PPT/Excel/MD/TXT/图片 → 自动解析 → 导入

- **文本型文档**：Tika 自动提取文字
- **扫描件/图片**：PaddleOCR 自动识别
- **混合文档**：文字 + OCR 双通道提取

### 验证
前台 → 学习页面 → 问 Agent 相关知识

---

## 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| user1 | admin123 | 普通用户 |
| user2 | admin123 | 协管员 |
