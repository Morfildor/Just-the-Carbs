# Just the Carbs — product context

> Written from the project's existing authoritative sources: `docs/design_handoff_just_the_carbs/README.md`
> (the visual brief), `docs/MASTER-PROMPT.md` (requirements, cited as §N), and `CLAUDE.md`
> (verified session record). Nothing here is invented; this file exists so design tooling has the
> context in the conventional place.

## Register

`product` — design SERVES the task. This is a utility the user is *in*, standing in a kitchen
holding food in their other hand. The tool should disappear into the task.

## Product purpose

Scan a food barcode (or read a nutrition label, or search by name) → enter a portion → read the
carbohydrate grams. That is the entire product.

> Open app → find food → choose amount → see carbs → done.

**It does NOT calculate insulin. It is not a diet tracker.** Scope discipline is a hard requirement
(§2), enforced structurally rather than by convention: `MealStore` has no meal id and
`PortionUsageStore` has no all-usage accessor, so a food diary is not buildable without first adding
the missing concept.

## Users

One primary user: someone who needs the carbohydrate figure for a portion of packaged food, quickly,
one-handed, often mid-cooking. They transcribe the result into another calculator, which is why the
**decimal** figure dominates and the whole-gram figure supports it (owner decision #2, revised
2026-08-14) — leading with a rounded number loses precision exactly where it matters.

They are not browsing. They are not logging. They want one number and to leave.

## Tone

Precise, simple, factual, second person, sentence case, no emoji. Never alarming: a crowd-sourced
value that is usually right must not train the user to ignore a warning.

**Regulatory constraint (binding, owner decision #9):** never describe the app as a medical device,
as an accessory to one, or as *not* one. Qualification is unresolved. Never market it for diabetes.
Document/regulatory language must never be dressed up as consumer UI.

## Vocabulary

One consistent set: Scan barcode · Scan nutrition label · Search products · Portion · Serving ·
Slices · Pieces · **Carbs** · Add to meal · Enter manually.

Prefer **Carbs** in consumer UI where brevity fits; use *Total carbohydrate* only where clarity
genuinely requires it (OCR interpretation, where it names a specific row on a printed label).

Internal concepts stay internal: `WeightBased`, `DirectCarbs`, `PER_100_G`, OCR, OFF, basis,
candidate, confidence must never appear in consumer UI.

## Anti-references

Not a dashboard. Not medical-looking. Not over-explained. Not feature-heavy. Not busy. Not a
generic Material demo. No stats, no fake recents, no tips, no streaks, no "eaten today".

Colour is spent on exactly a few jobs so nothing competes with the result.

## Strategic principles

1. **The result is the loudest thing on the screen.** Red is spent on exactly one thing per screen —
   the carbohydrate number. Blue owns every interactive control. Orange owns soft informational
   surfaces.
2. **Never invent a number.** No gram figure exists on the direct-carb path and none is fabricated.
   A basis mismatch offers no apply path rather than converting without density data.
3. **Provenance ≠ verification** (owner correction). `dataSource` and `verificationStatus` are
   separate fields and stay separate.
4. **Calculation-session immutability.** Once the calculator is open, a background refresh never
   changes the value being calculated with; it offers, and the user accepts.
5. **No dead ends.** Every failure offers a way to get a number anyway.
6. **Light is the default theme** regardless of the system setting; System and Dark remain offered.
