# Phase 2 文档处理主链路实施计划

> **给后续执行者：** 本计划按任务拆分记录 Phase 2 的实现过程。每个任务完成后需要运行对应验证命令，并在阶段完成时同步更新 `docs/管理/阶段任务.md` 和 `docs/管理/变更记录.md`。

**目标：** 实现 MVP 文档处理流程：上传文档、保存原始文件、解析文本、固定长度切片、暴露文档 API，并让前端文档页接入真实数据。

**架构：** 后端由 `DocumentService` 统一编排存储、解析、切片、JPA 仓储和状态流转。API 层在 `/api/documents` 下提供上传、列表、详情、状态和删除接口。前端文档页通过 `documentApi.ts` 调用后端，并展示上传、加载、错误、状态和删除反馈。

**技术栈：** Spring Boot 3.2、Spring Data JPA、Apache Tika、MinIO SDK、MySQL、H2 测试库、React 18、Ant Design、Vitest。

---

## 任务 1：阶段状态和计划文档

**文件：**
- 新增：`docs/superpowers/plans/2026-05-21-phase-2-document-processing.md`
- 修改：`docs/管理/阶段任务.md`

- [x] 写入 Phase 2 实施计划。
- [x] 将 Phase 2 状态从“未开始”改为“进行中”。

## 任务 2：后端领域模型和处理服务

**文件：**
- 修改：`backend/pom.xml`
- 新增：`backend/src/main/java/com/rag/document/model/DocumentStatus.java`
- 新增：`backend/src/main/java/com/rag/document/entity/DocumentEntity.java`
- 新增：`backend/src/main/java/com/rag/document/entity/ChunkEntity.java`
- 新增：`backend/src/main/java/com/rag/document/repository/DocumentRepository.java`
- 新增：`backend/src/main/java/com/rag/document/repository/ChunkRepository.java`
- 新增：`backend/src/main/java/com/rag/document/chunk/FixedLengthChunker.java`
- 新增：`backend/src/main/java/com/rag/document/parser/DocumentTextParser.java`
- 新增：`backend/src/main/java/com/rag/document/storage/StorageService.java`
- 新增：`backend/src/main/java/com/rag/document/storage/MinioStorageService.java`
- 新增：`backend/src/main/java/com/rag/document/service/DocumentService.java`
- 新增测试：`backend/src/test/java/com/rag/document/chunk/FixedLengthChunkerTest.java`
- 新增测试：`backend/src/test/java/com/rag/document/parser/DocumentTextParserTest.java`
- 新增测试：`backend/src/test/java/com/rag/document/service/DocumentServiceTest.java`

- [x] 先写固定长度切片和文档上传服务失败测试。
- [x] 运行 `mvn -f backend/pom.xml test` 验证 RED。
- [x] 实现文档实体、切片实体、仓储、解析器、存储接口、MinIO 存储和文档服务。
- [x] 补充 `commons-io` 兼容版本，修复 Tika 真实解析缺失类问题。
- [x] 运行 `mvn -f backend/pom.xml test` 验证 GREEN。

## 任务 3：后端文档 API

**文件：**
- 新增：`backend/src/main/java/com/rag/api/dto/DocumentResponse.java`
- 新增：`backend/src/main/java/com/rag/api/dto/ErrorResponse.java`
- 新增：`backend/src/main/java/com/rag/api/controller/DocumentController.java`
- 新增：`backend/src/main/java/com/rag/api/exception/GlobalExceptionHandler.java`
- 新增测试：`backend/src/test/java/com/rag/api/controller/DocumentControllerTest.java`

- [x] 先写文档列表、上传、删除和非法上传错误处理测试。
- [x] 运行 `mvn -f backend/pom.xml test` 验证 RED。
- [x] 实现文档 API 控制器、响应 DTO 和全局异常处理。
- [x] 运行 `mvn -f backend/pom.xml test` 验证 GREEN。

## 任务 4：前端文档页联动

**文件：**
- 新增：`frontend/src/services/documentApi.ts`
- 修改：`frontend/src/App.tsx`
- 修改：`frontend/src/App.test.tsx`
- 修改：`frontend/src/styles.css`
- 修改：`frontend/vite.config.ts`

- [x] 先写前端从后端加载文档列表的失败测试。
- [x] 运行 `npm --prefix frontend test` 验证 RED。
- [x] 实现文档列表、上传、删除、加载状态和错误提示。
- [x] 增加 Vite `/api` 代理到后端。
- [x] 修复窄屏布局，避免标题和表格被压成竖排。
- [x] 运行 `npm --prefix frontend test` 验证 GREEN。

## 任务 5：完成验证和阶段记录

**文件：**
- 修改：`docs/管理/阶段任务.md`
- 修改：`docs/管理/变更记录.md`
- 修改：`README.md`
- 修改：`.env.example`
- 修改：`docker/docker-compose.yml`

- [x] 运行 `mvn -f backend/pom.xml test`，结果通过。
- [x] 运行 `npm --prefix frontend test`，结果通过。
- [x] 运行 `npm --prefix frontend run build`，结果通过。
- [x] 运行 `npm --prefix frontend audit --audit-level=high`，结果通过。
- [x] 运行 `docker compose --env-file .env.example -f docker/docker-compose.yml config --quiet`，结果通过。
- [x] 使用 MySQL `3307` 和 MinIO 完成真实 TXT 上传验证，返回状态 `READY`，切片数为 `1`。
- [x] 使用浏览器验证前端显示真实上传文档、`READY` 状态和上传入口。
- [x] 更新中文阶段任务和变更记录。
