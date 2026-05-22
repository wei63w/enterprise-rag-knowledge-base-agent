export interface ChatRequest {
  question: string;
  sessionId?: string;
}

export interface SourceReference {
  docId: string;
  docName: string;
  content: string;
  score: number;
}

export interface ChatResponse {
  answer: string;
  sources: SourceReference[];
  sessionId: string;
}

export interface ChatHistoryMessage {
  role: "user" | "assistant";
  content: string;
  timestamp?: string;
}

export async function sendChat(request: ChatRequest): Promise<ChatResponse> {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}) as { message?: string });
    throw new Error(errorBody.message || `对话请求失败：${response.status}`);
  }

  return response.json();
}

export async function getChatHistory(sessionId: string): Promise<ChatHistoryMessage[]> {
  const response = await fetch(`/api/chat/history?sessionId=${encodeURIComponent(sessionId)}`);

  if (!response.ok) {
    return [];
  }

  const history: Array<{ question: string; answer: string; createdAt?: string }> = await response.json();
  const messages: ChatHistoryMessage[] = [];
  for (const entry of history) {
    messages.push({ role: "user", content: entry.question, timestamp: entry.createdAt });
    messages.push({ role: "assistant", content: entry.answer, timestamp: entry.createdAt });
  }
  return messages;
}