"""Ubuntu에서 원자료를 재계산한다. 이미 저장된 요약은 덮어쓰지 않는다."""
import argparse
from pathlib import Path
from portfolio_summary import write_summary
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run_dir',type=Path)
    print(write_summary(parser.parse_args().run_dir))
