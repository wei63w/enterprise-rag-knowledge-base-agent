# Enterprise RAG Knowledge Base Agent

Enterprise RAG Knowledge Base Agent 是一个学习演示项目，用于展示企业级 RAG 知识库系统的核心原理。项目目标是从文档上传、解析、切片、向量化、检索问答到答案溯源，串起一套可理解、可运行、可逐步扩展的最小系统。

当前状态：Phase 3 向量索引与基础检索问答已完成，系统支持文档上传、向量化、基础 RAG 问答。

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
- [阶段任务](docs/管理/阶段任务.md)
- [变更记录](docs/管理/变更记录.md)
- [Phase 1 实施计划](docs/superpowers/plans/2026-05-21-phase-1-project-skeleton.md)

## 计划中的能力

- 文档上传与原始文件存储
- PDF、Word、Markdown、TXT 文档解析
- 文档切片、向量化和索引构建
- 基础 RAG 检索问答
- 答案引用与来源追溯
- 多轮对话记忆和检索增强能力

## 本地开发

复制环境变量模板：

```bash
cp .env.example .env
```

启动本地依赖：

```bash
docker compose --env-file .env -f docker/docker-compose.yml up -d
```

如果本机 `3306` 已被占用，可以在 `.env` 中把 `MYSQL_PORT` 改为 `3307`，并同步调整 `SPRING_DATASOURCE_URL` 中的端口。

启动后端：

```bash
mvn -f backend/pom.xml spring-boot:run
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

## 验证

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
```
