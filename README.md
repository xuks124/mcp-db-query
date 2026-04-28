<div align="center">

# MCP DB Query Server 🔌

### 让 AI 直接查询你的数据库 — 支持 MySQL / PostgreSQL / SQLite

[![npm version](https://img.shields.io/npm/v/mcp-db-query)](https://www.npmjs.com/package/mcp-db-query)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Node](https://img.shields.io/badge/node-%3E%3D18-green)](https://nodejs.org)

</div>

---

## 🚀 这是什么？

一个 **MCP 协议** 数据库查询服务。让 Claude、Cursor、VS Code 等 AI 工具直接查你的数据库，不用写 SQL，动嘴就行。

> 一句话：**对着 AI 说人话，它帮你查数据库。**

## ✨ 能干什么？

| 场景 | 效果 |
|------|------|
| 👨‍💻 程序员查线上问题 | 说"最近一小时报错最多的接口"，AI 直接查 MySQL 出结果 |
| 📊 运营看数据 | 说"这周新增用户数"，AI 查 PostgreSQL 返回报表 |
| 🗄️ 本地开发调试 | 直接连 SQLite 文件，边开发边查 |
| 🤖 集成到 AI Agent | 让你的 Agent（OpenAI、Claude、DeepSeek）拥有查数据库的能力 |

## 📦 快速安装

```bash
# 全局安装
npm install -g mcp-db-query

# 或者用 npx 直接跑
npx mcp-db-query
```

## ⚙️ 配置使用

### 方式 1：SQLite（最简单）

```json
{
  "mcpServers": {
    "db-query": {
      "command": "npx",
      "args": ["-y", "mcp-db-query"],
      "env": {
        "DB_TYPE": "sqlite",
        "SQLITE_PATH": "/path/to/your.db"
      }
    }
  }
}
```

### 方式 2：MySQL

```json
{
  "mcpServers": {
    "db-query": {
      "command": "npx",
      "args": ["-y", "mcp-db-query"],
      "env": {
        "DB_TYPE": "mysql",
        "MYSQL_HOST": "localhost",
        "MYSQL_PORT": "3306",
        "MYSQL_USER": "root",
        "MYSQL_PASSWORD": "your_password",
        "MYSQL_DATABASE": "your_db"
      }
    }
  }
}
```

### 方式 3：PostgreSQL

```json
{
  "mcpServers": {
    "db-query": {
      "command": "npx",
      "args": ["-y", "mcp-db-query"],
      "env": {
        "DB_TYPE": "postgres",
        "PG_HOST": "localhost",
        "PG_PORT": "5432",
        "PG_USER": "postgres",
        "PG_PASSWORD": "your_password",
        "PG_DATABASE": "your_db"
      }
    }
  }
}
```

## 🔒 安全特性

- **只读模式** — 默认禁止 INSERT/UPDATE/DELETE
- **参数化查询** — 防止 SQL 注入
- **查询超时** — 防止慢查询拖垮数据库
- **表名白名单** — 可限制只能查指定表

## 📋 支持的 MCP 客户端

| 客户端 | 是否支持 |
|--------|----------|
| Claude Desktop | ✅ |
| Cursor IDE | ✅ |
| VS Code (MCP 扩展) | ✅ |
| 自定义 MCP 客户端 | ✅ |

## 🧩 需要 API Key？

本项目查询数据库本身不需要 AI API，但如果你需要：

- 大模型 API（DeepSeek / 通义千问 / Claude）
- 稳定便宜的中转服务
- 即买即用，无需绑卡

👉 **[www.9hcn.pw](https://www.9hcn.pw)** — 山哥 AI 网关，精选主流模型，稳定高效

## 📄 License

MIT
