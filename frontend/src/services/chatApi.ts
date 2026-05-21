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
    throw new Error(`Chat request failed: ${response.status}`);
  }
 
  return response.json();
}