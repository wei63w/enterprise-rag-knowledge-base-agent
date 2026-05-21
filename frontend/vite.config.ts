import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules/react") || id.includes("node_modules/react-dom")) {
            return "react";
          }

          if (id.includes("node_modules/antd") || id.includes("node_modules/@ant-design")) {
            return "antd";
          }

          if (id.includes("node_modules")) {
            return "vendor";
          }
        }
      }
    }
  },
  test: {
    globals: true,
    setupFiles: "./src/test/setup.ts"
  }
});
