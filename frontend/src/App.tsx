import { ChatProvider } from './contexts/ChatContext';
import Layout from './components/layout/Layout';
import ChatWindow from './components/chat/ChatWindow';

/**
 * App Component
 *
 * Root component of the application.
 *
 * Structure:
 * ChatProvider (provides global state)
 *   └── Layout (sidebar + main area)
 *       └── ChatWindow (messages + input)
 *
 * Why this structure:
 * - ChatProvider at the top so all components can access context
 * - Layout provides consistent UI structure
 * - ChatWindow handles all chat functionality
 */

function App() {
  return (
    <ChatProvider>
      <Layout>
        <ChatWindow />
      </Layout>
    </ChatProvider>
  );
}

export default App;

/**
 * ✅ Complete Application Structure:
 *
 * App
 *   └── ChatProvider (global state)
 *       └── Layout
 *           ├── Sidebar
 *           │   ├── Nexa branding
 *           │   ├── New Chat button
 *           │   └── Conversation list
 *           │
 *           └── ChatWindow
 *               ├── Empty state (no conversation)
 *               ├── Loading state
 *               └── Chat view
 *                   ├── MessageList
 *                   │   └── Message components
 *                   └── MessageInput
 *
 * All components connected through ChatContext!
 */
