# GARP — Design System

World: **MapLibre GL + OpenFreeMap** (libre, keyless vector tiles, style `dark`). (user-pinned: "flat, gausah muluk-muluk, dashboard shadcn pada umumnya" → Vercel/Linear variant chosen via clarify).

## Tokens (frontend/styles.css :root)
- bg `#0a0a0b` · panel `#101012` · panel-2 `#141417`
- line `#232327` · line-soft `#1c1c20`
- text `#ededf0` · text-2 `#a1a1aa` · text-3 `#63636b`
- accent `#5e6ad2` (Linear indigo — used sparingly: primary buttons, active states, chart, highlight KPI)
- status: green `#3fb27f`, amber `#d9a03f`, red `#e5484d`
- font: Inter (UI, feature-settings cv02–cv04/ss01) + JetBrains Mono (labels, codes, meta)
- radius 10px cards / 8px buttons; shadow soft double-layer

## Rules
- Near-black neutral; hierarchy from text-color steps, not borders heavier than 1px.
- Mono uppercase micro-labels for KPI/table headers; tabular-nums for all figures.
- Accent only where it means "primary" or "highlight" — never decorative.
- Map tiles: grayscale + brightness .42 + contrast 1.12 (Leaflet filter).
- Badges/pills: color-tinted translucent backgrounds, mono 11px.
- Icon buttons: 28px ghost squares, hover border+color; watch variant tints accent.
- Reduced-motion respected; entrance animations 45ms stagger.

## Charts (Chart.js)
- defaults: color #63636b, borderColor #1c1c20, font Inter 11
- line: #5e6ad2, 2.5px, fill rgba(94,106,210,.10), points only where y>0 (3px)
