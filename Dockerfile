# 多阶段构建：先打前端，再把静态产物塞进后端镜像。
# 后端零运行时依赖（只用 Node 内置模块 + node:sqlite），所以最终镜像很小。

FROM node:22-bookworm-slim AS web
WORKDIR /web
COPY web/package.json web/package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY web/ ./
RUN npm run build

FROM node:22-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production \
    LOSNOTE_PORT=8787 \
    LOSNOTE_DB=/data/losnote.db \
    LOSNOTE_STATIC=/app/web-dist

COPY server/src ./src
COPY server/package.json ./package.json
COPY --from=web /web/dist ./web-dist

RUN mkdir -p /data && chown -R node:node /data /app
USER node
VOLUME ["/data"]
EXPOSE 8787

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.LOSNOTE_PORT||8787)+'/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "src/index.js"]
