import { useEffect, useRef } from 'react';
import Message from './Message';
import type { Message as MessageType } from '../../types';

/**
 * MessageList Component - REDESIGNED
 *
 * Beautiful scrollable message list with:
 * - Smooth animations
 * - Better empty state
 * - Custom scrollbar
 */

interface MessageListProps {
  messages: MessageType[];
}

export default function MessageList({ messages }: MessageListProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Empty state - no messages yet
  if (messages.length === 0) {
    return (
      <div className="flex items-center justify-center h-full p-8">
        <div className="text-center max-w-md animate-bounce-in">
          {/* Animated illustration */}
          <div className="relative mb-6">
            <div className="absolute inset-0 bg-gradient-to-r from-indigo-400 to-purple-400 rounded-full blur-2xl opacity-20 animate-pulse"></div>
            <div className="relative bg-gradient-to-br from-indigo-50 to-purple-50 rounded-3xl p-8 border-2 border-indigo-100">
              <svg
                className="w-20 h-20 mx-auto text-transparent bg-clip-text"
                fill="none"
                stroke="url(#gradient)"
                strokeWidth="1.5"
                viewBox="0 0 24 24"
              >
                <defs>
                  <linearGradient id="gradient" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="#6366f1" />
                    <stop offset="100%" stopColor="#a855f7" />
                  </linearGradient>
                </defs>
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-5 5v-5z"
                />
              </svg>
            </div>
          </div>

          {/* Text */}
          <h3 className="text-2xl font-bold mb-3 bg-gradient-to-r from-indigo-600 to-purple-600 text-transparent bg-clip-text">
            Start a Conversation
          </h3>
          <p className="text-slate-600 mb-6">
            Type your first message below to begin chatting with Nexa AI
          </p>

          {/* Suggestions */}
          <div className="space-y-2">
            <p className="text-sm text-slate-500 font-medium mb-3">Try asking:</p>
            <div className="grid gap-2">
              {[
                'Explain quantum computing',
                'Write a poem about space',
                'Help me debug my code',
              ].map((suggestion, i) => (
                <div
                  key={i}
                  className="bg-white border border-slate-200 rounded-xl px-4 py-2 text-sm text-slate-600 hover:border-indigo-300 hover:bg-indigo-50 transition-colors duration-200 cursor-pointer"
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
    <div className="h-full overflow-y-auto scrollbar-thin scrollbar-thumb-slate-300 scrollbar-track-transparent hover:scrollbar-thumb-slate-400">
      <div className="max-w-4xl mx-auto p-6 space-y-4">
        {/* Map through messages and render each one */}
        {messages
          .filter((message) => message && message.id)
          .map((message) => (
            <Message key={message.id} message={message} />
          ))}

        {/* Invisible div at the bottom for auto-scroll */}
        <div ref={messagesEndRef} />
      </div>
    </div>
  );
}
