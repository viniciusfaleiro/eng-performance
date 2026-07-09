import js from "@eslint/js";
import globals from "globals";

// Flat config. Two source contexts:
//  - build.mjs  → Node build script (top-level await, esbuild)
//  - src/**     → browser ES module bundled into the served static resources
export default [
  { ignores: ["node_modules/**"] },
  js.configs.recommended,
  {
    files: ["build.mjs"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      globals: { ...globals.node },
    },
  },
  {
    files: ["src/**/*.js"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      globals: { ...globals.browser },
    },
  },
];
