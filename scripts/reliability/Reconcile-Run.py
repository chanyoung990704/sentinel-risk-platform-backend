"""기록한 요청 분모와 DB 관찰을 독립 대조한다."""
import argparse,json
from pathlib import Path
from reconcile import reconcile
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('input',type=Path);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    data=json.loads(args.input.read_text());result=reconcile(data['acceptedIds'],data['rows'],data.get('expectedFailed',[]),data.get('quarantinedMessages',0))
    with args.output.open('x') as file:json.dump(result,file,ensure_ascii=False,indent=2)
    print(json.dumps(result,ensure_ascii=False));raise SystemExit(0 if result['passed'] else 2)
