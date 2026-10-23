# M1 AI 辅导评测状态

**状态：评测工具已就绪，业务评测与人工评分尚未执行；本页不报告准确率或引用支持率。**

语料为自编和 AI 辅助参数化样例，不含真实学生课堂数据。corpus-v1 有 60 个开发案例、40 个冻结留出案例，讲义和评分标准仍待教师复核。开发集每个案例尚未映射到隔离平台数据库中的作答记录（`questionId`/`recordId`），因此没有调用模型 API；留出集没有进入调参。

已运行无网络准备：baseline、vector、hybrid 各准备 60 个开发集请求记录。三种模式都是 0 已评、60 待评；这只是输入准备计数，不是模型效果。准备文件保存在被忽略的 `.local/` 目录，不入库。

## 可复现步骤

先在一次性本地数据库创建并授权对应案例的错题作答记录，把每个案例映射写入运行输入的 `platformRequest.questionId` 与 `platformRequest.recordId`。平台使用隔离的讲义、模型 mock 或经过预算审批的测试凭据。不要把生产账号、真实学生数据或真实密钥写入仓库。

默认只准备输入，不调用网络：

```bash
python3 scripts/verification/evaluate_platform.py --split development --mode hybrid --output .local/hybrid-dev.jsonl
```

显式业务调用必须同时指定请求数、每次估算费用、总费用上限、价格版本并设置独立 bearer token 环境变量：

```bash
python3 scripts/verification/evaluate_platform.py \
  --split development --mode hybrid --max-requests 10 \
  --estimated-cost-cny-per-request 0.10 --max-estimated-cost-cny 1.00 \
  --price-version 'provider-price-sheet-YYYY-MM-DD' \
  --allow-model-calls --output .local/hybrid-dev.jsonl
```

审阅者另行填写 JSONL，每行含 `caseId`、`correctness`、`supportReview`、`reviewer` 和 `reasons`；没有人工记录的案例保持 `pass: null`。随后运行 `score_ai_evaluation.py` 与 `compare_ai_evaluation.py`。冻结方案后才对留出集跑 baseline 与最终模式，不可把留出结果回用于阈值或提示词调整。

## 当前限制

- 开发集 Recall@5、阈值误拒率、A/B/C 生成效果与成本对照：待执行。
- 留出集冻结评测：待开发集配置冻结后执行。
- 教师复核：待进行；不得把自动结构检查称为教学正确性评测。
- 实际请求用量缺失时保留 `actualUsage: null`、`actualCostCny: null`，不会按零成本统计。
