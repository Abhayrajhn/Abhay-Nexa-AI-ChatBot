import { useState, KeyboardEvent } from 'react';
import { useChatContext } from '../../contexts/ChatContext';

/**
 * MessageInput Component - REDESIGNED
 *
 * Beautiful modern input with:
 * - Gradient send button
 * - Smooth animations
 * - Better UX
 */

export default function MessageInput() {
  const { sendMessage, sendingMessage } = useChatContext();
  const [input, setInput] = useState('');

  const handleSend = async () => {
    if (!input.trim()) return;
    if (sendingMessage) return;

    try {
      await sendMessage(input.trim());
      setInput('');
    } catch (error) {
      console.error('Failed to send message:', error);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="p-6 border-t bg-gray-900 border-red-900/30">
      <div className="flex gap-3 items-end max-w-4xl mx-auto">
        {/* Textarea */}
        <div className="flex-1 relative">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Ask anything..."
            disabled={sendingMessage}
            rows={1}
            className="
              w-full resize-none rounded-2xl border-2 px-5 py-4
              border-gray-700 bg-gray-800 text-gray-100 placeholder-gray-500
              focus:outline-none focus:ring-2 focus:ring-red-500 focus:border-transparent
              disabled:bg-gray-900 disabled:cursor-not-allowed
              max-h-40 overflow-y-auto
              transition-all duration-200
              shadow-sm hover:shadow-md focus:shadow-lg
            "
            style={{
              minHeight: '56px',
            }}
          />

          {/* Character hint */}
          {input.length > 0 && (
            <div className="absolute bottom-2 right-4 text-xs text-gray-500">
              {input.length} chars
            </div>
          )}
        </div>

        {/* Send Button - Red gradient */}
        <button
          onClick={handleSend}
          disabled={!input.trim() || sendingMessage}
          className="
            px-6 py-4 rounded-2xl font-semibold
            bg-gradient-to-r from-red-600 to-red-700 hover:from-red-700 hover:to-red-800
            text-white
            transition-all duration-300 transform hover:scale-105 active:scale-95
            disabled:cursor-not-allowed
            disabled:transform-none disabled:hover:scale-100
            flex items-center justify-center gap-2 group
            shadow-lg shadow-red-900/50 hover:shadow-xl hover:shadow-red-800/60
            relative overflow-hidden
            min-w-[120px]
            disabled:from-gray-700 disabled:to-gray-800
          "
        >
          {/* Shimmer effect */}
          <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-1000"></div>

          {sendingMessage ? (
            <>
              {/* Loading spinner - circular buffering */}
              <div className="animate-spin rounded-full h-5 w-5 border-2 border-white border-t-transparent relative"></div>
            </>
          ) : (
            <>
              {/* Send icon with animation */}
              <svg
                className="w-5 h-5 transition-transform group-hover:translate-x-1 group-hover:-translate-y-1 duration-300 relative"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
                />
              </svg>
              <span className="relative">Send</span>
            </>
          )}
        </button>
      </div>

      {/* Hint text */}
      <div className="text-xs text-center mt-3 flex items-center justify-center gap-2 text-gray-500">
        <span className="inline-flex items-center gap-1 px-2 py-1 rounded bg-gray-800">
          <kbd className="font-mono font-semibold text-gray-300">Enter</kbd>
          to send
        </span>
        <span className="text-gray-700">•</span>
        <span className="inline-flex items-center gap-1 px-2 py-1 rounded bg-gray-800">
          <kbd className="font-mono font-semibold text-gray-300">Shift + Enter</kbd>
          for new line
        </span>
      </div>
    </div>
  );
}
