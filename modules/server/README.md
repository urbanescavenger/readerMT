# reader

阅读3服务器版，不需要手机。

> Fork 自 [hectorqin/reader](https://github.com/hectorqin/reader)，已合并社区 PR（#648 #653 #667 #668 #701）并持续维护。

## 功能

书源管理、书架管理、搜索、书海、看书、移动端适配、换源、翻页方式、手势支持、自定义主题、WebDAV 同步、文字替换过滤、听书、漫画、音频、本地书导入（TXT/EPUB/UMD/PDF）、书籍分组、RSS 订阅、定时更新书架、并发搜书、本地书仓、Kindle 阅读。

## Docker 部署

```bash
# 标准版（推荐）
docker pull changshengyu/reader:latest

# Slim 版
# docker pull changshengyu/reader:slim-latest

# OpenJ9 版（启动快、省内存）
# docker pull changshengyu/reader:openj9-latest
```

```bash
docker run -d \
  --name reader \
  -p 4396:8080 \
  -v /home/reader/logs:/logs \
  -v /home/reader/storage:/storage \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e READER_APP_SECURE=true \
  -e READER_APP_SECUREKEY=adminpwd \
  -e READER_APP_INVITECODE=registercode \
  changshengyu/reader:latest
```

或使用 docker-compose：

```bash
wget https://raw.githubusercontent.com/changshengyu/reader-dev/master/docker-compose.yaml
docker-compose up -d
```

## 自行构建

```bash
docker build -f Dockerfile.source -t reader:latest .
docker build -f Dockerfile.slim -t reader:slim-latest .
docker build -f Dockerfile.openj9 -t reader:openj9-latest .
```

## 感谢

- [hectorqin/reader](https://github.com/hectorqin/reader)
- [阅读3.0](https://github.com/gedoor/legado)
