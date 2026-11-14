import React, { useEffect, useRef, useState } from 'react';
import { ApiRequestError } from '../../api/client';
import { downloadCustomReport, readCustomReport, submitCustomReport, ReportIntent, ReportStatus } from '../../api/customReports';
import { getStoredUser } from '../../services/auth';

type Pending = { key: string; body: ReportIntent; requestId?: string };
const terminal = (status?: string) => status === 'SUCCEEDED' || status === 'FAILED';
const labels: Record<string, string> = { ACCEPTED: '접수됨', ENQUEUED: '생성 대기', PROCESSING: '생성 중', RETRY_WAIT: '재시도 대기' };
const messages: Record<number, string> = {
  401: '다시 로그인해 주세요.', 404: '요청을 찾을 수 없거나 접근 권한이 없습니다.',
  409: '요청 내용이 기존 키와 다릅니다. 새 요청으로 시작해 주세요.',
  429: '진행 중인 요청이 많습니다. 잠시 후 같은 요청을 다시 보내세요.',
  503: '현재 요청을 접수할 수 없습니다. 잠시 후 다시 시도해 주세요.',
};
export default function CustomReportRequestPanel({ companyId }: { companyId: string }) {
  const owner = String(getStoredUser()?.id ?? '');
  const storageKey = `sentinel.custom-report:${owner}:${companyId}`;
  const storageUnreadable = useRef(false);
  const [pending, setPending] = useState<Pending | null>(() => {
    try {
      const raw = sessionStorage.getItem(storageKey);
      let stored: Pending | null;
      try { stored = JSON.parse(raw || 'null'); } catch { return null; }
      if (!stored || typeof stored.key !== 'string' || !/^[A-Za-z0-9._:-]{8,128}$/.test(stored.key) ||
        typeof stored.body?.additionalInstructions !== 'string' ||
        (stored.requestId !== undefined && (typeof stored.requestId !== 'string' || !/^[0-9a-f-]{36}$/i.test(stored.requestId)))) return null;
      return stored;
    } catch { storageUnreadable.current = true; return null; }
  });
  const [input, setInput] = useState(pending?.body.additionalInstructions ?? '');
  const [status, setStatus] = useState<ReportStatus | null>(null);
  const [error, setError] = useState('');
  const [storageWarning, setStorageWarning] = useState(storageUnreadable.current
    ? '브라우저에 저장된 요청 정보를 읽지 못했습니다. 저장 설정을 확인하고 화면을 새로고침해 주세요.' : '');
  const [busy, setBusy] = useState(false);
  const [blocked, setBlocked] = useState(false);
  const lock = useRef(false);
  const save = (value: Pending | null) => {
    let persisted = false;
    try {
      if (value) sessionStorage.setItem(storageKey, JSON.stringify(value)); else sessionStorage.removeItem(storageKey);
      persisted = true; setStorageWarning('');
    } catch { setStorageWarning('브라우저에 진행 정보를 저장하지 못했습니다. 새로고침 뒤에는 보존된 같은 키로 다시 확인해 주세요.'); }
    setPending(value);
    return persisted;
  };
  const describe = (err: unknown) => err instanceof ApiRequestError
    ? messages[err.apiError?.status ?? 0] ?? '요청 처리에 실패했습니다. 같은 요청으로 다시 확인해 주세요.'
    : '응답을 확인하지 못했습니다. 같은 요청으로 다시 보내도 중복 접수되지 않습니다.';

  useEffect(() => {
    if (!pending?.requestId) return;
    let active = true; let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      try {
        const next = await readCustomReport(pending.requestId!);
        if (!active) return;
        setStatus(old => !old || next.stateVersion >= old.stateVersion ? next : old); setError('');
        if (terminal(next.status)) return;
      } catch (err) {
        if (!active) return; setError(describe(err));
        if (err instanceof ApiRequestError && [401,404].includes(err.apiError?.status ?? 0)) { setBlocked(true); return; }
      }
      timer = setTimeout(poll, 1000);
    };
    void poll(); return () => { active = false; clearTimeout(timer); };
  }, [pending?.requestId]);

  const submit = async () => {
    if (lock.current || !owner || storageUnreadable.current) return;
    const text = input.replace(/\r\n?/g, '\n').trim();
    if (!text || [...text].length > 2000) { setError('추가 내용은 1~2,000자로 입력해 주세요.'); return; }
    lock.current = true; setBusy(true); setError('');
    // 응답 유실 뒤에도 같은 내용·키를 유지하며, 사용자가 바꾼 내용만 새 요청으로 만든다.
    const intent = pending && pending.body.additionalInstructions === text && !terminal(status?.status)
      ? pending : { key: crypto.randomUUID(), body: { additionalInstructions: text } };
    try {
      // 키가 남지 않으면 재마운트 후 같은 의도를 복구할 수 없어 접수하지 않는다.
      if (!save(intent)) { setStorageWarning('브라우저에 요청 정보를 저장하지 못해 접수하지 않았습니다. 저장 공간을 확인하고 다시 시도해 주세요.'); return; }
      const accepted = await submitCustomReport(companyId, intent.key, intent.body); save({ ...intent, requestId: accepted.requestId });
    }
    catch (err) { setError(describe(err)); }
    finally { lock.current = false; setBusy(false); }
  };
  const download = async () => {
    if (!pending?.requestId || lock.current) return;
    lock.current = true; setBusy(true);
    try {
      const blob = await downloadCustomReport(pending.requestId); const url = URL.createObjectURL(blob);
      const a = document.createElement('a'); a.href = url; a.download = 'custom-report.pdf'; a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) { setError(describe(err)); }
    finally { lock.current = false; setBusy(false); }
  };
  const tracking = !!pending?.requestId && !terminal(status?.status) && !blocked;
  return <section aria-label="맞춤 보고서" className="rounded-2xl border border-white/10 p-4 space-y-3 min-w-0" style={{ overflowWrap: 'anywhere' }}>
    <h3 className="text-lg text-white">맞춤 보고서</h3>
    <label htmlFor="custom-report-input" className="block text-sm">보고서에 추가할 내용</label>
    <textarea id="custom-report-input" value={input} onChange={event => setInput(event.target.value)} disabled={busy || tracking}
      style={{ width: '100%', maxWidth: '100%', boxSizing: 'border-box' }}
      rows={3} className="w-full rounded border border-white/20 bg-slate-900 p-3" />
    <p className="text-xs">{[...input].length} / 2,000자 · 검증된 최신 재무 데이터로 요청합니다.</p>
    <button type="button" onClick={() => void submit()} disabled={busy || tracking || blocked || storageUnreadable.current || terminal(status?.status)} className="rounded border px-4 py-2 disabled:opacity-50">
      {busy ? '처리 중' : pending && !pending.requestId ? '같은 요청 다시 보내기' : '맞춤 보고서 요청'}
    </button>
    {pending?.requestId && <p className="text-xs" data-testid="custom-request-id">요청 ID: {pending.requestId}</p>}
    <p role="status">{status?.status === 'SUCCEEDED' ? '맞춤 보고서 생성 완료' : status?.status === 'FAILED' ? '생성 실패 · 자동 재시도가 끝났습니다.' : pending?.requestId ? `접수 완료 · ${labels[status?.status ?? ''] ?? '상태 확인 중'} (시도 ${status?.attemptNo ?? 0})` : ''}</p>
    {status?.status === 'SUCCEEDED' && <button onClick={() => void download()} disabled={busy} className="rounded border px-4 py-2">맞춤 PDF 다운로드</button>}
    {(terminal(status?.status) || blocked || error.includes('새 요청')) && <button onClick={() => { save(null); setStatus(null); setError(''); setBlocked(false); }} className="ml-3 underline">새 요청 작성</button>}
    {error && <p role="alert" className="text-amber-200">{error}</p>}
    {storageWarning && <p role="alert" className="text-amber-200">{storageWarning}</p>}
  </section>;
}
