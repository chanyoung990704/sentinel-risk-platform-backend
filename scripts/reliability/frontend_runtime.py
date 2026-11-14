"""Ubuntu 전용 프런트 소스 동기화. 원본/비밀/생성물은 복사하지 않는다."""
import os
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = Path.home() / '.local/share/sentinel/workspaces/r07-frontend'

def sync():
    if os.name != 'posix':
        raise RuntimeError('Ubuntu에서 실행해야 합니다')
    TARGET.mkdir(parents=True, exist_ok=True)
    for item in (ROOT / 'frontend').rglob('*'):
        rel = item.relative_to(ROOT / 'frontend')
        if any(part in ('.git', 'node_modules', 'dist', 'test-results', 'playwright-report') for part in rel.parts):
            continue
        if item.is_file() and not item.is_symlink() and not item.name.startswith('.env'):
            dest = TARGET / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(item, dest)
    return TARGET

if __name__ == '__main__':
    print(sync())
