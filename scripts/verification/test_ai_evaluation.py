import importlib.util,json,tempfile,unittest
from pathlib import Path

class EvaluationCaptureTest(unittest.TestCase):
 def test_preparation_keeps_reference_out_of_model_prompt_and_marks_review_pending(self):
  spec=importlib.util.spec_from_file_location('ai_evaluation',Path(__file__).with_name('ai_evaluation.py'))
  module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  case={'id':'fixture','question':'2kg receives 6N','options':{'A':'3','B':'12'},'studentAnswer':'B','referenceAnswer':'PRIVATE_REFERENCE','rubric':['PRIVATE_RUBRIC']}
  captured=module.prepare(case,'fixture-model')
  self.assertEqual(captured['reviewStatus'],'pending')
  self.assertEqual(len(captured['inputSha256']),64)
  self.assertNotIn('PRIVATE_REFERENCE',captured['prompt'])
  self.assertNotIn('PRIVATE_RUBRIC',captured['prompt'])
  self.assertEqual(captured['model'],'fixture-model')

if __name__=='__main__':unittest.main()
