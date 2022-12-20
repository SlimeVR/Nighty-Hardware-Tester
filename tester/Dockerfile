FROM ghcr.io/cross-rs/aarch64-unknown-linux-gnu:main

# RUN apt-get update && apt-get install -y --no-install-recommends apt-utils
RUN dpkg --add-architecture arm64
RUN apt-get update && apt-get install -y libudev-dev:arm64 libssl-dev:arm64

# ENV CARGO_TARGET_AARCH64_UNKNOWN_LINUX_RUSTFLAGS="-L /usr/lib/aarch64-linux-gnu -C link-args=-Wl,-rpath-link,/usr/lib/aarch64-linux-gnu $CARGO_TARGET_AARCH64_UNKNOWN_LINUX_RUSTFLAGS"
ENV PKG_CONFIG_PATH="/usr/lib/aarch64-linux-gnu/pkgconfig:${PKG_CONFIG_PATH}"
