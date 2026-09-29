/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#0A0C0E',
        panel: '#101418',
        panel2: '#141A21',
        line: '#1E242C',
        line2: '#2A323D',
        ok: '#22C55E',
        warn: '#F59E0B',
        err: '#EF4444',
        info: '#4F8CFF',
        mute: '#6B7280',
      },
      fontFamily: {
        mono: ['ui-monospace', 'JetBrains Mono', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
    },
  },
  plugins: [],
};
