# 🎓 省考试院自学考试计划管理系统

> 一套面向省考试院自学考试业务的 **全栈管理系统**，覆盖考试计划发布、考生报名、成绩录入、统计分析等核心场景。
> 后端基于 **Spring Boot 3.2 + MyBatis-Plus + SQLite**，前端基于 **React 18 + Vite 5 + TypeScript 5 + Ant Design 5**。

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/JDK-17-007396?logo=openjdk&logoColor=white)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.7-red)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-5-646CFF?logo=vite&logoColor=white)
![Ant Design](https://img.shields.io/badge/Ant%20Design-5-0170FE?logo=antdesign&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-3-003B57?logo=sqlite&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)
![Version](https://img.shields.io/badge/Version-v1.0.0-blue)

---

## 📑 目录

- [项目简介](#-项目简介)
- [技术栈](#-技术栈)
- [功能模块](#-功能模块)
- [演示账号](#-演示账号)
- [快速开始](#-快速开始)
- [项目结构](#-项目结构)
- [API 设计](#-api-设计)
- [核心业务规则](#-核心业务规则)
- [系统截图](#-系统截图)
- [已知约束与扩展](#-已知约束与扩展)
- [开发日志](#-开发日志)
- [License](#-license)

---

## 📖 项目简介

**省考试院自学考试计划管理系统**是为省级自考管理部门设计的 Web 应用，覆盖从计划发布、考生报名、准考证打印、成绩录入到统计分析的完整业务闭环。

系统采用前后端分离架构，以 **Monorepo** 形式组织：

```text
shixunwork/
├── backend/    Spring Boot 3.2 后端服务（端口 8080）
└── frontend/   React 18 + TypeScript 前端单页应用（端口 5173）
```

支持 **三类角色**：

| 角色 | 主要职责 |
| --- | --- |
| 🛡️ 管理员 (admin) | 维护用户/专业/课程；发布考试计划；审核报名；下载报表 |
| 👨‍🏫 教师 (teacher) | 录入成绩、Excel 批量导入成绩 |
| 🎒 考生 (student) | 浏览计划、在线报名、下载准考证 PDF、查询成绩 |

---

## 🛠 技术栈

### 后端 `backend/`

| 类别 | 选型 |
| --- | --- |
| 语言 / 运行时 | JDK 17 |
| 构建 | Maven |
| Web 框架 | Spring Boot 3.2 |
| ORM | MyBatis-Plus 3.5.7 |
| 数据库 | SQLite（单文件 `backend/exam.db`） |
| 鉴权 | JWT（jjwt 0.12） |
| API 文档 | Knife4j 4.5（OpenAPI 3） |
| Excel 处理 | Alibaba EasyExcel 4.0 |
| PDF 生成 | iText 5.5 + iText-Asian（中文字体） |
| 工具集 | Hutool 5.8 |

### 前端 `frontend/`

| 类别 | 选型 |
| --- | --- |
| 框架 | React 18 |
| 语言 | TypeScript 5 |
| 构建 | Vite 5 |
| UI 组件库 | Ant Design 5 |
| 样式 | Tailwind CSS 3 + SCSS Modules |
| 图表 | ECharts 5 |
| 状态管理 | zustand |
| HTTP 客户端 | axios |
| 包管理器 | pnpm（推荐）/ npm |

---

## 🧩 功能模块

系统共分为 **8 大业务模块**，其中 ⭐ 标记为核心模块：

| # | 模块 | 关键能力 |
| --- | --- | --- |
| 1 | 🔐 认证管理 | 登录、注册、JWT Token 签发与校验 |
| 2 | 👥 用户管理 | 管理员管理「管理员 / 教师 / 考生」三类账号 |
| 3 | 🎓 专业管理 | 专业 CRUD、专业-课程关联维护 |
| 4 | 📚 课程管理 | 课程 CRUD、课程查询 |
| 5 | 📅 考试计划管理 ⭐ | 计划发布、草稿保存、计划结束、状态流转 |
| 6 | 📝 报名管理 ⭐ | 考生报名、管理员审核、准考证 PDF 下载、Excel 导出名册 |
| 7 | 📊 成绩管理 ⭐ | 教师录入、Excel 批量导入、学生自助查询 |
| 8 | 📈 统计分析 ⭐ | 总览卡片、报名趋势、合格率、专业分布 4 类可视化图表 |

---

## 👤 演示账号

数据库初始化脚本会自动写入以下账号，**所有密码均为 `123456`**（BCrypt 加密入库）：

| 用户名 | 角色 | 说明 |
| --- | --- | --- |
| `admin` | 管理员 | 拥有全部权限 |
| `teacher` | 教师 | 可录入 / 导入成绩 |
| `student1` | 考生 | 演示考生账号 |
| `student2` | 考生 | 演示考生账号 |
| `student3` | 考生 | 演示考生账号 |

---

## 🚀 快速开始

### 环境要求

- JDK **17+**
- Maven **3.8+**
- Node.js **18+**
- pnpm **8+**（推荐）或 npm 9+

### 1️⃣ 启动后端（先启动）

```bash
cd backend

# 方式 A：开发模式
mvn spring-boot:run

# 方式 B：打包后运行
mvn clean package -DskipTests
java -jar target/exam-backend.jar
```

- 接口文档（Knife4j / OpenAPI 3）：<http://localhost:8080/doc.html>
- 数据库文件：首次启动时根据 `schema.sql` + `data.sql` 自动生成于 `backend/exam.db`

### 2️⃣ 启动前端

```bash
cd frontend
pnpm install     # 或 npm install
pnpm dev         # 或 npm run dev
```

- 前端访问地址：<http://localhost:5173>
- 已在 `vite.config.ts` 配置代理：`/api` → `http://localhost:8080`

### 3️⃣ 生产构建

```bash
cd frontend
pnpm build       # 产物输出至 frontend/dist
```

将 `frontend/dist` 静态文件部署至 Nginx，或交由后端整合托管即可。

---

## 🗂 项目结构

### 后端目录树 `backend/`

```text
backend/
├── pom.xml
├── exam.db                              # SQLite 数据库（运行时生成）
└── src/main/
    ├── resources/
    │   ├── application.yml              # 应用配置（端口/JWT/数据源）
    │   ├── schema.sql                   # 表结构初始化脚本
    │   ├── data.sql                     # 初始数据（演示账号等）
    │   └── mapper/                      # MyBatis XML
    └── java/com/exam/
        ├── ExamBackendApplication.java  # 启动类
        ├── common/                      # 通用响应 / 异常 / 常量 / 工具类
        ├── config/                      # MyBatis-Plus / Knife4j / CORS / WebMvc
        ├── interceptor/                 # JWT 拦截器
        └── module/
            ├── auth/                    # 1. 认证管理
            │   ├── controller/
            │   ├── service/
            │   ├── mapper/
            │   ├── entity/
            │   └── dto/
            ├── user/                    # 2. 用户管理
            ├── major/                   # 3. 专业管理
            ├── course/                  # 4. 课程管理
            ├── plan/                    # 5. 考试计划管理
            ├── registration/            # 6. 报名管理
            ├── score/                   # 7. 成绩管理
            └── statistics/              # 8. 统计分析
```

### 前端目录树 `frontend/`

```text
frontend/
├── package.json
├── vite.config.ts
├── tailwind.config.js
├── tsconfig.json
└── src/
    ├── main.tsx                         # 入口
    ├── App.tsx
    ├── types.ts                         # 全局 TS 类型声明
    ├── api/                             # axios 封装 + 各模块接口
    ├── store/                           # zustand 全局状态
    ├── router/                          # 路由配置 / 权限守卫
    ├── layouts/                         # 后台 / 考生端布局
    ├── components/                      # 通用组件
    ├── styles/                          # Tailwind / SCSS 全局样式
    └── pages/
        ├── auth/                        # 登录 / 注册
        ├── admin/                       # 管理员端
        │   ├── dashboard/
        │   ├── user/
        │   ├── major/
        │   ├── course/
        │   ├── plan/
        │   ├── registration/
        │   ├── score/
        │   └── statistics/
        └── student/                     # 考生端
            ├── plan/
            ├── registration/
            └── score/
```

---

## 🌐 API 设计

### 统一响应格式

所有接口均返回如下结构（除 PDF/Excel 文件下载外）：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 业务状态码，`200` 表示成功 |
| `msg` | string | 状态描述 |
| `data` | object / array / null | 业务数据 |

### 鉴权方式

除登录 / 注册外，所有接口需携带 JWT：

```text
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxxxx.yyyyy
```

Token 由 `POST /api/auth/login` 返回，前端 axios 拦截器会自动注入。

### 主要接口分组

| 前缀 | 模块 | 主要操作 |
| --- | --- | --- |
| `/api/auth/**` | 认证 | 登录、注册、当前用户信息 |
| `/api/user/**` | 用户 | 三类用户的增删改查 |
| `/api/major/**` | 专业 | 专业 CRUD + 课程关联 |
| `/api/course/**` | 课程 | 课程 CRUD |
| `/api/plan/**` | 考试计划 | 发布 / 草稿 / 结束 |
| `/api/registration/**` | 报名 | 报名 / 审核 / 准考证 PDF / Excel 导出 |
| `/api/score/**` | 成绩 | 录入 / 批量导入 / 查询 |
| `/api/statistics/**` | 统计 | 总览、趋势、合格率、专业分布 |

> 完整接口文档与在线调试入口：<http://localhost:8080/doc.html>

---

## 🧠 核心业务规则

### 考试计划状态机

```text
草稿(DRAFT) ──发布──▶ 已发布(PUBLISHED) ──结束──▶ 已结束(FINISHED)
```

- 仅 `已发布` 的计划允许考生报名
- `已结束` 的计划允许教师录入成绩、考生查询成绩

### 准考证号生成规则

```text
AT + 年份(4位) + 学期(01/02) + 计划ID(4位) + 学生ID后4位
示例：AT 2026 01 0007 0123  →  AT2026010007 0123
```

### Excel / PDF 处理

- **Excel 导出**：使用 EasyExcel 流式写出，适合大数据量报名册导出
- **Excel 导入**：成绩批量导入按行校验，错误行集中提示
- **PDF 准考证**：iText 5.5 + iText-Asian 字体包，支持中文渲染

---

## 🖼 系统截图

> 以下截图位为占位，实际部署后可替换为真实截图。

### 登录页

![登录页](./docs/screenshots/login.png)

### 管理员仪表盘

![管理员仪表盘](./docs/screenshots/admin-dashboard.png)

### 考生考试计划页

![考生考试计划页](./docs/screenshots/student-plan.png)

---

## ⚠️ 已知约束与扩展

- **SQLite 并发写入限制**：SQLite 适合中小数据量与读多写少场景，不适合高并发写入。
- **切换至 MySQL** 仅需两步：
  1. 修改 `backend/pom.xml`：将 `sqlite-jdbc` 依赖替换为 `mysql-connector-j`
  2. 修改 `backend/src/main/resources/application.yml` 中 `spring.datasource.url / username / password / driver-class-name`
- **PDF 中文字体**：依赖 `iText-Asian`，已在 pom 中声明；若部署到精简版 JRE 镜像，需保证字体资源可加载。
- **跨域**：开发环境通过 Vite Proxy 转发；生产环境建议同源部署或在 `CorsConfig` 中显式配置允许域名。
- **安全性**：密码使用 BCrypt；JWT 默认有效期可在 `application.yml` 中调整；建议生产环境替换 `jwt.secret`。

---

## 📜 开发日志

### v1.0.0 — 2026/05/11 🎉

- ✨ 完成 8 大业务模块全部功能
- ✨ 接入 Knife4j（OpenAPI 3）接口文档
- ✨ 实现准考证 PDF 在线生成与下载
- ✨ 实现成绩 Excel 批量导入 / 导出
- ✨ 接入 ECharts 5 完成 4 类统计图表
- 🔐 JWT 鉴权 + BCrypt 密码加密
- 🧪 内置演示数据：1 个管理员 / 1 个教师 / 3 个考生

### Roadmap

- [ ] 单元测试覆盖率提升至 70%+
- [ ] Docker Compose 一键部署脚本
- [ ] 报名缴费模块（接入沙箱支付）
- [ ] 短信 / 邮件通知
- [ ] 数据库支持 MySQL / PostgreSQL 切换

---

## 📄 License

本项目基于 [MIT License](./LICENSE) 开源，欢迎学习与二次开发。

---

<p align="center">Made with ❤️ by ExamPlan Team · v1.0.0</p>
