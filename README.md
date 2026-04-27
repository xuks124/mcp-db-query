# MCP DB Query Server

A Model Context Protocol (MCP) server that enables AI assistants (Claude, Cursor, etc.) to query MySQL, PostgreSQL, and SQLite databases.

## Features

- **Multi-database support**: MySQL, PostgreSQL, SQLite
- **MCP-compliant**: Works with any MCP client (Claude Desktop, Cursor, VS Code with MCP extension)
- **Read-only by default**: Only SELECT, SHOW, DESCRIBE, EXPLAIN, and WITH (CTE) queries are allowed — write operations are blocked
- **Zero config**: SQLite mode needs only a file path
- **Security hardened**: Authentication, path traversal protection, connection timeouts, safe error messages

## Installation

```bash
npm install -g mcp-db-query
```

Or clone and run:

```bash
git clone https://github.com/xuks124/mcp-db-query.git
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

Execute read-only SQL queries against databases.

Parameters:
- `type` (required): `"mysql"`, `"postgres"`, or `"sqlite"`
- `query` (required): SQL query string (only SELECT/SHOW/DESCRIBE/EXPLAIN/WITH allowed)
- `host`, `port`, `database`, `user`, `password`: For MySQL/PostgreSQL (override env defaults)
- `file`: SQLite database file path
- `auth_token`: Required if server has `MCP_AUTH_TOKEN` configured

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `DB_HOST` | Default database host | `localhost` |
| `DB_PORT` | Default database port | `3306` (MySQL) / `5432` (PG) |
| `DB_NAME` | Default database name | — |
| `DB_USER` | Default database user | — |
| `DB_PASSWORD` | Default database password | — |
| `MCP_AUTH_TOKEN` | Authentication token (optional, recommended for network exposure) | — |
| `SQLITE_DIR` | Restrict SQLite file access to this directory (optional) | — |
| `NODE_ENV` | Set to `production` to hide error details | — |

## Security Notes

- **Read-only enforced**: Write queries (INSERT, UPDATE, DELETE, DROP, ALTER, etc.) are blocked at the code level
- **Credentials via environment**: Use `DB_HOST`/`DB_USER`/`DB_PASSWORD` env vars instead of passing credentials in each call
- **Authentication**: Set `MCP_AUTH_TOKEN` to require a token on every request
- **SQLite path protection**: Set `SQLITE_DIR` to restrict which directory SQLite files can be read from
- **⚠️ Do not expose this server directly to the internet** without setting `MCP_AUTH_TOKEN` and a reverse proxy with HTTPS

## Examples

### SQLite (read local db)
```
query(type="sqlite", file="/data/app.db", query="SELECT * FROM users LIMIT 5")
```

### MySQL
```
query(type="mysql", host="localhost", port=3306, database="mydb", user="root", password="pass", query="SHOW TABLES")
```

### MySQL (using env variables)
```
# Set env vars first:
#   DB_HOST=db.example.com DB_USER=admin DB_PASSWORD=secret DB_NAME=mydb

query(type="mysql", query="SELECT id, name FROM users LIMIT 10")
```

### Authenticated request
```
query(type="sqlite", file="/data/app.db", query="SELECT * FROM config", auth_token="your-secret-token")
```

## License

MIT
