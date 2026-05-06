const ALLOWED_TAGS = new Set([
  "blockquote",
  "br",
  "code",
  "em",
  "h2",
  "h3",
  "h4",
  "li",
  "ol",
  "p",
  "pre",
  "strong",
  "ul",
]);

const BLOCK_TAGS = new Set(["blockquote", "h2", "h3", "h4", "li", "ol", "p", "pre", "ul"]);

export function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function looksLikeHtml(value: string): boolean {
  return /<\/?[a-z][\s\S]*>/i.test(value);
}

function plainTextToHtml(value: string): string {
  return value
    .split(/\n{2,}/)
    .map((paragraph) => {
      const content = escapeHtml(paragraph).replace(/\n/g, "<br>");
      return content.trim() ? `<p>${content}</p>` : "";
    })
    .join("");
}

function sanitizeNode(node: Node, documentRef: Document): Node | null {
  if (node.nodeType === Node.TEXT_NODE) {
    return documentRef.createTextNode(node.textContent ?? "");
  }

  if (node.nodeType !== Node.ELEMENT_NODE) {
    return null;
  }

  const element = node as HTMLElement;
  const tagName = element.tagName.toLowerCase();

  if (tagName === "b") {
    const strong = documentRef.createElement("strong");
    element.childNodes.forEach((child) => {
      const safeChild = sanitizeNode(child, documentRef);
      if (safeChild) strong.appendChild(safeChild);
    });
    return strong;
  }

  if (tagName === "i") {
    const emphasis = documentRef.createElement("em");
    element.childNodes.forEach((child) => {
      const safeChild = sanitizeNode(child, documentRef);
      if (safeChild) emphasis.appendChild(safeChild);
    });
    return emphasis;
  }

  if (!ALLOWED_TAGS.has(tagName)) {
    const fragment = documentRef.createDocumentFragment();
    element.childNodes.forEach((child) => {
      const safeChild = sanitizeNode(child, documentRef);
      if (safeChild) fragment.appendChild(safeChild);
    });
    return fragment;
  }

  const safeElement = documentRef.createElement(tagName);
  element.childNodes.forEach((child) => {
    const safeChild = sanitizeNode(child, documentRef);
    if (safeChild) safeElement.appendChild(safeChild);
  });

  if (safeElement.childNodes.length === 0 && tagName !== "br") {
    safeElement.appendChild(documentRef.createElement("br"));
  }

  return safeElement;
}

function sanitizeHtml(value: string): string {
  if (typeof window === "undefined" || typeof DOMParser === "undefined") {
    return plainTextToHtml(value);
  }

  const parser = new DOMParser();
  const parsed = parser.parseFromString(`<div>${value}</div>`, "text/html");
  const safeDocument = document.implementation.createHTMLDocument("");
  const wrapper = safeDocument.createElement("div");

  parsed.body.firstElementChild?.childNodes.forEach((node) => {
    const safeNode = sanitizeNode(node, safeDocument);
    if (safeNode) wrapper.appendChild(safeNode);
  });

  return wrapper.innerHTML;
}

export function sanitizeRichText(value: string | null | undefined): string {
  const source = String(value ?? "").trim();
  if (!source) return "";

  return looksLikeHtml(source) ? sanitizeHtml(source) : plainTextToHtml(source);
}

export function richTextToPlainText(value: string | null | undefined): string {
  const source = String(value ?? "");
  if (!source) return "";

  if (typeof DOMParser === "undefined") {
    return source.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
  }

  const parser = new DOMParser();
  const parsed = parser.parseFromString(sanitizeRichText(source), "text/html");
  return (parsed.body.textContent ?? "").replace(/\s+/g, " ").trim();
}

export function hasRichTextBlocks(value: string | null | undefined): boolean {
  if (!looksLikeHtml(String(value ?? ""))) return false;

  if (typeof DOMParser === "undefined") return true;

  const parser = new DOMParser();
  const parsed = parser.parseFromString(sanitizeRichText(value), "text/html");
  return Array.from(parsed.body.querySelectorAll("*")).some((node) =>
    BLOCK_TAGS.has(node.tagName.toLowerCase())
  );
}
