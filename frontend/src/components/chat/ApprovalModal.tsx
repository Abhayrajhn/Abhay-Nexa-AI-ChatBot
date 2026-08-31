/**
 * ApprovalModal Component
 *
 * Displays when the agent needs human approval before executing an action.
 * Shows tool details and allows user to approve or reject.
 *
 * Props:
 * - approvalRequest: The approval request data from the backend
 * - onApprove: Callback when user clicks approve
 * - onReject: Callback when user clicks reject
 * - isProcessing: Whether an action is being processed (disables buttons)
 */

export interface ApprovalRequest {
  approvalId: string;
  toolName: string;
  toolArguments: string; // JSON string
  conversationId: number;
}

interface ApprovalModalProps {
  approvalRequest: ApprovalRequest;
  onApprove: () => void;
  onReject: () => void;
  isProcessing: boolean;
}

export default function ApprovalModal({
  approvalRequest,
  onApprove,
  onReject,
  isProcessing,
}: ApprovalModalProps) {
  // Parse tool arguments to display human-readable description
  const getActionDescription = () => {
    try {
      const args = JSON.parse(approvalRequest.toolArguments);

      switch (approvalRequest.toolName) {
        case 'delete_conversation':
          return {
            title: 'Delete Conversation',
            description: `Permanently delete conversation #${args.conversationId}`,
            icon: (
              <svg className="w-12 h-12 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
            ),
            warning: 'This action cannot be undone. All messages in this conversation will be permanently deleted.',
            isDangerous: true,
          };

        default:
          return {
            title: 'Action Approval Required',
            description: `Execute ${approvalRequest.toolName}`,
            icon: (
              <svg className="w-12 h-12 text-yellow-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            ),
            warning: 'The AI agent wants to perform this action.',
            isDangerous: false,
          };
      }
    } catch (e) {
      return {
        title: 'Action Approval Required',
        description: `Execute ${approvalRequest.toolName}`,
        icon: (
          <svg className="w-12 h-12 text-yellow-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
        ),
        warning: 'The AI agent wants to perform this action.',
        isDangerous: false,
      };
    }
  };

  const action = getActionDescription();

  return (
    <>
      {/* Backdrop */}
      <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-40 animate-fade-in" />

      {/* Modal */}
      <div className="fixed inset-0 flex items-center justify-center z-50 p-4 animate-scale-in">
        <div className="bg-gradient-to-br from-gray-900 to-gray-800 border border-gray-700 rounded-2xl shadow-2xl max-w-md w-full overflow-hidden">
          {/* Header */}
          <div className={`p-6 border-b ${action.isDangerous ? 'border-red-900/30 bg-red-950/20' : 'border-yellow-900/30 bg-yellow-950/20'}`}>
            <div className="flex items-center gap-4">
              <div className={`p-3 rounded-xl ${action.isDangerous ? 'bg-red-950/50' : 'bg-yellow-950/50'}`}>
                {action.icon}
              </div>
              <div className="flex-1">
                <h3 className="text-xl font-bold text-gray-100">{action.title}</h3>
                <p className="text-sm text-gray-400 mt-1">Approval Required</p>
              </div>
            </div>
          </div>

          {/* Content */}
          <div className="p-6 space-y-4">
            {/* Description */}
            <div className="bg-gray-800/50 rounded-xl p-4 border border-gray-700">
              <p className="text-gray-300 font-medium">{action.description}</p>
            </div>

            {/* Warning */}
            <div className={`rounded-xl p-4 border flex items-start gap-3 ${
              action.isDangerous
                ? 'bg-red-950/30 border-red-900/50'
                : 'bg-yellow-950/30 border-yellow-900/50'
            }`}>
              <svg
                className={`w-5 h-5 mt-0.5 flex-shrink-0 ${action.isDangerous ? 'text-red-500' : 'text-yellow-500'}`}
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <p className={`text-sm ${action.isDangerous ? 'text-red-300' : 'text-yellow-300'}`}>
                {action.warning}
              </p>
            </div>

            {/* Technical details (collapsible) */}
            <details className="text-xs text-gray-500">
              <summary className="cursor-pointer hover:text-gray-400 transition-colors">
                Technical details
              </summary>
              <div className="mt-2 p-3 bg-gray-900/50 rounded border border-gray-800 font-mono text-xs overflow-auto">
                <div><span className="text-gray-600">Approval ID:</span> {approvalRequest.approvalId}</div>
                <div><span className="text-gray-600">Tool:</span> {approvalRequest.toolName}</div>
                <div><span className="text-gray-600">Arguments:</span> {approvalRequest.toolArguments}</div>
              </div>
            </details>
          </div>

          {/* Actions */}
          <div className="p-6 bg-gray-900/50 border-t border-gray-800 flex gap-3">
            {/* Reject Button */}
            <button
              onClick={onReject}
              disabled={isProcessing}
              className="flex-1 px-6 py-3 rounded-xl font-semibold transition-all duration-200 border-2 border-gray-700 text-gray-300 hover:bg-gray-800 hover:border-gray-600 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isProcessing ? 'Processing...' : 'Reject'}
            </button>

            {/* Approve Button */}
            <button
              onClick={onApprove}
              disabled={isProcessing}
              className={`flex-1 px-6 py-3 rounded-xl font-semibold transition-all duration-200 shadow-lg ${
                action.isDangerous
                  ? 'bg-gradient-to-r from-red-600 to-red-700 hover:from-red-500 hover:to-red-600 text-white shadow-red-900/50'
                  : 'bg-gradient-to-r from-green-600 to-green-700 hover:from-green-500 hover:to-green-600 text-white shadow-green-900/50'
              } disabled:opacity-50 disabled:cursor-not-allowed`}
            >
              {isProcessing ? 'Approving...' : 'Approve'}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
