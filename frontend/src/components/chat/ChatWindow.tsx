import { useChatContext } from '../../contexts/ChatContext';
import MessageList from './MessageList';
import MessageInput from './MessageInput';

/**
 * ChatWindow Component - REDESIGNED
 *
 * Beautiful modern chat area with:
 * - Gradient background
 * - Smooth animations
 * - Modern empty states
 */

export default function ChatWindow() {
  const { selectedConversationId, messages, loadingMessages } = useChatContext();

  // No conversation selected - beautiful empty state
  if (!selectedConversationId) {
    return (
      <div className="flex items-center justify-center h-full bg-gradient-to-br from-gray-900 via-black to-gray-800">
        <div className="text-center max-w-md px-4 animate-fade-in">
          {/* Animated icon */}
          <div className="relative mb-8">
            <div className="absolute inset-0 bg-gradient-to-r from-red-600 to-red-700 rounded-full blur-3xl opacity-30 animate-pulse"></div>
            <div className="relative rounded-3xl p-8 shadow-2xl mx-auto w-36 h-36 flex items-center justify-center border bg-gray-800 border-red-900/30">
              <svg
                className="w-20 h-20 text-red-500"
                fill="currentColor"
                viewBox="0 0 24 24"
              >
                <path d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
              </svg>
            </div>
          </div>

          {/* Welcome text */}
          <h2 className="text-4xl font-bold mb-4 bg-gradient-to-r from-red-500 to-red-600 text-transparent bg-clip-text">
            Welcome to Nexa
          </h2>
          <p className="text-lg mb-8 text-gray-400">
            Your intelligent AI assistant is ready to help
          </p>

          {/* Feature cards */}
          <div className="grid grid-cols-1 gap-4 text-left">
            <div className="rounded-xl p-4 shadow-md hover:shadow-lg hover:shadow-red-900/30 transition-all duration-300 border bg-gray-800 border-gray-700">
              <div className="flex items-center gap-3">
                <div className="bg-gradient-to-br from-red-600 to-red-700 text-white p-2 rounded-lg">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                </div>
                <div>
                  <div className="font-semibold text-gray-100">Fast Responses</div>
                  <div className="text-sm text-gray-500">Powered by OpenAI</div>
                </div>
              </div>
            </div>

            <div className="rounded-xl p-4 shadow-md hover:shadow-lg hover:shadow-red-900/30 transition-all duration-300 border bg-gray-800 border-gray-700">
              <div className="flex items-center gap-3">
                <div className="bg-gradient-to-br from-red-600 to-red-700 text-white p-2 rounded-lg">
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                </div>
                <div>
                  <div className="font-semibold text-gray-100">Conversation History</div>
                  <div className="text-sm text-gray-500">Never lose context</div>
                </div>
              </div>
            </div>
          </div>

          <p className="text-sm mt-8 text-gray-500">
            Select a conversation or create a new one to get started
          </p>
        </div>
      </div>
    );
  }

  // Loading messages - beautiful loading state
  if (loadingMessages) {
    return (
      <div className="flex items-center justify-center h-full bg-gradient-to-br from-gray-900 via-black to-gray-800">
        <div className="text-center">
          {/* Animated spinner */}
          <div className="relative mb-6">
            <div className="absolute inset-0 bg-gradient-to-r from-red-600 to-red-700 rounded-full blur-xl opacity-50"></div>
            <div className="relative inline-block animate-spin rounded-full h-16 w-16 border-4 border-t-red-500 border-gray-800"></div>
          </div>
          <p className="font-medium text-lg text-gray-200">Loading messages...</p>
          <p className="text-sm mt-2 text-gray-500">Just a moment</p>
        </div>
      </div>
    );
  }

  // Conversation selected - show messages and input
  return (
    <div className="flex flex-col h-full bg-gradient-to-br from-gray-900 via-black to-gray-800">
      {/* Messages Area */}
      <div className="flex-1 overflow-hidden">
        <MessageList messages={messages} />
      </div>

      {/* Input Area */}
      <MessageInput />
    </div>
  );
}
