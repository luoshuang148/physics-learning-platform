# 知学物理 v1.0.0 — 发布准备（尚未发布）

面向中学课堂和二次开发的开源物理教学平台。支持班级/账号、题库与作业、在线作答、错题本、学情分析、可选 AI 辅导/批量报告与 3D 实验。

Java 21 + Spring Boot 3 + Vue 3 + MySQL / Redis / RabbitMQ。演示入口：http://47.110.38.57；本地体验：`./scripts/demo-up.sh`，具体依赖与账号见 README。

边界：没有生产流量或分布式性能证明；演示 AI 默认关闭；初次镜像构建耗时受网络影响；在线演示数据可被重置，不输入真实信息。

本轮前端 10 项测试与生产构建通过，在线截图 6 张已核对，错误学生密码使截图非零退出。Compose 合成配置另行检查；本机无运行中的 Docker daemon，容器端到端登录/种子与本轮后端回归未验证，不能据此发布稳定安装体验承诺。历史 CI 不代替本轮结果。

确认最终 commit 和全套 CI/演示验收后，才建议执行（以下命令未执行）：

```sh
git tag -a v1.0.0 -m '知学物理 v1.0.0' VERIFIED_COMMIT
git push origin v1.0.0
gh release create v1.0.0 --repo luoshuang148/physics-learning-platform --verify-tag --title '知学物理 v1.0.0' --notes-file docs/release-notes-v1.0.0.md --draft
```

先核实 tag 不存在，替换 VERIFIED_COMMIT；公开发布需要单独确认。
