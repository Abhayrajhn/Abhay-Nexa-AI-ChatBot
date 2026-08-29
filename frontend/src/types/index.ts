// TypeScript interfaces matching backend DTOs

export interface Conversation {
  id: string;
  title: string;
  createdAt: string;  // ISO 8601 timestamp
  updatedAt: string;  // ISO 8601 timestamp
}

export type MessageRole = 'USER' | 'ASSISTANT';

export const MessageRole = {
  USER: 'USER' as MessageRole,
  ASSISTANT: 'ASSISTANT' as MessageRole,
};

export interface Message {
  id: string;
  conversationId: string;
  role: MessageRole;
  content: string;
  createdAt: string;  // ISO 8601 timestamp
}

// API Request/Response types
export interface CreateConversationRequest {
  title?: string;
  userId?: number;  // Optional for now (will be required when auth is implemented)
}

export interface SendMessageRequest {
  content: string;
  userId?: number;  // Optional for now (will be required when auth is implemented)
}

// Backend only returns the assistant message
// The user message is already saved in the database
export type SendMessageResponse = Message;

// UI State types
export interface ApiError {
  status: number;
  message: string;
}
