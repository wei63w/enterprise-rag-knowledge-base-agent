# Enterprise RAG Knowledge Base Agent

Enterprise RAG Knowledge Base Agent 是一个学习演示项目，用于展示企业级 RAG 知识库系统的核心原理。项目目标是从文档上传、解析、切片、向量化、检索问答到答案溯源，串起一套可理解、可运行、可逐步扩展的最小系统。

当前状态：设计确认，待实施。

## 技术栈概览

| 层级 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.2.x, Java 17+ |
| RAG | LangChain4j 0.35.x |
| 向量数据库 | Milvus 2.3.x |
| 元数据存储 | MySQL 8.0 |
| 缓存 | Redis 7.x |
| 对象存储 | MinIO |
| 文档解析 | Apache Tika 2.9.x |
| 前端 | React 18.x, TypeScript, Ant Design 5.x |
| 大模型 | OpenAI, 通义千问, 文心一言, DeepSeek 等模型提供方 |

## 文档

- [系统设计文档](docs/specs/2026-05-21-rag-system-design.md)
- [MVP 范围、验收标准与异常处理](docs/specs/2026-05-21-mvp-scope-acceptance-exception-handling.md)

## 计划中的能力

- 文档上传与原始文件存储
- PDF、Word、Markdown、TXT 文档解析
- 文档切片、向量化和索引构建
- 基础 RAG 检索问答
- 答案引用与来源追溯
- 多轮对话记忆和检索增强能力

## 后续开发启动命令

当前仓库还只有设计文档，后端、前端和 Docker 配置尚未落地。代码骨架创建后，可在这里补充实际启动命令，例如：

```bash
# backend
# ./mvnw spring-boot:run

# frontend
# npm install
# npm run dev

# infrastructure
# docker compose up -d
```
