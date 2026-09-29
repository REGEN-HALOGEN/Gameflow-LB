// Connection configuration. Same-origin relative defaults work with the Vite
// dev proxy (/api, /ws -> localhost:8080). Override for production builds.
export const API_BASE: string =
  (import.meta.env.VITE_API_URL as string | undefined) || '/api';

function defaultWsUrl(): string {
  const proto = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
  return proto + window.location.host + '/ws/events';
}

export const WS_URL: string =
  (import.meta.env.VITE_WS_URL as string | undefined) || defaultWsUrl();
