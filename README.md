# 省考试院自学考试计划管理系统

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/JDK-17-007396?logo=openjdk&logoColor=white)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.7-red)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-5-646CFF?logo=vite&logoColor=white)
![Ant Design](https://img.shields.io/badge/Ant%20Design-5-0170FE?logo=antdesign&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-3-003B57?logo=sqlite&logoColor=white)
![Version](https://img.shields.io/badge/Version-v1.0.0-blue)

## 目录

- [项目简介](#项目简介)
- [技术栈](#技术栈)
- [角色与功能范围](#角色与功能范围)
- [演示账号](#演示账号)
- [快速开始](#快速开始)
- [运行入口](#运行入口)
- [项目结构](#项目结构)
- [接口与认证说明](#接口与认证说明)
- [核心业务规则](#核心业务规则)
- [当前约束与说明](#当前约束与说明)
- [版本记录](#版本记录)

## 项目简介

省考试院自学考试计划管理系统是一个面向省级自学考试业务的前后端分离 Web 应用，覆盖考试计划发布、考生报名、准考证下载、成绩录入、成绩查询和统计分析等核心流程。

当前仓库采用 Monorepo 结构：

```text
shixunwork/
├── backend/    Spring Boot 3.2 后端服务，默认端口 8080
├── frontend/   React 18 + TypeScript 前端应用，默认端口 5173
└── exam.db     本地 SQLite 数据库文件，运行时生成，已加入 .gitignore
```

当前界面与交互特性包括：

- 首页公开可访问，访客无需登录即可浏览已发布考试计划
- 登录后按角色进入管理员工作台或考生中心
- 管理端采用浅色卡片化布局，统计页使用 ECharts 展示核心数据
- 考生端支持报名、取消报名、下载准考证、查询成绩和维护个人信息
- 前端主要使用 Ant Design 组件构建，避免重度渐变效果，界面风格偏轻量和自然

## 技术栈

### 后端

| 类别 | 选型 |
| --- | --- |
| 语言 / 运行时 | JDK 17 |
| 构建工具 | Maven |
| Web 框架 | Spring Boot 3.2 |
| ORM | MyBatis-Plus 3.5.7 |
| 数据库 | SQLite |
| 鉴权 | JWT |
| API 文档 | Knife4j 4.5 / OpenAPI 3 |
| Excel 处理 | EasyExcel 4.0 |
| PDF 生成 | iText 5.5 + iText-Asian |
| 其他工具 | Hutool |

### 前端

| 类别 | 选型 |
| --- | --- |
| 框架 | React 18 |
| 语言 | TypeScript 5 |
| 构建工具 | Vite 5 |
| UI 组件库 | Ant Design 5 |
| 样式方案 | Tailwind CSS 3 + SCSS Modules |
| 图表 | ECharts 5 |
| 状态管理 | Zustand |
| HTTP 客户端 | Axios |
| 包管理器 | npm 或 pnpm |

## 角色与功能范围

| 角色 | 主要能力 |
| --- | --- |
| 管理员 | 用户管理、专业管理、课程管理、考试计划管理、报名审核、成绩查看、统计分析 |
| 教师 | 查看后台数据、录入成绩、批量导入成绩、查看统计分析 |
| 考生 | 浏览已发布计划、提交报名、下载准考证、查询成绩、维护个人资料 |

当前系统对应的主要模块为：

1. 认证管理
2. 用户管理
3. 专业管理
4. 课程管理
5. 考试计划管理
6. 报名管理
7. 成绩管理
8. 统计分析

## 演示账号

数据库初始化脚本会自动写入以下演示账号，所有默认密码均为 `123456`。

| 用户名 | 角色 | 说明 |
| --- | --- | --- |
| `admin` | 管理员 | 拥有全部后台权限 |
| `teacher` | 教师 | 可录入成绩与查看统计 |
| `student1` | 考生 | 演示考生账号 |
| `student2` | 考生 | 演示考生账号 |
| `student3` | 考生 | 演示考生账号 |

## 快速开始

### 环境要求

- JDK 17 或更高版本
- Maven 3.8 或更高版本
- Node.js 18 或更高版本
- npm 9 或 pnpm 8

### 启动后端

推荐进入 `backend` 目录后启动：

```bash
cd backend
mvn clean spring-boot:run
```

也可以在仓库根目录指定 `pom.xml` 启动：

```bash
mvn -f backend/pom.xml clean spring-boot:run
```

启动后可访问：

- 后端服务：<http://localhost:8080>
- 接口文档：<http://localhost:8080/doc.html>

说明：

- 当前配置中 `spring.sql.init.mode=always`，每次启动后端都会重新执行 `schema.sql` 和 `data.sql`
- 这意味着演示数据会在每次启动时重建
- SQLite 文件名为 `exam.db`，会在当前启动工作目录生成，且已经被 Git 忽略

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

如果使用 pnpm，也可以执行：

```bash
cd frontend
pnpm install
pnpm dev
```

前端默认访问地址为：<http://localhost:5173>

前端开发代理已配置为：

```text
/api -> http://localhost:8080
```

### 生产构建

```bash
cd frontend
npm run build
```

构建产物输出到 `frontend/dist/`。

## 运行入口

| 地址 | 说明 | 权限要求 |
| --- | --- | --- |
| `/` | 公开首页，可浏览已发布考试计划 | 无 |
| `/login` | 登录页 | 无 |
| `/register` | 注册页 | 无 |
| `/admin/dashboard` | 管理后台首页 | 管理员 / 教师 |
| `/admin/statistics` | 统计分析页 | 管理员 / 教师 |
| `/student/home` | 考生首页 | 考生 |
| `/student/my-registrations` | 我的报名记录 | 考生 |
| `/student/my-scores` | 我的成绩 | 考生 |

## 项目结构

### 后端目录

```text
backend/
├── pom.xml
└── src/main/
    ├── java/com/exam/
    │   ├── ExamApplication.java
    │   ├── common/
    │   ├── config/
    │   └── module/
    │       ├── auth/
    │       ├── user/
    │       ├── major/
    │       ├── course/
    │       ├── plan/
    │       ├── registration/
    │       ├── score/
    │       └── statistics/
    └── resources/
        ├── application.yml
        ├── db/
        │   ├── schema.sql
        │   └── data.sql
        └── mapper/
```

### 前端目录

```text
frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
└── src/
    ├── api/
    ├── layouts/
    ├── pages/
    │   ├── auth/
    │   ├── public/
    │   ├── admin/
    │   │   ├── dashboard/
    │   │   ├── user/
    │   │   ├── major/
    │   │   ├── course/
    │   │   ├── plan/
    │   │   ├── registration/
    │   │   ├── score/
    │   │   └── statistics/
    │   └── student/
    │       ├── home/
    │       ├── plan/
    │       ├── profile/
    │       ├── registration/
    │       └── score/
    ├── router/
    ├── store/
    ├── styles/
    └── types.ts
```

## 接口与认证说明

### 统一响应格式

除文件下载接口外，普通 JSON 接口统一返回：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

### JWT 认证

除登录、注册和公开访问接口外，其余接口都需要在请求头中携带 JWT：

```text
Authorization: Bearer <token>
```

前端的 Axios 拦截器会自动注入该请求头。

### 文件下载接口说明

准考证 PDF、Excel 导出等接口属于二进制下载接口，不返回统一 JSON。

需要注意：

- 从前端页面中的下载按钮触发时，会自动携带认证头
- 如果直接在浏览器地址栏访问类似 `/api/registrations/{id}/ticket` 的地址，请求不会自动带上 Bearer Token，接口会返回 `401 未登录`

### 主要接口分组

| 前缀 | 模块 | 说明 |
| --- | --- | --- |
| `/api/auth/**` | 认证 | 登录、注册、当前用户信息 |
| `/api/user/**` | 用户 | 用户管理 |
| `/api/major/**` | 专业 | 专业管理 |
| `/api/course/**` | 课程 | 课程管理 |
| `/api/plan/**` | 考试计划 | 草稿、发布、结束、查询 |
| `/api/registrations/**` | 报名 | 报名、审核、准考证下载、导出 |
| `/api/score/**` | 成绩 | 录入、导入、查询 |
| `/api/statistics/**` | 统计 | 总览、趋势、合格率、分布 |

## 核心业务规则

### 考试计划状态

```text
DRAFT -> PUBLISHED -> FINISHED
```

规则如下：

- 只有 `PUBLISHED` 状态的计划允许考生报名
- `FINISHED` 状态通常用于后续成绩整理与查询阶段

### 报名趋势统计

当前统计页中的报名趋势按考试学期聚合，现已覆盖多学期演示数据，默认可看到：

- 2025-上
- 2025-下
- 2026-上

### 准考证与成绩文件处理

- 准考证采用 PDF 输出
- 报名导出与成绩导入采用 Excel 处理
- 文件下载接口均为受保护接口，需要认证信息

## 当前约束与说明

- 当前数据库使用 SQLite，适合演示、课程作业和中小规模数据场景
- 当前后端每次启动都会重建演示数据，不适合作为持久化生产配置
- 当前仓库未附带单独的 `LICENSE` 文件，如需对外发布，请按实际授权方式补充
- 当前 README 不再引用占位截图，避免出现失效图片链接
- 生产环境应替换 `application.yml` 中的 JWT 密钥和数据源配置

如果需要切换到 MySQL，可重点调整：

1. `backend/pom.xml` 中的数据库驱动依赖
2. `backend/src/main/resources/application.yml` 中的数据源配置

## 版本记录

### v1.0.0

- 完成认证、用户、专业、课程、考试计划、报名、成绩、统计等核心模块
- 接入 Knife4j 在线接口文档
- 完成准考证下载、成绩管理和统计分析页面
- 首页调整为公开浏览模式，访客可先查看已发布计划，再自行选择登录
- 后台与考生端界面已统一为更轻量的 Ant Design 风格

### 后续可继续完善

- 单元测试与集成测试
- Docker Compose 部署脚本
- 缴费模块
- 短信 / 邮件通知
- MySQL 或 PostgreSQL 支持
