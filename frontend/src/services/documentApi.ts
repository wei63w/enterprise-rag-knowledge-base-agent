export type DocumentRecord = {
  id: string;
  name: string;
  type: string;
  filePath?: string;
  fileSize: number;
  status: "PROCESSING" | "READY" | "ERROR" | "DELETING" | "DELETE_FAILED";
  errorMessage?: string | null;
  chunkCount: number;
  uploadTime?: string;
  processTime?: string | null;
};

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `请求失败：${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function listDocuments(): Promise<DocumentRecord[]> {
  const response = await fetch("/api/documents");
  return parseResponse<DocumentRecord[]>(response);
}

export async function uploadDocument(file: File): Promise<DocumentRecord> {
  const form = new FormData();
  form.append("file", file);
  const response = await fetch("/api/documents/upload", {
    method: "POST",
    body: form
  });
  return parseResponse<DocumentRecord>(response);
}

export async function deleteDocument(id: string): Promise<void> {
  const response = await fetch(`/api/documents/${id}`, { method: "DELETE" });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `删除失败：${response.status}`);
  }
}
