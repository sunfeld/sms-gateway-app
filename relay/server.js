const express = require('express');
const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');
const { v4: uuidv4 } = require('uuid');
const crypto = require('./crypto');

const PORT = process.env.PORT || 3100;
const API_KEY = process.env.SMS_RELAY_API_KEY || null; // Optional API key for internal callers

const app = express();
app.use(express.json());

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

// Track connected phone
let phoneSocket = null;
let phoneConnectedAt = null;
const pendingCommands = new Map(); // id -> { resolve, reject, timeout }

// ---- WebSocket: phone connection ----

wss.on('connection', (ws, req) => {
  console.log(`[ws] Phone connected from ${req.socket.remoteAddress}`);

  // Authenticate: phone must send its public key + signature within 10s
  let authenticated = false;
  const authTimeout = setTimeout(() => {
    if (!authenticated) {
      ws.close(4001, 'Authentication timeout');
    }
  }, 10000);

  ws.on('message', (data) => {
    let msg;
    try { msg = JSON.parse(data.toString()); } catch { return; }

    if (!authenticated) {
      // First message must be auth handshake
      if (msg.type === 'auth') {
        const phonePubKey = crypto.getPhonePublicKey();
        if (!phonePubKey) {
          ws.close(4003, 'Phone not paired — register first via /api/register-phone');
          return;
        }
        // Verify the auth challenge signature
        const valid = crypto.verifyMessage(
          JSON.stringify(msg.challenge),
          msg.signature,
          phonePubKey
        );
        if (!valid) {
          ws.close(4002, 'Invalid signature');
          return;
        }
        authenticated = true;
        clearTimeout(authTimeout);
        phoneSocket = ws;
        phoneConnectedAt = new Date().toISOString();
        ws.send(JSON.stringify({ type: 'auth_ok', serverTime: Date.now() }));
        console.log('[ws] Phone authenticated successfully');
        return;
      }
      ws.close(4001, 'Expected auth message');
      return;
    }

    // Handle authenticated messages
    switch (msg.type) {
      case 'sms_receipt': {
        const pending = pendingCommands.get(msg.commandId);
        if (pending) {
          // Verify phone signature on receipt
          const phonePubKey = crypto.getPhonePublicKey();
          const receiptPayload = JSON.stringify({ commandId: msg.commandId, status: msg.status, detail: msg.detail });
          const valid = phonePubKey && crypto.verifyMessage(receiptPayload, msg.signature, phonePubKey);
          if (valid) {
            pending.resolve({ status: msg.status, detail: msg.detail });
          } else {
            pending.reject(new Error('Invalid receipt signature'));
          }
          clearTimeout(pending.timeout);
          pendingCommands.delete(msg.commandId);
        }
        break;
      }
      case 'ping':
        ws.send(JSON.stringify({ type: 'pong', ts: Date.now() }));
        break;
    }
  });

  ws.on('close', () => {
    if (ws === phoneSocket) {
      phoneSocket = null;
      phoneConnectedAt = null;
      console.log('[ws] Phone disconnected');
    }
    clearTimeout(authTimeout);
  });

  ws.on('error', (err) => {
    console.error('[ws] Error:', err.message);
  });
});

// ---- REST API: internal systems ----

// Health check
app.get('/api/status', (req, res) => {
  res.json({
    ok: true,
    phoneConnected: phoneSocket?.readyState === WebSocket.OPEN,
    phoneConnectedAt,
    phonePaired: crypto.isPhonePaired(),
    serverFingerprint: crypto.getKeyFingerprint(crypto.getServerPublicKeyBase64()),
  });
});

// Receive crash reports from the phone app
app.post('/api/crash-report', (req, res) => {
  const { device, sdk, log, timestamp } = req.body;
  const fs = require('fs');
  const path = require('path');
  const crashFile = path.join(__dirname, 'keys', 'crash-reports.log');
  const entry = `\n=== CRASH REPORT ${timestamp || new Date().toISOString()} ===\nDevice: ${device || 'unknown'} SDK: ${sdk || '?'}\n${log || 'no log'}\n`;
  fs.appendFileSync(crashFile, entry);
  console.log(`[crash-report] Received from ${device} SDK ${sdk}`);
  res.json({ ok: true });
});

// Get crash reports (for debugging)
app.get('/api/crash-reports', (req, res) => {
  const fs = require('fs');
  const path = require('path');
  const crashFile = path.join(__dirname, 'keys', 'crash-reports.log');
  try {
    const content = fs.readFileSync(crashFile, 'utf8');
    res.type('text/plain').send(content);
  } catch {
    res.type('text/plain').send('No crash reports yet');
  }
});

// Get server public key (for phone pairing)
app.get('/api/server-pubkey', (req, res) => {
  res.json({
    publicKey: crypto.getServerPublicKeyBase64(),
    fingerprint: crypto.getKeyFingerprint(crypto.getServerPublicKeyBase64()),
  });
});

// Register phone public key (pairing ceremony)
app.post('/api/register-phone', (req, res) => {
  const { publicKey } = req.body;
  if (!publicKey) {
    return res.status(400).json({ error: 'publicKey required' });
  }
  try {
    crypto.setPhonePublicKey(publicKey);
    res.json({
      ok: true,
      phoneFingerprint: crypto.getKeyFingerprint(publicKey),
      serverPublicKey: crypto.getServerPublicKeyBase64(),
      serverFingerprint: crypto.getKeyFingerprint(crypto.getServerPublicKeyBase64()),
    });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// Send SMS command (called by internal systems)
app.post('/api/send', (req, res) => {
  // Optional API key check
  if (API_KEY) {
    const authHeader = req.headers.authorization;
    if (authHeader !== `Bearer ${API_KEY}`) {
      return res.status(401).json({ error: 'Invalid API key' });
    }
  }

  const { to, message } = req.body;
  if (!to || !message) {
    return res.status(400).json({ error: 'to and message required' });
  }

  if (!crypto.isPhonePaired()) {
    return res.status(503).json({ error: 'Phone not paired — use /api/register-phone first' });
  }

  if (!phoneSocket || phoneSocket.readyState !== WebSocket.OPEN) {
    return res.status(503).json({ error: 'Phone not connected' });
  }

  const commandId = uuidv4();
  const signed = crypto.signMessage({ commandId, to, message, type: 'send_sms' });

  // Send signed command to phone via WebSocket
  phoneSocket.send(JSON.stringify({
    type: 'send_sms',
    commandId,
    payload: signed.payload,
    signature: signed.signature,
  }));

  // Wait for receipt (30s timeout)
  const receiptPromise = new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      pendingCommands.delete(commandId);
      reject(new Error('Timeout waiting for SMS receipt'));
    }, 30000);
    pendingCommands.set(commandId, { resolve, reject, timeout });
  });

  receiptPromise
    .then((receipt) => {
      res.json({ ok: true, commandId, ...receipt });
    })
    .catch((err) => {
      res.status(504).json({ error: err.message, commandId });
    });
});

// Rotate server keys (generates new keypair, requires re-pairing)
app.post('/api/rotate-keys', (req, res) => {
  // Optional API key check
  if (API_KEY) {
    const authHeader = req.headers.authorization;
    if (authHeader !== `Bearer ${API_KEY}`) {
      return res.status(401).json({ error: 'Invalid API key' });
    }
  }

  const fs = require('fs');
  const path = require('path');
  const keysDir = path.join(__dirname, 'keys');

  // Delete existing keys
  ['server-secret.key', 'server-public.key', 'phone-public.key'].forEach((f) => {
    const fp = path.join(keysDir, f);
    if (fs.existsSync(fp)) fs.unlinkSync(fp);
  });

  // Generate new keypair
  const newPubKey = crypto.getServerPublicKeyBase64();

  // Disconnect phone (must re-pair)
  if (phoneSocket) {
    phoneSocket.close(4010, 'Server keys rotated — re-pair required');
  }

  res.json({
    ok: true,
    newServerPublicKey: newPubKey,
    newFingerprint: crypto.getKeyFingerprint(newPubKey),
    message: 'Keys rotated. Phone must re-pair.',
  });
});

// ---- Start ----

server.listen(PORT, () => {
  // Ensure keypair exists on startup
  crypto.getServerKeyPair();
  console.log(`[sms-relay] Listening on port ${PORT}`);
  console.log(`[sms-relay] Server fingerprint: ${crypto.getKeyFingerprint(crypto.getServerPublicKeyBase64())}`);
  console.log(`[sms-relay] Phone paired: ${crypto.isPhonePaired()}`);
});
