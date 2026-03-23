const nacl = require('tweetnacl');
const naclUtil = require('tweetnacl-util');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const KEYS_DIR = path.join(__dirname, 'keys');

/**
 * Ed25519 key management for the SMS relay server.
 * - Auto-generates server keypair on first start
 * - Signs outbound messages (SMS commands to phone)
 * - Verifies inbound messages (receipts from phone)
 * - Stores/loads phone public key after pairing
 */

function ensureKeysDir() {
  if (!fs.existsSync(KEYS_DIR)) fs.mkdirSync(KEYS_DIR, { recursive: true });
}

function getServerKeyPair() {
  ensureKeysDir();
  const secretPath = path.join(KEYS_DIR, 'server-secret.key');
  const publicPath = path.join(KEYS_DIR, 'server-public.key');

  if (fs.existsSync(secretPath) && fs.existsSync(publicPath)) {
    const secretKey = naclUtil.decodeBase64(fs.readFileSync(secretPath, 'utf8').trim());
    const publicKey = naclUtil.decodeBase64(fs.readFileSync(publicPath, 'utf8').trim());
    return { publicKey, secretKey };
  }

  // Generate new keypair
  const keyPair = nacl.sign.keyPair();
  fs.writeFileSync(secretPath, naclUtil.encodeBase64(keyPair.secretKey), 'utf8');
  fs.writeFileSync(publicPath, naclUtil.encodeBase64(keyPair.publicKey), 'utf8');
  fs.chmodSync(secretPath, 0o600);
  console.log('[crypto] Generated new Ed25519 server keypair');
  return keyPair;
}

function getServerPublicKeyBase64() {
  const kp = getServerKeyPair();
  return naclUtil.encodeBase64(kp.publicKey);
}

function signMessage(messageObj) {
  const kp = getServerKeyPair();
  const nonce = generateNonce();
  const payload = { ...messageObj, nonce };
  const payloadBytes = naclUtil.decodeUTF8(JSON.stringify(payload));
  const signature = nacl.sign.detached(payloadBytes, kp.secretKey);
  return {
    payload,
    signature: naclUtil.encodeBase64(signature),
  };
}

function verifyMessage(payloadStr, signatureBase64, publicKeyBase64) {
  try {
    const payloadBytes = naclUtil.decodeUTF8(payloadStr);
    const signature = naclUtil.decodeBase64(signatureBase64);
    const publicKey = naclUtil.decodeBase64(publicKeyBase64);
    return nacl.sign.detached.verify(payloadBytes, signature, publicKey);
  } catch {
    return false;
  }
}

function generateNonce() {
  return `${Date.now()}-${crypto.randomBytes(8).toString('hex')}`;
}

// Phone public key management
function getPhonePublicKey() {
  const phonePubPath = path.join(KEYS_DIR, 'phone-public.key');
  if (!fs.existsSync(phonePubPath)) return null;
  return fs.readFileSync(phonePubPath, 'utf8').trim();
}

function setPhonePublicKey(base64Key) {
  ensureKeysDir();
  const phonePubPath = path.join(KEYS_DIR, 'phone-public.key');
  // Validate it's a valid base64 Ed25519 public key (32 bytes)
  try {
    const decoded = naclUtil.decodeBase64(base64Key);
    if (decoded.length !== 32) throw new Error('Invalid key length');
  } catch (e) {
    throw new Error(`Invalid Ed25519 public key: ${e.message}`);
  }
  fs.writeFileSync(phonePubPath, base64Key, 'utf8');
  console.log('[crypto] Stored phone public key');
}

function isPhonePaired() {
  return getPhonePublicKey() !== null;
}

function getKeyFingerprint(base64Key) {
  const hash = crypto.createHash('sha256').update(base64Key).digest('hex');
  return hash.substring(0, 16).match(/.{4}/g).join(':');
}

module.exports = {
  getServerKeyPair,
  getServerPublicKeyBase64,
  signMessage,
  verifyMessage,
  generateNonce,
  getPhonePublicKey,
  setPhonePublicKey,
  isPhonePaired,
  getKeyFingerprint,
};
