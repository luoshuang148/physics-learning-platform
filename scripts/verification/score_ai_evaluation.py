#!/usr/bin/env python3
"""Join independently entered human reviews to captured runs; never auto-grade correctness."""
import argparse,json,re
from pathlib import Path

MARKER=re.compile(r'\[(S[1-9][0-9]*)\]')

def score_case(run,review):
    source_ids={item.get('sourceId') for item in run.get('sources',[]) if item.get('sourceId')}
    markers=set(MARKER.findall(run.get('output','')))
    citation_valid=bool(markers) and markers.issubset(source_ids) if run.get('mode')!='baseline' else not markers
    if not review or not review.get('reviewer') or review.get('correctness') not in (True,False) or review.get('supportReview') not in (True,False):
        return {'caseId':run['caseId'],'pass':None,'citationValid':citation_valid,'supportReview':'pending','reviewer':None,'reasons':['not_manually_reviewed']}
    passed=bool(review['correctness'] and review['supportReview'] and citation_valid)
    reasons=list(review.get('reasons') or [])
    if not citation_valid: reasons.append('citation_invalid')
    return {'caseId':run['caseId'],'pass':passed,'citationValid':citation_valid,
        'supportReview':'pass' if review['supportReview'] else 'fail','reviewer':review['reviewer'],'reasons':reasons}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runs',type=Path,required=True);parser.add_argument('--reviews',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();reviews={json.loads(line)['caseId']:json.loads(line) for line in args.reviews.read_text(encoding='utf-8').splitlines() if line.strip()}
    with args.output.open('x',encoding='utf-8') as out:
        for line in args.runs.read_text(encoding='utf-8').splitlines():
            if line.strip(): out.write(json.dumps(score_case(json.loads(line),reviews.get(json.loads(line)['caseId'])),ensure_ascii=False)+'\n')

if __name__=='__main__':main()
