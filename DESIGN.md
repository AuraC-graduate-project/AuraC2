# AuraC² Design System

**Two themes, one architecture.** AuraC² ships a light theme — *The Digital
Manuscript* — and a dark theme — *The Obsidian Compiler*. Both share the
same component library and token names; only the values change.

The single source of truth for tokens is [`UI/src/index.css`](UI/src/index.css).
Components consume them via Tailwind v4's `@theme inline` bridge or directly via
CSS variables. Theme switching is class-based: a `.dark` class on `<html>`
flips every token at once.

---

## 1. North Stars

### Light — *The Digital Manuscript*
Inspired by Swiss International Style and modern technical whitepapers.
Treats competitive programming as a high-stakes academic pursuit. The
aesthetic is defined by:
- Extreme legibility
- Intentional white space
- A **No-Line** philosophy (no `1px` solid section borders)
- Tonal layering instead of structural shadows

### Dark — *The Obsidian Compiler*
A high-end integrated environment: a precision instrument that recedes
behind the code. Built on:
- A monochromatic deep-slate surface stack
- High-contrast typographic scale (display vs. mono)
- **Intentional asymmetry** in layout
- Emissive lighting (light comes from the UI itself, never from external
  shadows)

Both themes obey the same rules: no heavy section borders, tonal layering
over structural shadows, mono fonts on every byte of technical data, and
text colors that never go to pure `#000` or pure `#fff`.

---

## 2. Color & Surface Architecture

### 2.1 The "No-Line" Rule
Section boundaries come from background-value shifts, **never** from a
`1px` solid border. Use the `surface-container-*` family to lift / nest
panels.

The only place borders are tolerated is the **Ghost Border** —
`outline-variant` at ~15–22% opacity — used as a hairline on inputs,
ghost-buttons, and rare "card-on-card" cases where contrast is otherwise
insufficient. They should be felt, not seen.

### 2.2 Surface Hierarchy

| Token | Light (Manuscript) | Dark (Obsidian) | Use |
|---|---|---|---|
| `--background` | `#f5f7fa` | `#0c1322` | App canvas |
| `--surface-container-low` | `#eef1f5` | `#11192b` | Sidebars, secondary panels |
| `--surface-container` | `#fcfcfb` | `#161f33` | Default cards & content surfaces |
| `--surface-container-high` | `#e8ebef` | `#1c263c` | Hover surface, secondary buttons (light) |
| `--surface-container-highest` | `#dfe3e8` | `#232e47` | Active row, tonal-chip background (dark) |
| `--surface-container-lowest` | `#fbfaf8` | `#050b18` | Code editor, input fills |
| `--surface-bright` | `#fcfcfb` | `#2b3653` | Tertiary buttons hover (dark) |

**Layering principle.** Place a `surface-container` card on a
`surface-container-low` shell to "lift" it through value contrast alone —
no shadow, no border. In dark mode the code editor uses
`surface-container-lowest` (`#050b18`, near-black) sitting on the
workspace's `surface-container` to create the "terminal-on-desk" effect
the Obsidian spec calls for.

### 2.3 Ink

| Token | Light | Dark | Use |
|---|---|---|---|
| `--on-surface` (`--foreground`) | `#2a2e36` | `#c4ccdf` | Body text |
| `--on-surface-variant` | `#545862` | `#9ea7be` | Labels, secondary text |
| `--on-surface-soft` | `#7a808b` | `#767f95` | Captions, eyebrows, placeholders |

Both themes intentionally avoid pure black and pure white — long coding
sessions need calm contrast, not maximum contrast.

### 2.4 Brand & Semantic

| Token | Light | Dark | Notes |
|---|---|---|---|
| `--primary` | `#4338ca` (indigo) | `#5cb6ea` (cyan) | CTA fills, focus rings, eyebrow text |
| `--primary-container` | `#5b51e0` | `#4aa6dc` | Hover state for primary CTAs |
| `--primary-fixed` | `#e6e4fb` | `#1c3548` | Subtle primary-tinted backgrounds |
| `--primary-gradient` | indigo `linear-gradient(135°)` | cyan `linear-gradient(135°)` | Dark-mode primary CTAs use the gradient for "glow" |
| `--secondary` | `#515f74` (slate) | `#699cff` (azure) | Mono numeric data, secondary buttons |
| `--tertiary` | `#005424` | `#5fdf6c` | AC / success states |
| `--tertiary-fixed` | `#95f8a7` | `#c5ffc9` | Soft AC fills for chips |
| `--error` | `#ba1a1a` | `#ffb4ab` | WA / destructive |
| `--error-container` | `#ffdad6` | `#93000a` | Soft error fills |
| `--outline-variant` | `#c7c4d8` | `#40485d` | Ghost-border base color |

**Glassmorphism.** Floating elements (modals, popovers, dropdowns,
tooltips) use `surface-container-lowest` at ~85% opacity with
`backdrop-filter: blur(12px) saturate(1.05)`. The result feels like
layered vellum (light) or polished obsidian (dark) — never disconnected
plastic.

---

## 3. Typography

The system uses three font families to create deliberate tension between
"Editorial Authority", "Functional UI", and "Technical Precision":

| Stack | Family | Used for |
|---|---|---|
| `--font-display` | **Space Grotesk** → Inter fallback | Headlines, page titles, problem titles. Geometric apertures, slightly quirky — gives an "editorial / hacker-chic" feel. Tight letter-spacing (`-0.02em`). |
| `--font-sans` | **Inter** | All other UI text — navigation, body, labels. Neutral; recedes into the experience. |
| `--font-mono` | **JetBrains Mono** → Fira Code fallback | Mandatory for **all** code, submission IDs, execution times (`124ms`), memory (`512 MB`), countdowns (`01:00:00`), dates, durations. Mono ⇒ "this is data, not prose." |

Fonts are loaded once in [`UI/index.html`](UI/index.html) via Google Fonts.

### Hierarchy idioms used throughout the app

| Pattern | Example |
|---|---|
| **Eyebrow label.** Tiny uppercase Inter, `tracking-[0.18em]`, `text-on-surface-soft`, with a primary-tinted icon. | `CONTEST LIFECYCLE` above the header |
| **Display headline.** Space Grotesk, semibold, `tracking-tight`. | "Contest Overview" |
| **Numeric data row.** JetBrains Mono in `text-primary` (light) / `text-secondary` (dark) with `tabular-nums`. | `May 15, 2026, 2:57 PM GMT+3` |
| **Hero countdown.** Mono, `text-2xl`, semibold, `tabular-nums`, in `text-on-surface`. | `01:00:00` |

---

## 4. Elevation & Depth

Depth is **tonal**, not structural.

- **The Layering Principle.** Stack `surface-container-lowest` →
  `surface-container-low` → `surface-container` → `…-high` → `…-highest`
  to create perceptible depth without a single drop shadow.
- **Ambient shadow.** When something genuinely floats (dropdown, dialog),
  use the diffused `--ambient-shadow` (light: `0 10px 30px -5px
  rgba(25,28,30,0.06)`; dark: `0 20px 40px -10px rgba(222,229,255,0.05)`).
  The dark variant uses a *light* shadow color at low opacity so it reads
  as **emissive glow** rather than dirt.
- **Ghost border fallback.** Only when contrast truly fails — `1px
  outline-variant` at 15–22% opacity. It must be felt, not seen.

Heavy `shadow-xl`, `shadow-lg`, and stark `0 0 0 1px black` outlines are
forbidden across both themes.

---

## 5. Component Logic

All component primitives live under `UI/src/{admin,team,auth/loginui}/components/ui/`.
Three copies of the same shadcn-style library exist (one per top-level
module); changes are mirrored across all three.

### 5.1 Buttons — [`button.tsx`](UI/src/admin/components/ui/button.tsx)

| Variant | Light | Dark |
|---|---|---|
| `default` | `bg-primary` indigo, `rounded-xl` | `bg-[image:var(--primary-gradient)]` cyan gradient |
| `secondary` | `bg-surface-container-high`, no border | `bg-surface-container`, no border |
| `outline` | Ghost border `outline-variant/20`, transparent fill | Ghost border, `hover:bg-surface-bright` |
| `ghost` | Transparent, hover `surface-container-low` | Transparent, hover `surface-bright` |
| `success` | `bg-tertiary-container` soft green | `bg-tertiary/15`, text `text-tertiary` |
| `warning` | `bg-secondary-container` warm amber | `bg-secondary/15`, text `text-secondary` |
| `info` | `bg-primary-fixed` soft indigo | `bg-primary/15`, text `text-primary` |
| `destructive` | `bg-error/12`, text `text-error` | `bg-error/15`, text `text-error` |
| `link` | `text-primary`, underline on hover | same |

Default radius is `rounded-xl` (0.75rem) — the spec ceiling. Larger radii
are forbidden ("technical tool, not social media app").

### 5.2 Inputs / Textareas / Selects

- Background `bg-surface-container-lowest`
- Border: ghost (`border-outline-variant/20`)
- Focus: border transitions to `border-primary` and a `ring-2
  ring-primary/25` glow appears
- Placeholder: `text-on-surface-soft`

### 5.3 Tables — [`table.tsx`](UI/src/admin/components/ui/table.tsx)

- **No row borders.** `border-separate` with `border-spacing-y-1` for
  visual breathing.
- Header: tiny uppercase `tracking-wider`, `text-on-surface-variant`.
- Row hover tints to `surface-container-low` (light) / `surface-container-high`
  (dark).
- Active row uses `surface-container-highest`.

### 5.4 Status Chips — [`StatusBadge.tsx`](UI/src/components/StatusBadge.tsx)

Pill-shaped (`rounded-full`), `label-md` size, semantic palette:

| Verdict | Tone | Light fill | Dark treatment |
|---|---|---|---|
| **ACCEPTED** | emerald | `bg-emerald-100 text-emerald-800` | `surface-container-highest` + 2px **emerald** left accent bar + `text-emerald-300` |
| **PENDING / RUNNING** | sky | `bg-sky-100 text-sky-800` | `surface-container-highest` + sky accent bar (clock icon pulses) |
| **TLE** (timeout) | amber | `bg-amber-100 text-amber-900` | amber accent bar |
| **WRONG_ANSWER** | rose | `bg-rose-100 text-rose-800` | rose accent bar |
| **RUNTIME_ERROR** | violet | `bg-violet-100 text-violet-800` | violet accent bar |
| **COMPILATION_ERROR** | orange | `bg-orange-100 text-orange-800` | orange accent bar |
| **INTERNAL_ERROR** | slate | `bg-surface-container-high` + `on-surface-variant` | outline-variant accent bar |

The dark **Pulse Chip** rule (per the Obsidian spec): low-key
`surface-container-highest` background with a `border-l-2` accent in the
semantic color — the surface stays calm, the accent carries the meaning.

In light mode the chip uses a soft pastel fill instead — calmer than a
loud border on white. WCAG AAA contrast is preserved on both.

### 5.5 Code Editor — [`CodeEditor.tsx`](UI/src/team/components/CodeEditor.tsx)

The editor uses `surface-container-lowest` in both themes:
- **Light:** `#fbfaf8` paper-warm white — like printed code.
- **Dark:** `#050b18` true near-black — like a terminal.

Syntax token colors are CSS variables that re-skin per theme:

| Token | Light | Dark |
|---|---|---|
| `--code-keyword` | `#4338ca` indigo | `#86b7ff` blue |
| `--code-string` | `#00701a` forest | `#a8c98f` sage |
| `--code-number` | `#8b5a00` ochre | `#d8bd82` gold |
| `--code-comment` | `#6c707a` muted | `#7d8899` muted |
| `--code-preprocessor` | `#993a00` rust | `#d4a373` warm |
| `--code-operator` | `#545862` slate | `#b8c1ce` cool |
| `--code-caret` | `#4338ca` indigo | `#d6c285` gold |

### 5.6 Dialogs / Popovers / Tooltips

Glassmorphism: `surface-container-lowest` at 85% opacity +
`backdrop-blur-md`. Animated entry via a 6-px translate + scale `0.985 →
1` over 260 ms.

### 5.7 Sonner toasts

`Toaster` reads from `useTheme()` and forwards the active theme to
Sonner so toast surfaces follow the app theme automatically.

---

## 6. Spacing & Rhythm

- Major sections separated by `gap-8` / `gap-12` ("breathing room"
  per both specs). Never use a visible `<hr>` divider.
- Cards use `p-5` / `p-6` interior padding.
- Numeric data rows use `space-y-5` for editorial breath; tight
  technical groupings use `gap-x-8 gap-y-5`.

## 7. Motion

All transitions use one of:

```css
--aura-ease:      cubic-bezier(0.2, 0.8, 0.2, 1);  /* default */
--aura-ease-soft: cubic-bezier(0.16, 1, 0.3, 1);   /* entrance/exit */
```

Durations:
- Micro-interaction (hover, focus): **160–220 ms**
- Panel / dialog entrance: **240–280 ms**
- View transitions: **320 ms**

A `aura-pulse` keyframe (1.4 s, opacity 0.6 → 1 → 0.6) is used on
in-flight indicators (pending verdict icon, connecting status dot).

`prefers-reduced-motion: reduce` clamps every animation to 1 ms.

---

## 8. Do's and Don'ts

### Do
- **Do** use JetBrains Mono on all numeric / technical data
  (`124ms`, `512 MB`, `01:00:00`, dates, IDs).
- **Do** use asymmetrical layouts — narrow sticky metadata column
  beside a wide problem statement, etc.
- **Do** use `surface-container-highest` to mark the active row in a
  list.
- **Do** use a primary-tinted icon as part of an eyebrow label group.
- **Do** keep pure-color CTAs (`bg-primary`) for *actions* only;
  structural panels stay tonal.

### Don't
- **Don't** use 100% black text in light mode. Use `--on-surface`
  (`#2a2e36`).
- **Don't** use 100% white text in dark mode. Use `--on-surface`
  (`#c4ccdf`).
- **Don't** use standard `shadow-xl` / multi-layer drop shadows.
  Increase blur + drop opacity instead — or remove the shadow and
  rely on tonal layering.
- **Don't** use traditional `<hr>` dividers. Use `2.75rem`
  (`spacing-12`) gap between sections.
- **Don't** use rounded corners larger than `xl` (0.75 rem).
- **Don't** apply a bright primary fill to large structural surfaces
  (page headers, banner panels). Reserve full primary saturation for
  buttons, links, and small accent elements.

---

## 9. Implementation Map

| File | Role |
|---|---|
| [`UI/index.html`](UI/index.html) | Loads Inter + JetBrains Mono + Space Grotesk via Google Fonts |
| [`UI/src/index.css`](UI/src/index.css) | All tokens, `@theme inline` bridge, `.aura-*` legacy class layer, override layer that re-skins legacy `bg-slate-*` / `text-blue-*` Tailwind classes onto semantic tokens |
| [`UI/src/components/ThemeProvider.tsx`](UI/src/components/ThemeProvider.tsx) | Stores `'light' \| 'dark'` in `localStorage('aurac_theme')`, toggles `.dark` class on `<html>` |
| [`UI/src/components/ThemeToggle.tsx`](UI/src/components/ThemeToggle.tsx) | Button labelled "Manuscript" / "Obsidian" with sun/moon icon |
| [`UI/src/components/StatusBadge.tsx`](UI/src/components/StatusBadge.tsx) | All semantic verdict / contest / clarification chips |
| `UI/src/{admin,team,auth/loginui}/components/ui/*.tsx` | shadcn primitives (button, input, table, dialog, badge, sonner, etc.) — all three copies kept in sync |

---

## 10. Adding a New Component

1. Compose with semantic tokens — `bg-surface-container`, `text-on-surface`,
   `text-on-surface-variant`, `text-primary`, `text-tertiary`, etc.
   Never reach for `bg-white` / `text-slate-900` etc. — those are remapped
   by the override layer for legacy compatibility, but new code should be
   semantic.
2. Numeric or code-like content gets `font-mono tabular-nums` and
   `text-primary dark:text-secondary` (or the reverse if you specifically
   need brand emphasis in dark).
3. Section labels get the **eyebrow** treatment:
   `text-[10px] font-medium uppercase tracking-[0.18em] text-on-surface-soft`,
   often paired with a 3.5×3.5 primary-tinted icon.
4. Cards / panels: `rounded-xl bg-surface-container` with no border.
   If they sit *inside* another `surface-container`, drop them onto
   `bg-surface-container-low` for the tonal lift instead.
5. Action buttons: pick a Button `variant` (`success` / `warning` /
   `info` / `destructive`) — never apply a raw color class like
   `bg-green-600` that clashes with the dark gradient.
6. Run the app, toggle the theme, and verify both states. The whole
   point of the system is that no component should need a `dark:`
   prefix on every property — the tokens carry that for you.
