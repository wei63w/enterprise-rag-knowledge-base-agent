# Phase 3 向量索引与基础检索问答设计

## 目标

接入 embedding 和 Milvus，让系统可以基于已处理文档执行基础向量召回，并通过 LLM 生成回答。

## 架构概述

```
用户提问 → ChatController → ChatService → 向量检索(Milvus) → LLM(DeepSeek) → 返回答案
                    ↓
              问答历史保存(MySQL)
                    
文档切片 → EmbeddingService → Milvus 向量索引
```

## 核心模块

### 1. Embedding 服务 (`embedding/`)

| 类 | 职责 |
|---|---|
| `EmbeddingService` 接口 | 定义 `embed(List<String> texts)` 返回向量列表 |
| `DeepSeekEmbeddingService` | 使用 LangChain4j DeepSeek EmbeddingModel 实现 |
| `EmbeddingProperties` | 配置模型名称、向量维度(默认 1024) |

### 2. Milvus 向量服务 (`vector/`)

| 类 | 职责 |
|---|---|
| `MilvusService` | Collection 初始化、向量写入、向量检索 |
| `MilvusProperties` | host、port、collection、dimension 配置 |

**Collection 结构：**
```
document_chunks_v1
├── id (主键，自增)
├── doc_id (文档ID)
├── chunk_index (切片序号)
├── content (切片文本)
├── embedding (向量，FLOAT_VECTOR, dim=1024)
```

**向量写入流程：**
- 文档处理完成后(DocumentService.markReady)，触发异步向量化
- 批量 embed 切片文本，写入 Milvus

### 3. Chat 服务 (`chat/`)

| 类 | 职责 |
|---|---|
| `ChatService` | RAG 流程编排：检索切片 → 构建 prompt → 调用 LLM |
| `ChatController` | `/api/chat` POST 接口，接收问题和文档范围 |
| `ChatRequest` DTO | question, docIds(可选) |
| `ChatResponse` DTO | answer, sources(引用切片) |
| `ChatHistoryEntity` | 问答历史实体，保存问题和答案 |

**RAG 流程：**
```
1. 用户提问 → ChatService.chat(question)
2. EmbeddingService.embed(question) → 获取问题向量
3. MilvusService.search(vector, topK=8) → 返回相关切片
4. 构建 RAG prompt: "基于以下内容回答问题...\n[切片内容]\n问题: ..."
5. LLM 聃用 → DeepSeek ChatModel.generate(prompt)
6. 保存 ChatHistoryEntity → 返回 ChatResponse
```

**无相关内容兜底：**
- 检索结果为空或相关性分数过低时，返回 "当前知识库中不具备足够依据回答该问题"

### 4. 前端对话页接入

| 文件 | 改动 |
|---|---|
| `frontend/src/services/chatApi.ts` | 新增 Chat API 调用服务 |
| `frontend/src/App.tsx` | Chat 页接入真实问答，替换 mock 数据 |
| `frontend/src/styles.css` | Chat 页样式优化，显示来源引用 |

**ChatResponse 显示结构：**
```
[答案文本]

--- 来源引用 ---
1. 文档A - 切片3 (相关性: 0.85)
   [片段文本预览...]
2. 文档B - 切片5 (相关性: 0.72)
   [片段文本预览...]
```

## 依赖变更

```xml
<!-- LangChain4j 核心 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.35.0</version>
</dependency>

<!-- DeepSeek 集成 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-deepseek</artifactId>
    <version>0.35.0</version>
</dependency>

<!-- Milvus 集成 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-milvus</artifactId>
    <version>0.35.0</version>
</dependency>
```

## 配置变更

```yaml
# application.yml 新增
embedding:
  model: deepseek-embedding
  dimension: 1024

chat:
  model: deepseek-chat
  temperature: 0.7
```

## 完成标准

1. 已处理文档可以生成向量索引
2. 用户提问后可以得到基于知识库的回答
3. 无相关内容时返回"不具备足够依据"的兜底回答
4. 问答记录可持久化