---
name: Academic Precision
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#43474e'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#74777f'
  outline-variant: '#c4c6cf'
  surface-tint: '#455f87'
  primary: '#022448'
  on-primary: '#ffffff'
  primary-container: '#1e3a5f'
  on-primary-container: '#8aa4cf'
  inverse-primary: '#adc8f5'
  secondary: '#0051d5'
  on-secondary: '#ffffff'
  secondary-container: '#316bf3'
  on-secondary-container: '#fefcff'
  tertiary: '#735c00'
  on-tertiary: '#ffffff'
  tertiary-container: '#cea700'
  on-tertiary-container: '#4e3e00'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d5e3ff'
  primary-fixed-dim: '#adc8f5'
  on-primary-fixed: '#001c3b'
  on-primary-fixed-variant: '#2d486d'
  secondary-fixed: '#dbe1ff'
  secondary-fixed-dim: '#b4c5ff'
  on-secondary-fixed: '#00174b'
  on-secondary-fixed-variant: '#003ea8'
  tertiary-fixed: '#ffe083'
  tertiary-fixed-dim: '#eec200'
  on-tertiary-fixed: '#231b00'
  on-tertiary-fixed-variant: '#574500'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  h1:
    fontFamily: manrope
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.02em
  h2:
    fontFamily: manrope
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  h3:
    fontFamily: manrope
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-base:
    fontFamily: inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  code-block:
    fontFamily: spaceGrotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
  table-data:
    fontFamily: spaceGrotesk
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
  label-caps:
    fontFamily: inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 20px
  container-max: 1440px
---

## Brand & Style

This design system is engineered for high-stakes academic environments where clarity and reliability are paramount. The aesthetic follows a **Corporate / Modern** philosophy with a specific tilt toward scholarly professionalism. It avoids visual noise and trends in favor of a stable, structured interface that facilitates deep concentration during competitive programming.

The brand personality is authoritative yet supportive. It treats code and data as the primary citizens of the UI. The visual language uses a high-density information layout, disciplined alignment, and a restrained color application to evoke a sense of calm under pressure.

## Colors

The palette is anchored by **Deep Navy (#1E3A5F)** for structural elements like sidebars and navigation, providing a grounded, institutional foundation. Actionable elements utilize **Blue (#2563EB)** to clearly signify interactivity.

The background uses a cool **Off-White (#F8FAFC)** to reduce eye strain during long sessions, while cards remain pure white to pop against the canvas. The **Soft Gold accent (#FACC15)** is reserved for moments of achievement or high-level alerts, acting as a scholarly "seal" of quality. Status colors are distinct and high-contrast, ensuring immediate recognition of submission results in dense tables.

## Typography

This design system employs a dual-font strategy. **Manrope** is used for headlines to provide a modern, refined executive feel. **Inter** handles the majority of the UI for its exceptional legibility and neutral tone.

Crucially, **Space Grotesk** is utilized for code blocks, submission IDs, and scoreboard data. While technically a "grotesk," its geometric rigor serves the function of a monospace font in this system, providing the technical "tech-dashboard" edge required for a programming contest. All code displays must prioritize character distinction (e.g., 0 vs O, l vs 1).

## Layout & Spacing

The layout utilizes a **Fixed Grid** approach for the main content area (max-width 1440px) to ensure readability of long-form problem statements, while the sidebar remains fixed to the viewport.

A rigorous 4px baseline grid ensures vertical rhythm. Dashboards should use a 12-column system with 20px gutters. Content is organized into clear logical groups using ample white space between sections (32px+) but tight, efficient spacing within components (8px-16px) to maximize information density without clutter.

## Elevation & Depth

Depth is conveyed through **Tonal Layers** and **Low-Contrast Outlines**. The application does not use heavy shadows. 

1.  **Level 0 (Background):** #F8FAFC - The base canvas.
2.  **Level 1 (Cards/Surface):** #FFFFFF - Primary content containers with a 1px #E2E8F0 border and a very subtle ambient shadow (0px 1px 3px rgba(0,0,0,0.05)).
3.  **Level 2 (Dropdowns/Modals):** #FFFFFF - Use a slightly more pronounced shadow (0px 10px 15px rgba(0,0,0,0.1)) to indicate focus.

Navigation sidebars are treated as structural pillars; they use the Primary Structure color (#1E3A5F) with no shadow, relying on color contrast to define depth.

## Shapes

The design system uses a **Soft** shape language. Standard components (inputs, buttons, cards) use a 4px (0.25rem) radius. This provides a professional, "standard-issue" feel that is more modern than sharp corners but more serious than highly rounded "pill" designs. Status badges may use a slightly higher radius (8px) to distinguish them from functional buttons.

## Components

- **Buttons:** Primary buttons use #2563EB with white text. Secondary buttons use a white background with #E2E8F0 borders and #1E3A5F text.
- **Status Badges:** Use a light tinted background (10% opacity of the status color) with a dark solid text of the same color. Always pair the color with a label for accessibility.
- **Monospace Code Blocks:** Use a #F1F5F9 background, 1px border, and syntax highlighting that respects the professional palette. No "glow" or neon effects.
- **Input Fields:** 1px #E2E8F0 border, switching to #2563EB on focus. Use Inter-medium for labels and Inter-regular for placeholder text.
- **Cards:** Clean white surfaces. Header sections of cards should have a subtle bottom border of #F1F5F9 to separate the title from the content.
- **Sidebar:** Deep navy background. Active states should be indicated by a solid blue left-edge border (3px) and a subtle background highlight.
- **Data Tables:** High density. Use Space Grotesk for numeric values and submission IDs. Rows should have a subtle hover state (#F8FAFC) to help users track information across columns.