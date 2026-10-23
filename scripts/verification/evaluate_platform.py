#!/usr/bin/env python3
"""Bounded, provenance-preserving runner for the isolated tutoring API."""
import argparse, datetime, hashlib, json, os, subprocess, time, urllib.error, urllib.request
from pathlib import Path
from urllib.parse import urlparse

ROOT=Path(__file__).resolve().parents[2]
MODES={'baseline':'OFF','vector':'VECTOR','hybrid':'HYBRID'}
SPLITS={'development','holdout'}

def corpus_path(split):
    if split not in SPLITS: raise ValueError('split must be development or holdout')
    return ROOT/'docs/ai-evaluation/corpus-v1'/f'{split}.jsonl'

def read_cases(split):
    return [json.loads(line) for line in corpus_path(split).read_text(encoding='utf-8').splitlines() if line.strip()]

def assert_tunable_split(split):
    if split!='development': raise ValueError('holdout must never enter tuning')

def assert_isolated_url(url):
    if urlparse(url).hostname not in ('localhost','127.0.0.1','::1'):
        raise ValueError('model calls are restricted to a loopback isolated platform')

def build_payload(case,mode):
    if mode not in MODES: raise ValueError(f'unknown mode: {mode}')
    request=case.get('platformRequest') or {}
    return {'questionId':request.get('questionId'),'recordId':request.get('recordId'),
            'question':case['question'],'knowledgeMode':MODES[mode]}

def cost_ledger(estimated_cost,usage,price_version):
    if estimated_cost is None or estimated_cost<0 or not price_version:
        raise ValueError('a non-negative estimated cost and price version are required')
    return {'estimatedCostCny':round(estimated_cost,6),'actualUsage':usage,
            'actualCostCny':None if usage is None else None,'priceVersion':price_version}

def repository_revision():
    try: return subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True,stderr=subprocess.DEVNULL,timeout=3).strip()
    except (OSError,subprocess.SubprocessError): return 'unknown'

def parse_sse(response):
    event='message'; data=[]
    for raw in response.read().decode('utf-8','replace').splitlines()+['']:
        if not raw:
            if data: yield event,'\n'.join(data)
            event='message';data=[]
        elif raw.startswith('event:'): event=raw[6:].strip()
        elif raw.startswith('data:'): data.append(raw[5:].lstrip(' '))

def invoke(base_url,token,session_id,payload):
    request=urllib.request.Request(base_url.rstrip('/')+'/ai/student/wrong-question-analysis',
        data=json.dumps(payload,ensure_ascii=False).encode(),headers={'Authorization':'Bearer '+token,
        'X-Session-Id':session_id,'Content-Type':'application/json','Accept':'text/event-stream'})
    with urllib.request.urlopen(request,timeout=120) as response:
        answer=[];sources=[];usage=None;phase='captured'
        for event,data in parse_sse(response):
            if event=='content': answer.append(data)
            elif event=='sources':
                try:sources=json.loads(data)
                except ValueError:pass
            elif event=='complete':
                try:
                    result=json.loads(data);usage={k:result[k] for k in ('promptTokens','completionTokens','totalTokens') if k in result}
                except ValueError:usage=None
            elif event=='error': phase='failed'
        return {'output':''.join(answer),'sources':sources,'usage':usage,'phase':phase}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--split',choices=sorted(SPLITS),default='development');parser.add_argument('--mode',choices=sorted(MODES),default='hybrid')
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--base-url',default='http://127.0.0.1:8080/api/v1')
    parser.add_argument('--api-key-env',default='AI_EVAL_BEARER_TOKEN');parser.add_argument('--session-prefix',default='ai-eval')
    parser.add_argument('--max-requests',type=int,default=0);parser.add_argument('--max-estimated-cost-cny',type=float,default=0)
    parser.add_argument('--estimated-cost-cny-per-request',type=float);parser.add_argument('--price-version')
    parser.add_argument('--allow-model-calls',action='store_true');parser.add_argument('--tuning',action='store_true')
    args=parser.parse_args()
    if args.tuning: assert_tunable_split(args.split)
    cases=read_cases(args.split);limit=min(len(cases),args.max_requests) if args.max_requests else len(cases)
    if args.max_requests<0 or args.max_estimated_cost_cny<0: parser.error('request and cost limits must be non-negative')
    token=os.getenv(args.api_key_env,'')
    if args.allow_model_calls:
        try: assert_isolated_url(args.base_url)
        except ValueError as error: parser.error(str(error))
        if not token: parser.error(f'{args.api_key_env} is empty')
        if not args.max_requests or not args.max_estimated_cost_cny or args.estimated_cost_cny_per_request is None or not args.price_version:
            parser.error('model calls require positive request/cost caps, per-request estimate and price version')
        if args.estimated_cost_cny_per_request<0: parser.error('per-request estimate must be non-negative')
        if limit*args.estimated_cost_cny_per_request>args.max_estimated_cost_cny: parser.error('estimated maximum exceeds cost budget')
    corpus=corpus_path(args.split).read_bytes();version='sha256:'+hashlib.sha256(corpus).hexdigest()
    args.output.parent.mkdir(parents=True,exist_ok=True);done=0;reserved=0.0
    with args.output.open('x',encoding='utf-8') as out:
        args.output.chmod(0o600)
        for case in cases[:limit]:
            record={'caseId':case['id'],'category':case.get('category'),'familyId':case.get('familyId'),
                'split':args.split,'mode':args.mode,'payload':build_payload(case,args.mode),
                'expectedSourceIds':case.get('expectedSourceIds',[]),'codeSha':repository_revision(),'corpusVersion':version,
                'capturedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'phase':'prepared',
                'reviewStatus':'pending','priceVersion':args.price_version}
            if args.allow_model_calls:
                if not record['payload'].get('recordId') or not record['payload'].get('questionId'):
                    record['phase']='unmapped';record['error']='case_missing_isolated_platform_record'
                elif reserved+args.estimated_cost_cny_per_request>args.max_estimated_cost_cny:
                    record['phase']='budget_stopped';record['error']='estimated_cost_budget_exhausted'
                else:
                    reserved+=args.estimated_cost_cny_per_request;record.update(cost_ledger(args.estimated_cost_cny_per_request,None,args.price_version))
                    record['estimatedCostCny']=args.estimated_cost_cny_per_request
                    try:
                        started=time.monotonic();result=invoke(args.base_url,token,f"{args.session_prefix}-{case['id']}",record['payload'])
                        record.update(result,elapsedMs=round((time.monotonic()-started)*1000))
                    except urllib.error.HTTPError as error: record.update(phase='failed',error='HTTP_'+str(error.code))
                    except Exception as error: record.update(phase='failed',error=type(error).__name__)
            out.write(json.dumps(record,ensure_ascii=False)+'\n');out.flush();done+=1
            if record['phase']=='failed': break
    print(f'Saved {done} {args.split} records ({args.mode}); review remains pending: {args.output}')

if __name__=='__main__': main()
