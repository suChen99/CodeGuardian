# CodeGuardian

本地 Git 增量 AI 代码审查工作台。规则给出可审计的地板，大模型只对**当前 Diff 窗口**提意见，结果写入 MySQL，用 Vue 查看和再审。

这不是全仓 Sonar，也不是「模型说了算」。一次改很多文件时可能超时。

## 它做什么

- 扫描本地仓库的新 commit，按文件做增量审查
- 规则检查 `System.out.println`、空 `catch`、`printStackTrace`
- 先给变更分类，再决定：只跑规则、规则+模型、还是提示人工看
- 模型新意见必须能锚定到本次 hunk 的 `+` 行或上下文行
- 同一 `commit + 文件 + 配置版本` 已成功则跳过；页面支持再审

## 它不做什么

- 不扫整个仓库，不做调用图 / AST
- 规则已经给出的严重度，模型不能往上抬
- 格式 / 文档变更不调用大模型
- 单文件 `public` / `protected` 签名变更，或路径含 auth / security 等，只提示人工确认，不会自动改代码

## 审查流程

```
本地 Git 新 commit
    → Java（JGit 扫描 + 调度 + 入库）
    → Python（分类 → 规则 → 可选 LLM → 行号锚定 → 合并）
    → MySQL
    → Vue 列表 / 详情 / 再审
```

| 目录 | 作用 | 端口 |
|---|---|---|
| `code-review-backend` | Spring Boot 4 / Java 21 / JGit / JPA | 8080 |
| `code-review-ai` | FastAPI：分类、规则、锚定、调用硅基流动 Qwen | 8000 |
| `code-review-frontend` | Vue 3 + Vite + Element Plus | 5173 |

## 本机准备

- Java 21、Maven、Node.js、Python 3.12、MySQL
- 新建数据库：`code_guardian`
- 硅基流动 API Key（只放本地 `.env`）

## 配置

**Python**

```powershell
cd code-review-ai
copy .env.example .env
```

编辑 `.env`，填入 `SILICON_API_KEY`。

**Java**

```powershell
cd code-review-backend\src\main\resources
copy application-local.yml.example application-local.yml
```

把 `codemate.repo.path` 改成你本机要审查的 Git 仓库路径，并填写 MySQL 密码。那个仓库只在你电脑上，不用放进本项目的开源仓库。

## 启动

三个终端，先 Python，再 Java，再前端。

```
cd code-review-ai
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
python main.py
```

```
cd code-review-backend
mvn spring-boot:run
```

```
cd code-review-frontend
npm install
npm run dev
```

浏览器打开 `http://127.0.0.1:5173`。在 需审查项目 里提交代码后，大约 10 秒会被扫到。

健康检查：`http://127.0.0.1:8000/health`。

## 回放

改分类、规则、锚定或合并逻辑后，先跑确定性回放（不调用大模型）：

```
cd code-review-ai
python run_replay.py
```

当前基线是 20 组全绿。若审查策略本身变了，同时改 `ReviewProfile.VERSION`。


