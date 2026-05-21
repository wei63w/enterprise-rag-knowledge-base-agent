import { render, screen } from "@testing-library/react";
import App from "./App";

describe("App", () => {
  it("renders the MVP workspace shell", async () => {
    render(<App />);

    expect(await screen.findByRole("heading", { name: /Enterprise RAG/i })).toBeInTheDocument();
    expect(await screen.findByRole("menuitem", { name: /文档/i })).toBeInTheDocument();
    expect(await screen.findByRole("menuitem", { name: /对话/i })).toBeInTheDocument();
    expect(await screen.findByRole("menuitem", { name: /配置/i })).toBeInTheDocument();
  });
});
