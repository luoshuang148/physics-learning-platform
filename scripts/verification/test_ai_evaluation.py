import importlib.util,json,tempfile,unittest,subprocess,sys,hashlib
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

 def test_default_cases_path_points_to_existing_repository_fixture(self):
  spec=importlib.util.spec_from_file_location('ai_evaluation',Path(__file__).with_name('ai_evaluation.py'))
  module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  self.assertTrue(callable(getattr(module,'default_cases_path',None)), 'Missing repository fixture locator')
  path=module.default_cases_path()
  self.assertTrue(path.is_file())
  self.assertEqual(len([line for line in path.read_text().splitlines() if line.strip()]),10)

 def test_default_cli_prepares_ten_cases_with_provenance_without_credentials(self):
  with tempfile.TemporaryDirectory() as folder:
   output=Path(folder)/'prepared.jsonl'
   result=subprocess.run([sys.executable,str(Path(__file__).with_name('ai_evaluation.py')),
     '--model','mock-no-network','--output',str(output)],capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)
   records=[json.loads(line) for line in output.read_text().splitlines()]
   self.assertEqual(len(records),10)
   revision=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
   fixture=Path(__file__).resolve().parents[2]/'src/test/resources/ai-evaluation/cases.jsonl'
   for record in records:
    self.assertEqual(record['phase'],'prepared')
    self.assertEqual(record['reviewStatus'],'pending')
    self.assertEqual(record['mode'],'baseline')
    self.assertEqual(record['codeSha'],revision)
    self.assertEqual(record['corpusVersion'],'sha256:'+hashlib.sha256(fixture.read_bytes()).hexdigest())
    self.assertTrue(record['capturedAt'])
   self.assertEqual(output.stat().st_mode & 0o777,0o600)

if __name__=='__main__':unittest.main()
