#!/usr/bin/env python3
"""Summarize all outcomes without dropping failures, cancellations or unreviewed cases."""
import argparse,json
from collections import Counter,defaultdict
from pathlib import Path

def compare(paths):
    groups=defaultdict(list)
    for path in paths:
        for line in Path(path).read_text(encoding='utf-8').splitlines():
            if not line.strip():continue
            row=json.loads(line);groups[row.get('mode','unknown')].append(row)
    report={}
    for mode,rows in groups.items():
        report[mode]=summarize(rows)
        categories=defaultdict(list)
        for row in rows: categories[row.get('category') or 'unclassified'].append(row)
        report[mode]['byCategory']={key:summarize(value) for key,value in sorted(categories.items())}
    return report

def summarize(rows):
    scores={row['caseId']:row for row in rows if 'pass' in row}
    phases=Counter(row.get('phase','reviewed') for row in rows)
    reviewed=[r for r in scores.values() if r.get('pass') is not None]
    unreviewed=[r for r in rows if r.get('pass') is None and r.get('phase') not in ('failed','cancelled','budget_stopped','unmapped')]
    return {'denominator':len(rows),'phases':dict(sorted(phases.items())),
        'reviewed':len(reviewed),'unreviewed':len(unreviewed),
        'passed':sum(r.get('pass') is True for r in reviewed),
        'failedCaseIds':[r['caseId'] for r in reviewed if r.get('pass') is False],
        'cancelledCaseIds':[r['caseId'] for r in rows if r.get('phase')=='cancelled'],
        'unreviewedCaseIds':[r['caseId'] for r in unreviewed]}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('files',nargs='+',type=Path);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();args.output.write_text(json.dumps(compare(args.files),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':main()
