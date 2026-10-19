import { defineConfig } from "@lovable.dev/vite-tanstack-config";
import netlify from "@netlify/vite-plugin-tanstack-start";

export default defineConfig({
  // Netlify handles the TanStack Start SSR/runtime.
  // Do not let the Lovable config generate a Nitro deployment.
  nitro: false,

  tanstackStart: {
    server: {
      entry: "server",
    },
  },

  plugins: [
    netlify(),
  ],
});