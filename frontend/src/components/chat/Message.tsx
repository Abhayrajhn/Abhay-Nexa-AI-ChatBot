import { MessageRole, type Message as MessageType } from '../../types';

/**
 * Message Component - REDESIGNED
 *
 * Beautiful message bubbles with:
 * - Gradients for user messages
 * - Clean design for AI messages
 * - Smooth animations
 * - Copy button on hover
 */

interface MessageProps {
  message: MessageType;
}

export default function Message({ message }: MessageProps) {
  const isUser = message.role === MessageRole.USER;

  // Format timestamp
  const timestamp = new Date(message.createdAt).toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
  });

  return (
    <div
      className={`flex ${isUser ? 'justify-end' : 'justify-start'} animate-fade-in-up`}
    >
      <div
        className={`
          group relative max-w-[75%] rounded-2xl px-5 py-4 shadow-lg
          transition-all duration-300 hover:shadow-xl
          ${
            isUser
              ? 'bg-gradient-to-br from-red-600 to-red-700 text-white border border-red-500/50'
              : 'bg-gray-800 text-gray-100 border border-gray-700'
          }
        `}
      >
        {/* Role Label with Icon */}
        <div
          className={`flex items-center gap-2 text-xs font-bold mb-2 ${
            isUser ? 'text-red-100' : 'text-gray-400'
          }`}
        >
          {isUser ? (
            <>
              <div className="w-6 h-6 rounded-full bg-white/20 flex items-center justify-center">
                <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clipRule="evenodd" />
                </svg>
              </div>
              <span>You</span>
            </>
          ) : (
            <>
              <div className="w-6 h-6 rounded-full bg-gradient-to-br from-red-600 to-red-700 flex items-center justify-center">
                <svg className="w-4 h-4 text-white" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M2 5a2 2 0 012-2h7a2 2 0 012 2v4a2 2 0 01-2 2H9l-3 3v-3H4a2 2 0 01-2-2V5z" />
                  <path d="M15 7v2a4 4 0 01-4 4H9.828l-1.766 1.767c.28.149.599.233.938.233h2l3 3v-3h2a2 2 0 002-2V9a2 2 0 00-2-2h-1z" />
                </svg>
              </div>
              <span>Nexa AI</span>
            </>
          )}
        </div>

        {/* Message Content */}
        <div
          className={`text-[15px] leading-relaxed whitespace-pre-wrap break-words ${
            isUser ? 'text-white' : 'text-gray-100'
          }`}
        >
          {message.content}
        </div>

        {/* Timestamp */}
        <div
          className={`flex items-center gap-1 text-xs mt-3 ${
            isUser ? 'text-red-200' : 'text-gray-500'
          }`}
        >
          <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
            <path
              fillRule="evenodd"
              d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z"
              clipRule="evenodd"
            />
          </svg>
          {timestamp}
        </div>

        {/* Copy Button - appears on hover */}
        <button
          onClick={() => {
            navigator.clipboard.writeText(message.content);
          }}
          className={`
            absolute -top-2 ${isUser ? '-left-2' : '-right-2'}
            opacity-0 group-hover:opacity-100
            transition-all duration-200 transform hover:scale-110
            ${
              isUser
                ? 'bg-white text-red-600 hover:bg-red-50'
                : 'bg-gradient-to-br from-red-600 to-red-700 text-white hover:from-red-700 hover:to-red-800'
            }
            p-2 rounded-lg shadow-lg
          `}
          title="Copy message"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"
            />
          </svg>
        </button>
      </div>
    </div>
  );
}
