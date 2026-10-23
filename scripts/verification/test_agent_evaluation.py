import importlib.util
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('score_agent_evaluation',ROOT/'scripts/verification/score_agent_evaluation.py')
scorer=importlib.util.module_from_spec(spec);spec.loader.exec_module(scorer)

class AgentEvaluationTest(unittest.TestCase):
    def test_partial_plan_is_never_full_success(self):
        scored=scorer.score_case({'caseId':'p1','mode':'agent','phase':'partial','expectedOutcome':'complete','steps':['search']}, {'reviewer':'teacher','decision':'pass'})
        self.assertFalse(scored['fullSuccess'])
        self.assertTrue(scored['taskSuccess'])

    def test_unreviewed_failed_and_cancelled_calls_remain_in_denominator(self):
        rows=scorer.score([
            {'caseId':'ok','phase':'success','expectedOutcome':'complete'},
            {'caseId':'bad','phase':'failed','expectedOutcome':'complete'},
            {'caseId':'stop','phase':'cancelled','expectedOutcome':'complete'},
        ], {})
        self.assertEqual(len(rows),3)
        self.assertEqual(sum(row['taskSuccess'] is True for row in rows),0)
        self.assertEqual(sum(row['phase']=='failed' for row in rows),1)
        self.assertEqual(sum(row['phase']=='cancelled' for row in rows),1)

    def test_pricing_snapshot_and_unknown_usage_are_preserved(self):
        scored=scorer.score_case({'caseId':'u1','phase':'success','expectedOutcome':'complete','actualUsage':None,'estimatedCost':None,'priceVersion':'fixture-v2'},None)
        self.assertIsNone(scored['actualUsage'])
        self.assertIsNone(scored['estimatedCost'])
        self.assertEqual(scored['priceVersion'],'fixture-v2')
        self.assertIsNone(scored['taskSuccess'])

if __name__=='__main__':unittest.main()
