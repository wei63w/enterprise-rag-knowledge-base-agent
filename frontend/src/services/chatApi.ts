export interface ChatRequest {
  question: string;
  docIds?: string[];
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
}
 
export async function sendChat(request: ChatRequest): Promise<ChatResponse> {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request)
  });
 
  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(errorBody.message || `对话请求失败：${response.status}`);
  }
 
  return response.json();
}