import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts/verification' / f'{name}.py')
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module); return module

class PlatformRunnerTest(unittest.TestCase):
    def test_platform_runner_uses_selected_mode(self):
        runner=load('evaluate_platform')
        case={'id':'case-1','question':'q','platformRequest':{'questionId':1,'recordId':'r1'}}
        self.assertEqual(runner.build_payload(case,'vector')['knowledgeMode'],'VECTOR')
        self.assertEqual(runner.build_payload(case,'baseline')['knowledgeMode'],'OFF')

    def test_holdout_never_enters_tuning(self):
        runner=load('evaluate_platform')
        with self.assertRaisesRegex(ValueError,'holdout'):
            runner.assert_tunable_split('holdout')
        runner.assert_tunable_split('development')

    def test_model_calls_are_restricted_to_loopback(self):
        runner=load('evaluate_platform')
        runner.assert_isolated_url('http://127.0.0.1:8080/api/v1')
        with self.assertRaisesRegex(ValueError,'loopback'):
            runner.assert_isolated_url('https://api.example.com/v1')

class EvaluationReviewTest(unittest.TestCase):
    def test_unreviewed_cases_are_not_passes(self):
        scorer=load('score_ai_evaluation')
        run={'caseId':'case-1','phase':'captured','sourceIds':['S1'],'output':'据[S1]可得结论。'}
        result=scorer.score_case(run,None)
        self.assertIsNone(result['pass'])
        self.assertEqual(result['supportReview'],'pending')

    def test_missing_usage_is_not_free(self):
        runner=load('evaluate_platform')
        estimate=runner.cost_ledger(estimated_cost=0.25,usage=None,price_version='fixture-2026-01')
        self.assertEqual(estimate['estimatedCostCny'],0.25)
        self.assertIsNone(estimate['actualUsage'])
        self.assertIsNone(estimate['actualCostCny'])
        with self.assertRaisesRegex(ValueError,'price version'):
            runner.cost_ledger(estimated_cost=None,usage=None,price_version=None)

    def test_comparison_preserves_failures_cancellations_and_unreviewed_denominator(self):
        comparer=load('compare_ai_evaluation')
        with tempfile.TemporaryDirectory() as folder:
            source=Path(folder)/'runs.jsonl'
            source.write_text('\n'.join(json.dumps(row) for row in [
                {'caseId':'a','mode':'hybrid','phase':'failed'},
                {'caseId':'b','mode':'hybrid','phase':'cancelled'},
                {'caseId':'c','mode':'hybrid','phase':'captured','pass':None},
            ]))
            report=comparer.compare([source])['hybrid']
        self.assertEqual(report['denominator'],3)
        self.assertEqual(report['phases'],{'cancelled':1,'captured':1,'failed':1})
        self.assertEqual(report['unreviewedCaseIds'],['c'])
        self.assertEqual(report['cancelledCaseIds'],['b'])

if __name__=='__main__': unittest.main()
