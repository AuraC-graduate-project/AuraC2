export function TechBackground() {
  return (
    <div className="fixed inset-0 overflow-hidden pointer-events-none opacity-10">
      <svg className="absolute w-full h-full" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <pattern id="circuit-pattern" x="0" y="0" width="100" height="100" patternUnits="userSpaceOnUse">
            {/* Horizontal lines */}
            <line x1="0" y1="20" x2="30" y2="20" stroke="#FACC15" strokeWidth="0.5" />
            <line x1="70" y1="20" x2="100" y2="20" stroke="#FACC15" strokeWidth="0.5" />
            
            {/* Vertical lines */}
            <line x1="20" y1="0" x2="20" y2="30" stroke="#FACC15" strokeWidth="0.5" />
            <line x1="20" y1="70" x2="20" y2="100" stroke="#FACC15" strokeWidth="0.5" />
            
            <line x1="80" y1="30" x2="80" y2="60" stroke="#FACC15" strokeWidth="0.5" />
            
            {/* Diagonal lines */}
            <line x1="40" y1="40" x2="60" y2="60" stroke="#FACC15" strokeWidth="0.5" />
            
            {/* Circles (connection points) */}
            <circle cx="20" cy="20" r="2" fill="#FACC15" />
            <circle cx="80" cy="80" r="2" fill="#FACC15" />
            <circle cx="50" cy="50" r="1.5" fill="#FACC15" />
            <circle cx="30" cy="70" r="1.5" fill="#FACC15" />
            
            {/* Small rectangles (chips) */}
            <rect x="28" y="18" width="4" height="4" fill="none" stroke="#FACC15" strokeWidth="0.5" />
            <rect x="68" y="18" width="4" height="4" fill="none" stroke="#FACC15" strokeWidth="0.5" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#circuit-pattern)" />
      </svg>
      
      {/* Animated glow effects */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-yellow-500/5 rounded-full blur-3xl animate-pulse" />
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-blue-500/5 rounded-full blur-3xl animate-pulse" style={{ animationDelay: '1s' }} />
    </div>
  );
}
