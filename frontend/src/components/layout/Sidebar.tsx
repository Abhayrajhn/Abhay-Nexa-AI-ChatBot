import { useEffect } from 'react';
import { useChatContext } from '../../contexts/ChatContext';

/**
 * Sidebar Component - REDESIGNED
 *
 * Beautiful modern sidebar with:
 * - Gradient background
 * - Smooth animations
 * - Hover effects
 * - Glass morphism
 */

export default function Sidebar() {
  const {
    conversations,
    selectedConversationId,
    loadingConversations,
    loadConversations,
    selectConversation,
    createConversation,
    deleteConversation,
  } = useChatContext();

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  const handleNewChat = async () => {
    await createConversation('New Chat');
  };

  const handleDelete = async (id: string, title: string) => {
    if (window.confirm(`Delete conversation "${title}"?`)) {
      await deleteConversation(id);
    }
  };

  return (
    <div className="flex flex-col h-full bg-gradient-to-br from-gray-900 via-black to-gray-800 border-r border-red-900/30">
      {/* Header Section */}
      <div className="p-6 border-b border-red-900/30 backdrop-blur-sm">
        {/* Nexa Logo with red accent */}
        <div className="mb-6">
          <h1 className="text-4xl font-bold text-white mb-1 tracking-tight">
            Nexa
          </h1>
          <p className="text-red-400 text-sm">Your AI Assistant</p>
        </div>

        {/* New Chat Button - Red gradient */}
        <button
          onClick={handleNewChat}
          className="
            w-full bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800
            text-white font-medium py-3 px-4 rounded-xl
            transition-all duration-300 transform hover:scale-105
            border border-red-500/50
            flex items-center justify-center gap-2 group
            shadow-lg shadow-red-900/50 hover:shadow-xl hover:shadow-red-800/60
          "
        >
          <svg
            className="w-5 h-5 transition-transform group-hover:rotate-90 duration-300"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 4v16m8-8H4"
            />
          </svg>
          <span>New Chat</span>
        </button>
      </div>

      {/* Conversations List */}
      <div className="flex-1 overflow-y-auto p-3 scrollbar-thin scrollbar-thumb-red-900/50 scrollbar-track-transparent">
        {/* Loading State */}
        {loadingConversations && (
          <div className="text-center py-12">
            <div className="inline-block animate-spin rounded-full h-10 w-10 border-4 border-red-900/30 border-t-red-500 mb-4"></div>
            <p className="text-gray-400 text-sm">Loading conversations...</p>
          </div>
        )}

        {/* Empty State */}
        {!loadingConversations && conversations.length === 0 && (
          <div className="text-center py-12 px-4">
            <div className="bg-gray-800/50 backdrop-blur-md rounded-2xl p-6 border border-red-900/30">
              <svg
                className="w-16 h-16 mx-auto mb-4 text-red-500/60"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
                />
              </svg>
              <p className="text-white font-medium mb-2">No conversations yet</p>
              <p className="text-gray-400 text-sm">Click "New Chat" to start!</p>
            </div>
          </div>
        )}

        {/* Conversations Grid */}
        {!loadingConversations && conversations.length > 0 && (
          <div className="space-y-2">
            {conversations.map((conversation) => (
              <div
                key={conversation.id}
                className={`
                  group relative rounded-xl cursor-pointer
                  transition-all duration-300 transform hover:scale-[1.02]
                  ${
                    selectedConversationId === conversation.id
                      ? 'bg-red-900/30 backdrop-blur-md border-2 border-red-500/50 shadow-lg shadow-red-900/30'
                      : 'bg-gray-800/30 backdrop-blur-sm border border-gray-700/50 hover:bg-gray-800/50 hover:border-red-900/50'
                  }
                `}
                onClick={() => selectConversation(conversation.id)}
              >
                <div className="p-4">
                  {/* Conversation Title */}
                  <div className="font-semibold text-white truncate pr-8 mb-1">
                    {conversation.title}
                  </div>

                  {/* Timestamp */}
                  <div className="text-xs text-gray-400 flex items-center gap-1">
                    <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                      <path
                        fillRule="evenodd"
                        d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z"
                        clipRule="evenodd"
                      />
                    </svg>
                    {new Date(conversation.updatedAt).toLocaleDateString('en-US', {
                      month: 'short',
                      day: 'numeric',
                    })}
                  </div>

                  {/* Delete Button */}
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDelete(conversation.id, conversation.title);
                    }}
                    className="
                      absolute right-3 top-3
                      opacity-0 group-hover:opacity-100
                      bg-red-600 hover:bg-red-700
                      text-white p-2 rounded-lg
                      transition-all duration-200
                      transform hover:scale-110
                      shadow-lg shadow-red-900/50
                    "
                    title="Delete conversation"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                      />
                    </svg>
                  </button>
                </div>

                {/* Selected indicator */}
                {selectedConversationId === conversation.id && (
                  <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-8 bg-red-500 rounded-r-full animate-pulse shadow-lg shadow-red-500/50"></div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Footer - Powered by info */}
      <div className="p-4 border-t border-red-900/30 backdrop-blur-sm">
        <div className="text-center text-gray-500 text-xs">
          Powered by OpenAI
        </div>
      </div>
    </div>
  );
}
