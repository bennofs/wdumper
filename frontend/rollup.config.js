import typescript from 'rollup-plugin-typescript2';
import resolve from 'rollup-plugin-node-resolve';

export default {
  input: "./ts/main.ts",
  output: {
    file: "dist/bundle.js",
    format: "iife",
    sourcemap: true
  },
  plugins: [
    resolve({ browser: true }),
    typescript()
  ]
}
