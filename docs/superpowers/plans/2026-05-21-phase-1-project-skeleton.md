# Phase 1 Project Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal runnable backend, frontend, and local infrastructure skeleton for the Enterprise RAG Knowledge Base Agent.

**Architecture:** The backend is a Spring Boot service with a health endpoint and typed RAG configuration placeholders. The frontend is a Vite React TypeScript app with Ant Design navigation and placeholder pages for documents, chat, and settings. Docker Compose provides local MySQL, Redis, MinIO, Milvus dependencies for later MVP work.

**Tech Stack:** Java 17 target on Spring Boot 3.2.x, Maven, React 18, Vite, TypeScript, Ant Design 5, Vitest, Docker Compose.

---

### Task 1: Backend Skeleton

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/test/java/com/rag/api/controller/HealthControllerTest.java`
- Create: `backend/src/main/java/com/rag/RagApplication.java`
- Create: `backend/src/main/java/com/rag/api/controller/HealthController.java`
- Create: `backend/src/main/java/com/rag/config/RagProperties.java`
- Create: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing health endpoint test**

Create a `@WebMvcTest` that expects `GET /api/health` to return `status=UP` and `service=enterprise-rag-knowledge-base-agent`.

- [ ] **Step 2: Run backend test to verify RED**

Run: `mvn -f backend/pom.xml test`

Expected: compilation or context failure because the controller/application classes do not exist yet.

- [ ] **Step 3: Implement minimal Spring Boot app**

Add the application class, health controller, typed RAG properties, and placeholder configuration.

- [ ] **Step 4: Run backend test to verify GREEN**

Run: `mvn -f backend/pom.xml test`

Expected: tests pass.

### Task 2: Frontend Skeleton

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/App.test.tsx`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/src/styles.css`

- [ ] **Step 1: Write the failing render test**

Create a Vitest test that renders `<App />` and expects the title plus navigation items for documents, chat, and settings.

- [ ] **Step 2: Run frontend test to verify RED**

Run: `npm install` in `frontend`, then `npm test`.

Expected: module or assertion failure before `App.tsx` exists.

- [ ] **Step 3: Implement minimal React app**

Add a compact Ant Design shell with sidebar navigation and placeholder panels for the three MVP pages.

- [ ] **Step 4: Run frontend test to verify GREEN**

Run: `npm test`.

Expected: tests pass.

### Task 3: Local Infrastructure and Docs

**Files:**
- Create: `docker/docker-compose.yml`
- Create: `.env.example`
- Modify: `.gitignore`
- Modify: `README.md`

- [ ] **Step 1: Add local dependency compose file**

Define MySQL, Redis, MinIO, etcd, and Milvus services with development credentials and persistent named volumes.

- [ ] **Step 2: Add environment template**

Create `.env.example` with local development values and placeholder LLM keys.

- [ ] **Step 3: Update README startup commands**

Replace placeholder commands with backend, frontend, and infrastructure commands.

- [ ] **Step 4: Verify repository state**

Run:

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
git status --short
```

Expected: tests pass and only intended files are changed before commit.
