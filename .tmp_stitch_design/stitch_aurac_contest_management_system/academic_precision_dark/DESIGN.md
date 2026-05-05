---
name: Academic Precision Dark
colors:
  surface: '#10131a'
  surface-dim: '#10131a'
  surface-bright: '#363941'
  surface-container-lowest: '#0b0e15'
  surface-container-low: '#191b23'
  surface-container: '#1d2027'
  surface-container-high: '#272a31'
  surface-container-highest: '#32353c'
  on-surface: '#e1e2ec'
  on-surface-variant: '#c2c6d6'
  inverse-surface: '#e1e2ec'
  inverse-on-surface: '#2e3038'
  outline: '#8c909f'
  outline-variant: '#424754'
  surface-tint: '#adc6ff'
  primary: '#adc6ff'
  on-primary: '#002e6a'
  primary-container: '#4d8eff'
  on-primary-container: '#00285d'
  inverse-primary: '#005ac2'
  secondary: '#bec6e0'
  on-secondary: '#283044'
  secondary-container: '#3f465c'
  on-secondary-container: '#adb4ce'
  tertiary: '#ffb786'
  on-tertiary: '#502400'
  tertiary-container: '#df7412'
  on-tertiary-container: '#461f00'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#d8e2ff'
  primary-fixed-dim: '#adc6ff'
  on-primary-fixed: '#001a42'
  on-primary-fixed-variant: '#004395'
  secondary-fixed: '#dae2fd'
  secondary-fixed-dim: '#bec6e0'
  on-secondary-fixed: '#131b2e'
  on-secondary-fixed-variant: '#3f465c'
  tertiary-fixed: '#ffdcc6'
  tertiary-fixed-dim: '#ffb786'
  on-tertiary-fixed: '#311400'
  on-tertiary-fixed-variant: '#723600'
  background: '#10131a'
  on-background: '#e1e2ec'
  surface-variant: '#32353c'
typography:
  display:
    fontFamily: Manrope
    fontSize: 3.75rem
    fontWeight: '800'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  h1:
    fontFamily: Manrope
    fontSize: 2.25rem
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.01em
  h2:
    fontFamily: Manrope
    fontSize: 1.875rem
    fontWeight: '600'
    lineHeight: '1.3'
  h3:
    fontFamily: Manrope
    fontSize: 1.5rem
    fontWeight: '600'
    lineHeight: '1.4'
  body-lg:
    fontFamily: Manrope
    fontSize: 1.125rem
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Manrope
    fontSize: 1rem
    fontWeight: '400'
    lineHeight: '1.5'
  body-sm:
    fontFamily: Manrope
    fontSize: 0.875rem
    fontWeight: '400'
    lineHeight: '1.5'
  label-md:
    fontFamily: Manrope
    fontSize: 0.875rem
    fontWeight: '600'
    lineHeight: '1'
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Manrope
    fontSize: 0.75rem
    fontWeight: '700'
    lineHeight: '1'
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  xs: 0.25rem
  sm: 0.5rem
  md: 1rem
  lg: 1.5rem
  xl: 2rem
  xxl: 3rem
---

## Brand & Style

This design system is engineered for intellectual rigor and digital craftsmanship. It targets high-density information environments—research platforms, financial analytics, and educational management systems—where clarity is paramount. The brand personality is authoritative yet unobtrusive, prioritizing the user's focus on data and content over decorative elements.

The visual style follows a **Corporate / Modern** aesthetic with **Minimalist** sensibilities. It utilizes a deep, monochromatic foundation to reduce eye strain during prolonged sessions of deep work, while employing high-energy accents to guide the eye toward critical interactions and status updates.

## Colors

The palette is built upon a "Deep Slate" foundation to provide a stable, low-glare environment. The primary interaction color is a vibrant "Primary Blue," which provides a clear signal for action against the dark background. 

The "Aura Gold" is reserved strictly for highlights, accolades, or specific milestones within the user journey, ensuring it retains its psychological impact. Status badges utilize highly saturated colors but are implemented with semi-transparent backgrounds to ensure they integrate harmoniously into the dark UI without causing visual vibration.

## Typography

The design system exclusively utilizes **Manrope**, a modern geometric sans-serif that excels in both large headlines and small-scale data points. The typographic scale is optimized for readability, with generous line heights to prevent "text crowding" in dark mode.

Headlines use tighter tracking and heavier weights to establish a clear hierarchy, while body text maintains a standard tracking to ensure maximum legibility. Labels and metadata should use the `label-sm` style to provide architectural cues without distracting from primary content.

## Layout & Spacing

This design system employs a strict 8px grid (with a 4px sub-grid for icons and small components) to ensure mathematical alignment. The layout follows a **Fixed grid** model for desktop dashboards, centering content within a 1440px container to maintain optimal line lengths for reading.

Vertical rhythm is maintained by using consistent spacing increments. Grouped elements should use `sm` (8px) spacing, while distinct sections of a page should be separated by `xl` (32px) or `xxl` (48px) to create clear mental boundaries between different types of information.

## Elevation & Depth

In this dark mode environment, depth is communicated through **Tonal layers** and **Low-contrast outlines** rather than traditional drop shadows. 

1.  **Level 0 (Background):** #0F172A — The canvas.
2.  **Level 1 (Surfaces):** #1E293B — Used for cards, sidebars, and main content areas.
3.  **Level 2 (Overlays):** #334155 — Used for modals, tooltips, or elevated states like hover.

Each surface is defined by a 1px border (#334155). This subtle line provides the necessary "edge" to separate elements of similar luminance. Shadows, when used for high-impact modals, should be ultra-diffused with 0% offset and a 20% opacity black tint to create a soft ambient glow rather than a directional shadow.

## Shapes

The shape language is "Soft," utilizing a 0.25rem (4px) base radius. This minimal rounding retains the "Precision" aspect of the system, feeling structured and professional, while avoiding the harshness of sharp 0px corners.

- **Standard Elements (Buttons, Inputs):** 4px (rounded)
- **Containers (Cards, Modals):** 8px (rounded-lg)
- **Large Sections:** 12px (rounded-xl)

This consistent application of subtle radii ensures that the interface feels modern and approachable without sacrificing its institutional character.

## Components

### Buttons
- **Primary:** Solid #3B82F6 background with #F1F5F9 text. 4px border radius.
- **Secondary:** #1E293B surface with a #334155 border. Text in #F1F5F9.
- **Ghost:** Transparent background with #94A3B8 text, turning to #F1F5F9 on hover.

### Status Badges
Badges use a 10% opacity version of the status color for the background and a 100% opacity version for the text. This ensures vibrancy without overwhelming the dark interface. For example, a "Success" badge uses a #10B981 (Green) text on a #10B9811A background.

### Input Fields
Inputs should use the #0F172A background with a #334155 border. On focus, the border transitions to #3B82F6 with a subtle 2px outer glow. Labels sit above the field using the `label-sm` typography style in #94A3B8.

### Cards
Cards utilize the #1E293B surface color and the #334155 border. They should not have shadows by default, relying instead on the tonal difference from the background to establish hierarchy.

### Data Tables
Rows are separated by 1px solid #334155 lines. Header cells use #1E293B background to differentiate from the data rows, with text in `label-sm` weight for clarity.