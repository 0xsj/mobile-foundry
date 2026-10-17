# syntax=docker/dockerfile:1
FROM golang:1.27.1-bookworm AS build
WORKDIR /source
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN --mount=type=cache,target=/root/.cache/go-build CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/server ./cmd/server && \
    CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/migrate ./cmd/migrate && \
    CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/healthcheck ./cmd/healthcheck && \
    CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/maintenance ./cmd/maintenance && \
    mkdir -p /out/files && chown 65532:65532 /out/files && chmod 700 /out/files
FROM scratch
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /out/server /server
COPY --from=build /out/migrate /migrate
COPY --from=build /out/maintenance /maintenance
COPY --from=build /out/healthcheck /healthcheck
COPY --from=build --chown=65532:65532 /out/files /data/files
USER 65532:65532
EXPOSE 8080 9090
ENTRYPOINT ["/server"]
