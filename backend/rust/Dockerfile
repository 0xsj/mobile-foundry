# syntax=docker/dockerfile:1
FROM rust:1.86.0-bookworm AS build
WORKDIR /source
COPY . .
# Bound compiler memory; retain only build caches, never runtime secrets.
RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/source/target \
    CARGO_BUILD_JOBS=4 cargo build --locked --release --workspace && \
    mkdir -p /out/files && \
    cp target/release/api-server /out/server && \
    cp target/release/db-migrate /out/migrate && \
    cp target/release/api-maintenance /out/maintenance && \
    cp target/release/api-healthcheck /out/healthcheck && \
    chown 65532:65532 /out/files && chmod 700 /out/files
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && \
    rm -rf /var/lib/apt/lists/*
COPY --from=build /out/server /server
COPY --from=build /out/migrate /migrate
COPY --from=build /out/maintenance /maintenance
COPY --from=build /out/healthcheck /healthcheck
COPY --from=build --chown=65532:65532 /out/files /data/files
USER 65532:65532
EXPOSE 8080 9090
ENTRYPOINT ["/server"]
