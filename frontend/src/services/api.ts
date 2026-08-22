import type {
  Conversation,
  Message,
  CreateConversationRequest,
  SendMessageRequest,
  SendMessageResponse,
} from '../types';

// API base URL from environment variable, fallback to localhost:8081
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081/api';

// Custom error class for API errors
export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

// Generic fetch wrapper with error handling
async function fetchJSON<T>(url: string, options?: RequestInit): Promise<T> {
  try {
    const response = await fetch(`${API_BASE_URL}${url}`, {
      headers: {
        'Content-Type': 'application/json',
        ...options?.headers,
      },
      ...options,
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new ApiError(
        response.status,
        errorText || `HTTP ${response.status}: ${response.statusText}`
      );
    }

    // Handle empty responses (like DELETE)
    const contentType = response.headers.get('content-type');
    if (!contentType || !contentType.includes('application/json')) {
      return undefined as T;
    }

    return response.json();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    // Network errors, etc.
    throw new ApiError(0, error instanceof Error ? error.message : 'Network error');
  }
}

// Conversation API endpoints
export const conversationsApi = {
  /**
   * Get all conversations
   */
  getAll: (): Promise<Conversation[]> => {
    return fetchJSON<Conversation[]>('/conversations');
  },

  /**
   * Get a single conversation by ID
   */
  getById: (id: string): Promise<Conversation> => {
    return fetchJSON<Conversation>(`/conversations/${id}`);
  },

  /**
   * Create a new conversation
   */
  create: (data: CreateConversationRequest = {}): Promise<Conversation> => {
    return fetchJSON<Conversation>('/conversations', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  /**
   * Update a conversation's title
   */
  update: (id: string, data: { title: string }): Promise<Conversation> => {
    return fetchJSON<Conversation>(`/conversations/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  /**
   * Delete a conversation
   */
  delete: (id: string): Promise<void> => {
    return fetchJSON<void>(`/conversations/${id}`, {
      method: 'DELETE',
    });
  },
};

// Message API endpoints
export const messagesApi = {
  /**
   * Get all messages for a conversation
   */
  getByConversationId: (conversationId: string): Promise<Message[]> => {
    return fetchJSON<Message[]>(`/conversations/${conversationId}/messages`);
  },

  /**
   * Send a message in a conversation
   */
  send: (conversationId: string, data: SendMessageRequest): Promise<SendMessageResponse> => {
    return fetchJSON<SendMessageResponse>(`/conversations/${conversationId}/messages`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },
};
