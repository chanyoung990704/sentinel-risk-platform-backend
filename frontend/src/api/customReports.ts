import { apiGet, apiPost } from './client';
import { getAuthToken, refreshAccessToken, updateStoredToken, clearStoredSession } from '../services/auth';
import { ApiRequestError } from './client';

export type ReportStatus = { requestId: string; status: string; stateVersion: number; generation: number; attemptNo: number; errorCode?: string; resultUrl?: string };
export type ReportIntent = { additionalInstructions: string; targetQuarterKey?: number };
export const submitCustomReport = (company: string, key: string, body: ReportIntent) =>
  apiPost<{ requestId: string; statusUrl: string }>(`/api/companies/${company}/custom-report-requests`, body, { headers: { 'Idempotency-Key': key } });
export const readCustomReport = (id: string) => apiGet<ReportStatus>(`/api/report-requests/${encodeURIComponent(id)}`);

export async function downloadCustomReport(id: string, retried = false): Promise<Blob> {
  const base = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
  const response = await fetch(`${base}/api/report-requests/${encodeURIComponent(id)}/file`, {
    headers: { Authorization: `Bearer ${getAuthToken()}`, Accept: 'application/pdf' }, credentials: 'include',
  });
  if (response.status === 401 && !retried) {
    try { updateStoredToken((await refreshAccessToken()).accessToken); }
    catch { clearStoredSession(); throw new ApiRequestError('다시 로그인해 주세요.', { status: 401 }); }
    return downloadCustomReport(id, true);
  }
  if (!response.ok || !response.headers.get('content-type')?.includes('application/pdf'))
    throw new ApiRequestError('파일을 받을 수 없습니다.', { status: response.status });
  return response.blob();
}
