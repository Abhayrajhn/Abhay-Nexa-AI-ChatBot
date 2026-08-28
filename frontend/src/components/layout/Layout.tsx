import type { ReactNode } from 'react';
import Sidebar from './Sidebar';

/**
 * Layout Component
 *
 * Purpose: Main application layout structure
 *
 * Structure:
 * ┌─────────────────────────────────────┐
 * │  Layout                             │
 * │  ┌──────────┬──────────────────┐  │
 * │  │ Sidebar  │  Main Content    │  │
 * │  │          │  (children)      │  │
 * │  │          │                  │  │
 * │  └──────────┴──────────────────┘  │
 * └─────────────────────────────────────┘
 *
 * Why this exists:
 * - Provides consistent layout across the app
 * - Two-column layout: sidebar + main area
 * - Responsive: sidebar collapses on mobile
 * - Reusable: any content can go in the main area
 */

interface LayoutProps {
  children: ReactNode;  // Main content area (ChatWindow, etc.)
}

export default function Layout({ children }: LayoutProps) {
  return (
    <div className="flex h-screen bg-gray-50">
      {/*
        Sidebar Section
        - Fixed width on desktop (w-80 = 320px)
        - Scrollable if conversation list is long
        - Will be responsive/collapsible later
      */}
      <aside className="w-80 bg-white border-r border-gray-200 flex-shrink-0 overflow-y-auto">
        <Sidebar />
      </aside>

      {/*
        Main Content Section
        - Takes remaining space (flex-1)
        - Children rendered here (ChatWindow, etc.)
        - Scrollable independently from sidebar
      */}
      <main className="flex-1 overflow-hidden">
        {children}
      </main>
    </div>
  );
}

/**
 * Tailwind Classes Explained:
 *
 * flex - Flexbox container
 * h-screen - Height = 100vh (full viewport)
 * bg-gray-50 - Light gray background
 *
 * Sidebar:
 * w-80 - Width = 320px
 * bg-white - White background
 * border-r - Right border
 * border-gray-200 - Light gray border color
 * flex-shrink-0 - Don't shrink when space is tight
 * overflow-y-auto - Scrollable vertically
 *
 * Main:
 * flex-1 - Take remaining space
 * overflow-hidden - Prevent scrolling (children will handle it)
 */
