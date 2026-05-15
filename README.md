<div align="center">

# MCP DB Query Server 🗄️

### 让 AI（Claude/Cursor/VS Code）直接查你的数据库 — 零配置、只读安全

[![npm](https://img.shields.io/npm/v/mcp-db-query)](https://www.npmjs.com/package/mcp-db-query)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/xuks124/mcp-db-query)](https://github.com/xuks124/mcp-db-query)

</div>

---

## 👀 这是啥？

MCP (Model Context Protocol) 服务端，让 AI 工具直接查你的数据库。

**一句话：** 装上之后，你的 Claude Desktop / Cursor / VS Code 可以直接 `SELECT * FROM users` 并看到结果，不用你手动复制粘贴。

## ✨ 核心功能

| 功能 | 说明 |
|------|------|
| 🗄️ **多数据库** | MySQL / PostgreSQL / SQLite 全支持 |
| 🔒 **只读安全** | 默认只允许 SELECT / SHOW / DESCRIBE / EXPLAIN — 删不了数据 |
| 🔌 **MCP 标准** | 跟任何 MCP 客户端兼容（Claude Desktop、Cursor、VS Code） |
| ⚡ **零配置** | SQLite 模式只需一个文件路径 |
| 🛡️ **安全加固** | 认证、路径遍历防护、连接超时、错误信息脱敏 |

## 🚀 3 分钟上手

```bash
# 安装
npm install -g mcp-db-query

# 配置到 Claude Desktop
# 编辑 claude_desktop_config.json 加这一段（看下方示例）
```

### Claude Desktop 配置

```json
{
  "mcpServers": {
    "db-query": {
      "command": "node",
      "args": ["/path/to/mcp-db-query/index.js"]
    }
  }
}
```

## 💰 怎么用这个赚钱？

| 场景 | 操作 | 定价 |
|------|------|------|
| **帮人配置 MCP 服务** | 帮企业/开发者连数据库到 AI 工具 | ¥50-200/次 |
| **私有化部署** | 部署到客户内网 | ¥500-2000/次 |
| **培训教程** | 录视频教人用 MCP + 数据库 | 引流接单 |

## 📄 License

MIT
