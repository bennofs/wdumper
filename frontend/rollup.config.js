import typescript from 'rollup-plugin-typescript2';
import resolve from 'rollup-plugin-node-resolve';
import commonjs from 'rollup-plugin-commonjs';

export default {
  input: "./ts/main.ts",
  output: {
    file: "static/main.js",
    format: "iife",
    sourcemap: true
  },
  plugins: [
    resolve({ browser: true }),
    commonjs(),
    typescript()
  ]
}
