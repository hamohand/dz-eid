import { io, Socket } from 'socket.io-client';
import * as QRCode from 'qrcode';

export interface CardDataResponse {
  mobPubKeyB64: string;
  ivB64: string;
  ciphertextB64: string;
}

export class DzEidSession {
  private socket: Socket;
  private keyPair: CryptoKeyPair | null = null;
  public sessionId: string | null = null;

  constructor(private relayUrl: string) {
    this.socket = io(this.relayUrl, {
      autoConnect: false,
    });
  }

  /**
   * Initializes the session, generates crypto keys, and connects to the relay.
   * Returns the data URI for the QR Code.
   */
  public async initSessionAndGetQrCode(): Promise<string> {
    // 1. Generate ECDH Key Pair (P-256)
    this.keyPair = await crypto.subtle.generateKey(
      { name: 'ECDH', namedCurve: 'P-256' },
      true,
      ['deriveBits']
    );

    // Export the public key to 'raw' format (65 bytes)
    const pubKeyRaw = await crypto.subtle.exportKey('raw', this.keyPair.publicKey);
    const pubKeyB64 = this.arrayBufferToBase64(pubKeyRaw);

    // 2. Connect to the relay and get a session ID
    return new Promise((resolve, reject) => {
      this.socket.connect();
      
      this.socket.on('connect', () => {
        this.socket.emit('request_session');
      });

      this.socket.once('session_created', async (data: { sessionId: string }) => {
        this.sessionId = data.sessionId;

        // 3. Construct the payload for the mobile app
        const qrPayload = JSON.stringify({
          u: this.relayUrl,
          s: this.sessionId,
          k: pubKeyB64
        });

        // 4. Generate the QR Code SVG or Data URL
        try {
          const qrDataUrl = await QRCode.toDataURL(qrPayload, { width: 300, margin: 2 });
          resolve(qrDataUrl);
        } catch (err) {
          reject(err);
        }
      });

      this.socket.on('connect_error', (err) => reject(err));
    });
  }

  /**
   * Waits for the mobile app to scan, read, and send the encrypted card data.
   */
  public async waitForCardData(): Promise<any> {
    return new Promise((resolve, reject) => {
      if (!this.sessionId || !this.keyPair) {
        return reject(new Error('Session not initialized. Call initSessionAndGetQrCode first.'));
      }

      this.socket.once('card_data_received', async (encryptedPayload: CardDataResponse) => {
        try {
          const record = await this.decryptPayload(encryptedPayload);
          this.socket.disconnect(); // Clean up
          resolve(record);
        } catch (err) {
          reject(err);
        }
      });
    });
  }

  private async decryptPayload(payload: CardDataResponse): Promise<any> {
    if (!this.keyPair) throw new Error("Missing keys");

    // 1. Import Mobile Public Key
    const mobPubKeyRaw = this.base64ToArrayBuffer(payload.mobPubKeyB64);
    const mobPubKey = await crypto.subtle.importKey(
      'raw',
      mobPubKeyRaw,
      { name: 'ECDH', namedCurve: 'P-256' },
      true,
      []
    );

    // 2. Derive Shared Secret (ECDH)
    const sharedSecret = await crypto.subtle.deriveBits(
      { name: 'ECDH', public: mobPubKey },
      this.keyPair.privateKey,
      256
    );

    // 3. Hash the shared secret with SHA-256 to get a clean 32-byte AES key
    const aesKeyMaterial = await crypto.subtle.digest('SHA-256', sharedSecret);
    const aesKey = await crypto.subtle.importKey(
      'raw',
      aesKeyMaterial,
      { name: 'AES-GCM' },
      false,
      ['decrypt']
    );

    // 4. Decrypt the ciphertext
    const iv = this.base64ToArrayBuffer(payload.ivB64);
    const ciphertext = this.base64ToArrayBuffer(payload.ciphertextB64);

    const decryptedBuffer = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: new Uint8Array(iv) },
      aesKey,
      ciphertext
    );

    // 5. Parse JSON
    const textDecoder = new TextDecoder();
    const jsonStr = textDecoder.decode(decryptedBuffer);
    return JSON.parse(jsonStr);
  }

  // --- Helpers ---
  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }
}
