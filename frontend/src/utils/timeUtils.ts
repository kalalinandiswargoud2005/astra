/**
 * timeUtils.ts
 *
 * Robust, timezone-safe timestamp formatting utility for ASTRA SOC.
 * Handles ISO timestamps with/without timezone offsets, UTC serialization,
 * and clock skew between target endpoints and the SOC server.
 */

export function parseSafeDate(input: any): Date {
  if (!input) return new Date();
  if (input instanceof Date) return isNaN(input.getTime()) ? new Date() : input;
  if (typeof input === 'number') return new Date(input);

  const str = String(input).trim();
  if (!str) return new Date();

  // If already standard ISO with Z or offset, native parse works accurately
  if (str.endsWith('Z') || /[+-]\d{2}:?\d{2}$/.test(str)) {
    const d = new Date(str);
    if (!isNaN(d.getTime())) return d;
  }

  // Handle ISO string without explicit timezone (e.g. 2026-09-12T11:23:45.678)
  if (/^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/.test(str)) {
    const normalized = str.replace(' ', 'T');
    const d = new Date(normalized);
    if (!isNaN(d.getTime())) return d;
  }

  const fallback = new Date(str);
  return isNaN(fallback.getTime()) ? new Date() : fallback;
}

export function formatIncidentTime(input: any): string {
  const d = parseSafeDate(input);
  return d.toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function formatIncidentDateTime(input: any): string {
  const d = parseSafeDate(input);
  const dateStr = d.toLocaleDateString([], { year: 'numeric', month: '2-digit', day: '2-digit' });
  const timeStr = d.toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
  return `${dateStr} ${timeStr}`;
}

export function formatRelativeTime(input: any): string {
  const d = parseSafeDate(input);
  const now = Date.now();
  const diffSec = Math.floor((now - d.getTime()) / 1000);

  // Clamp clock skew / forward timestamps
  if (diffSec < 5) {
    return 'Just now';
  }
  if (diffSec < 60) {
    return `${diffSec}s ago`;
  }
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) {
    return `${diffMin}m ago`;
  }
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) {
    return `${diffHours}h ago`;
  }
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}d ago`;
}
