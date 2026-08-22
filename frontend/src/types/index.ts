// TypeScript interfaces matching backend DTOs

export interface Conversation {
  id: string;
  title: string;
  createdAt: string;  // ISO 8601 timestamp
  updatedAt: string;  // ISO 8601 timestamp
}

export enum MessageRole {
  USER = 'USER',
  ASSISTANT = 'ASSISTANT'
}

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
}

export interface SendMessageRequest {
  content: string;
}

// Backend only returns the assistant message
// The user message is already saved in the database
export type SendMessageResponse = Message;

// UI State types
export interface ApiError {
  status: number;
  message: string;
}
