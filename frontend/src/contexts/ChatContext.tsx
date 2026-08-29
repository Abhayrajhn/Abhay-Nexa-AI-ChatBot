import React, { createContext, useContext, useState, useCallback, useRef } from 'react';
import { conversationsApi, messagesApi } from '../services/api';
import type { Conversation, Message, MessageRole } from '../types';

/**
 * ChatContext - Global State Management
 *
 * Purpose: Centralized state for conversations and messages
 *
 * Why we need this:
 * - Multiple components need access to conversations (Sidebar, ChatWindow)
 * - Components need to trigger actions (create, select, send message)
 * - Avoid "prop drilling" (passing props through many levels)
 * - Keep backend as single source of truth
 *
 * Pattern: Context API + Custom Hook
 * - Context provides state to entire component tree
 * - Custom hook (useChatContext) makes it easy to consume
 *
 * STREAMING:
 * - Added streamingMessage state for partial AI responses
 * - Added isStreaming flag to disable input during streaming
 * - sendMessage() now uses streaming by default
 */

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

interface ChatContextType {
  // ---- State ----
  conversations: Conversation[];
  selectedConversationId: string | null;
  messages: Message[];
  loadingConversations: boolean;
  loadingMessages: boolean;
  sendingMessage: boolean;
  isStreaming: boolean;  // NEW: True while streaming AI response
  streamingMessage: string;  // NEW: Accumulated chunks of streaming response
  error: string | null;

  // ---- Actions ----
  loadConversations: () => Promise<void>;
  selectConversation: (id: string) => Promise<void>;
  createConversation: (title?: string) => Promise<void>;
  deleteConversation: (id: string) => Promise<void>;
  sendMessage: (content: string) => Promise<void>;
  clearError: () => void;
}

// ============================================================================
// CREATE CONTEXT
// ============================================================================

/**
 * Create the context with undefined as default
 * We'll provide the actual value in the Provider component
 */
const ChatContext = createContext<ChatContextType | undefined>(undefined);

// ============================================================================
// PROVIDER COMPONENT
// ============================================================================

/**
 * ChatProvider Component
 *
 * Wraps the entire app and provides state to all components
 * This is the "controller" that manages all the state and logic
 */
export function ChatProvider({ children }: { children: React.ReactNode }) {
  // ---- State Variables ----

  // All conversations from backend
  const [conversations, setConversations] = useState<Conversation[]>([]);

  // Currently selected conversation ID
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null);

  // Messages for the selected conversation
  const [messages, setMessages] = useState<Message[]>([]);

  // Loading states (different loading indicators for different operations)
  const [loadingConversations, setLoadingConversations] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sendingMessage, setSendingMessage] = useState(false);

  // Streaming states (NEW)
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamingMessage, setStreamingMessage] = useState('');

  // Error message to show to user
  const [error, setError] = useState<string | null>(null);

  // Ref to store cancel function for streaming
  const cancelStreamRef = useRef<(() => void) | null>(null);

  // ---- Actions ----

  /**
   * Load all conversations from backend
   *
   * Called when:
   * - App first loads
   * - After creating a new conversation
   * - After deleting a conversation
   */
  const loadConversations = useCallback(async () => {
    try {
      setLoadingConversations(true);
      setError(null);

      console.log('Loading conversations...');
      const data = await conversationsApi.getAll();

      setConversations(data);
      console.log(`Loaded ${data.length} conversations`);
    } catch (err) {
      console.error('Error loading conversations:', err);
      setError('Failed to load conversations. Please try again.');
    } finally {
      setLoadingConversations(false);
    }
  }, []);

  /**
   * Select a conversation and load its messages
   *
   * Called when:
   * - User clicks a conversation in the sidebar
   * - After creating a new conversation (auto-select)
   *
   * @param id - Conversation ID to select
   */
  const selectConversation = useCallback(async (id: string) => {
    try {
      setLoadingMessages(true);
      setError(null);
      setSelectedConversationId(id);
      setStreamingMessage(''); // Clear any streaming state

      console.log(`Selecting conversation ${id}...`);
      const data = await messagesApi.getByConversationId(id);

      setMessages(data);
      console.log(`Loaded ${data.length} messages`);
    } catch (err) {
      console.error('Error loading messages:', err);
      setError('Failed to load messages. Please try again.');
      setMessages([]);
    } finally {
      setLoadingMessages(false);
    }
  }, []);

  /**
   * Create a new conversation
   *
   * Called when:
   * - User clicks "New Chat" button
   *
   * After creation:
   * - Reloads conversation list
   * - Auto-selects the new conversation
   *
   * @param title - Optional title (backend may auto-generate)
   */
  const createConversation = useCallback(async (title?: string) => {
    try {
      setError(null);

      console.log('Creating new conversation...');
      // MEMORY SYSTEM: Pass userId (hardcoded to 1 for now, until authentication is implemented)
      const newConversation = await conversationsApi.create({
        title,
        userId: 1  // TODO: Get from authentication context when implemented
      });

      console.log(`Created conversation: ${newConversation.id}`);

      // Reload conversations to include the new one
      await loadConversations();

      // Auto-select the new conversation
      await selectConversation(newConversation.id);
    } catch (err) {
      console.error('Error creating conversation:', err);
      setError('Failed to create conversation. Please try again.');
    }
  }, [loadConversations, selectConversation]);

  /**
   * Delete a conversation
   *
   * Called when:
   * - User clicks delete button on a conversation
   *
   * After deletion:
   * - Reloads conversation list
   * - If deleted conversation was selected, deselect it
   *
   * @param id - Conversation ID to delete
   */
  const deleteConversation = useCallback(async (id: string) => {
    try {
      setError(null);

      console.log(`Deleting conversation ${id}...`);
      await conversationsApi.delete(id);

      console.log('Conversation deleted');

      // If we deleted the selected conversation, clear selection
      if (selectedConversationId === id) {
        setSelectedConversationId(null);
        setMessages([]);
      }

      // Reload conversations
      await loadConversations();
    } catch (err) {
      console.error('Error deleting conversation:', err);
      setError('Failed to delete conversation. Please try again.');
    }
  }, [selectedConversationId, loadConversations]);

  /**
   * Send a message in the current conversation with STREAMING
   *
   * NEW STREAMING BEHAVIOR:
   * 1. Immediately add user message to UI (optimistic update)
   * 2. Create placeholder for streaming assistant message
   * 3. Start streaming from backend
   * 4. Update placeholder as chunks arrive
   * 5. Replace with final message when done
   *
   * Called when:
   * - User types message and clicks send
   *
   * @param content - Message text
   */
  const sendMessage = useCallback(async (content: string) => {
    // Must have a selected conversation
    if (!selectedConversationId) {
      setError('No conversation selected');
      return;
    }

    try {
      setSendingMessage(true);
      setIsStreaming(true);
      setStreamingMessage('');
      setError(null);

      console.log('Sending message with streaming...');

      // Check if this is the first message (for title generation)
      const isFirstMessage = messages.length === 0;

      // 1. Optimistically add user message to UI immediately
      const tempUserMessage: Message = {
        id: 'temp-user-' + Date.now(),
        conversationId: selectedConversationId,
        role: 'USER' as MessageRole,
        content: content,
        createdAt: new Date().toISOString(),
      };

      setMessages((prev) => [...prev, tempUserMessage]);

      // 2. Start streaming
      let accumulatedContent = '';

      const cancelStream = messagesApi.sendStream(
        selectedConversationId,
        {
          content,
          userId: 1  // TODO: Get from authentication context when implemented
        },
        // onChunk: Called for each chunk
        (chunk) => {
          accumulatedContent += chunk;
          setStreamingMessage(accumulatedContent);
        },
        // onDone: Called when streaming completes
        async (finalMessage) => {
          console.log('Streaming completed, received final message:', finalMessage);

          // Clear streaming state
          setIsStreaming(false);
          setStreamingMessage('');
          setSendingMessage(false);

          // Reload all messages to get both user and assistant messages from backend
          const allMessages = await messagesApi.getByConversationId(selectedConversationId);
          setMessages(allMessages);

          // If this was the first message, generate a title
          if (isFirstMessage) {
            const words = content.trim().split(/\s+/);
            const title = words.slice(0, 5).join(' ');
            const shortTitle = title.length > 50 ? title.substring(0, 47) + '...' : title;

            console.log('Generated title:', shortTitle);

            try {
              await conversationsApi.update(selectedConversationId, { title: shortTitle });
              console.log('Title updated in backend');
            } catch (err) {
              console.error('Error updating title:', err);
            }
          }

          // Reload conversations to get updated title and timestamp
          await loadConversations();

          console.log('Messages reloaded after streaming');
        },
        // onError: Called if an error occurs
        (errorMessage) => {
          console.error('Streaming error:', errorMessage);
          setError('Failed to get response: ' + errorMessage);
          setIsStreaming(false);
          setStreamingMessage('');
          setSendingMessage(false);

          // Remove the optimistic user message on error
          setMessages((prev) => prev.filter((m) => m.id !== tempUserMessage.id));
        }
      );

      // Store cancel function in ref
      cancelStreamRef.current = cancelStream;

    } catch (err) {
      console.error('Error sending message:', err);
      setError('Failed to send message. Please try again.');
      setIsStreaming(false);
      setStreamingMessage('');
      setSendingMessage(false);
    }
  }, [selectedConversationId, messages.length, loadConversations]);

  /**
   * Clear error message
   *
   * Called when:
   * - User dismisses error notification
   * - Before starting a new operation
   */
  const clearError = useCallback(() => {
    setError(null);
  }, []);

  // ---- Context Value ----

  /**
   * This object contains everything components need:
   * - Current state
   * - Actions to modify state
   */
  const value: ChatContextType = {
    // State
    conversations,
    selectedConversationId,
    messages,
    loadingConversations,
    loadingMessages,
    sendingMessage,
    isStreaming,
    streamingMessage,
    error,

    // Actions
    loadConversations,
    selectConversation,
    createConversation,
    deleteConversation,
    sendMessage,
    clearError,
  };

  // ---- Render ----

  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>;
}

// ============================================================================
// CUSTOM HOOK FOR CONSUMING CONTEXT
// ============================================================================

/**
 * useChatContext Hook
 *
 * Custom hook to access ChatContext from any component
 *
 * Usage in components:
 *   const { conversations, loadConversations } = useChatContext();
 *
 * Why this pattern:
 * - Cleaner than using useContext(ChatContext) everywhere
 * - Provides better error message if used outside Provider
 * - TypeScript knows the type (no undefined check needed)
 */
export function useChatContext() {
  const context = useContext(ChatContext);

  // If context is undefined, it means the component is not wrapped in ChatProvider
  if (context === undefined) {
    throw new Error('useChatContext must be used within a ChatProvider');
  }

  return context;
}
