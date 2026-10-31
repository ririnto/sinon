# Cargo workspace sample

The workspace shares package metadata and the local greeting dependency between a library and an application.
Install the stable Rust toolchain with rustfmt and Clippy, then run:

```sh
pnpm install
pnpm run rust:check
pnpm run rust:doc
cargo run -p greeter -- Ada
```

The root hooks run the Markdown and Cargo checks through the project-owned pnpm scripts.
