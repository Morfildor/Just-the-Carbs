# Handoff: Just the Carbs — visual redesign

## Overview
"Just the Carbs" is a rebrand and visual redesign of the CarbScan Android app — a tool that scans a food barcode, takes a portion size, and shows the carbohydrate grams. The redesign keeps the app's core simplicity but replaces its plain Material 3 look with a warmer, bolder, more distinctive visual identity: a bright pastel trio of blue, red and orange over a soft cream base, paired with a geometric display typeface. Goal: modern, fun, trendy — while staying fast and readable for someone scanning food in a kitchen.

## About the design files
The files in `screens/` are **design references built in HTML/React** — high-fidelity visual and interaction prototypes, not production code. Recreate these designs in the target codebase's actual environment (native Android/Kotlin/Jetpack Compose, since that's what CarbScan is built in — or React Native/Flutter if the app is being rebuilt) using that platform's own component and state-management patterns. Do not literally copy the HTML/CSS/JS.

Each screen file is self-contained and opens directly in a browser (it renders inside a phone-frame illustration — ignore the phone bezel, it's a presentation device only, not part of the design). `android-frame.jsx` is that presentation shell; it is not part of the product design.

## Fidelity
**High-fidelity.** Colors, typography, spacing, and copy below are final — implement pixel-close using the design tokens listed. Exact px/rem values, not rounded to a generic grid.

## Screens

### 1. Onboarding (`onboarding.html`)
**Purpose:** First-launch, 3-slide carousel explaining the core loop before the user ever scans anything.
**Layout:** Full-screen slide, vertical stack: top-right "Skip" link → center content block (icon mark, eyebrow label, headline, body) → bottom block (dot pagination, full-width CTA button). Two decorative background circles (radius ~110px) bleed off the top-right and bottom-left corners at low opacity, color-matched to the slide's accent.
**Slides:**
  1. Background `--blue` (#2F8FE0), white text/icon. Eyebrow "STEP 1", headline "Scan it.", body "Point your camera at any barcode. No searching, no typing a product name."
  2. Background `--cream` (#FFF6EE), ink text, orange icon/accent circles. Eyebrow "STEP 2", headline "Size the portion.", body "Drag, type, or tap a preset. Grams, slices, whatever makes sense for the food."
  3. Background `--red` (#FF5C5C), white text/icon. Eyebrow "STEP 3", headline "Get the number.", body "Just the carbs. Nothing else on screen competes with it."
**Components:**
  - Icon mark: 64px, an abstract "sliced rounded square revealing a core circle" glyph (see Assets) — tinted to the slide's foreground color.
  - Eyebrow: Space Grotesk 700, 13px, letter-spacing 2.5px, uppercase, 70% opacity.
  - Headline: Space Grotesk 700, 44px, letter-spacing -1.5px, line-height 1.05.
  - Body: Roboto 400, 17px, line-height 1.5, 85% opacity, max-width 280px.
  - Dot pagination: 7px circles, 8px gap; active dot stretches to 22px wide; 220ms width transition, ease `cubic-bezier(.2,0,0,1)`.
  - CTA button: 60px tall, 18px radius, full width. On slides 1 & 3 (colored bg): white button, colored text (blue on slide 1, red on slide 3). On slide 2 (cream bg): blue button, white text. Label "Next" (chevron ›) on slides 1–2, "Get started" (arrow →) on slide 3.
**Interactions:** Tapping the CTA advances one slide; on the last slide it becomes "Get started" and would navigate to Home. "Skip" jumps straight to the last slide. Background/text color cross-fades 280ms between slides.

### 2. Scanner (`scanner.html`)
**Purpose:** Live barcode capture.
**Layout:** Full-bleed near-black camera background (radial gradient `#242231` → `#111014` standing in for the live camera feed). Top row: close button (left), "Just the Carbs" wordmark (center), flash toggle (right) — all in frosted circular chips. Centered scan frame, 280×180px, violet-stroked rounded rectangle (26px radius) with corner brackets and an animated horizontal scan line. Bottom: instruction text + "Enter barcode manually" pill button.
**Components:**
  - Icon chips: 44px circle, `rgba(255,255,255,0.12)` fill, white icon.
  - Scan frame: violet (#5546D4) 3px stroke, `rgba(85,70,212,0.06)` fill, 4 white corner brackets (22px legs, 4px stroke, rounded caps).
  - Scan line: 2px, violet, glow (`box-shadow 0 0 12px 2px violet`), animates top 18%→82%→18% over 1.8s ease-in-out, looping.
  - Manual entry button: 52px tall, 16px radius, 1px `rgba(255,255,255,0.25)` border, `rgba(255,255,255,0.08)` fill, white text, keyboard icon + "Enter barcode manually".
**Interactions:** Close returns to Home. A successful scan navigates to Result with the scanned product. Manual entry navigates to Manual Entry screen.

### 3. Result / Calculator (`result.html`) — the most important screen
**Purpose:** Confirm the product, size the portion, read the carbohydrate result. This is the screen the entire app exists to show — it should never require scrolling to see the number.
**Layout:** Cream background, one decorative blue-soft circle bleeding off the top-right. Vertical stack: header → scrollable middle (product card, portion input) → pinned bottom result sheet (white, rounded top corners, drop shadow) that is always fully visible.
**Components:**
  - Header: 44px circular back button, centered product name (Space Grotesk 600, 15px), 44px favorite star toggle (blue when active, ink when not).
  - Product card: white, 22px radius, 6px/18px soft shadow. Contains a **large product image area — 168px tall, full card width**, rounded 16px, blue-soft (`#E4F1FC`) placeholder background with a large centered monogram (e.g. "VB") in blue, 44px bold — sized so the user can genuinely compare it against the package in their hand. Below the image: product name + "{X}g carbs / 100g" (Roboto, 13px, muted) on the left, an "ONLINE" source pill (dark amber text on soft orange `#FFEEDC`, 11px bold, pill-shaped) on the right.
  - Portion input: centered label "How much are you having?" (Roboto 13px muted), then a large inline numeric input + "g" unit (Space Grotesk 700, 48px, letter-spacing -1.5px, right-aligned number), a full-width range slider (blue thumb, light track), a row of 4 stepper pills (−10/−5/+5/+10, 44px tall, white, 14px radius, 1px border), and a horizontally-scrollable row of preset chips ("¼ pack", "½ pack", "Full pack", "1 slice" — pill-shaped, blue-outlined when active).
  - **Result sheet** (pinned, always visible): white background, 32px top corner radius, lifted shadow. Centered: the result number in **red** (#FF5C5C), Space Grotesk 700, 64px with a smaller 28px "g" unit, letter-spacing -2px — then a supporting line "≈ {whole}g whole grams · carbs" (Roboto 15px muted). A brief "pop" scale animation (260ms, `cubic-bezier(.2,0,0,1)`, 0.94→1.02→1.0) plays whenever the portion changes, to give the number life without delaying it. Below: two buttons side by side — outlined "Add to meal" (white, 1.5px border, ink text) and filled blue "Scan next →" (weighted 1.3fr vs 1fr).
**Interactions:** Typing, dragging the slider, tapping a stepper, or tapping a preset chip all update the portion and trigger the result's pop animation live. "Add to meal" adds this result to the running meal total and returns to Home or Scanner. "Scan next" adds to meal and reopens the Scanner.
**Why red here and blue elsewhere:** blue is the app's primary interactive color (buttons, active states, favorites) — but the calculator result is red, so the single most important number on screen has its own unmistakable, energetic color, never confused with a button. Orange is reserved for informational badges and soft accents.

### 4. Home (`home.html`)
**Purpose:** Landing screen — recent products and the two ways to start a new lookup.
**Layout:** Cream background, top-right decorative violet-soft circle. Vertical stack: header (wordmark + settings button) → meal-in-progress banner (only shown when a meal has items) → "RECENT" section label → scrollable list of recent-product cards → bottom action block.
**Components:**
  - Header: "Just the Carbs" (Space Grotesk 700, 24px, letter-spacing -0.5px) + 44px circular settings icon button.
  - Meal banner: blue-soft pill/card, 18px radius, blue text "Meal · {n} items · {total}g", chevron-right, tappable.
  - Section label: Space Grotesk 700, 12px, letter-spacing 1.6px, uppercase, muted.
  - Recent card: white, 18px radius, soft shadow, 52px rounded-14 monogram tile (blue-soft bg, blue text) + product name (15px 600) + last portion→result summary (13px muted, Roboto) + favorite star (orange filled / light gray outline).
  - Primary CTA: 60px tall blue button, 18px radius, white text, scan icon, "Scan barcode", blue glow shadow.
  - Secondary CTA: text-only blue button, "Enter manually".
**Interactions:** Tapping a recent card opens Result pre-filled with that product. Tapping the star toggles favorite in place. "Scan barcode" opens Scanner; "Enter manually" opens Manual Entry; the meal banner opens Meal.

### 5. Manual entry (`manual-entry.html`)
**Purpose:** Add a product the barcode scanner can't find.
**Layout:** Cream background, top-left decorative circle. Header (back + "Enter product") → scrollable form → pinned "Save product" button.
**Components:**
  - Field: label (12px 600 muted) above a white 16px-radius input row (56px tall, soft shadow), Roboto 16px input text, optional unit suffix text.
  - Fields, in order: Product name, Carbohydrates (with dynamic suffix "g / 100g" or "g / 100ml"), a "Measured per" toggle (two pill buttons, "100 g" / "100 ml", blue fill when selected), Package size (optional).
  - Save button: 58px tall, 18px radius, blue when the two required fields are filled, disabled state a flat pale blue-gray (`#DCE8F5`).
**Interactions:** Save is disabled until name + carbs are entered. Saving returns to Home with the new product added to Recent.

### 6. Settings (`settings.html`)
**Purpose:** Theme, interaction, and data preferences plus the safety disclaimer.
**Layout:** Cream background. Header (back + "Settings") → scrollable grouped list, each group preceded by an uppercase section label.
**Groups:**
  - Appearance: System/Light/Dark pill selector (same pill style as manual entry's toggle).
  - Interaction: "Haptic feedback" row with a blue toggle switch (46×27px track, 21px white knob, 200ms ease slide).
  - Data: "Clear recent history" / "Clear saved products" rows, each with a blue chevron.
  - About: version text, then an **orange-tinted safety card** (`#FFEEDC` bg, 18px radius, deliberately warm rather than alarming) — bold amber "Important" heading + the disclaimer copy verbatim (see Content below) — then small muted attribution text for Open Food Facts / ODbL.
**Interactions:** Standard settings toggles/selectors; no navigation beyond back.

### 7. Meal (`meal.html`)
**Purpose:** Running total across multiple scanned items in one sitting.
**Layout:** Cream background, top-right decorative circle. Header (back, "Meal" title, violet "Clear" text button) → scrollable item list → pinned bottom total sheet (same white/rounded-top/shadow treatment as the Result screen).
**Components:**
  - Item row: white, 16px radius, soft shadow — name (15px 600) + "{portion} · {carbs}g" (13px muted Roboto) + a small circular remove (×) button.
  - Total sheet: uppercase "MEAL TOTAL" label, big red number (60px, same style family as the Result screen's number, smaller), "≈ {whole}g whole grams" supporting line, full-width blue "Done" button (54px, 16px radius).
**Interactions:** Removing an item updates the total live. "Clear" empties the meal (should confirm first in production). "Done" returns to Home.

## Design tokens

**Colors — a bright pastel trio over a warm neutral base**
- Blue (primary interactive accent — buttons, active states, links, favorites): `#2F8FE0`
- Blue soft (accent tint, cards/badges): `#E4F1FC`
- Red (reserved for the carbohydrate result number only — the one thing that must pop): `#FF5C5C`
- Orange (secondary accent — informational badges, safety card, decorative warmth): `#FFA94D`
- Orange soft (safety card / badge bg): `#FFEEDC`
- Cream (app background): `#FFF6EE`
- Ink (primary text): `#181A1E`
- Ink muted (secondary text): `#6B6A72`
- Neutral line/disabled: `#E4DFD3`
- Disabled blue button: `#DCE8F5`

Color roles are deliberately separated so nothing competes with the result: blue owns every interactive control, orange owns soft informational surfaces, and red is spent on exactly one thing per screen — the carbohydrate number (Result screen and Meal total).

**Typography**
- Display/UI font: **Space Grotesk** (weights 500/600/700) — headlines, numbers, buttons, labels.
- Body font: **Roboto** (weights 400/500/600) — supporting copy, descriptions, form input text.
- Result number: Space Grotesk 700, 64px, letter-spacing -2px (result screen); 60px on the Meal total.
- Portion input number: Space Grotesk 700, 48px, letter-spacing -1.5px.
- Onboarding headline: Space Grotesk 700, 44px, letter-spacing -1.5px.
- Section/eyebrow labels: Space Grotesk 700, 12–13px, letter-spacing 1.6–2.5px, uppercase.
- Body text: Roboto 400, 13–17px depending on context.

**Spacing / radius**
- Card radius: 16–22px (18px is the most common).
- Button radius: 14–18px.
- Pill/chip radius: 999px (full).
- Bottom sheet top corners: 32px.
- Standard screen padding: 20px horizontal.

**Shadows**
- Card: `0 4px 14px rgba(24,26,30,0.05)` to `0 6px 18px rgba(24,26,30,0.06)`.
- Pinned bottom sheet: `0 -8px 24px rgba(24,26,30,0.06)`.
- Primary CTA glow (violet button on Home): `0 8px 20px rgba(85,70,212,0.3)`.

**Motion**
- Standard easing: `cubic-bezier(.2, 0, 0, 1)`.
- Result "pop" on value change: 260ms, scale 0.94 → 1.02 → 1.0.
- Toggle/dot/slide transitions: 200–280ms.
- Scanner scan-line loop: 1.8s ease-in-out, infinite.

## Content (verbatim copy used)
- Onboarding: "Scan it." / "Point your camera at any barcode. No searching, no typing a product name." — "Size the portion." / "Drag, type, or tap a preset. Grams, slices, whatever makes sense for the food." — "Get the number." / "Just the carbs. Nothing else on screen competes with it."
- Settings safety card: "Just the Carbs calculates the carbohydrate content of a portion. It does not calculate insulin or any other medication, and it does not replace the information printed on the package."
- Attribution: "Product data from Open Food Facts, used under the Open Database License (ODbL)."
- Tone carries over from the parent CarbScan design system: precise, simple, factual, second-person, sentence case, no emoji. See that system's `readme.md` (Content Fundamentals section) for the full voice guide.

## Assets
- **Icon mark**: an original abstract glyph — a rounded square "sliced open" at the top-left, revealing a smaller solid circle with a light center dot, meant to read as "the essential thing, isolated." Defined as inline SVG in `onboarding.html` (component `Mark`), tinted per-slide (blue / orange / red). No external file. This mark is new to this redesign and has not been used elsewhere — treat it as a placeholder brand mark pending sign-off, not a final logo.
- **No wordmark/logotype file** — "Just the Carbs" is currently set in plain Space Grotesk 700 wherever a lockup appears.
- **Product images**: placeholder monogram tiles (2-letter initials on violet-soft) stand in for real product photography (would come from Open Food Facts in production, `object-fit: contain`, never cropped — see the parent CarbScan design system for that rationale).
- **Icons**: Google Material Symbols (outlined, filled variant), loaded via `https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:FILL@1` — same icon system as the parent CarbScan design system.
- **Fonts**: Space Grotesk and Roboto, both loaded via Google Fonts in each HTML file's `<head>`.

## Files
All 7 screens plus the presentation-only phone frame are in `screens/`:
- `onboarding.html`, `scanner.html`, `result.html`, `home.html`, `manual-entry.html`, `settings.html`, `meal.html`
- `android-frame.jsx` — phone bezel used only to preview the screens; not part of the product design.

These originated from the CarbScan design system project (design tokens, base component set, and product/content rationale) — that project's `readme.md` has additional background on the source app this redesign builds from.
