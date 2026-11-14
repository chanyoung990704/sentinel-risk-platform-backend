# SENTINEL 로컬 프런트 복원

보관된 기존 React/Vite 프런트의 소스·공개 정적 자산을 복원한 디렉터리다. 원본 `.git`, `.env`, `node_modules`, `dist`와 개인 로그는 가져오지 않았다. 백엔드의 기존 공개 API와 화면을 유지하며 기업 상세에 맞춤 보고서 요청 패널을 추가했다.

Ubuntu native 사본에서 `npm ci` 후 실행한다. 기본 브라우저 주소는 `http://localhost:3000`, 백엔드는 `http://localhost:18080`이다.

```bash
VITE_API_BASE_URL=http://localhost:18080 npm run dev -- --host 127.0.0.1 --strictPort
VITE_API_BASE_URL=http://localhost:18080 npm run build
VITE_API_BASE_URL=http://localhost:18080 npm run test:e2e -- --project=chromium custom-report.spec.ts
```

`VITE_USE_MOCK_AUTH`를 켜지 않아야 실제 로그인/JWT를 사용한다. 모의 AI는 로컬 백엔드 설정에서만 연결한다. CSS는 npm에 고정한 Tailwind/PostCSS로 빌드한다. 원본의 외부 폰트·아이콘·배경/프로필 이미지에는 인터넷이 필요하며 실제 브라우저 시험에서는 이 자산이 실제 백엔드/PDF 처리를 대신하지 않는다. 프런트 번들 크기 개선과 전면적인 디자인 변경은 이번 범위에 포함하지 않았다.

실행 조건과 측정 한계는 [실행 계약](../docs/runtime/browser-and-measurement.md)에 기록한다. `.env*`와 자격 정보·브라우저 trace를 저장소에 추가하지 않는다.
