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
class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export { ApiError };

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
   * Send a message in a conversation (NON-STREAMING)
   * This is the original endpoint, kept for compatibility
   */
  send: (conversationId: string, data: SendMessageRequest): Promise<SendMessageResponse> => {
    return fetchJSON<SendMessageResponse>(`/conversations/${conversationId}/messages`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  /**
   * Send a message and receive streaming response (STREAMING VERSION)
   *
   * How SSE (Server-Sent Events) works in the browser:
   * 1. We make a POST request to the streaming endpoint
   * 2. Backend keeps the connection open
   * 3. Backend sends events in this format:
   *    event: chunk
   *    data: Hello
   *
   *    event: chunk
   *    data: world
   *
   *    event: done
   *    data: {...}
   *
   *    event: approval_required
   *    data: {...}
   *
   * 4. We listen for events and call callbacks
   * 5. When we receive "done", we close the connection
   *
   * Note: We can't use EventSource for POST requests, so we use fetch
   * with a streaming response body instead.
   *
   * @param conversationId - The conversation to send to
   * @param data - Message content
   * @param onChunk - Called for each chunk of text
   * @param onDone - Called when streaming completes with the complete message
   * @param onError - Called if an error occurs
   * @param onApprovalRequired - Called when approval is needed
   * @returns A function to cancel the stream
   */
  sendStream: (
    conversationId: string,
    data: SendMessageRequest,
    onChunk: (chunk: string) => void,
    onDone: (message: Message) => void,
    onError: (error: string) => void,
    onApprovalRequired?: (approvalData: any) => void
  ): (() => void) => {
    console.log('Starting streaming request to:', `${API_BASE_URL}/conversations/${conversationId}/messages/stream`);

    // AbortController allows us to cancel the request
    const abortController = new AbortController();

    // Start the streaming request
    fetch(`${API_BASE_URL}/conversations/${conversationId}/messages/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
      signal: abortController.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        // Get the response body as a stream
        const reader = response.body?.getReader();
        if (!reader) {
          throw new Error('Response body is not readable');
        }

        const decoder = new TextDecoder();
        let buffer = '';
        let currentEvent = '';
        let currentData = '';

        // Read the stream chunk by chunk
        while (true) {
          const { done, value } = await reader.read();

          if (done) {
            console.log('Stream completed');
            break;
          }

          // Decode the chunk and add to buffer
          buffer += decoder.decode(value, { stream: true });

          // Process complete lines from the buffer
          let newlineIndex;
          while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
            const line = buffer.substring(0, newlineIndex);
            buffer = buffer.substring(newlineIndex + 1);

            // Process this line
            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              // Extract data after "data:" (5 chars) - keep the space after colon
              currentData = line.substring(5);
            } else if (line.trim() === '') {
              // Empty line marks end of SSE event - process it now
              if (currentEvent && currentData) {
                console.log(`Received event: ${currentEvent}`, currentData.substring(0, 50));

                if (currentEvent === 'chunk') {
                  // Text chunk from OpenAI
                  console.log('Chunk received:', JSON.stringify(currentData));
                  onChunk(currentData);
                } else if (currentEvent === 'done') {
                  // Streaming complete, parse the final message
                  try {
                    const message = JSON.parse(currentData) as Message;
                    console.log('Stream done, received complete message:', message.id);
                    onDone(message);
                  } catch (e) {
                    console.error('Error parsing done event:', e);
                    onError('Failed to parse completion message');
                  }
                } else if (currentEvent === 'approval_required') {
                  // Agent needs human approval
                  try {
                    const approvalData = JSON.parse(currentData);
                    console.log('Approval required:', approvalData);
                    if (onApprovalRequired) {
                      onApprovalRequired(approvalData);
                    }
                  } catch (e) {
                    console.error('Error parsing approval event:', e);
                    onError('Failed to parse approval request');
                  }
                } else if (currentEvent === 'error') {
                  // Error from backend
                  console.error('Received error event:', currentData);
                  onError(currentData);
                }

                // Reset for next event
                currentEvent = '';
                currentData = '';
              }
            }
          }
        }
      })
      .catch((error) => {
        if (error.name === 'AbortError') {
          console.log('Stream aborted by user');
        } else {
          console.error('Stream error:', error);
          onError(error.message || 'Failed to stream response');
        }
      });

    // Return a cancel function
    return () => {
      console.log('Aborting stream');
      abortController.abort();
    };
  },
};

// Approval API endpoints
export const approvalsApi = {
  /**
   * Get pending approvals for a user
   */
  getPending: (userId: number): Promise<any[]> => {
    return fetchJSON<any[]>(`/approvals?userId=${userId}`);
  },

  /**
   * Approve an approval request and receive streaming response
   *
   * @param approvalId - The approval request ID
   * @param userId - The user ID
   * @param onChunk - Called for each chunk of text
   * @param onDone - Called when execution completes
   * @param onError - Called if an error occurs
   * @returns A function to cancel the stream
   */
  approve: (
    approvalId: string,
    userId: number,
    onChunk: (chunk: string) => void,
    onDone: () => void,
    onError: (error: string) => void
  ): (() => void) => {
    console.log('Approving request:', approvalId);

    const abortController = new AbortController();

    fetch(`${API_BASE_URL}/approvals/${approvalId}/approve`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ userId }),
      signal: abortController.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const reader = response.body?.getReader();
        if (!reader) {
          throw new Error('Response body is not readable');
        }

        const decoder = new TextDecoder();
        let buffer = '';
        let currentEvent = '';
        let currentData = '';

        while (true) {
          const { done, value } = await reader.read();

          if (done) {
            console.log('Approval stream completed');
            break;
          }

          buffer += decoder.decode(value, { stream: true });

          let newlineIndex;
          while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
            const line = buffer.substring(0, newlineIndex);
            buffer = buffer.substring(newlineIndex + 1);

            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              currentData = line.substring(5);
            } else if (line.trim() === '') {
              if (currentEvent && currentData) {
                console.log(`Approval event: ${currentEvent}`);

                if (currentEvent === 'chunk') {
                  console.log('Approval chunk received:', currentData);
                  onChunk(currentData);
                } else if (currentEvent === 'done') {
                  console.log('Approval execution complete');
                  onDone();
                } else if (currentEvent === 'error') {
                  console.error('Approval error:', currentData);
                  onError(currentData);
                }

                currentEvent = '';
                currentData = '';
              }
            }
          }
        }
      })
      .catch((error) => {
        if (error.name === 'AbortError') {
          console.log('Approval stream aborted');
        } else {
          console.error('Approval error:', error);
          onError(error.message || 'Failed to process approval');
        }
      });

    return () => {
      console.log('Aborting approval stream');
      abortController.abort();
    };
  },

  /**
   * Reject an approval request and receive streaming response
   *
   * @param approvalId - The approval request ID
   * @param userId - The user ID
   * @param onChunk - Called for each chunk of text
   * @param onDone - Called when rejection message completes
   * @param onError - Called if an error occurs
   * @returns A function to cancel the stream
   */
  reject: (
    approvalId: string,
    userId: number,
    onChunk: (chunk: string) => void,
    onDone: () => void,
    onError: (error: string) => void
  ): (() => void) => {
    console.log('Rejecting request:', approvalId);

    const abortController = new AbortController();

    fetch(`${API_BASE_URL}/approvals/${approvalId}/reject`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ userId }),
      signal: abortController.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const reader = response.body?.getReader();
        if (!reader) {
          throw new Error('Response body is not readable');
        }

        const decoder = new TextDecoder();
        let buffer = '';
        let currentEvent = '';
        let currentData = '';

        while (true) {
          const { done, value } = await reader.read();

          if (done) {
            console.log('Rejection stream completed');
            break;
          }

          buffer += decoder.decode(value, { stream: true });

          let newlineIndex;
          while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
            const line = buffer.substring(0, newlineIndex);
            buffer = buffer.substring(newlineIndex + 1);

            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              currentData = line.substring(5);
            } else if (line.trim() === '') {
              if (currentEvent && currentData) {
                console.log(`Rejection event: ${currentEvent}`);

                if (currentEvent === 'chunk') {
                  onChunk(currentData);
                } else if (currentEvent === 'done') {
                  console.log('Rejection complete');
                  onDone();
                } else if (currentEvent === 'error') {
                  console.error('Rejection error:', currentData);
                  onError(currentData);
                }

                currentEvent = '';
                currentData = '';
              }
            }
          }
        }
      })
      .catch((error) => {
        if (error.name === 'AbortError') {
          console.log('Rejection stream aborted');
        } else {
          console.error('Rejection error:', error);
          onError(error.message || 'Failed to process rejection');
        }
      });

    return () => {
      console.log('Aborting rejection stream');
      abortController.abort();
    };
  },
};
