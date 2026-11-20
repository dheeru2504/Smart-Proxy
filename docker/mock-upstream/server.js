const http = require('http');
const url = require('url');

const PORT = process.env.PORT || 8091;
const INSTANCE_ID = process.env.INSTANCE_ID || `instance-${PORT}`;

const server = http.createServer(async (req, res) => {
    const parsedUrl = url.parse(req.url, true);
    const pathname = parsedUrl.pathname;
    const query = parsedUrl.query;

    // Simulate delay if requested (?delay=500)
    if (query.delay) {
        const delayMs = parseInt(query.delay, 10) || 0;
        await new Promise(resolve => setTimeout(resolve, delayMs));
    }

    // Simulate error status code if requested (?status=500)
    if (query.status) {
        const statusCode = parseInt(query.status, 10) || 500;
        res.writeHead(statusCode, {
            'Content-Type': 'application/json',
            'X-Served-By': INSTANCE_ID
        });
        res.end(JSON.stringify({
            error: true,
            status: statusCode,
            instance: INSTANCE_ID,
            timestamp: new Date().toISOString()
        }));
        return;
    }

    if (pathname === '/health' || pathname === '/healthz') {
        res.writeHead(200, { 'Content-Type': 'application/json', 'X-Served-By': INSTANCE_ID });
        res.end(JSON.stringify({
            status: 'UP',
            instance: INSTANCE_ID,
            port: PORT,
            uptime: process.uptime()
        }));
        return;
    }

    // Default API handler
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
        res.writeHead(200, {
            'Content-Type': 'application/json',
            'X-Served-By': INSTANCE_ID,
            'X-Instance-Port': PORT
        });
        res.end(JSON.stringify({
            message: `Response from upstream server instance: ${INSTANCE_ID}`,
            instance: INSTANCE_ID,
            port: PORT,
            path: pathname,
            method: req.method,
            headers: req.headers,
            query: query,
            body: body ? (tryParseJson(body) || body) : null,
            timestamp: new Date().toISOString()
        }, null, 2));
    });
});

function tryParseJson(str) {
    try {
        return JSON.parse(str);
    } catch (e) {
        return null;
    }
}

server.listen(PORT, () => {
    console.log(`[${INSTANCE_ID}] Mock Upstream Server running on port ${PORT}`);
});
