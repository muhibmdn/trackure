---
name: Obsidian Kinetic
colors:
  surface: '#101419'
  surface-dim: '#101419'
  surface-bright: '#36393f'
  surface-container-lowest: '#0a0e13'
  surface-container-low: '#181c21'
  surface-container: '#1c2025'
  surface-container-high: '#262a30'
  surface-container-highest: '#31353b'
  on-surface: '#e0e2ea'
  on-surface-variant: '#e6beb2'
  inverse-surface: '#e0e2ea'
  inverse-on-surface: '#2d3136'
  outline: '#ac897e'
  outline-variant: '#5c4038'
  surface-tint: '#ffb59f'
  primary: '#ffb59f'
  on-primary: '#5e1700'
  primary-container: '#ff571b'
  on-primary-container: '#531300'
  inverse-primary: '#ae3100'
  secondary: '#7dffa2'
  on-secondary: '#003918'
  secondary-container: '#05e777'
  on-secondary-container: '#00622e'
  tertiary: '#8dcdff'
  on-tertiary: '#00344f'
  tertiary-container: '#0099df'
  on-tertiary-container: '#002d45'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdbd0'
  primary-fixed-dim: '#ffb59f'
  on-primary-fixed: '#3a0a00'
  on-primary-fixed-variant: '#852400'
  secondary-fixed: '#62ff96'
  secondary-fixed-dim: '#00e475'
  on-secondary-fixed: '#00210b'
  on-secondary-fixed-variant: '#005226'
  tertiary-fixed: '#cae6ff'
  tertiary-fixed-dim: '#8dcdff'
  on-tertiary-fixed: '#001e30'
  on-tertiary-fixed-variant: '#004b70'
  background: '#101419'
  on-background: '#e0e2ea'
  surface-variant: '#31353b'
typography:
  display-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 40px
    fontWeight: '800'
    lineHeight: 48px
  display-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '800'
    lineHeight: 38px
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 34px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  metric-xl:
    fontFamily: JetBrains Mono
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 40px
  metric-lg:
    fontFamily: JetBrains Mono
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 28px
  metric-md:
    fontFamily: JetBrains Mono
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 20px
  label-caps:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.25rem
---

## Brand & Style

The design system establishes a high-performance visual language tuned for disciplined habit formation and athletic accountability. Built around the commitment "One Sure Step Everyday," the aesthetic bridges the physiological drive of endurance training (Strava) with the visual proof of consistency found in developer telemetry and commit matrices (GitHub).

The personality is intense, precise, and utilitarian. It rejects decorative bloat, soft pastels, and gamified novelty in favor of deep-field contrast, laser-focused data readouts, and immediate tactile feedback. Every interaction reinforces momentum: streaks are engineered as chains of unbroken data points, routine completions trigger crisp affirmative pulses, and metrics are rendered with surgical clarity. The target user seeks mastery, routine stability, and the raw visual satisfaction of verifiable daily progress.

## Colors

The palette operates in a default deep obsidian dark mode, using luminance contrasts to drive cognitive focus without causing optical fatigue during dawn or late-evening workouts.

- **Canvas & Structural Neutrals:**
  - Base Canvas: `#0B0F14` (Deep Obsidian)
  - Elevated Container / Card Fill: `#131A22` (Graphite Plate)
  - Surface Highlight / Interactive Inactive: `#18222E`
  - Structural Outline / Ghost Border: `#1E2936`
  - Subtle Divider: `rgba(255, 255, 255, 0.06)`

- **Accent System:**
  - **Primary Kinetic Accent (`#FC4C02`):** Reserved for primary calls-to-action, active workout tracking sessions, focal interactive buttons, and high-energy highlights. Emits an ambient low-spread orange glow on active states (`rgba(252, 76, 2, 0.25)`).
  - **Success / Streak Emerald (`#00E676`):** Signals completed reps, verified routines, positive target deltas, and the highest tiers of the daily activity matrix. Muted fallback green `#10B981` is applied to sub-indicators and secondary streak tags.
  - **Telemetry Electric Blue (`#00B0FF`):** Used for scheduled events, pending blocks, split timestamps, and analytical routine tags. Deep utility blue `#0284C7` handles secondary badge backdrops.

- **Typography & Icon Contrast:**
  - High-Contrast Primary: `#F8FAFC` (Headers, essential metrics, primary labels)
  - Mid-Tone Secondary: `#94A3B8` (Descriptions, passive metrics, day labels)
  - Low-Tone Muted: `#64748B` (Inactive matrix squares, past timestamps, borders)

## Typography

The type system balances kinetic athletic energy with surgical scientific recording through a tripartite font stack:

1. **Plus Jakarta Sans (Headings & Display):** Chosen for its geometric, high-energy stance and forward-leaning structural cut. Used at extra-bold weights to anchor section goals, titles, and heroic summary cards.
2. **Inter (Body & Narrative):** Provides neutral, highly legible copy for instructions, habit descriptions, settings, and form contexts without distracting from key figures.
3. **JetBrains Mono (Metrics, Telemetry & Streaks):** The central data engine. All counts, timers, streak tally badges, commit dates, pacing metrics, and split data are locked to monospaced tabular alignment. This guarantees that numbers do not shift or jitter when counting up during active tracking.

Capitalization rules: Section eyebrow tags, streak tracking codes, and matrix headers are set in uppercase `label-caps` with `+0.06em` letter spacing for an authentic telemetry aesthetic.

## Layout & Spacing

The layout is built around a mobile-first, single-column fluid frame that snaps to a strict 4px/8px incremental grid.

- **Canvas Margins & Gutters:** Mobile viewports use a fixed outer horizontal safe margin of `1.25rem` (20px), ensuring touch targets never bleed into bezel curves or device gestures. Element columns use a `1rem` (16px) gutter.
- **Rhythm & Stacking:**
  - `space-xs` (4px): Used between metric values and small unit labels (e.g., `42` and `KM`), and matrix grid gaps.
  - `space-sm` (8px): Inner padding of compact badge chips, spacing between title and subtitle lines, and gap between checklist checkboxes and routine titles.
  - `space-md` (16px): Internal padding of habit cards, gap between consecutive list cards.
  - `space-lg` (24px): Boundary padding between thematic blocks (e.g., Habit Checklist transitioning into the Routine Matrix).
  - `space-xl` (36px): Top-of-page hero offsets and primary CTA isolation zones.
- **Form Factor Adaptations:** On tablet or wider split-screen dashboards, the interface expands into an asymmetric 12-column layout: an 8-column primary habit and metric stream flanked by a 4-column routine summary and analytics rail.

## Elevation & Depth

Visual hierarchy does not rely on soft, diffuse drop shadows, which dilute the aggressive dark aesthetic. Instead, depth is articulated through **tonal stratification, hairline border containment, and directed neon glows**:

1. **Level 0 (Canvas Base):** Pure `#0B0F14`. The foundational background for all screen canvases.
2. **Level 1 (Card & Section Containers):** Flat fill of `#131A22` bound by a razor-thin 1px solid border of `#1E2936`. This provides distinct containment without muddy blur artifacts.
3. **Level 2 (Interactive Floating Layers & Modals):** Background elevated to `#18222E`, with a 1px border colored at `rgba(255, 255, 255, 0.1)`. Modals and floating sheets carry a deep boundary shadow: `0 16px 32px rgba(0, 0, 0, 0.7)`.
4. **Active Action Glow:** The primary action element (center action button, activated recording tracker) casts an ambient radial underglow: `0 0 20px rgba(252, 76, 2, 0.35)`.
5. **Glassmorphism Spec for Bottom Nav:** Floating navigational dock uses a dark frosted glass substrate:
   - Fill: `rgba(11, 15, 20, 0.82)`
   - Backdrop Filter: `blur(16px)`
   - Top Border: `1px solid rgba(255, 255, 255, 0.08)`

## Shapes

The design system employs a disciplined, balanced geometry (roundedness level 2):

- **Cards & Data Modules:** `0.75rem` (12px) to `1rem` (16px) corner radius. Corners remain tight and athletic, avoiding the hyper-soft look of consumer apps while preventing the severe harshness of 0px industrial brutalism.
- **Inputs & Action Buttons:** `0.75rem` (12px) radius. Stable, firm tap zones that align flush with metric card edges.
- **Metric Chips, Heatmap Cells & Progress Indicators:**
  - Micro Matrix Squares (GitHub-style habit grid): `3px` corner radius with `3px` gap intervals.
  - Status Pills & Streak Count Badges: Fully pill-shaped (`9999px`) to immediately distinguish analytical state tags from actionable rectangular cards.

## Components

### 1. Action Buttons
- **Primary Kinetic Button:** Solid `#FC4C02` background, text in high-contrast `#FFFFFF` using `Plus Jakarta Sans` 16px Bold. Height: 52px. Active press scales down slightly (`scale(0.98)`) and intensifies the base orange glow.
- **Secondary Surface Button:** Fill `#131A22`, border 1px `#1E2936`, text `#F8FAFC`. On hover/touch, border brightens to `#94A3B8`.
- **Ghost Action:** Borderless, text in `#FC4C02` or `#00E676`, padding `0.5rem 1rem`.

### 2. High-Performance Habit Cards
- Base container: `#131A22` with a 1px `#1E2936` stroke.
- Left indicator: A 3px vertical accent stripe indicating habit category (Orange = Athletic, Emerald = Discipline/Health, Electric Blue = Cognitive/Routines).
- Layout: Left side houses the title and target metric (`JetBrains Mono`); right side hosts the interactive completion node.

### 3. Verification Checkboxes & Quick-Logs
- Uncompleted: 24x24px rounded box (`4px` radius), 1.5px border `#1E2936`, interior clear.
- Completed: Crisp transition to filled `#00E676` with a white checkmark icon, accompanied by a quick numeric streak increment ping (`+1`).

### 4. GitHub-Style Habit Contribution Heatmap
- Dense 7-row matrix representing days of the week, with rolling 12 to 24-week columns.
- Cell dimensions: 10px × 10px with 3px gaps and 3px border radius.
- Color ramp (Obsidian to Radiant Emerald):
  - Tier 0 (Empty/Missed): `#18222E`
  - Tier 1 (1–25% completed daily routines): `#064E3B`
  - Tier 2 (26–50% completed): `#047857`
  - Tier 3 (51–75% completed): `#10B981`
  - Tier 4 (100% completed / Goal Exceeded): `#00E676` (with subtle glow)

### 5. Circular Kinetic Progress Rings
- SVG-based circular gauges for daily completion percentages.
- Track backdrop: `#1E2936` with 6px stroke width.
- Progress stroke: Dynamic gradient transitioning from `#00B0FF` through `#00E676` to `#FC4C02` depending on intensity tier. Cap style: rounded (`stroke-linecap: round`). Center readout features `metric-lg` numeric values.

### 6. Badges & Chips
- Compact height (24px), font `label-caps` (`JetBrains Mono` 11px).
- Streak Badge: Background `rgba(252, 76, 2, 0.12)`, border `1px solid rgba(252, 76, 2, 0.3)`, text `#FC4C02`, accompanied by a monochrome flame glyph.
- Schedule Badge: Background `rgba(0, 176, 255, 0.12)`, border `1px solid rgba(0, 176, 255, 0.3)`, text `#00B0FF`.

### 7. Glassmorphic Bottom Navigation
- Fixed anchored bar with safe-area spacing.
- Substrate: `#0B0F14` at 85% opacity with 20px blur.
- Center Prominent Action Button: 56px circle elevated -16px above the bar line, filled with `#FC4C02`, casting a `0 0 16px rgba(252, 76, 2, 0.4)` bloom. Dedicated to rapid daily check-in or live workout logging.