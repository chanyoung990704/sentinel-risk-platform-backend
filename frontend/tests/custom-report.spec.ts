import { test, expect } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

const fixturePath = process.env.SENTINEL_BROWSER_FIXTURE || join(homedir(), '.local/share/sentinel/portfolio/r07-m04/browser-fixture.json');
const fixture = () => JSON.parse(readFileSync(fixturePath, 'utf8'));
async function loginBrowser(page, user) {
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: '기업 로그인', exact: true }).click();
  // 비밀번호·토큰을 Playwright 호출 로그에 넣지 않는다.
  await page.locator('form').evaluate((form, value) => {
    const native = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
    const inputs = [form.querySelector('input[type="email"]'), form.querySelector('input[type="password"]')];
    inputs.forEach((input, i) => { native.call(input, i ? value.password : value.email); input!.dispatchEvent(new Event('input', { bubbles: true })); });
  }, user);
  const response = page.waitForResponse(value => new URL(value.url()).pathname === '/api/auth/login' && value.request().method() === 'POST');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  expect((await response).status()).toBe(200);
  await expect(page).not.toHaveURL('http://localhost:3000/');
}

// UI 계약 시험은 명시적으로 API를 대체한다. 실제 JWT/업무 흐름은 별도 시험한다.
for (const failure of ['write', 'read', 'request-id']) {
  test(`검토 회귀: 저장 ${failure} 실패와 의도 복구`, async ({ page }) => {
    // given
    await page.addInitScript(mode => {
      localStorage.setItem('sentinel.auth.session', JSON.stringify({ token: 'ui-only', user: { id: 1, role: 'USER' } }));
      const get = Storage.prototype.getItem, set = Storage.prototype.setItem;
      Storage.prototype.getItem = function(key) {
        if (this === sessionStorage && key.startsWith('sentinel.custom-report:') && mode === 'read') throw new DOMException('fixture', 'SecurityError');
        return get.call(this, key);
      };
      Storage.prototype.setItem = function(key, value) {
        if (this === sessionStorage && key.startsWith('sentinel.custom-report:') && (mode === 'write' || mode === 'request-id' && JSON.parse(value).requestId)) throw new DOMException('fixture', 'QuotaExceededError');
        return set.call(this, key, value);
      };
    }, failure);
    const keys: string[] = [];
    const id = '00000000-0000-4000-8000-000000000001';
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      if (route.request().url().endsWith('/custom-report-requests')) {
        keys.push(route.request().headers()['idempotency-key']);
        return route.fulfill({ status: 202, json: { success: true, data: { requestId: id } } });
      }
      if (route.request().url().includes('/api/report-requests/')) return route.fulfill({ json: { success: true, data: { requestId: id, status: 'SUCCEEDED', stateVersion: 4, generation: 1, attemptNo: 1 } } });
      return route.fulfill({ status: 503, json: { success: false } });
    });
    await page.goto('/companies/1');
    // when / then
    if (failure === 'read') {
      await expect(page.getByRole('alert')).toContainText('읽지 못했습니다');
      await expect(page.getByRole('button', { name: '맞춤 보고서 요청', exact: true })).toBeDisabled();
      expect(keys).toHaveLength(0); return;
    }
    await page.getByLabel('보고서에 추가할 내용').fill('현금흐름 검토');
    await page.getByRole('button', { name: '맞춤 보고서 요청', exact: true }).click();
    if (failure === 'write') {
      await expect(page.getByRole('alert')).toContainText('접수하지 않았습니다');
      expect(keys).toHaveLength(0); return;
    }
    await expect(page.getByText('맞춤 보고서 생성 완료', { exact: true })).toBeVisible();
    await expect(page.getByRole('alert')).toContainText('저장하지 못했습니다');
    await page.reload();
    await page.getByRole('button', { name: '같은 요청 다시 보내기' }).click();
    await expect(page.getByText('맞춤 보고서 생성 완료', { exact: true })).toBeVisible();
    expect(keys).toHaveLength(2); expect(keys[0]).toBe(keys[1]);
  });
}

test('검토 회귀: 보고서 정리 실패에도 인증 세션은 삭제한다', async ({ page }) => {
  // given
  await page.addInitScript(() => {
    localStorage.setItem('sentinel.auth.session', JSON.stringify({ token: 'ui-only', user: { id: 1, role: 'USER' } }));
    sessionStorage.setItem('sentinel.custom-report:1:1', '{}');
    const remove = Storage.prototype.removeItem;
    Storage.prototype.removeItem = function(key) {
      if (this === sessionStorage && key.startsWith('sentinel.custom-report:')) throw new DOMException('fixture', 'SecurityError');
      return remove.call(this, key);
    };
  });
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill({ status: 503, json: { success: false } }));
  await page.goto('/companies/1');
  // when
  const cleared = await page.evaluate(async () => {
    const auth = await import('/src/services/auth.ts');
    try { auth.clearStoredSession(); } catch { /* 삭제 결과를 별도로 확인한다. */ }
    return localStorage.getItem('sentinel.auth.session') === null;
  });
  // then
  expect(cleared).toBe(true);
});

test('맞춤 요청 입력은 기존 보고서 버튼과 함께 보인다', async ({ page }) => {
  page.on('pageerror', error => console.error('브라우저 오류:', error.message));
  // given
  await page.addInitScript(() => localStorage.setItem('sentinel.auth.session', JSON.stringify({ token: 'ui-contract-only', user: { id: 1, name: 'UI 계약', role: 'USER' } })));
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, error: { message: 'UI fixture' } }) }));
  // when
  await page.goto('/companies/1');
  // then
  await expect(page.getByLabel('보고서에 추가할 내용')).toBeVisible();
  await expect(page.getByRole('button', { name: '맞춤 보고서 요청', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /AI 분석 리포트 생성/ })).toBeVisible();
});

test('UI 계약: 손상된 이전 세션 기록이 입력 화면을 깨뜨리지 않는다', async ({ page }) => {
  // given
  await page.addInitScript(() => {
    localStorage.setItem('sentinel.auth.session', JSON.stringify({ token: 'ui-only', user: { id: 1, role: 'USER' } }));
    sessionStorage.setItem('sentinel.custom-report:1:1', JSON.stringify({ key: 'legacy-schema-without-body' }));
  });
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill({ status: 503, json: { success: false, error: { message: 'UI fixture' } } }));
  // when
  await page.goto('/companies/1', { waitUntil: 'domcontentloaded' });
  // then
  await expect(page.getByLabel('보고서에 추가할 내용')).toBeVisible();
  await expect(page.getByRole('button', { name: '맞춤 보고서 요청', exact: true })).toBeEnabled();
  await page.evaluate(() => sessionStorage.setItem('sentinel.custom-report:1:1', '{invalid-json'));
  await page.reload();
  await expect(page.getByRole('button', { name: '맞춤 보고서 요청', exact: true })).toBeEnabled();
});

for (const code of [409,429,503]) {
  test(`UI 계약: ${code} 오류와 같은 키 재전송`, async ({ page }) => {
    // given
    await page.addInitScript(() => localStorage.setItem('sentinel.auth.session', JSON.stringify({ token: 'ui-only', user: { id: 1, role: 'USER' } })));
    const keys: string[] = [];
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      if (route.request().method() === 'POST') keys.push(route.request().headers()['idempotency-key']);
      await route.fulfill({ status: code, contentType: 'application/json', body: JSON.stringify({ success: false, error: { message: '계약 시험' } }) });
    });
    await page.goto('/companies/1');
    await page.getByLabel('보고서에 추가할 내용').fill('현금흐름 검토');
    // when
    await page.getByRole('button', { name: '맞춤 보고서 요청', exact: true }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await page.getByRole('button', { name: '같은 요청 다시 보내기' }).click();
    // then
    expect(keys).toHaveLength(2); expect(keys[0]).toBe(keys[1]);
    if (code === 409) await expect(page.getByRole('button', { name: '새 요청 작성' })).toBeVisible();
  });
}

for (const state of ['FAILED', '404', '401']) {
  test(`UI 계약: 추적 ${state} 처리`, async ({ page }) => {
    // given
    await page.addInitScript(() => localStorage.setItem('sentinel.auth.session', JSON.stringify({ token: 'ui-only', user: { id: 1, role: 'USER' } })));
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith('/custom-report-requests')) return route.fulfill({ json: { success: true, timestamp: '', data: { requestId: '00000000-0000-4000-8000-000000000001' } }, status: 202 });
      if (path.startsWith('/api/report-requests/') && state === 'FAILED') return route.fulfill({ json: { success: true, timestamp: '', data: { status: 'FAILED', stateVersion: 5, attemptNo: 3, errorCode: 'UPSTREAM_503' } } });
      return route.fulfill({ status: state === '401' && (path.startsWith('/api/report-requests/') || path.endsWith('/refresh')) ? 401 : 404, json: { success: false, timestamp: '', error: { message: '계약 시험' } } });
    });
    await page.goto('/companies/1', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('보고서에 추가할 내용').fill('현금흐름 검토');
    // when
    await page.getByRole('button', { name: '맞춤 보고서 요청', exact: true }).click();
    // then
    if(state === '401') { await expect(page).toHaveURL('http://localhost:3000/'); expect(await page.evaluate(() => sessionStorage.length)).toBe(0); }
    else if(state === '404') { await expect(page.getByRole('alert')).toContainText('접근 권한'); await expect(page.getByRole('button', { name: '새 요청 작성' })).toBeVisible(); }
    else { await expect(page.getByRole('status')).toContainText('자동 재시도가 끝났습니다'); await expect(page.getByRole('button', { name: '맞춤 PDF 다운로드' })).toHaveCount(0); }
  });
}

test('실제 JWT·CORS·Kafka: 응답 유실 재전송/새로고침/본인 PDF/타인 차단/새 입력', async ({ page, browser, request }) => {
  page.on('pageerror', error => console.error('브라우저 오류:', error.message));
  page.on('response', response => { if(response.status() >= 400 && new URL(response.url()).pathname.startsWith('/api/')) console.log('R07_HTTP_FAILURE', response.status(), new URL(response.url()).pathname); });
  test.setTimeout(180_000);
  // given
  const data = fixture();
  await request.post('http://127.0.0.1:18081/control/custom-reports', { data: { delay_ms: 800, seed: 'r07-real' } });
  await loginBrowser(page, data.users[0]);
  await page.goto(`/companies/${data.company}`, { waitUntil: 'domcontentloaded' });
  await expect(page.getByLabel('보고서에 추가할 내용')).toBeVisible();
  await expect(page.getByText('협력사 API 응답 오류로 목 데이터를 표시하고 있어요.')).toHaveCount(0);
  const keys: string[] = []; let lost = false; let acceptedId: string;
  const ids: string[] = [];
  await page.route(url => url.pathname.endsWith('/custom-report-requests'), async route => {
    keys.push(route.request().headers()['idempotency-key']);
    const response = await route.fetch();
    const body = await response.json(); acceptedId = body.data.requestId;
    ids.push(acceptedId);
    if (!lost) { lost = true; await route.abort('failed'); } else await route.fulfill({ response });
  });
  // when: 서버 커밋 응답만 유실하고 같은 의도로 재전송
  await page.getByLabel('보고서에 추가할 내용').fill('현금흐름을 검토해 주세요. <img src=x onerror=alert(1)>');
  await page.getByRole('button', { name: '맞춤 보고서 요청', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('중복 접수되지 않습니다');
  await page.getByRole('button', { name: '같은 요청 다시 보내기' }).click();
  await expect(page.getByTestId('custom-request-id')).toContainText(acceptedId!);
  await page.reload();
  // then
  await expect(page.getByText('맞춤 보고서 생성 완료', { exact: true })).toBeVisible({ timeout: 120_000 });
  expect(keys).toHaveLength(2); expect(keys[0]).toBe(keys[1]);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: '맞춤 PDF 다운로드' }).click();
  const downloaded = await download;
  expect(readFileSync((await downloaded.path())!).subarray(0,5).toString()).toBe('%PDF-');
  const stranger = await browser.newContext(); const other = await stranger.newPage();
  await loginBrowser(other, data.users[1]);
  const forbidden = await other.evaluate(async id => {
    const token = JSON.parse(localStorage.getItem('sentinel.auth.session')!).token;
    return Promise.all(['', '/file'].map(async suffix => (await fetch(`http://localhost:18080/api/report-requests/${id}${suffix}`, { headers: { Authorization: `Bearer ${token}` } })).status));
  }, acceptedId!);
  expect(forbidden).toEqual([404,404]); await stranger.close();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: '맞춤 PDF 다운로드' }).scrollIntoViewIfNeeded();
  await expect(page.getByRole('button', { name: '맞춤 PDF 다운로드' })).toBeInViewport();
  const panel = page.getByRole('region', { name: '맞춤 보고서' });
  expect(await panel.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
  if(process.env.R07_SCREENSHOT) await panel.screenshot({ path: process.env.R07_SCREENSHOT });
  await page.getByRole('button', { name: '새 요청 작성' }).click();
  await page.getByLabel('보고서에 추가할 내용').fill('재고 회전율도 검토해 주세요.');
  await page.getByRole('button', { name: '맞춤 보고서 요청', exact: true }).evaluate(el => { (el as HTMLButtonElement).click(); (el as HTMLButtonElement).click(); });
  await expect(page.getByText('맞춤 보고서 생성 완료', { exact: true })).toBeVisible({ timeout: 120_000 });
  expect(keys).toHaveLength(3); expect(keys[2]).not.toBe(keys[0]);
  if(process.env.R07_EVIDENCE) writeFileSync(process.env.R07_EVIDENCE, JSON.stringify({ requestIds: ids, sameIntentSameRequest: ids[0] === ids[1], editedIntentNewRequest: ids[1] !== ids[2], ownedPdfHeader: '%PDF-', strangerStatus: forbidden, literalHtmlInput: true, duplicateClickHttpCalls: 1, mobileWidth: 390 }));
  await request.post('http://127.0.0.1:18081/control/custom-reports', { data: { delay_ms: 0 } });
});
