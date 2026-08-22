# Nexa Frontend

The React + TypeScript frontend for Nexa AI Chat Application.

## Tech Stack

- **React 18** - UI library
- **TypeScript** - Type safety
- **Vite** - Build tool and dev server
- **Tailwind CSS** - Utility-first styling
- **React Router** - Client-side routing

## Project Structure

```
src/
├── components/
│   ├── layout/          # Layout components (Sidebar, Layout)
│   ├── chat/            # Chat-specific components (ChatWindow, MessageList, Message, MessageInput)
│   └── ui/              # Reusable UI components (Button, Loading, EmptyState)
│
├── services/
│   └── api.ts           # All backend API calls (conversationsApi, messagesApi)
│
├── types/
│   └── index.ts         # TypeScript interfaces matching backend DTOs
│
├── contexts/
│   └── ChatContext.tsx  # Global state management for conversations and messages
│
├── hooks/
│   ├── useConversations.ts  # Custom hooks for conversation logic
│   └── useMessages.ts       # Custom hooks for message logic
│
├── App.tsx              # Root component
├── main.tsx             # Application entry point
└── index.css            # Global styles and Tailwind imports
```

## Setup

### Prerequisites

- Node.js 18+ 
- npm or yarn
- Backend running on `http://localhost:8081`

### Installation

```bash
# Install dependencies
npm install

# Create environment file
cp .env.example .env

# Start development server
npm run dev
```

The app will run on `http://localhost:5173`

## Environment Variables

Create a `.env` file:

```env
VITE_API_URL=http://localhost:8081/api
```

## Available Scripts

```bash
# Development server with hot reload
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Type checking
npm run type-check

# Linting
npm run lint
```

## Architecture

### State Management

- **ChatContext** provides global state for:
  - List of conversations
  - Currently selected conversation
  - Messages for the selected conversation
  - Loading states
  - Actions (create, select, delete, send message)

- Backend is the source of truth
- Frontend caches data in context
- Components are mostly presentational

### API Communication

All backend communication is centralized in `src/services/api.ts`:

```typescript
// Fetch conversations
const conversations = await conversationsApi.getAll();

// Create new conversation
const newConv = await conversationsApi.create({ title: 'New Chat' });

// Send message
const response = await messagesApi.send(conversationId, { content: 'Hello!' });
```

### Component Hierarchy

```
App
└── Layout
    ├── Sidebar
    │   ├── New Chat Button
    │   └── Conversation List
    │       └── Conversation Items
    │
    └── ChatWindow
        ├── MessageList
        │   └── Message Components
        └── MessageInput
```

## User Flows

### 1. App Startup
1. Load all conversations from backend
2. Display conversation list in sidebar
3. Show empty state (no conversation selected)

### 2. Select Conversation
1. User clicks conversation in sidebar
2. Fetch messages for that conversation
3. Display messages in chat window
4. Highlight selected conversation

### 3. New Conversation
1. User clicks "New Chat" button
2. Create conversation via backend
3. Auto-select the new conversation
4. Show empty message list with input ready

### 4. Send Message
1. User types and sends message
2. Optimistically show user message immediately
3. Send to backend
4. Backend returns user message + AI response
5. Display AI response
6. Auto-scroll to bottom

## Future Enhancements

The architecture is designed to easily support:

- **Streaming responses** - Replace `messagesApi.send()` with `messagesApi.sendStreaming()`
- **Dark mode** - Tailwind's dark mode utilities ready to use
- **File uploads** - Extend `SendMessageRequest` to include files
- **Markdown rendering** - Add `react-markdown` for formatted responses
- **User authentication** - Add auth context and protected routes

## Development Guidelines

### Adding a New Component

1. Create component in appropriate directory
2. Export from component file
3. Import where needed
4. Keep components focused and reusable

### Making API Calls

- Always use the centralized `api.ts` service
- Handle errors with try/catch
- Show loading states during async operations
- Type all requests and responses

### State Updates

- Use ChatContext for global state
- Use local useState for component-specific UI state
- Keep backend as source of truth
- Optimistic updates for better UX

## Troubleshooting

### Backend Connection Issues

- Verify backend is running on port 8081
- Check `.env` file has correct `VITE_API_URL`
- Check browser console for CORS errors
- Ensure backend allows requests from `http://localhost:5173`

### Build Errors

- Clear node_modules and reinstall: `rm -rf node_modules && npm install`
- Clear Vite cache: `rm -rf node_modules/.vite`
- Check TypeScript errors: `npm run type-check`

## Learn More

- [React Documentation](https://react.dev)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/)
- [Vite Guide](https://vitejs.dev/guide/)
- [Tailwind CSS Docs](https://tailwindcss.com/docs)
