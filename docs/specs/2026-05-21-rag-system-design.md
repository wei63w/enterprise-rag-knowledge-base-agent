# Enterprise RAG Knowledge Base Agent - 设计文档

## 1. 项目概述

**名称**: Enterprise RAG Knowledge Base Agent  
**定位**: 学习演示项目，展示企业级RAG系统核心原理  
**规模**: 小型（<1万文档）  
**日期**: 2026-05-21

## 2. 技术栈

| 类别 | 选型 | 版本 | 说明 |
|-----|------|------|------|
| 后端框架 | Spring Boot | 3.2.x | Java 17+ |
| RAG框架 | LangChain4j | 0.35.x | Java RAG核心框架 |
| 向量数据库 | Milvus | 2.3.x | 支持混合检索（向量+关键词） |
| 关系数据库 | MySQL | 8.0 | 元数据存储 |
| 缓存 | Redis | 7.x | 会话存储、短期记忆 |
| 对象存储 | MinIO | latest | 原始文件存储 |
| 文档解析 | Apache Tika | 2.9.x | 多格式文档解析 |
| 前端框架 | React | 18.x | TypeScript |
| UI组件库 | Ant Design | 5.x | 企业级组件库 |
| 大模型 | 多模型路由 | - | OpenAI/通义千问/文心一言/DeepSeek |

## 3. 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端层 (React)                            │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────────────────┐ │
│  │ 文档管理 │  │ 对话界面 │  │ 来源追溯 │  │ 系统配置            │ │
│  └─────────┘  └─────────┘  └─────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      后端层 (Spring Boot)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ 文档处理模块   │  │ RAG检索模块   │  │ 对话管理模块         │  │
│  │ - 多格式解析   │  │ - 向量检索    │  │ - 短期记忆          │  │
│  │ - 智能切片    │  │ - 关键词检索   │  │ - 意图识别          │  │
│  │ - 摘要生成    │  │ - 重排序      │  │ - 话题延续          │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        存储层                                    │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────────────┐│
│  │ Milvus     │  │ MySQL      │  │ MinIO                     ││
│  │ 向量存储    │  │ 元数据存储  │  │ 原始文件存储              ││
│  └────────────┘  └────────────┘  └────────────────────────────┘│
│  ┌────────────┐                                                │
│  │ Redis      │                                                │
│  │ 会话缓存    │                                                │
│  └────────────┘                                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        AI服务层                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    多模型路由网关                          │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │ │
│  │  │ OpenAI   │ │ 通义千问  │ │ 文心一言  │ │ DeepSeek │      │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## 4. 核心功能模块

### 4.1 文档处理模块

```
┌─────────────────────────────────────────────────────────────┐
│                     文档处理Pipeline                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌────────┐│
│  │ 文件上传  │───▶│ 格式解析  │───▶│ 智能切片  │───▶│ 向量化 ││
│  └──────────┘    └──────────┘    └──────────┘    └────────┘│
│       │               │               │               │     │
│       ▼               ▼               ▼               ▼     │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌────────┐│
│  │ MinIO    │    │ Apache   │    │ 混合切片  │    │ Milvus ││
│  │ 原始存储  │    │ Tika解析 │    │ 策略引擎  │    │ 向量库 ││
│  └──────────┘    └──────────┘    └──────────┘    └────────┘│
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**支持格式**: PDF、Word(.docx)、Markdown、TXT

**切片策略**:
| 策略 | 说明 | 适用场景 |
|-----|------|---------|
| 固定长度 | 500-1000 tokens | 通用场景 |
| 语义切片 | 段落边界识别 | 结构化文档 |
| 滑动窗口 | 重叠200 tokens | 保持上下文连贯 |
| 父子切片 | 小块检索，大块喂入LLM | 提高召回精度 |

**长文档处理**:
- 自动摘要 → 生成概要索引
- 章节识别 → 保留结构信息
- 元数据提取 → 标题/页码/作者

**核心类设计**:
- `DocumentParser`: 多格式解析器接口
- `PdfParser`/`DocxParser`/`MdParser`/`TxtParser`: 具体解析实现
- `ChunkStrategy`: 切片策略接口
- `ChunkEngine`: 切片引擎（策略选择与执行）
- `EmbeddingService`: 向量化服务
- `DocumentIndexer`: 索引构建器

### 4.2 RAG检索增强模块

```
┌─────────────────────────────────────────────────────────────────┐
│                     多路召回 + 重排序架构                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                      用户问题                                    │
│                         │                                       │
│                         ▼                                       │
│              ┌─────────────────────┐                           │
│              │   Query预处理        │                           │
│              │ • 问题改写           │                           │
│              │ • 关键词提取         │                           │
│              │ • 意图识别           │                           │
│              └─────────────────────┘                           │
│                         │                                       │
│          ┌──────────────┼──────────────┐                       │
│          ▼              ▼              ▼                       │
│   ┌────────────┐ ┌────────────┐ ┌────────────┐                │
│   │ 向量检索   │ │ BM25检索  │ │ 概要索引   │                │
│   │ (语义相似) │ │ (关键词)  │ │ (文档概要) │                │
│   │ Top-K=20  │ │ Top-K=10  │ │ Top-K=5   │                │
│   └────────────┘ └────────────┘ └────────────┘                │
│          │              │              │                       │
│          └──────────────┼──────────────┘                       │
│                         ▼                                       │
│              ┌─────────────────────┐                           │
│              │   结果合并去重       │                           │
│              └─────────────────────┘                           │
│                         │                                       │
│                         ▼                                       │
│              ┌─────────────────────┐                           │
│              │   重排序模型        │                           │
│              │ • BGE-Reranker     │                           │
│              │ • 相关性打分        │                           │
│              │ • Top-N筛选(5-10)  │                           │
│              └─────────────────────┘                           │
│                         │                                       │
│                         ▼                                       │
│              ┌─────────────────────┐                           │
│              │   Context构建       │                           │
│              │ • Token限制控制     │                           │
│              │ • 来源信息注入      │                           │
│              └─────────────────────┘                           │
│                         │                                       │
│                         ▼                                       │
│                    LLM生成答案                                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**召回策略对比**:
| 召回路径 | 用途 | 召回数 | 特点 |
|---------|------|-------|------|
| 向量检索 | 语义相似，处理同义改写 | Top-20 | 模糊匹配能力强 |
| BM25检索 | 关键词精准匹配 | Top-10 | 专业术语、编号查询 |
| 概要索引 | 快速定位相关文档 | Top-5 | 长文档场景优化 |

**重排序策略**: 使用BGE-Reranker对合并结果重新打分，筛选Top-5~10最相关片段

### 4.3 对话记忆与多轮理解

```
┌─────────────────────────────────────────────────────────────────┐
│                    对话记忆架构                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    短期记忆 (Window Memory)              │   │
│  │  ┌───────────────────────────────────────────────────┐  │   │
│  │  │ Session Store (Redis)                             │  │   │
│  │  │ • 最近5轮对话历史                                  │  │   │
│  │  │ • 当前话题上下文                                   │  │   │
│  │  │ • 用户偏好记录                                     │  │   │
│  │  └───────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                  │
│                              ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    对话理解引擎                          │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │   │
│  │  │ 问题改写      │  │ 意图识别      │  │ 话题追踪      │  │   │
│  │  │ 结合上下文    │  │ • 信息查询    │  │ • 话题延续    │  │   │
│  │  │ 补全省略信息  │  │ • 比较/分析   │  │ • 话题切换    │  │   │
│  │  │              │  │ • 操作指令    │  │ • 话题关联    │  │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**多轮对话场景示例**:

**追问场景**:
```
用户: "公司的报销流程是什么？"
机器人: [回答报销流程...]
用户: "需要哪些材料？" ← 识别为追问，保留话题上下文
```

**反问场景**:
```
用户: "那个文档在哪？"
机器人: "请问您指的是哪个文档？" ← 引导澄清
```

**补充条件**:
```
用户: "查一下上个月的销售数据"
用户: "只看华东区域的" ← 补充筛选条件，合并检索
```

**核心类设计**:
- `ConversationMemory`: 对话记忆管理接口
- `RedisSessionStore`: Redis会话存储实现
- `QueryRewriter`: 问题改写器（结合上下文补全省略）
- `IntentClassifier`: 意图分类器
- `TopicTracker`: 话题追踪器

### 4.4 答案溯源与引用展示

```
┌─────────────────────────────────────────────────────────────────┐
│                    答案溯源架构                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  每个召回片段携带元数据:                                          │
│  ┌───────────────────────────────────────────────────────┐     │
│  │ {                                                      │     │
│  │   "doc_id": "DOC-001",                                 │     │
│  │   "doc_name": "公司报销制度.pdf",                       │     │
│  │   "page": 12,                                          │     │
│  │   "chunk_id": "chunk-005",                             │     │
│  │   "text": "...报销流程分为三个步骤...",                  │     │
│  │   "score": 0.89                                        │     │
│  │ }                                                      │     │
│  └───────────────────────────────────────────────────────┘     │
│                                                                 │
│  LLM生成带引用答案:                                              │
│  ┌───────────────────────────────────────────────────────┐     │
│  │ 根据公司规定，报销流程分为三个步骤[1]:                   │     │
│  │ 1. 提交申请表                                          │     │
│  │ 2. 部门审批                                            │     │
│  │ 3. 财务审核[2]                                         │     │
│  │                                                        │     │
│  │ 每人每月报销限额为5000元[2]                             │     │
│  └───────────────────────────────────────────────────────┘     │
│                                                                 │
│  引用详情展示:                                                   │
│  ┌────┐ ┌────────────────────────────────────┐                │
│  │[1] ││ 公司报销制度.pdf · 第12页 · 段落5   │                │
│  └────┘└────────────────────────────────────┘                │
│  ┌────┐ ┌────────────────────────────────────┐                │
│  │[2] ││ 财务管理制度.doc · 第3章 · 段落12   │                │
│  └────┘└────────────────────────────────────┘                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**前端交互设计**:
- 点击引用标签 → 展示原文片段（高亮显示）
- 点击查看原文 → 打开原始文档PDF（定位到具体页码）
- 引用置信度展示 → 颜色区分（高置信绿色/中置信黄色/低置信灰色）

## 5. 数据模型设计

### 5.1 MySQL表结构

```sql
-- 文档表
CREATE TABLE documents (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'PDF/DOCX/MD/TXT',
    file_path VARCHAR(512) NOT NULL COMMENT 'MinIO存储路径',
    file_size BIGINT COMMENT '文件大小(bytes)',
    summary TEXT COMMENT '文档摘要',
    upload_time DATETIME NOT NULL,
    process_time DATETIME COMMENT '处理完成时间',
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/READY/ERROR',
    error_msg TEXT COMMENT '错误信息',
    chunk_count INT DEFAULT 0 COMMENT '切片数量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 切片表
CREATE TABLE chunks (
    id VARCHAR(32) PRIMARY KEY,
    doc_id VARCHAR(32) NOT NULL,
    chunk_index INT NOT NULL COMMENT '切片序号',
    content TEXT NOT NULL COMMENT '切片内容',
    page_number INT COMMENT '页码(PDF)',
    section VARCHAR(100) COMMENT '章节标题',
    embedding_id VARCHAR(64) COMMENT 'Milvus向量ID',
    metadata JSON COMMENT '扩展元数据',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id),
    INDEX idx_embedding_id (embedding_id)
);

-- 对话会话表
CREATE TABLE conversations (
    id VARCHAR(32) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    role VARCHAR(10) NOT NULL COMMENT 'USER/ASSISTANT',
    content TEXT NOT NULL COMMENT '对话内容',
    sources JSON COMMENT '引用来源列表',
    intent VARCHAR(20) COMMENT '意图分类',
    model_used VARCHAR(50) COMMENT '使用的模型',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id)
);

-- 模型配置表
CREATE TABLE model_configs (
    id VARCHAR(32) PRIMARY KEY,
    model_name VARCHAR(50) NOT NULL,
    provider VARCHAR(20) NOT NULL COMMENT 'OPENAI/QWEN/WENXIN/DEEPSEEK',
    api_key VARCHAR(256) COMMENT '加密存储',
    endpoint VARCHAR(256),
    enabled BOOLEAN DEFAULT TRUE,
    priority INT DEFAULT 0 COMMENT '路由优先级',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 5.2 Milvus Collection设计

```python
# Collection: document_chunks
fields = [
    FieldSchema(name="id", dtype=DataType.VARCHAR, max_length=64, is_primary=True),
    FieldSchema(name="doc_id", dtype=DataType.VARCHAR, max_length=32),
    FieldSchema(name="chunk_id", dtype=DataType.VARCHAR, max_length=32),
    FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=2000),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1536),  # OpenAI embedding维度
    FieldSchema(name="page_number", dtype=DataType.INT64),
    FieldSchema(name="doc_name", dtype=DataType.VARCHAR, max_length=255),
]

# 索引
index_params = {
    "metric_type": "COSINE",
    "index_type": "HNSW",
    "params": {"M": 8, "efConstruction": 64}
}
```

## 6. API设计

### 6.1 文档管理API

| 接口 | 方法 | 说明 | 请求体/参数 |
|-----|------|------|------------|
| `/api/documents/upload` | POST | 上传文档 | multipart/form-data |
| `/api/documents/list` | GET | 文档列表 | page, size, status |
| `/api/documents/{id}` | GET | 文档详情 | - |
| `/api/documents/{id}` | DELETE | 删除文档 | - |
| `/api/documents/{id}/status` | GET | 处理状态 | - |

### 6.2 对话API

| 接口 | 方法 | 说明 | 请求体 |
|-----|------|------|-------|
| `/api/chat/send` | POST | 发送问题 | `{session_id, question, model?}` |
| `/api/chat/history` | GET | 对话历史 | session_id, limit |
| `/api/chat/clear` | DELETE | 清空会话 | session_id |
| `/api/chat/sessions` | GET | 会话列表 | page, size |

### 6.3 溯源API

| 接口 | 方法 | 说明 | 参数 |
|-----|------|------|------|
| `/api/sources/{chunk_id}` | GET | 获取原文片段 | chunk_id |
| `/api/sources/document/{doc_id}` | GET | 获取文档切片列表 | doc_id |

### 6.4 配置API

| 接口 | 方法 | 说明 | 请求体 |
|-----|------|------|-------|
| `/api/models/list` | GET | 可用模型列表 | - |
| `/api/models/switch` | POST | 切换默认模型 | `{model_name}` |
| `/api/config/chunk-strategy` | GET/POST | 切片策略配置 | - |

## 7. 项目目录结构

```
enterprise-rag/
├── docs/                           # 项目文档
│   ├── specs/                      # 设计文档
│   └── api/                        # API文档
│
├── backend/                        # Spring Boot 后端
│   ├── src/main/java/com/rag/
│   │   ├── RagApplication.java     # 启动类
│   │   │
│   │   ├── config/                 # 配置类
│   │   │   ├── MilvusConfig.java
│   │   │   ├── ModelRouterConfig.java
│   │   │   ├── RedisConfig.java
│   │   │   └── MinioConfig.java
│   │   │
│   │   ├── document/               # 文档处理模块
│   │   │   ├── parser/             # 解析器
│   │   │   │   ├── DocumentParser.java
│   │   │   │   ├── PdfParser.java
│   │   │   │   ├── DocxParser.java
│   │   │   │   ├── MdParser.java
│   │   │   │   └── TxtParser.java
│   │   │   ├── chunk/              # 切片策略
│   │   │   │   ├── ChunkStrategy.java
│   │   │   │   ├── FixedLengthChunker.java
│   │   │   │   ├── SemanticChunker.java
│   │   │   │   ├── SlidingWindowChunker.java
│   │   │   │   └── ParentChildChunker.java
│   │   │   ├── embedding/          # 向量化
│   │   │   │   └ EmbeddingService.java
│   │   │   └── indexer/            # 索引构建
│   │   │       ├── DocumentIndexer.java
│   │   │       └── SummaryIndexer.java
│   │   │
│   │   ├── retrieval/              # 检索模块
│   │   │   ├── vector/             # 向量检索
│   │   │   │   └── VectorRetriever.java
│   │   │   ├── keyword/            # BM25检索
│   │   │   │   └── KeywordRetriever.java
│   │   │   ├── rerank/             # 重排序
│   │   │   │   ├── Reranker.java
│   │   │   │   └── BgeReranker.java
│   │   │   └── hybrid/             # 混合检索
│   │   │       └── HybridRetriever.java
│   │   │       └── RetrievalContext.java
│   │   │
│   │   ├── conversation/           # 对话模块
│   │   │   ├── memory/             # 记忆管理
│   │   │   │   ├── ConversationMemory.java
│   │   │   │   └── RedisSessionStore.java
│   │   │   ├── intent/             # 意图识别
│   │   │   │   ├── IntentClassifier.java
│   │   │   │   └── QueryRewriter.java
│   │   │   └── context/            # 上下文管理
│   │   │       └── ContextManager.java
│   │   │       └── TopicTracker.java
│   │   │
│   │   ├── llm/                    # 大模型服务
│   │   │   ├── router/             # 模型路由
│   │   │   │   ├── ModelRouter.java
│   │   │   │   ├── OpenAIProvider.java
│   │   │   │   ├── QwenProvider.java
│   │   │   │   ├── WenxinProvider.java
│   │   │   │   └── DeepSeekProvider.java
│   │   │   └── prompt/             # Prompt模板
│   │   │       ├── PromptTemplate.java
│   │   │       └── RagPromptBuilder.java
│   │   │
│   │   ├── api/                    # REST API
│   │   │   ├── controller/
│   │   │   │   ├── DocumentController.java
│   │   │   │   ├── ChatController.java
│   │   │   │   ├── SourceController.java
│   │   │   │   └── ModelController.java
│   │   │   ├── dto/
│   │   │   │   ├── DocumentDto.java
│   │   │   │   ├── ChatRequest.java
│   │   │   │   ├── ChatResponse.java
│   │   │   │   └── SourceDto.java
│   │   │   └── exception/
│   │   │       └── GlobalExceptionHandler.java
│   │   │
│   │   └── entity/                 # 数据实体
│   │   │   ├── Document.java
│   │   │   ├── Chunk.java
│   │   │   ├── Conversation.java
│   │   │   └── ModelConfig.java
│   │   │
│   │   └── repository/             # 数据访问
│   │   │   ├── DocumentRepository.java
│   │   │   ├── ChunkRepository.java
│   │   │   └ ConversationRepository.java
│   │   │
│   │   └── service/                # 业务服务
│   │   │   ├── DocumentService.java
│   │   │   ├── ChatService.java
│   │   │   └── RagService.java
│   │   │
│   │   └── util/                   # 工具类
│   │   │   ├── FileUtils.java
│   │   │   └── TokenUtils.java
│   │   │
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   └── application-prod.yml
│   │
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/                       # React 前端
│   ├── public/
│   │   └── index.html
│   ├── src/
│   │   ├── App.tsx
│   │   ├── main.tsx
│   │   │
│   │   ├── pages/
│   │   │   ├── DocumentManage.tsx  # 文档管理页
│   │   │   ├── Chat.tsx            # 对话页面
│   │   │   ├── Settings.tsx        # 系统配置
│   │   │   └── SourceDetail.tsx    # 来源详情页
│   │   │
│   │   ├── components/
│   │   │   ├── ChatMessage.tsx     # 对话消息组件
│   │   │   ├── SourceCitation.tsx  # 引用展示组件
│   │   │   ├── DocumentUploader.tsx # 文档上传组件
│   │   │   ├── ChunkPreview.tsx    # 切片预览组件
│   │   │   ├── ModelSelector.tsx   # 模型选择器
│   │   │   └── Layout.tsx          # 布局组件
│   │   │
│   │   ├── services/
│   │   │   ├── api.ts              # API调用封装
│   │   │   ├── documentApi.ts
│   │   │   ├── chatApi.ts
│   │   │   └── modelApi.ts
│   │   │
│   │   ├── hooks/
│   │   │   ├── useChat.ts          # 对话逻辑Hook
│   │   │   ├── useDocuments.ts
│   │   │   └── useSession.ts
│   │   │
│   │   ├── stores/                 # 状态管理
│   │   │   ├── chatStore.ts
│   │   │   └── documentStore.ts
│   │   │
│   │   ├── types/
│   │   │   ├── document.ts
│   │   │   ├── chat.ts
│   │   │   └ source.ts
│   │   │
│   │   └── utils/
│   │   │   ├── request.ts
│   │   │   └── format.ts
│   │   │
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── Dockerfile
│
├── docker/                         # Docker配置
│   ├── docker-compose.yml          # 完整部署
│   ├── docker-compose.dev.yml      # 开发环境
│   ├── milvus/
│   │   └── milvus.yaml
│   ├── mysql/
│   │   └ init.sql
│   └── nginx/
│   │   └ nginx.conf
│   └── .env.example
│
├── scripts/                        # 脚本
│   ├── build.sh                    # 构建脚本
│   ├── deploy.sh                   # 部署脚本
│   └── init-milvus.py              # Milvus初始化
│
├── .gitignore
├── README.md
└── Makefile
```

## 8. 开发计划（分阶段实施）

### Phase 1: 基础框架搭建
**目标**: 项目骨架 + 基础环境部署  
**内容**:
- Spring Boot项目初始化
- React前端项目初始化
- Docker Compose环境搭建（Milvus/MySQL/Redis/MinIO）
- 基础配置类实现
- 数据库表创建

**产出**: 可运行的空项目框架

### Phase 2: 文档处理模块
**目标**: 完整文档处理Pipeline  
**内容**:
- Apache Tika集成
- 多格式解析器实现
- 四种切片策略实现
- Embedding服务集成
- Milvus索引构建
- 文档上传API

**产出**: 可上传文档并自动处理入库

### Phase 3: 检索问答基础
**目标**: 基础问答能力  
**内容**:
- 向量检索实现
- LangChain4j集成
- 多模型路由实现
- 基础Prompt模板
- Chat API实现
- 前端对话界面

**产出**: 可进行基础问答

### Phase 4: 检索增强
**目标**: 多路召回 + 重排序  
**内容**:
- BM25关键词检索
- 概要索引检索
- 混合检索器实现
- BGE-Reranker集成
- 结果合并去重

**产出**: 检索质量显著提升

### Phase 5: 对话记忆
**目标**: 多轮对话理解  
**内容**:
- Redis会话存储
- 问题改写器
- 意图识别
- 话题追踪
- 前端多轮对话展示

**产出**: 支持追问、补充条件等场景

### Phase 6: 答案溯源
**目标**: 完整溯源展示  
**内容**:
- 来源元数据完善
- 引用标注Prompt优化
- Source API实现
- 前端引用展示组件
- 原文定位展示

**产出**: 所有答案可溯源查看原文

## 9. 关键技术依赖

### 9.1 后端依赖 (pom.xml核心)

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- LangChain4j -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>0.35.0</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>0.35.0</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-dashscope</artifactId> <!-- 通义千问 -->
        <version>0.35.0</version>
    </dependency>
    
    <!-- Milvus Java SDK -->
    <dependency>
        <groupId>io.milvus</groupId>
        <artifactId>milvus-sdk-java</artifactId>
        <version>2.3.4</version>
    </dependency>
    
    <!-- Apache Tika 文档解析 -->
    <dependency>
        <groupId>org.apache.tika</groupId>
        <artifactId>tika-core</artifactId>
        <version>2.9.1</version>
    </dependency>
    <dependency>
        <groupId>org.apache.tika</groupId>
        <artifactId>tika-parsers-standard-package</artifactId>
        <version>2.9.1</version>
    </dependency>
    
    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- MySQL -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>8.0.33</version>
    </dependency>
    
    <!-- MinIO -->
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>8.5.7</version>
    </dependency>
</dependencies>
```

### 9.2 前端依赖 (package.json核心)

```json
{
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "antd": "^5.12.0",
    "axios": "^1.6.0",
    "zustand": "^4.4.0",
    "react-router-dom": "^6.20.0",
    "@ant-design/icons": "^5.2.0"
  },
  "devDependencies": {
    "typescript": "^5.3.0",
    "vite": "^5.0.0",
    "@types/react": "^18.2.0"
  }
}
```

## 10. 配置示例

### 10.1 application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rag_db
    username: root
    password: ${MYSQL_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
  redis:
    host: localhost
    port: 6379

milvus:
  host: localhost
  port: 19530
  collection: document_chunks

minio:
  endpoint: http://localhost:9000
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket: rag-documents

llm:
  default-model: qwen-plus
  providers:
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4-turbo
    qwen:
      api-key: ${QWEN_API_KEY}
      model: qwen-plus
    wenxin:
      api-key: ${WENXIN_API_KEY}
      secret-key: ${WENXIN_SECRET_KEY}
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      model: deepseek-chat

rag:
  chunk:
    default-strategy: semantic
    fixed-length: 500
    overlap: 200
  retrieval:
    vector-top-k: 20
    keyword-top-k: 10
    summary-top-k: 5
    rerank-top-n: 8
  memory:
    window-size: 5
    session-ttl: 3600
```

## 11. Docker Compose配置

```yaml
version: '3.8'

services:
  milvus:
    image: milvusdb/milvus:v2.3.3
    ports:
      - "19530:19530"
      - "9091:9091"
    volumes:
      - milvus_data:/var/lib/milvus
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    depends_on:
      - etcd
      - minio

  etcd:
    image: quay.io/coreos/etcd:v3.5.5
    ports:
      - "2379:2379"
    volumes:
      - etcd_data:/etcd

  minio:
    image: minio/minio:latest
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data
    environment:
      MINIO_ROOT_USER: admin
      MINIO_ROOT_PASSWORD: admin123
    command: server /data --console-address ":9001"

  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./mysql/init.sql:/docker-entrypoint-initdb.d/init.sql
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: rag_db

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    depends_on:
      - milvus
      - mysql
      - redis
      - minio
    environment:
      MYSQL_PASSWORD: root123
      MINIO_ACCESS_KEY: admin
      MINIO_SECRET_KEY: admin123

  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - backend

volumes:
  milvus_data:
  etcd_data:
  minio_data:
  mysql_data:
  redis_data:
```

---

**文档版本**: v1.0  
**创建日期**: 2026-05-21  
**状态**: 已确认，待实施