#!/usr/bin/env node

/**
 * MCP DB Query Server — Secure Edition
 *
 * Security hardening:
 * - Read-only SQL enforcement (SELECT/SHOW/DESCRIBE/EXPLAIN/WITH only)
 * - Optional MCP_AUTH_TOKEN authentication
 * - Environment variable defaults for database credentials (no plaintext in params)
 * - Connection timeouts (prevents hanging on unreachable DB)
 * - SQLite path traversal protection
 * - Safe error messages in production mode
 * - PostgreSQL: Client instead of Pool (per-query resource safety)
 */

const { Server } = require("@modelcontextprotocol/sdk/server/index.js");
const { StdioServerTransport } = require("@modelcontextprotocol/sdk/server/stdio.js");
const {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} = require("@modelcontextprotocol/sdk/types.js");

const initSqlJs = require("sql.js");
const mysql = require("mysql2/promise");
const { Client } = require("pg");
const path = require("path");
const fs = require("fs");

// ── Configuration ──────────────────────────────────────────────
const AUTH_TOKEN = process.env.MCP_AUTH_TOKEN || null;
const PRODUCTION = process.env.NODE_ENV === "production";
const SQLITE_ALLOWED_DIR = process.env.SQLITE_DIR
  ? path.resolve(process.env.SQLITE_DIR)
  : null;

// Default database credentials from environment variables (avoids plaintext in params)
const DEFAULT_DB = {
  host: process.env.DB_HOST || null,
  port: process.env.DB_PORT ? parseInt(process.env.DB_PORT, 10) : null,
  database: process.env.DB_NAME || null,
  user: process.env.DB_USER || null,
  password: process.env.DB_PASSWORD || null,
};

// ── Security Helpers ───────────────────────────────────────────

/**
 * Validate read-only SQL: only SELECT, SHOW, DESCRIBE, EXPLAIN, WITH (CTE).
 * Strips SQL comments first to prevent comment-based bypass.
 */
function isReadOnlyQuery(sql) {
  if (!sql || typeof sql !== "string") return false;
  // Remove comments to reveal the real first keyword
  const cleaned = sql
    .replace(/--.*$/gm, "")           // line comments
    .replace(/\/\*[\s\S]*?\*\//g, "") // block comments
    .trim();
  const firstKeyword = cleaned.split(/\s+/)[0];
  if (!firstKeyword) return false;
  const allowed = ["SELECT", "SHOW", "DESCRIBE", "EXPLAIN", "WITH"];
  return allowed.includes(firstKeyword.toUpperCase());
}

/**
 * Return a safe error message (hide internals in production).
 */
function safeError(err) {
  if (PRODUCTION) {
    return "Database query failed";
  }
  return err.message || String(err);
}

/**
 * Authenticate the request if MCP_AUTH_TOKEN is configured.
 */
function authenticate(args) {
  if (!AUTH_TOKEN) return;
  const token = args && args.auth_token;
  if (!token || token !== AUTH_TOKEN) {
    throw new Error(
      "Authentication failed: invalid or missing auth_token parameter " +
      "(server has MCP_AUTH_TOKEN configured)"
    );
  }
}

/**
 * Validate SQLite file path — prevent path traversal.
 */
function resolveSqlitePath(filePath) {
  if (!filePath) {
    throw new Error("SQLite requires 'file' path parameter");
  }
  const resolved = path.resolve(filePath);

  if (SQLITE_ALLOWED_DIR && !resolved.startsWith(SQLITE_ALLOWED_DIR)) {
    throw new Error(
      `Access denied: file path must be under ${SQLITE_ALLOWED_DIR}`
    );
  }

  if (!fs.existsSync(resolved)) {
    throw new Error(`SQLite database file not found: ${resolved}`);
  }

  return resolved;
}

/**
 * Merge per-call connection params with env defaults.
 */
function resolveConfig(args, defaultPort) {
  return {
    host: args.host || DEFAULT_DB.host || "localhost",
    port: args.port || DEFAULT_DB.port || defaultPort,
    database: args.database || DEFAULT_DB.database,
    user: args.user || DEFAULT_DB.user,
    password: args.password || DEFAULT_DB.password,
  };
}

// ── Database Query Functions ───────────────────────────────────

async function queryMysql(config, sql) {
  const conn = await mysql.createConnection({
    host: config.host,
    port: config.port,
    database: config.database,
    user: config.user,
    password: config.password,
    connectTimeout: 5000,
  });
  try {
    const [rows] = await conn.query({ sql, timeout: 30000 });
    return rows;
  } finally {
    await conn.end().catch(() => {});
  }
}

async function queryPostgres(config, sql) {
  const client = new Client({
    host: config.host,
    port: config.port,
    database: config.database,
    user: config.user,
    password: config.password,
    connectionTimeoutMillis: 5000,
    statement_timeout: 30000,
  });
  try {
    await client.connect();
    const result = await client.query(sql);
    return result.rows;
  } finally {
    await client.end().catch(() => {});
  }
}

async function querySqlite(filePath, sql) {
  const resolved = resolveSqlitePath(filePath);
  const SQL = await initSqlJs();
  const buffer = fs.readFileSync(resolved);
  const db = new SQL.Database(buffer);
  try {
    const results = db.exec(sql);
    return results;
  } finally {
    db.close();
  }
}

// ── MCP Server ─────────────────────────────────────────────────

const server = new Server(
  { name: "mcp-db-query", version: "1.0.1" },
  { capabilities: { tools: {} } }
);

// ── Tool Definition ────────────────────────────────────────────

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "query",
      description:
        "Execute read-only SQL query against MySQL/PostgreSQL/SQLite databases. " +
        "Only SELECT, SHOW, DESCRIBE, EXPLAIN, and WITH (CTE) queries are allowed.",
      inputSchema: {
        type: "object",
        properties: {
          type: {
            type: "string",
            enum: ["mysql", "postgres", "sqlite"],
            description: "Database type",
          },
          query: {
            type: "string",
            description:
              "SQL query (SELECT/SHOW/DESCRIBE/EXPLAIN/WITH only — write operations are blocked)",
          },
          host: {
            type: "string",
            description:
              "Database host (default: DB_HOST env or localhost)",
          },
          port: {
            type: "number",
            description: "Database port (default: DB_PORT env or 3306/5432 per type)",
          },
          database: {
            type: "string",
            description: "Database name (default: DB_NAME env)",
          },
          user: {
            type: "string",
            description: "Username (default: DB_USER env)",
          },
          password: {
            type: "string",
            description: "Password (default: DB_PASSWORD env)",
          },
          file: {
            type: "string",
            description: "SQLite database file path (restricted to SQLITE_DIR if configured)",
          },
          auth_token: {
            type: "string",
            description:
              "Authentication token (required if MCP_AUTH_TOKEN env is set on server)",
          },
        },
        required: ["type", "query"],
      },
    },
  ],
}));

// ── Tool Handler ───────────────────────────────────────────────

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  try {
    // C02: Authenticate if token is configured
    authenticate(request.params.arguments);

    const { type, query: sql, host, port, database, user, password, file } =
      request.params.arguments;

    // C01: Enforce read-only queries
    if (!isReadOnlyQuery(sql)) {
      throw new Error(
        "Only read-only queries are allowed (SELECT, SHOW, DESCRIBE, EXPLAIN, WITH). " +
        "Write operations (INSERT, UPDATE, DELETE, DROP, ALTER, etc.) are blocked."
      );
    }

    let result;
    switch (type) {
      case "mysql":
        result = await queryMysql(
          resolveConfig({ host, port, database, user, password }, 3306),
          sql
        );
        break;
      case "postgres":
        result = await queryPostgres(
          resolveConfig({ host, port, database, user, password }, 5432),
          sql
        );
        break;
      case "sqlite":
        if (file && (host || port || user || password)) {
          console.warn(
            "[warn] SQLite mode ignores host/port/user/password — use only 'file' parameter"
          );
        }
        result = await querySqlite(file, sql);
        break;
      default:
        throw new Error(`Unsupported database type: ${type}`);
    }

    return {
      content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
    };
  } catch (err) {
    // C06: Safe error messages (hide internals in production)
    return {
      content: [{ type: "text", text: `Error: ${safeError(err)}` }],
      isError: true,
    };
  }
});

// ── Start Server ───────────────────────────────────────────────

const transport = new StdioServerTransport();
server.connect(transport).catch((err) => {
  console.error("Fatal server error:", err.message);
  process.exit(1);
});
