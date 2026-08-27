import { useEffect, useState } from 'react';
import { conversationsApi } from '../services/api';
import type { Conversation } from '../types';

/**
 * ApiTest Component
 *
 * Purpose: Test that frontend can communicate with Spring Boot backend
 *
 * This is a temporary component to verify:
 * 1. Backend is running and accessible
 * 2. CORS is configured correctly
 * 3. Our API service layer works
 * 4. Data flows from backend → frontend
 *
 * We'll delete this component once we build the real UI.
 */
export default function ApiTest() {
  // State to store conversations fetched from backend
  const [conversations, setConversations] = useState<Conversation[]>([]);

  // State to track loading status
  const [loading, setLoading] = useState(true);

  // State to store any errors
  const [error, setError] = useState<string | null>(null);

  // useEffect runs when component mounts
  useEffect(() => {
    // Define async function to fetch data
    const fetchConversations = async () => {
      try {
        setLoading(true);
        setError(null);

        // Call our API service (which calls Spring Boot)
        console.log('Fetching conversations from backend...');
        const data = await conversationsApi.getAll();

        console.log('Received data:', data);
        setConversations(data);

      } catch (err) {
        // If API call fails, show error
        console.error('Error fetching conversations:', err);
        setError(err instanceof Error ? err.message : 'Unknown error');
      } finally {
        setLoading(false);
      }
    };

    // Call the function
    fetchConversations();
  }, []); // Empty dependency array = run once on mount

  // Render loading state
  if (loading) {
    return (
      <div className="p-8">
        <h1 className="text-2xl font-bold mb-4">Backend Connection Test</h1>
        <p className="text-gray-600">Loading conversations from backend...</p>
      </div>
    );
  }

  // Render error state
  if (error) {
    return (
      <div className="p-8">
        <h1 className="text-2xl font-bold mb-4">Backend Connection Test</h1>
        <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
          <p className="font-bold">Error connecting to backend:</p>
          <p>{error}</p>
          <p className="mt-2 text-sm">
            Make sure your Spring Boot backend is running on http://localhost:8081
          </p>
        </div>
      </div>
    );
  }

  // Render success state with data
  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold mb-4">✅ Backend Connection Test</h1>

      <div className="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded mb-6">
        <p className="font-bold">Successfully connected to backend!</p>
        <p>Found {conversations.length} conversation(s)</p>
      </div>

      <div className="space-y-4">
        <h2 className="text-xl font-semibold">Conversations from Database:</h2>

        {conversations.length === 0 ? (
          <p className="text-gray-600">No conversations found. Create one in the backend!</p>
        ) : (
          <div className="space-y-3">
            {conversations.map((conversation) => (
              <div
                key={conversation.id}
                className="border border-gray-300 rounded-lg p-4 bg-white shadow-sm"
              >
                <div className="font-semibold text-lg">{conversation.title}</div>
                <div className="text-sm text-gray-500 mt-1">
                  ID: {conversation.id}
                </div>
                <div className="text-sm text-gray-500">
                  Created: {new Date(conversation.createdAt).toLocaleString()}
                </div>
                <div className="text-sm text-gray-500">
                  Updated: {new Date(conversation.updatedAt).toLocaleString()}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="mt-8 p-4 bg-blue-50 border border-blue-200 rounded">
        <p className="font-semibold mb-2">🎉 What this proves:</p>
        <ul className="list-disc list-inside space-y-1 text-sm">
          <li>✅ Backend is running and accessible</li>
          <li>✅ Frontend can make HTTP requests to backend</li>
          <li>✅ Our API service layer (src/services/api.ts) works</li>
          <li>✅ TypeScript types match backend DTOs</li>
          <li>✅ Data flows correctly: Backend → API → Component → UI</li>
        </ul>
      </div>
    </div>
  );
}
