import hashlib
import json
from pathlib import Path
import unittest
from evaluation_corpus import prepare_model_case

ROOT = Path(__file__).resolve().parents[2] / 'docs/ai-evaluation/corpus-v1'

class EvaluationCorpusTest(unittest.TestCase):
    def records(self, split):
        path=ROOT/(split+'.jsonl')
        self.assertTrue(path.is_file(), 'Missing versioned '+split+' corpus')
        return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]

    def test_splits_are_disjoint_and_have_expected_category_counts(self):
        development=self.records('development');holdout=self.records('holdout')
        self.assertEqual(len(development),60);self.assertEqual(len(holdout),40)
        self.assertTrue({x['id'] for x in development}.isdisjoint(x['id'] for x in holdout))
        self.assertTrue({x['familyId'] for x in development}.isdisjoint(x['familyId'] for x in holdout))
        counts={}
        for case in development+holdout: counts[case['category']]=counts.get(case['category'],0)+1
        self.assertEqual(counts,{'concept_calculation':40,'wrong_answer':20,'insufficient':15,'instruction_interference':10,'followup':15})

    def test_references_resolve_to_versioned_documents(self):
        manifest_path=ROOT/'manifest.json';self.assertTrue(manifest_path.is_file())
        manifest=json.loads(manifest_path.read_text());sources=set()
        for document in manifest['documents']:
            content=(ROOT/document['path']).read_bytes()
            self.assertEqual(hashlib.sha256(content).hexdigest(),document['sha256'])
            sources.update(document['id']+'#'+anchor for anchor in document['anchors'])
        for split in ['development','holdout']:
            for case in self.records(split):
                self.assertTrue(set(case['expectedSourceIds']) <= sources,case['id'])
                self.assertTrue(case['referenceAnswer'])
                self.assertGreaterEqual(len(case['rubric']),3)
                self.assertEqual(case['reviewStatus'],'pending')
                self.assertEqual(case['reviewer'],None)
        self.assertEqual(len(self.records('agent-tasks')),20)

    def test_holdout_matches_frozen_manifest_hash(self):
        path=ROOT/'manifest.json';self.assertTrue(path.is_file())
        manifest=json.loads(path.read_text())
        self.assertEqual(hashlib.sha256((ROOT/'holdout.jsonl').read_bytes()).hexdigest(),manifest['frozenHoldoutSha256'])

    def test_scoring_fields_are_not_model_context(self):
        case={'id':'fixture','question':'问力','referenceAnswer':'PRIVATE_GOLD','rubric':['PRIVATE_RUBRIC'],
              'expectedSourceIds':['PRIVATE_SOURCE'], 'allowedContext':{
                  'studentAnswer':'B','rubric':'PRIVATE_NESTED','questionSnapshot':{
                      'content':'真实题目','correctOption':'PRIVATE_CORRECT','referenceAnswer':'PRIVATE_REF'},
                  'previousTurns':[{'role':'user','content':'此前追问','rubric':'PRIVATE_TURN'}]}}
        prepared=prepare_model_case(case);text=json.dumps(prepared,ensure_ascii=False)
        self.assertNotIn('PRIVATE_',text)
        self.assertIn('allowedContext',prepared)
        self.assertEqual(prepared['allowedContext']['studentAnswer'],'B')
        self.assertEqual(prepared['allowedContext']['questionSnapshot']['content'],'真实题目')
        self.assertEqual(prepared['allowedContext']['previousTurns'],[{'role':'user','content':'此前追问'}])

if __name__=='__main__':unittest.main()
