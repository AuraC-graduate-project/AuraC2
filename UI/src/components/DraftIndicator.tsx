import React, { useEffect, useState } from "react";

type Props = {
  visible: boolean;
};

/**
 * Subtle UI indicator that shows "✓ Draft saved" when a draft is saved.
 * Fades in when visible becomes true and automatically disappears after 2 seconds.
 */
export function DraftIndicator({ visible }: Props) {
  const [show, setShow] = useState(visible);

  useEffect(() => {
    if (visible) {
      setShow(true);
      // Auto-hide after 2 seconds
      const timer = setTimeout(() => {
        setShow(false);
      }, 2000);
      return () => clearTimeout(timer);
    } else {
      setShow(false);
    }
  }, [visible]);

  if (!show) return null;

  return (
    <span className="text-xs text-gray-500 font-medium animate-fade-in">
      ✓ Draft saved
    </span>
  );
}
