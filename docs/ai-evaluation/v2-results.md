# 平台题库 RAG v2 评测结果

生成时间：2026-10-24 13:15:56
语料版本：platform-rag-v2
执行方式：HTTP 调用本地 API（`http://127.0.0.1:8080/api/v1`）

## 诚实声明

- 本表为**实测**数字，不是目标值；目标见 corpus-v2/manifest.json。
- Hit@5 分母只含「存在相关题」的样例；空检索任务单独统计。
- 引用有效性只校验来源 ID 集合归属，**不等同**语义支持正确。
- 无物理专业复核时，评测为开发者核对。

## split = development

| 指标 | 结果 |
|---|---|
| 样例数 | 30 |
| Hit@5 | 96.3%（26/27） |
| 无结果处理 | 3/3 |
| 引用结构有效 | 100.0%（112/112） |
| 延迟 p50/p95 | 49ms / 88ms |

失败样例（截断至 10 条）：

- `dev-017` {"caseId": "dev-017", "query": "牛顿第三定律的内容", "expected": ["newton-03"], "returned": ["newton-01"], "status": "MATCHED", "actualMode": "lexical_only"}

## split = holdout

| 指标 | 结果 |
|---|---|
| 样例数 | 20 |
| Hit@5 | 88.2%（15/17） |
| 无结果处理 | 3/3 |
| 引用结构有效 | 100.0%（73/73） |
| 延迟 p50/p95 | 47ms / 90ms |

失败样例（截断至 10 条）：

- `hold-001` {"caseId": "hold-001", "query": "作用力与反作用力的特点", "expected": ["newton-03"], "returned": ["energy-02", "friction-02", "friction-03", "newton-02"], "status": "MATCHED", "actualMode": "lexical_only"}
- `hold-009` {"caseId": "hold-009", "query": "动能公式是什么", "expected": ["energy-01"], "returned": [], "status": "INSUFFICIENT", "actualMode": "hybrid"}

