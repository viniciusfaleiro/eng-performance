import * as esbuild from "esbuild";

// Bundle the MD3 web components into a single self-hosted ES module served by Spring.
const outfile = "../adapters/in-web/src/main/resources/static/vendor/md3.js";

await esbuild.build({
  entryPoints: ["src/entry.js"],
  bundle: true,
  format: "esm",
  minify: true,
  sourcemap: false,
  target: ["es2022"],
  outfile,
});

console.log("MD3 bundle written to " + outfile);
