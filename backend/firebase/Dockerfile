FROM node:24-bookworm-slim AS node-runtime
FROM eclipse-temurin:21-jre-jammy

COPY --from=node-runtime /usr/local /usr/local
ARG FIREBASE_TOOLS_VERSION=15.32.1
RUN npm install --global "firebase-tools@${FIREBASE_TOOLS_VERSION}" \
    && firebase setup:emulators:firestore \
    && firebase setup:emulators:storage \
    && firebase setup:emulators:ui \
    && npm cache clean --force

WORKDIR /workspace
ENV FIREBASE_CLI_DISABLE_UPDATE_CHECK=true
ENTRYPOINT ["/bin/sh", "/workspace/start.sh"]
