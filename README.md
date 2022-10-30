# Nighty-Hardware-Tester

Software suit for testing hardware (like assemblied PCBs)

## Building

### Prerequisites

- [Rust](https://www.rust-lang.org/) (stable _should_ be fine)

### Building

```bash
cargo build --release
```

#### Cross-compiling

For cross compiling, I recommend using [`cross`](https://github.com/cross-rs/cross).

```bash
cross build --release --target aarch64-unknown-linux-gnu
```
