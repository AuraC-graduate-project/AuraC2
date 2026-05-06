export function TechBackground() {
  return (
    <div className="aura-tech-background pointer-events-none fixed inset-0 overflow-hidden" aria-hidden="true">
      <svg className="absolute inset-0 h-full w-full" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <pattern id="aura-login-grid" width="56" height="56" patternUnits="userSpaceOnUse">
            <path d="M 56 0 L 0 0 0 56" fill="none" stroke="currentColor" strokeWidth="0.6" />
          </pattern>
          <pattern id="aura-login-circuit" width="168" height="168" patternUnits="userSpaceOnUse">
            <path
              d="M18 46 H58 Q70 46 70 58 V86 H112 Q126 86 126 100 V132"
              fill="none"
              stroke="currentColor"
              strokeWidth="0.9"
            />
            <path
              d="M34 126 H72 Q84 126 84 114 V96 M110 42 H136 M136 42 V72"
              fill="none"
              stroke="currentColor"
              strokeWidth="0.9"
            />
            <circle cx="18" cy="46" r="2.5" fill="currentColor" />
            <circle cx="70" cy="86" r="2" fill="currentColor" />
            <circle cx="126" cy="132" r="2.5" fill="currentColor" />
            <circle cx="136" cy="72" r="2" fill="currentColor" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#aura-login-grid)" className="aura-login-grid-layer" />
        <rect width="100%" height="100%" fill="url(#aura-login-circuit)" className="aura-login-circuit-layer" />
        <path
          d="M-40 70 C210 20 310 140 500 96 C780 34 880 160 1120 108 C1320 65 1430 92 1510 130"
          className="aura-login-network-line"
          fill="none"
        />
        <path
          d="M-20 560 C210 488 366 610 560 540 C780 460 960 568 1220 510 C1360 478 1460 486 1540 520"
          className="aura-login-network-line aura-login-network-line-soft"
          fill="none"
        />
      </svg>
    </div>
  );
}
