/**
 * Minimal JWT payload decoder (no external libs).
 * Works for standard JWT: header.payload.signature
 */
function base64UrlDecode(input: string): string {
  // Replace URL-safe chars
  const base64 = input.replace(/-/g, "+").replace(/_/g, "/");
  // Pad with '='
  const pad = base64.length % 4;
  const padded = pad ? base64 + "=".repeat(4 - pad) : base64;

  // atob expects Latin1; JWT payload is UTF-8 JSON. Convert safely:
  const binary = atob(padded);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  const decoder = new TextDecoder("utf-8");
  return decoder.decode(bytes);
}

export function decodeJwtPayload(token: string): any | null {
  try {
    const parts = token.split(".");
    if (parts.length < 2) return null;
    const json = base64UrlDecode(parts[1]);
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/**
 * Tries to map common JWT claims into a role:
 * - role: "ADMIN" | "TEAM" | "ROLE_ADMIN" ...
 * - roles: ["ADMIN", ...]
 * - authorities: ["ROLE_ADMIN", ...]
 * - scope: "ROLE_ADMIN ..." (less common)
 */
export function decodeJwtRole(token: string): "ADMIN" | "TEAM" | "UNKNOWN" {
  const payload = decodeJwtPayload(token);
  if (!payload) return "UNKNOWN";

  const candidates: string[] = [];

  if (typeof payload.role === "string") candidates.push(payload.role);
  if (Array.isArray(payload.roles)) candidates.push(...payload.roles.map(String));
  if (Array.isArray(payload.authorities)) candidates.push(...payload.authorities.map(String));
  if (typeof payload.scope === "string") candidates.push(payload.scope);

  const joined = candidates.join(" ").toUpperCase();

  if (joined.includes("ADMIN")) return "ADMIN";
  if (joined.includes("TEAM") || joined.includes("STUDENT") || joined.includes("USER")) return "TEAM";

  return "UNKNOWN";
}

export function decodeJwtSubject(token: string): string | null {
  const payload = decodeJwtPayload(token);
  if (!payload) return null;
  if (typeof payload.sub === "string") return payload.sub;
  if (typeof payload.username === "string") return payload.username;
  return null;
}
