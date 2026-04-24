# MCP DB Query Server

A Model Context Protocol (MCP) server that enables AI assistants (Claude, Cursor, etc.) to query MySQL, PostgreSQL, and SQLite databases.

## Features

- **Multi-database support**: MySQL, PostgreSQL, SQLite
- **MCP-compliant**: Works with any MCP client (Claude Desktop, Cursor, VS Code with MCP extension)
- **Zero config**: SQLite mode needs only a file path
- **Safe**: Read-only by design, supports parameterized queries

## Installation

```bash
npm install -g mcp-db-query
```

Or clone and run:

```bash
git clone <repo-url>
cd mcp-db-query
npm install
```

## Usage with Claude Desktop

Add to your `claude_desktop_config.json`:

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

## API

### query
Execute SQL queries against databases.

Parameters:
- `type` (required): `"mysql"`, `"postgres"`, or `"sqlite"`
- `query` (required): SQL query string
- `host`, `port`, `database`, `user`, `password`: For MySQL/PostgreSQL
- `file`: SQLite database file path

## Examples

### SQLite (read local db)
```
query(type="sqlite", file="/data/app.db", query="SELECT * FROM users LIMIT 5")
```

### MySQL
```
query(type="mysql", host="localhost", port=3306, database="mydb", user="root", password="pass", query="SHOW TABLES")
```

## License

MIT

---

💡 **想要即拿即用？** 完整源码包 + 配置教程 + 远程协助支持，**¥68**。
📦 闲鱼购买：https://m.tb.cn/h.itNlsaT
📧 或直接留邮箱，获取付款码