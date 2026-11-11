# GARP — self-hosted (Node + SQLite, zero npm dependencies)
FROM node:24-alpine

ENV NODE_ENV=production
WORKDIR /app

# No npm install needed: only built-in modules (node:http, node:sqlite).
COPY package.json server.mjs ./
COPY src ./src
COPY public ./public
COPY scripts ./scripts

# SQLite data lives in /data (mount a volume here to persist).
ENV GARP_DATA_DIR=/data
VOLUME /data

EXPOSE 3000
CMD ["node", "server.mjs"]
