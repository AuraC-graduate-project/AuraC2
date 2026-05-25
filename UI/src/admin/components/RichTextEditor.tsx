import { useEffect, useRef, useState, type ComponentType } from "react";
import {
  Bold,
  Code,
  Eraser,
  Eye,
  Heading2,
  Heading3,
  Italic,
  List,
  ListOrdered,
  Pilcrow,
  Redo2,
  Undo2,
} from "lucide-react";
import { Button } from "./ui/button";
import { RichTextContent } from "../../components/RichTextContent";
import { escapeHtml, richTextToPlainText, sanitizeRichText } from "../../components/richText";

type RichTextEditorProps = {
  id: string;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  placeholder?: string;
  inputClassName?: string;
  ariaLabel?: string;
  footerText?: string;
  toolbarVariant?: "full" | "compact";
};

type ToolbarButton = {
  label: string;
  icon: ComponentType<{ className?: string }>;
  action: () => void;
};

export function RichTextEditor({
  id,
  value,
  onChange,
  disabled = false,
  placeholder = "Write the problem statement here...",
  inputClassName = "min-h-[220px]",
  ariaLabel = "Problem statement editor",
  footerText = "Use toolbar buttons for formatting",
  toolbarVariant = "full",
}: RichTextEditorProps) {
  const editorRef = useRef<HTMLDivElement | null>(null);
  const lastValueRef = useRef<string>("");
  const initializedRef = useRef(false);
  const [previewOpen, setPreviewOpen] = useState(false);

  useEffect(() => {
    const editor = editorRef.current;
    if (!editor) return;

    if (!initializedRef.current || value !== lastValueRef.current) {
      const sanitized = sanitizeRichText(value);
      editor.innerHTML = sanitized;
      lastValueRef.current = value;
      initializedRef.current = true;
    }
  }, [value]);

  const emitChange = () => {
    const editor = editorRef.current;
    if (!editor) return;

    const nextValue = editor.innerHTML;
    lastValueRef.current = nextValue;
    onChange(nextValue);
  };

  const focusEditor = () => {
    editorRef.current?.focus();
  };

  const runCommand = (command: string, commandValue?: string) => {
    if (disabled) return;
    focusEditor();
    document.execCommand(command, false, commandValue);
    emitChange();
  };

  const insertHtml = (html: string) => {
    if (disabled) return;
    focusEditor();
    document.execCommand("insertHTML", false, html);
    emitChange();
  };

  const selectedText = () => {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return "";
    return selection.toString();
  };

  const fullToolbarButtons: ToolbarButton[] = [
    { label: "Paragraph", icon: Pilcrow, action: () => runCommand("formatBlock", "p") },
    { label: "Heading", icon: Heading2, action: () => runCommand("formatBlock", "h2") },
    { label: "Subheading", icon: Heading3, action: () => runCommand("formatBlock", "h3") },
    { label: "Bold", icon: Bold, action: () => runCommand("bold") },
    { label: "Italic", icon: Italic, action: () => runCommand("italic") },
    { label: "Bullets", icon: List, action: () => runCommand("insertUnorderedList") },
    { label: "Numbers", icon: ListOrdered, action: () => runCommand("insertOrderedList") },
    {
      label: "Inline code",
      icon: Code,
      action: () => {
        const text = selectedText() || "code";
        insertHtml(`<code>${escapeHtml(text)}</code>`);
      },
    },
    {
      label: "Code block",
      icon: Code,
      action: () => {
        const text = selectedText() || "write code here";
        insertHtml(`<pre><code>${escapeHtml(text)}</code></pre><p><br></p>`);
      },
    },
    { label: "Undo", icon: Undo2, action: () => runCommand("undo") },
    { label: "Redo", icon: Redo2, action: () => runCommand("redo") },
    {
      label: "Clear",
      icon: Eraser,
      action: () => {
        runCommand("removeFormat");
        runCommand("formatBlock", "p");
      },
    },
  ];
  const compactToolbarButtons = fullToolbarButtons.filter((item) =>
    ["Paragraph", "Bold", "Italic", "Bullets", "Numbers", "Inline code", "Clear"].includes(item.label)
  );
  const toolbarButtons = toolbarVariant === "compact" ? compactToolbarButtons : fullToolbarButtons;

  const plainText = richTextToPlainText(value);

  return (
    <div className="aura-rich-text-editor rounded-lg border border-slate-200 bg-white">
      <div className="flex flex-wrap items-center gap-1 border-b border-slate-200 bg-slate-50 p-2">
        {toolbarButtons.map((item) => {
          const Icon = item.icon;

          return (
            <Button
              key={item.label}
              type="button"
              variant="ghost"
              size="sm"
              disabled={disabled}
              title={item.label}
              aria-label={item.label}
              className="h-8 gap-1.5 px-2 text-xs text-slate-700 hover:bg-white"
              onMouseDown={(event) => event.preventDefault()}
              onClick={item.action}
            >
              <Icon className="h-3.5 w-3.5" />
              {toolbarVariant === "full" && <span className="hidden sm:inline">{item.label}</span>}
            </Button>
          );
        })}

        {toolbarVariant === "full" && (
          <Button
            type="button"
            variant={previewOpen ? "default" : "outline"}
            size="sm"
            disabled={disabled}
            className="ml-auto h-8 gap-1.5 px-2 text-xs"
            onClick={() => setPreviewOpen((open) => !open)}
          >
            <Eye className="h-3.5 w-3.5" />
            Preview
          </Button>
        )}
      </div>

      <div
        id={id}
        ref={editorRef}
        role="textbox"
        aria-multiline="true"
        aria-label={ariaLabel}
        contentEditable={!disabled}
        suppressContentEditableWarning
        data-placeholder={placeholder}
        onInput={emitChange}
        onBlur={() => onChange(sanitizeRichText(editorRef.current?.innerHTML ?? ""))}
        onPaste={(event) => {
          event.preventDefault();
          const text = event.clipboardData.getData("text/plain");
          document.execCommand("insertText", false, text);
          emitChange();
        }}
        className={`aura-rich-text-input overflow-y-auto p-4 text-sm leading-7 text-slate-800 outline-none ${inputClassName}`}
      />

      <div className="flex items-center justify-between border-t border-slate-200 px-3 py-2 text-xs text-slate-500">
        <span>{plainText ? `${plainText.length} characters` : "Start writing the statement"}</span>
        <span>{footerText}</span>
      </div>

      {previewOpen && (
        <div className="border-t border-slate-200 bg-slate-50 p-4">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-500">Preview</p>
          <RichTextContent
            content={value}
            emptyText="The formatted statement preview will appear here."
            className="rounded-md border border-slate-200 bg-white p-4"
          />
        </div>
      )}
    </div>
  );
}
