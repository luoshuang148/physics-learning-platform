# 知识索引运行与重建

知识库默认关闭。启用前先启动固定版本 Qdrant，再将应用配置 `APP_KNOWLEDGE_ENABLED=true`、`APP_KNOWLEDGE_QDRANT_URL=http://qdrant:6333`。本地单独启动向量服务：

```sh
docker compose --profile knowledge up -d qdrant
```

容器使用 `qdrant/qdrant:v1.19.2`，`/data/qdrant` 保存向量数据，内存限制为 512 MiB。隔离开发环境的空载采样为 18.4 MiB；这不是有写入负载时的峰值，也不能作为生产容量结论。教师上传的原始文件保存在 `data/knowledge`，MySQL 保存文档、修订、chunk 和活动索引版本；三者都需要纳入备份。

每个 collection 由 revision、Embedding 模型/维度、PDF 提取器和切分器版本共同确定。索引 worker 最多并行 2 个，领取任务时写入有期限的租约，处理每批向量后续租；进程重启后，未开始的 revision 和租约过期的 revision 会再次入队。只有向量写入完成且 point 数量与 MySQL chunk 数相同后，系统才将 revision 标为 READY 并切换活动版本。失败版本会被标记为 FAILED，之前活动版本继续可读。

如果 Qdrant 数据卷丢失，保留 MySQL 和原始文件，启动 Qdrant 后通过 `POST /api/v1/knowledge/documents/{id}/reindex` 为每份活动资料创建新 revision。索引完成后每份资料会切到新 collection；失败资料可再次创建 revision 重试。这个步骤会重新运行本地 Embedding，因此耗时取决于资料量和机器性能。不要在完成重建和抽样验证前删除旧备份。

Docker Compose 的 `knowledge` profile 不会自动开启应用功能；两者需一起启用。模型当前只实现本地 BGE-small-zh-v1.5 quantized；把配置 ID 改成别的模型会明确报 MODEL_MISMATCH，不会把不同模型的向量混进同一索引。
