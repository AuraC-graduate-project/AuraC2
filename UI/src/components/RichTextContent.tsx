import { useMemo } from "react";
import { sanitizeRichText } from "./richText";

type RichTextContentProps = {
  content: string | null | undefined;
  emptyText?: string;
  className?: string;
};

export function RichTextContent({
  content,
  emptyText = "No problem statement provided.",
  className = "",
}: RichTextContentProps) {
  const sanitized = useMemo(() => sanitizeRichText(content), [content]);

  if (!sanitized) {
    return <p className={`text-sm leading-7 text-slate-500 ${className}`}>{emptyText}</p>;
  }

  return (
    <div
      className={`aura-prose ${className}`}
      dangerouslySetInnerHTML={{ __html: sanitized }}
    />
  );
}
