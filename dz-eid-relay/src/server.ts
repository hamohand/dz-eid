import express from 'express';
import http from 'http';
import { Server, Socket } from 'socket.io';
import cors from 'cors';
import crypto from 'crypto';

const app = express();
app.use(cors());

const server = http.createServer(app);

// Initialize Socket.IO with CORS enabled for any frontend
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST']
  }
});

app.use(express.json());

app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'dz-eid-relay' });
});

// A simple dictionary to track active sessions for logging/metrics
const activeSessions = new Set<string>();

app.post('/api/send_card_data', (req, res) => {
  const { sessionId, encryptedData } = req.body;
  
  if (!sessionId || !encryptedData) {
    res.status(400).json({ error: 'Missing sessionId or encryptedData' });
    return;
  }

  if (!activeSessions.has(sessionId)) {
    res.status(404).json({ error: 'Session not found or expired' });
    return;
  }

  console.log(`[Data] Forwarding encrypted card data via HTTP to session: ${sessionId}`);
  io.to(sessionId).emit('card_data_received', encryptedData);
  res.json({ success: true });
});

io.on('connection', (socket: Socket) => {
  console.log(`[+] Client connected: ${socket.id}`);

  // 1. Browser requests a new session
  socket.on('request_session', () => {
    // Generate a unique session ID
    const sessionId = crypto.randomUUID();
    
    // Join a room with this ID
    socket.join(sessionId);
    activeSessions.add(sessionId);

    console.log(`[Session] Created: ${sessionId} for ${socket.id}`);
    
    // Send it back to the browser
    socket.emit('session_created', { sessionId });
  });

  // 2. Mobile app sends the encrypted IdentityRecord to a specific session
  // payload should contain: { sessionId: string, encryptedData: any }
  socket.on('send_card_data', (payload) => {
    const { sessionId, encryptedData } = payload;
    
    if (!sessionId || !encryptedData) {
      console.warn(`[-] Invalid payload from ${socket.id}`);
      return;
    }

    if (!activeSessions.has(sessionId)) {
      console.warn(`[-] Session not found or expired: ${sessionId}`);
      // Notify mobile app that the session is invalid
      socket.emit('delivery_failed', { error: 'SESSION_NOT_FOUND' });
      return;
    }

    console.log(`[Data] Forwarding encrypted card data to session: ${sessionId}`);
    
    // Forward the data to the browser waiting in that room
    io.to(sessionId).emit('card_data_received', encryptedData);
    
    // Acknowledge receipt to the mobile app
    socket.emit('delivery_success');
  });

  socket.on('disconnect', () => {
    console.log(`[-] Client disconnected: ${socket.id}`);
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`🚀 dz-eid-relay is running on http://localhost:${PORT}`);
});
