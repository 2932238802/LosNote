# 部署到自己的服务器

目标机器：任意一台有公网 IP 的 Linux（1 核 1G 够用，SQLite 单文件，内存占用约 60MB）。

## 1. 装 Docker

```bash
curl -fsSL https://get.docker.com | sh          # Debian / Ubuntu
sudo systemctl enable --now docker
```

## 2. 拉代码、配令牌

```bash
cd /opt
git clone <你的仓库地址> losnote && cd losnote
cp .env.example .env
vim .env      # 把 LOSNOTE_TOKEN 改成 32 位以上随机串
```

生成随机令牌：

```bash
head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 32; echo
```

## 3. 起服务

```bash
docker compose up -d --build
```

> 没有域名、就先用 `http://服务器IP:8787` 直连的话，在 `.env` 里再加一行
> `LOSNOTE_BIND=0.0.0.0`（默认只监听 127.0.0.1，给反代用）。手机端地址就填
> `http://服务器IP:8787`，令牌填同一串。

```bash
docker compose ps
curl http://127.0.0.1:8787/api/health
```

`docker-compose.yml` 默认只监听 `127.0.0.1:8787`（安全），外网访问靠下面的反代。

## 4. HTTPS 反代（Caddy，最省事）

```bash
apt install -y caddy        # 或按官网装
```

`/etc/caddy/Caddyfile`：

```caddy
note.example.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8787
}
```

```bash
systemctl reload caddy
```

Caddy 会自动申请 Let's Encrypt 证书。把域名 A 记录指向服务器 IP，并确保 80/443 放行。

> 用 Nginx 的话，`proxy_pass http://127.0.0.1:8787;` 加上
> `proxy_set_header Host $host;` 和 `client_max_body_size 20m;`（批量导入大文本要用）。

## 5. 手机端

APK 装好后：**题库 → 设置 → 云同步**，地址填 `https://note.example.com`，令牌填 `.env` 里那串。
`MainActivity.onStart()` 会静默同步一次；勾上自动化就能当"手机联网自动更新"用。

必须在手机上能打开的地址才行：先在同一 Wi-Fi 下用 `http://电脑内网IP:8787` 试通，再换公网域名。

## 6. 备份与恢复

数据是一个 SQLite 文件，在命名卷 `losnote-data` 里。**库开了 WAL**，所以不能只拷
`losnote.db` 一个文件——实测某个有 1 条题的库，`losnote.db` 只有 4 KB（空壳），
140 KB 的真实数据全在 `losnote.db-wal` 里。单拷主文件会得到一份"空备份"。

用 SQLite 自带的 `VACUUM INTO` 做一致性快照（会把 WAL 合并进去）：

```bash
# 备份（建议加到 crontab，每天 3:00；容器名按 docker compose ps 来）
docker exec losnote node -e "const{DatabaseSync}=require('node:sqlite');new DatabaseSync('/data/losnote.db').exec(\"VACUUM INTO '/data/snapshot.db'\")"
docker cp losnote:/data/snapshot.db "/opt/backup/losnote-$(date +%F).db"
docker exec losnote rm -f /data/snapshot.db
```

快照文件可以直接用 `sqlite3 快照.db 'select count(*) from notes'` 验一下条数。

恢复（先停、再换、再起；顺手清掉旧 WAL，否则会和旧库混在一起）：

```bash
docker compose stop
docker run --rm -v losnote-data:/data -v /opt/backup:/backup losnote:latest \
  sh -c 'rm -f /data/losnote.db /data/losnote.db-wal /data/losnote.db-shm
         && cp /backup/losnote-2026-11-06.db /data/losnote.db'
docker compose start
```

> 整目录拷贝也行，但容器必须处于停止状态：`docker cp losnote:/data/. /opt/backup/`。

## 6.5 没有 docker compose 插件时

有的精简镜像/老版本只装了 `docker` 没装 compose 插件（`docker compose version` 报
`unknown command: docker compose`）。这套服务的 compose 文件很薄，等价的一行是：

```bash
docker volume create losnote-data
docker run -d --name losnote --restart unless-stopped \
  -p 127.0.0.1:8787:8787 \
  -e LOSNOTE_TOKEN='把 .env 里那串粘贴进来' \
  -e LOSNOTE_DB=/data/losnote.db \
  -v losnote-data:/data \
  losnote:latest
docker logs -f losnote    # 看到「监听中」即可
```

之后备份/恢复/升级把 `docker compose xxx` 换成 `docker xxx losnote` 即可。
（要装 compose 插件：`apt install docker-compose-plugin` 或从
`https://github.com/docker/compose/releases` 下 `docker-compose-linux-x86_64`
放到 `~/.docker/cli-plugins/docker-compose` 并 `chmod +x`。）

## 7. 升级

```bash
cd /opt/losnote && git pull
docker compose up -d --build      # 重建镜像，数据卷不受影响
```

## 8. 排错

| 现象 | 检查 |
|---|---|
| 手机同步报「服务器返回 401」 | 令牌不一致（两端大小写/空格） |
| 手机连不上、网页能开 | 用了 http 但 App 装的是 release？本项目已开 `usesCleartextTraffic`；确认走的是内网 IP 而不是 `localhost` |
| 网页白屏 | `curl -I https://域名/assets/xxx.js` 看静态资源是否 200；看 Caddy 日志 |
| 同步一直 0 条 | 手机上没配置成功，或在「云同步」里没点保存；`/api/health` 先确认连通 |
| 服务器日志 | `docker compose logs -f --tail=100` |

## 没有 git / 不能推送时：用发布包部署

仓库里的 `scripts/make-release.sh` 会打一个只含部署所需文件的小包（约 50 KB），
拷到服务器解压即可，不需要 clone，也不依赖仓库是否已推送：

```bash
# 本机（WSL）
bash scripts/make-release.sh            # 生成 dist/losnote-server-<commit>.tar.gz

# 传到服务器
scp dist/losnote-server-*.tar.gz user@你的服务器:~/

# 服务器上（已装 Docker）
tar xzf losnote-server-*.tar.gz && cd losnote-*/
echo "LOSNOTE_TOKEN=$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 32)" > .env
docker compose up -d --build
```

镜像会在服务器上构建（会下载一次 npm 依赖，约 1–2 分钟）。构建完成后
`curl http://127.0.0.1:8787/api/health` 应返回 `{"ok":true,...}`。

## 已部署实例（2026-11-06）

| 项 | 值 |
| --- | --- |
| 服务器 | `root@47.111.175.191`（Ubuntu 26.04，Docker 29.1.3 + compose 2.40.3） |
| 代码目录 | `/opt/losnote-3dfb6e2`（发布包解压，非 git 仓库） |
| 令牌 | 在服务器 `/opt/losnote-3dfb6e2/.env` 的 `LOSNOTE_TOKEN` 里（不要提交进仓库） |
| 数据卷 | `losnote_losnote-data`（`COMPOSE_PROJECT_NAME=losnote` 固定，换目录不影响数据） |
| 容器 | `losnote`，`0.0.0.0:8787->8787`，`restart: unless-stopped` |

升级：把新发布包 scp 上去解压到新目录，复制旧 `.env`，在新目录 `docker compose up -d --build`
（同名容器会替换；数据卷名由 `COMPOSE_PROJECT_NAME` 决定，不会丢）。

**注意**：该云主机安全组是白名单，当前放行 `22 / 8080 / 8081 / 8082 / 8790`，
所以要外网访问 LosNote 还需在云控制台安全组里放行 `8787`（或 80/443 + 反代）。
放行前可以用 `ssh -L 8788:127.0.0.1:8787 root@47.111.175.191` 隧道先在本地看。
