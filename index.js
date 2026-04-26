#!/usr/bin/env node
const { Server } = require("@modelcontextprotocol/sdk/server/index.js");
const { StdioServerTransport } = require("@modelcontextprotocol/sdk/server/stdio.js");
const {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} = require("@modelcontextprotocol/sdk/types.js");

const initSqlJs = require("sql.js");
const mysql = require("mysql2/promise");
const { Pool } = require("pg");

// ── 安全配置 ──────────────────────────────────────
const QUERY_TIMEOUT_MS = 30000;       // 最大查询时间
const SQLITE_MAX_FILE_MB = 100;       // SQLite 文件大小限制
const ALLOWED_SQLITE_DIRS = [         // SQLite 文件允许路径
  process.cwd(),
  process.env.HOME || "/home",
  "/data",
  "/var/lib",
];
const SELECT_RE = /^\s*SELECT\b/i;    // 只允许 SELECT 查询

// ── 安全检查 ──────────────────────────────────────

function validateReadOnly(query) {
  if (!SELECT_RE.test(query)) {
    throw new Error("只允许 SELECT 查询（只读模式），收到: " + query.trim().split(/\s+/)[0]);
  }
}

function validateSqlitePath(filePath) {
  if (!filePath) throw new Error("SQLite 需要 file 参数");
  const resolved = require("path").resolve(filePath);
  const allowed = ALLOWED_SQLITE_DIRS.some((dir) => resolved.startsWith(dir));
  if (!allowed) {
    throw new Error(`SQLite 文件路径不在允许范围内: ${resolved}`);
  }
  const stat = require("fs").statSync(resolved);
  if (!stat.isFile()) throw new Error("路径不是文件: " + resolved);
  if (stat.size > SQLITE_MAX_FILE_MB * 1024 * 1024) {
    throw new Error(`SQLite 文件过大 (${(stat.size / 1024 / 1024).toFixed(1)}MB)，限制 ${SQLITE_MAX_FILE_MB}MB`);
  }
}

// ── 服务定义 ──────────────────────────────────────

const server = new Server({ name: "mcp-db-query", version: "1.0.1" }, {
  capabilities: { tools: {} }
});

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [{
    name: "query",
    description: "Execute SQL SELECT query against MySQL/PostgreSQL/SQLite databases (read-only)",
    inputSchema: {
      type: "object",
      properties: {
        type: { type: "string", enum: ["mysql", "postgres", "sqlite"], description: "Database type" },
        query: { type: "string", description: "SQL SELECT query to execute" },
        host: { type: "string", description: "Host (for mysql/postgres)" },
        port: { type: "number", description: "Port (for mysql/postgres)" },
        database: { type: "string", description: "Database name" },
        user: { type: "string", description: "Username (for mysql/postgres)" },
        password: { type: "string", description: "Password (for mysql/postgres)" },
        file: { type: "string", description: "SQLite file path" },
      },
      required: ["type", "query"],
    }
  }]
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const args = request.params.arguments;
  const { type, query } = args;

  try {
    validateReadOnly(query);

    let result;
    switch (type) {
      case "mysql":
        result = await withTimeout(queryMysql(args, query), QUERY_TIMEOUT_MS);
        break;
      case "postgres":
        result = await withTimeout(queryPostgres(args, query), QUERY_TIMEOUT_MS);
        break;
      case "sqlite":
        validateSqlitePath(args.file);
        result = await withTimeout(querySqlite(args.file, query), QUERY_TIMEOUT_MS);
        break;
      default:
        throw new Error("不支持的数据库类型: " + type);
    }
    return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
  } catch (err) {
    return { content: [{ type: "text", text: `Error: ${err.message}` }], isError: true };
  }
});

// ── 数据库查询 ──────────────────────────────────────

async function queryMysql(config, query) {
  const conn = await mysql.createConnection({
    host: config.host || "localhost",
    port: config.port || 3306,
    database: config.database,
    user: config.user,
    password: config.password,
  });
  try {
    const [rows] = await conn.query(query);
    return rows;
  } finally {
    await conn.end();
  }
}

async function queryPostgres(config, query) {
  const pool = new Pool({
    host: config.host || "localhost",
    port: config.port || 5432,
    database: config.database,
    user: config.user,
    password: config.password,
  });
  try {
    const result = await pool.query(query);
    return result.rows;
  } finally {
    await pool.end();
  }
}

async function querySqlite(filePath, query) {
  const SQL = await initSqlJs();
  const buffer = require("fs").readFileSync(filePath);
  const db = new SQL.Database(buffer);
  try {
    const results = db.exec(query);
    return results;
  } finally {
    db.close();
  }
}

// ── 工具函数 ──────────────────────────────────────

function withTimeout(promise, ms) {
  return Promise.race([
    promise,
    new Promise((_, reject) =>
      setTimeout(() => reject(new Error(`查询超时 (${ms / 1000}s)`)), ms)
    ),
  ]);
}

// ── 启动 ──────────────────────────────────────

const transport = new StdioServerTransport();
server.connect(transport).catch(console.error);
