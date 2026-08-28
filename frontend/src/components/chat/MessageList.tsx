import { useEffect, useRef } from 'react';
import { useChatContext } from '../../contexts/ChatContext';
import Message from './Message';
import type { Message as MessageType } from '../../types';

/**
 * MessageList Component - REDESIGNED WITH STREAMING
 *
 * Beautiful scrollable message list with:
 * - Smooth animations
 * - Better empty state
 * - Custom scrollbar
 * - STREAMING support - shows partial AI response as it arrives
 */

interface MessageListProps {
  messages: MessageType[];
}

export default function MessageList({ messages }: MessageListProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const { isStreaming, streamingMessage } = useChatContext();

  // Auto-scroll when messages change or streaming updates
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingMessage]);

  // Empty state - no messages yet
  if (messages.length === 0 && !isStreaming) {
    return (
      <div className="flex items-center justify-center h-full p-8">
        <div className="text-center max-w-md animate-bounce-in">
          {/* Animated illustration */}
          <div className="relative mb-6">
            <div className="absolute inset-0 bg-gradient-to-r from-red-400 to-red-600 rounded-full blur-2xl opacity-20 animate-pulse"></div>
            <div className="relative bg-gradient-to-br from-gray-800 to-gray-900 rounded-3xl p-8 border-2 border-red-900/30">
              <svg
                className="w-20 h-20 mx-auto text-red-500"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"
                />
              </svg>
            </div>
          </div>

          {/* Text */}
          <h3 className="text-2xl font-bold mb-3 bg-gradient-to-r from-red-500 to-red-600 text-transparent bg-clip-text">
            Start a Conversation
          </h3>
          <p className="text-gray-400 mb-6">
            Type your first message below to begin chatting with Nexa AI
          </p>

          {/* Suggestions */}
          <div className="space-y-2">
            <p className="text-sm text-gray-500 font-medium mb-3">Try asking:</p>
            <div className="grid gap-2">
              {[
                'Explain quantum computing',
                'Write a poem about space',
                'Help me debug my code',
              ].map((suggestion, i) => (
                <div
                  key={i}
                  className="bg-gray-800 border border-gray-700 rounded-xl px-4 py-2 text-sm text-gray-300 hover:border-red-500/50 hover:bg-gray-700 transition-colors duration-200 cursor-pointer"
                >
                  💡 {suggestion}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }

  // Render messages
  return (
    <div className="h-full overflow-y-auto scrollbar-thin scrollbar-thumb-red-900/50 scrollbar-track-transparent hover:scrollbar-thumb-red-800/70">
      <div className="max-w-4xl mx-auto p-6 space-y-4">
        {/* Map through messages and render each one */}
        {messages
          .filter((message) => message && message.id)
          .map((message) => (
            <Message key={message.id} message={message} />
          ))}

        {/* STREAMING MESSAGE - Show partial AI response as it arrives */}
        {isStreaming && streamingMessage && (
          <div className="flex justify-start animate-fade-in-up">
            <div className="group relative max-w-[75%] rounded-2xl px-5 py-4 shadow-lg bg-gray-800 text-gray-100 border border-gray-700">
              {/* Role Label with Icon */}
              <div className="flex items-center gap-2 text-xs font-bold mb-2 text-gray-400">
                <div className="w-6 h-6 rounded-full bg-gradient-to-br from-red-600 to-red-700 flex items-center justify-center">
                  <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M2 5a2 2 0 012-2h7a2 2 0 012 2v4a2 2 0 01-2 2H9l-3 3v-3H4a2 2 0 01-2-2V5z" />
                    <path d="M15 7v2a4 4 0 01-4 4H9.828l-1.766 1.767c.28.149.599.233.938.233h2l3 3v-3h2a2 2 0 002-2V9a2 2 0 00-2-2h-1z" />
                  </svg>
                </div>
                <span>Nexa AI</span>
              </div>

              {/* Streaming Content */}
              <div className="text-[15px] leading-relaxed whitespace-pre-wrap break-words text-gray-100">
                {streamingMessage}
                {/* Blinking cursor to show it's actively typing */}
                <span className="inline-block w-2 h-4 bg-red-500 ml-1 animate-pulse"></span>
              </div>

              {/* Streaming indicator */}
              <div className="flex items-center gap-1 text-xs mt-3 text-gray-500">
                <svg className="w-3 h-3 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Streaming...
              </div>
            </div>
          </div>
        )}

        {/* Invisible div at the bottom for auto-scroll */}
        <div ref={messagesEndRef} />
      </div>
    </div>
  );
}
