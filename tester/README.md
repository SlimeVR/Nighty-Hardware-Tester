# Tester

This software runs on the testing jig, collects reports and uploads them to the backend.

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
