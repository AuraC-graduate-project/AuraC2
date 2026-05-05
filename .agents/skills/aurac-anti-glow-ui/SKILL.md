---

name: aurac-anti-glow-ui

description: Use when redesigning AuraC² UI, especially dark/light theme work. Enforces professional anti-glow university dashboard styling and prevents cyberpunk/neon AI-looking interfaces.

---



\# AuraC² Anti-Glow UI Skill



Use this skill whenever modifying AuraC² frontend UI, theme, dashboard layout, dark mode, light mode, cards, tables, buttons, badges, modals, code editor visuals, or page structure.



\## Product Context



AuraC² — Aura Contest Control is a university programming contest management system.



The UI must feel:

\- professional

\- academic

\- clean

\- calm

\- student-friendly

\- dashboard/tool-like

\- suitable for a graduation project presentation



It must NOT feel:

\- cyberpunk

\- neon

\- overly glowing

\- gaming-style

\- sci-fi

\- generic AI-generated SaaS

\- overly dark

\- flashy



\## Dark Mode Rules



Dark mode must be professional, not glowy.



Use:

\- deep slate/navy backgrounds, not pure black

\- dark cards with clear borders

\- soft contrast

\- readable text

\- restrained blue/gold accent

\- subtle shadows

\- clear focus states



Avoid:

\- neon borders

\- glowing cards

\- glowing buttons

\- aurora/mesh gradients

\- huge blur blobs

\- purple/cyan cyberpunk effects

\- glassmorphism overload

\- text with glow

\- dashboard that looks like a game HUD



\## Light Mode Rules



Light mode is the default.



Use:

\- off-white/slate background

\- white cards

\- slate/navy text

\- blue primary actions

\- restrained Aura gold accent

\- thin borders

\- clean tables

\- calm status badges



\## Theme Implementation Rules



Implement one shared set of pages with theme support.



Do NOT create duplicate dark pages.

Do NOT create separate dark-mode copies of components.



Use a central theme system:

\- ThemeProvider

\- useTheme hook

\- ThemeToggle component

\- localStorage persistence

\- root dark class on document.documentElement if using Tailwind dark mode



Theme toggle placement:

\- Login: top-right or inside login card

\- Admin: top nav

\- Team: team header near logout



\## AuraC² Accuracy Rules



Do not invent unimplemented features.



These must be Under Development only:

\- scoreboard

\- rejudge

\- reports/export

\- statistics/analytics backend

\- security monitor

\- live updates/SSE/WebSocket

\- announcements/broadcast

\- system logs

\- run custom tests

\- per-test-case result analytics



Implemented pages must use real API data when available.



\## Component Style Rules



Cards:

\- subtle border

\- soft shadow only

\- no glow

\- consistent radius

\- clear heading/body separation



Tables:

\- readable row spacing

\- clear headers

\- visible actions

\- status badges

\- empty/error/loading states



Buttons:

\- primary blue

\- destructive red

\- secondary slate/outline

\- no neon glow

\- disabled state with explanation



Badges:

\- ACCEPTED green

\- WRONG\_ANSWER red

\- TLE orange

\- COMPILATION\_ERROR violet but not neon

\- RUNTIME\_ERROR rose

\- PENDING amber

\- RUNNING blue

\- UPCOMING slate/blue

\- PAUSED amber

\- ENDED gray



Code editor:

\- monospace

\- line-number-like styling

\- dark code panel allowed in both themes

\- no neon glow

\- submit state clearly visible



\## Review Checklist



Before finishing, verify:

\- no wrong branding

\- no duplicate dark pages

\- no neon/glow UI

\- no fake implemented features

\- team pages do not use admin sidebar

\- dark mode is readable

\- light mode is clean

\- build passes

