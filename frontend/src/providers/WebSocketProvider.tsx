import React, { createContext, useContext, useEffect, useState, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import { toast } from 'sonner';

interface WebSocketContextType {
  isConnected: boolean;
  subscribe: (topic: string, callback: (message: any) => void) => () => void;
  publish: (destination: string, body: any) => void;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(undefined);

export function WebSocketProvider({ children }: { children: React.ReactNode }) {
  const [isConnected, setIsConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, Set<(message: any) => void>>>(new Map());

  useEffect(() => {
    const getWsUrl = (): string => {
      if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL;
      if (typeof window !== 'undefined') {
        const host = window.location.hostname;
        if (
          host === 'localhost' ||
          host === '127.0.0.1' ||
          /^192\.168\./.test(host) ||
          /^10\./.test(host) ||
          /^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(host) ||
          window.location.protocol === 'http:'
        ) {
          const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
          return `${proto}//${host}:8080/ws`;
        }
      }
      return 'wss://aegisx-backend-2k67.onrender.com/ws';
    };
    const rawWsUrl = getWsUrl();
    const wsUrl = rawWsUrl.endsWith('/ws')
      ? rawWsUrl
      : `${rawWsUrl.replace(/\/+$/, '')}/ws`;
    
    const client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 15000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setIsConnected(true);
        console.log('Connected to ASTRA STOMP Broker');
        
        // Re-subscribe to all active topics
        subscriptionsRef.current.forEach((callbacks, topic) => {
          client.subscribe(topic, (message) => {
            const body = JSON.parse(message.body);
            callbacks.forEach(cb => cb(body));
          });
        });
      },
      onDisconnect: () => {
        setIsConnected(false);
      },
      onWebSocketError: () => {
        // Silently handle offline WebSocket status when backend is not running
        setIsConnected(false);
      },
      onStompError: (frame) => {
        console.warn('STOMP Offline/Error:', frame.headers?.message || 'Broker unavailable');
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      client.deactivate();
    };
  }, []);

  const subscribe = (topic: string, callback: (message: any) => void) => {
    const topicStr = `/topic/${topic}`;
    
    if (!subscriptionsRef.current.has(topicStr)) {
      subscriptionsRef.current.set(topicStr, new Set());
      
      if (clientRef.current?.connected) {
        clientRef.current.subscribe(topicStr, (message) => {
          const body = JSON.parse(message.body);
          const cbs = subscriptionsRef.current.get(topicStr);
          cbs?.forEach(cb => cb(body));
        });
      }
    }
    
    subscriptionsRef.current.get(topicStr)?.add(callback);

    return () => {
      subscriptionsRef.current.get(topicStr)?.delete(callback);
    };
  };

  const publish = (destination: string, body: any) => {
    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: `/app/${destination}`,
        body: JSON.stringify(body)
      });
    } else {
      console.warn('Cannot publish, WebSocket not connected');
    }
  };

  return (
    <WebSocketContext.Provider value={{ isConnected, subscribe, publish }}>
      {children}
    </WebSocketContext.Provider>
  );
}

export const useWebSocket = () => {
  const context = useContext(WebSocketContext);
  if (context === undefined) {
    throw new Error('useWebSocket must be used within a WebSocketProvider');
  }
  return context;
};
