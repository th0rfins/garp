// GARP — self-hosted server. Zero npm dependencies: node:http + node:sqlite.
//
// Endpoints (identical to the production app):
//   GET  /api/orders       -> order list (newest first)
//   GET  /api/orders/:id   -> single order | 404 {"error":"Order tidak ditemukan"}
//   POST /api/orders       -> {link} creates an order, returns it (201)
//   GET  /api/stats        -> aggregate stats
//   /*                  -> static files from ./public
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createOrderFromLink, getOrder, listOrders, startPoller } from './src/orders.mjs';
import { getStats } from './src/stats.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC_DIR = path.join(__dirname, 'public');
const PORT = Number(process.env.SERVER_PORT || process.env.PORT) || 3000;
const HOST = process.env.HOST || '0.0.0.0';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8',
  '.woff2': 'font/woff2',
};

function json(res, code, body) {
  const data = JSON.stringify(body);
  res.writeHead(code, {
    'content-type': 'application/json; charset=utf-8',
    'x-robots-tag': 'noindex',
  });
  res.end(data);
}

async function handleApi(req, res, url) {
  const p = url.pathname;
  // GET /api/orders, GET /api/orders/:id
  if (p === '/api/orders' && req.method === 'GET') {
    return json(res, 200, listOrders());
  }
  const m = p.match(/^\/api\/orders\/([A-Za-z0-9]+)$/);
  if (m && req.method === 'GET') {
    const order = getOrder(m[1]);
    if (!order) return json(res, 404, { error: 'Order tidak ditemukan' });
    return json(res, 200, order);
  }
  if (p === '/api/orders' && req.method === 'POST') {
    let body = '';
    for await (const chunk of req) {
      body += chunk;
      if (body.length > 1e6) { req.destroy(); return; }
    }
    let parsed;
    try { parsed = JSON.parse(body || '{}'); } catch {
      return json(res, 400, { error: 'JSON tidak valid' });
    }
    const link = String(parsed.link || '').trim();
    if (!link || !/^https?:\/\//i.test(link)) {
      return json(res, 400, { error: 'Link tidak valid' });
    }
    try {
      const order = await createOrderFromLink(link);
      return json(res, 201, order);
    } catch (e) {
      return json(res, 500, { error: 'Gagal menyimpan: ' + e.message });
    }
  }
  if (p === '/api/stats' && req.method === 'GET') {
    return json(res, 200, getStats());
  }
  return json(res, 404, { error: 'Not found' });
}

async function serveStatic(res, pathname) {
  let filePath = path.normalize(path.join(PUBLIC_DIR, pathname));
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403); return res.end('Forbidden');
  }
  try {
    const data = await readFile(filePath);
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, { 'content-type': MIME[ext] || 'application/octet-stream' });
    res.end(data);
  } catch {
    // SPA fallback
    const index = await readFile(path.join(PUBLIC_DIR, 'index.html'));
    res.writeHead(404, { 'content-type': MIME['.html'] });
    res.end(index);
  }
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    if (url.pathname.startsWith('/api/')) {
      await handleApi(req, res, url);
    } else if (req.method === 'GET' || req.method === 'HEAD') {
      await serveStatic(res, decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname));
    } else {
      res.writeHead(405); res.end();
    }
  } catch (e) {
    console.error('[server]', e);
    if (!res.headersSent) json(res, 500, { error: 'Internal error' });
    else res.end();
  }
});

startPoller();
server.listen(PORT, HOST, () => {
  console.log(`GARP listening on http://${HOST}:${PORT}`);
});
