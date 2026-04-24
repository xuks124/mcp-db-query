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

const server = new Server({ name: "mcp-db-query", version: "1.0.0" }, {
  capabilities: { tools: {} }
});

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [{
    name: "query",
    description: "Execute SQL query against MySQL/PostgreSQL/SQLite databases",
    inputSchema: {
      type: "object",
      properties: {
        type: { type: "string", enum: ["mysql", "postgres", "sqlite"], description: "Database type" },
        query: { type: "string", description: "SQL query to execute" },
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
  const { type, query, host, port, database, user, password, file } = request.params.arguments;

  try {
    let result;
    switch (type) {
      case "mysql":
        result = await queryMysql({ host, port, database, user, password }, query);
        break;
      case "postgres":
        result = await queryPostgres({ host, port, database, user, password }, query);
        break;
      case "sqlite":
        result = await querySqlite(file, query);
        break;
    }
    return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
  } catch (err) {
    return { content: [{ type: "text", text: `Error: ${err.message}` }], isError: true };
  }
});

async function queryMysql(config, query) {
  const conn = await mysql.createConnection({
    host: config.host || "localhost", port: config.port || 3306,
    database: config.database, user: config.user, password: config.password,
  });
  const [rows] = await conn.query(query);
  await conn.end();
  return rows;
}

async function queryPostgres(config, query) {
  const pool = new Pool({
    host: config.host || "localhost", port: config.port || 5432,
    database: config.database, user: config.user, password: config.password,
  });
  const result = await pool.query(query);
  await pool.end();
  return result.rows;
}

async function querySqlite(filePath, query) {
  if (!filePath) throw new Error("SQLite requires 'file' path");
  const SQL = await initSqlJs();
  const buffer = require("fs").readFileSync(filePath);
  const db = new SQL.Database(buffer);
  const results = db.exec(query);
  db.close();
  return results;
}

const transport = new StdioServerTransport();
server.connect(transport).catch(console.error);