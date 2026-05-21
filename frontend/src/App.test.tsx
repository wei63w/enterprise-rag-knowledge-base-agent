import { render, screen } from "@testing-library/react";
import { beforeEach, vi } from "vitest";
import App from "./App";

describe("App", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [
          {
            id: "doc-1",
            name: "policy.txt",
            type: "TXT",
            status: "READY",
            chunkCount: 3,
            fileSize: 11,
            uploadTime: "2026-05-21T00:00:00Z"
          }
        ]
      })
    );
  });

  it("renders the MVP workspace shell", async () => {
    render(<App />);

    expect(await screen.findByRole("heading", { name: /Enterprise RAG/i })).toBeInTheDocument();
    expect(await screen.findByRole("menuitem", { name: /文档/i })).toBeInTheDocument();
    expect(await screen.findByRole("menuitem", { name: /对话/i })).toBeInTheDocument();
    expect(await screen.findByRole("menuitem", { name: /配置/i })).toBeInTheDocument();
  });

  it("loads documents from the backend", async () => {
    render(<App />);

    expect(await screen.findByText("policy.txt")).toBeInTheDocument();
    expect(screen.getByText("READY")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });
});
