# UI/UX review — Just the Carbs, from a type 1 diabetes consumer

**Date:** 2026-08-16
**Method:** code read of every screen in `app/src/main/kotlin/app/justthecarbs/ui/`, plus
`values/strings.xml`, `PRODUCT.md`, `DESIGN.md`. Not verified on a device — this is a design
critique, not a QA pass. Anything claiming a rendered result is marked as needing confirmation.
**Perspective:** a person with type 1 diabetes counting carbs to dose insulin, several times a day,
for years — not a first-time user, not a dieter.

---

## Preface: what the app already gets right

Stating this first because most of what follows is criticism, and the criticism is only useful
against an accurate baseline. The following are unusual and correct, and several apps in this
category get them wrong:

- **The decimal figure dominates.** Owner decision #2. This is right and it matters: the number is
  transcribed into a bolus calculator, and leading with `31 g` when the answer is `31.3 g` throws
  away precision exactly where it is consumed.
- **Copy emits only the number.** `clipboard.setText(AnnotatedString(copiedValue))` —
  [ProductScreen.kt:1533](../../app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt#L1533).
  Pasting `31.3 g carbs` into a pump app is a paste-then-delete task. This is a small decision that
  removes a daily irritation.
- **The result panel is pinned and never moves.** It reserves its own height when empty
  ([ProductScreen.kt:1476-1488](../../app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt#L1476-L1488)),
  so nothing jumps as the first digit lands.
- **Meal lines store their own figures and are never recomputed** from the product
  ([MealScreen.kt:182-187](../../app/src/main/kotlin/app/justthecarbs/ui/meal/MealScreen.kt#L182-L187)).
  A correction made after adding an item does not silently rewrite a total the user already dosed on.
- **Basis mismatch offers no apply path** rather than converting without density. Refusing to answer
  is the correct answer.
- **The result is a `liveRegion`.** TalkBack reads the new figure as the portion changes. Blind and
  low-vision T1D users exist in numbers, and this is frequently missed.
- **"Usual", not "Suggested".** Reporting behaviour rather than recommending intake. The distinction
  is exactly right and is the difference between a tool and a diet app.

The app's problems are not correctness problems. They are **workflow-shape** problems: it is built
around one product at a time, and real carb counting is usually not one product at a time.

---

## The central finding

> **The meal is treated as a feature of the calculator. For a T1D user it is usually the task.**

Evidence in the code, not inference:

1. `MealActions` renders only inside `ResultPanel`, and only when `exact != null`
   ([ProductScreen.kt:1567-1570](../../app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt#L1567-L1570)).
   You cannot begin a meal. You can only append to one after finishing a single-product calculation.
2. Home's meal bar is hidden the moment the search field is non-blank
   ([HomeScreen.kt:179](../../app/src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt#L179)) —
   i.e. it disappears precisely while you are finding the next thing to add to it.
3. There is no way to reach the meal from Home when the meal is empty, and no way to reach it from
   the scanner at all.

A real dinner is: bread, plus the pasta, plus the sauce, plus the yoghurt. The user is doing one
task ("what do I dose for this plate?") and the app models it as four tasks with an optional
accumulator bolted to each. The `Add & scan next` button
([MealComponents.kt:70-76](../../app/src/main/kotlin/app/justthecarbs/ui/meal/MealComponents.kt#L70-L76))
shows the team already understood the loop exists — it just isn't reachable from where the loop
actually starts.

**Scope tension, flagged as requested:** making the meal more prominent moves toward the
"tracker" silhouette §2 forbids. I believe it does not cross the line, and the reason is structural
rather than a matter of restraint: `MealStore` has no meal id and there is no history. A more
prominent *current* meal is still exactly one meal that cannot be saved, named, dated or retrieved.
The scope guarantee lives in the data model, not in how quiet the button is. **But this is the
owner's call, and it is the single judgement in this document I would most want challenged.**

---

## Ranked findings

Ranked by (impact on a T1D user) ÷ (effort + risk). `PRE` = safe before the Play release,
`POST` = better after, given the regulatory gate is still open and A2/A5/A6 are unresolved.

### 1. Hypo recovery is the app's fastest-needed path and it is the slowest — `POST`

**The finding.** When blood glucose is low, a T1D user needs a fast carb count *urgently*, often
shaking, one-handed, sometimes cognitively impaired by the hypo itself. The current fastest path to
a number is: open app → tap Scan barcode → grant/confirm camera → aim → wait for lookup → type
portion → read. That is the *slowest* the app ever is, at the moment the user can least afford it.

Meanwhile the app already stores the exact thing that would solve it: `favorite` on `Product`, and
`lastPortion` / `lastCount`. A user's hypo treatments are the most-repeated products they own — the
same juice, the same glucose tabs, the same four dextrose sweets, every time.

**Suggestion.** A favourite with a `lastPortion` already contains everything needed to show its
result without any further input. Render the computed figure directly on the `RecentCard` for
favourites — the calculation is already being done there
([HomeScreen.kt:681-697](../../app/src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt#L681-L697))
and then discarded into a summary string. Surfacing it costs no new calculation path and no new
storage.

**Scope tension:** this is where I would be most careful. "Your usual hypo treatment" is medical
framing and is forbidden. The mitigation is that the app must never *know* or *say* anything about
why a product is a favourite — it is just a favourite with a remembered portion, exactly as today.
No labelling, no category, no "quick treatment" section. The speed is a side effect of remembering,
not a feature about hypos.

**Why POST:** it touches Home's primary surface, and Home is what a Play reviewer sees first.

---

### 2. The meal cannot be started, only continued — `POST`

**The finding.** See the central finding above. Concretely: there is no entry point to the meal
except through a completed single-product calculation.

**Suggestion.** Two changes, both small:
- Stop hiding the meal bar during search
  ([HomeScreen.kt:179](../../app/src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt#L179)). The
  stated reason is that both want the space below the header, but searching is *how you add the next
  item*, so hiding the running total during the add is backwards. The `compact` variant already
  exists and is short enough to coexist.
- Let `Add & scan next` be reachable when arriving from the meal screen, so the loop closes without
  returning to Home each time.

**Not suggested:** a "start a meal" button. That would add a decision to a flow whose value is not
having one, and it edges toward the tracker silhouette. The meal should keep starting implicitly.

---

### 3. Nothing communicates how much to trust the number — `PRE`

**The finding.** `SourceBadge` distinguishes online / verified / manual / OCR, and the per-100 line
sits above it. But a T1D user's real question is narrower and more urgent than provenance: **"is
this number good enough to dose on?"** Open Food Facts is crowd-sourced, and a wrong per-100 figure
produces a wrong dose. The app knows the difference between `UNVERIFIED` online data and something
the user checked against the package, and it renders that difference as a small grey line
([ProductScreen.kt:684](../../app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt#L684))
that hides entirely while the keyboard is open.

The current hint — "Check package if needed" — is also the weakest possible phrasing. "If needed"
invites the user to decide, without giving them anything to decide *with*.

**Suggestion.** Keep the badge, but make unverified-online state visible at the moment of the
result, not only at the top of the screen. The user reads the bottom of the screen; that is where
the number is. This does not need alarm colour and must not have any — §36 and the tone rule are
explicit that a usually-right value must not train the user to ignore warnings. A single quiet mark
adjacent to the result is enough.

**Scope tension:** anything that reads as a safety warning drifts toward medical-device framing.
Wording must stay factual about *data provenance* ("not checked against the package") and never
about consequence ("may affect your dose"). The former is a fact about the database. The latter is
medical advice.

**Why PRE:** it is presentation-only, and it strengthens rather than weakens the regulatory position.

---

### 4. `Enter a portion` wastes the app's best moment — `PRE`

**The finding.** Before a portion is typed, the result panel shows "Enter a portion"
([ProductScreen.kt:1484](../../app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt#L1484))
in a 96 dp box. The app knows the per-100 figure at this point. It could show the carbs for a
serving, or for the whole pack, or simply nothing — but it instead spends its largest, most
prominent, best-positioned surface on an instruction the user does not need, since the field above
is already focused and labelled.

**Suggestion.** Show the per-100 figure in the result's own type while pending, greyed, clearly
marked as per 100 — it is the number the user is about to scale, and seeing it in the result
position teaches the relationship. Alternative: keep the space reserved but empty. Either beats an
instruction.

**Why PRE:** pure presentation, no new data, no calculation path.

---

### 5. Quick-adjust steps are wrong for the products people actually scan — `PRE`

**The finding.** `QuickAdjustRow` offers −10 / −5 / +5 / +10 grams
([ProductScreen.kt:734-765](../../app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt#L734-L765)).
For a 400 g loaf or a 500 g pasta pack, ±5 g is noise — it is roughly one-hundredth of the pack. For
a 20 g biscuit it is a quarter of the item. Fixed absolute steps are wrong at both ends of the range
the app serves.

**Suggestion.** Either scale the steps to the product's package size when one was confidently parsed
(the app already gates `PackShortcuts` on exactly that signal), or reduce to ±10 / ±50 and let the
pack fractions handle the large case. The current four buttons also consume a full row for a
capability the portion field already provides.

**Why PRE:** self-contained, no schema or navigation change.

---

### 6. The per-100 line is the screen's least legible important number — `PRE`

**The finding.** `product_per_100` renders as `48.2 g carbs / 100 g` in `titleLarge`
([ProductScreen.kt:674-682](../../app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt#L674-L682)).
This is the figure everything else derives from, and an experienced carb counter sanity-checks it
first — they know roughly what bread and pasta should be, and a wrong OFF entry is usually obvious
at a glance. It currently sits in ordinary text weight beneath a large photo.

**Suggestion.** Give it more typographic weight relative to the product name above it. It is more
useful than the name (which the photo already establishes) and currently reads as secondary to it.

---

### 7. `MEAL TOTAL` has no per-item provenance — `POST`

**The finding.** The meal screen shows each item's carbs, and a total. It does not show that item 2
came from unverified crowd-sourced data while items 1 and 3 were verified. The total inherits the
weakest input's reliability without saying so.

**Suggestion.** Carry the verification state onto the meal line. `MealItem` would need the field;
this is why it is `POST` — it is a schema change, and the migration discipline in this repo is
deliberately careful.

---

### 8. Copy is a single tap with no undo and no confirmation of *what* — `PRE`

**The finding.** The copy button shows a Toast reading "31.3 copied"
([ProductScreen.kt:1537-1541](../../app/src/main/kotlin/app/justthecarbs/ui/product/ProductScreen.kt#L1537-L1541)).
That is good. But a Toast is transient and easily missed one-handed, and the user is about to paste
this into a device that doses insulin. There is no way to confirm afterwards what is on the
clipboard without re-copying.

**Suggestion.** Low priority, but consider a persistent (not transient) state change on the button
itself — a checkmark for a few seconds — so the confirmation survives being glanced away from.

---

### 9. Onboarding body copy overpromises the portion step — `PRE`

**The finding.** `onboarding_body_2` says "Drag, type, or tap a preset."
([strings.xml:384](../../app/src/main/res/values/strings.xml#L384)) There is no drag interaction
anywhere in the portion UI. `PortionField` is a text field; `QuickAdjustRow` and `PackShortcuts` are
buttons. The word "Drag" describes a control that does not exist.

**Suggestion.** Correct the copy. **Needs confirmation on device** that no drag affordance exists
that I missed reading — but I found none in `ProductScreen.kt`.

**Why PRE:** it is a one-word string fix and it is currently inaccurate, which is worth fixing
before a store listing draws attention to onboarding.

---

### 10. `verify_label_basis_mismatch` has a sentence-case bug — `PRE`

**The finding.** "...but this product is measured per 100 %2$s. **t**his app does not convert
between them." ([strings.xml:178](../../app/src/main/res/values/strings.xml#L178)) — lowercase `t`
mid-sentence after a full stop.

**Suggestion.** Fix. It is one character, and it appears in a dialog specifically about the app
refusing to do something risky, which is the worst place to look sloppy.

---

### 11. Minor observations, batched — `PRE`

- **`product_result_pending` and the 96 dp box** are also what the direct-carb path shows before a
  count is entered, where "Enter a portion" is slightly wrong wording — the user enters a *count*.
- **`meal_bar_summary`** reads `Meal · 2 items · 38.0 g`. The unit is grams of carbs, but the string
  says only `g`. Everywhere else the app is careful to say "carbs". Minor, but the vocabulary rule
  in `PRODUCT.md` is explicit.
- **The decorative circle** at the top-right of Home, Product and Meal
  ([HomeScreen.kt:127-133](../../app/src/main/kotlin/app/justthecarbs/ui/home/HomeScreen.kt#L127-L133))
  is `primaryContainer` at 0.7 alpha on Home and Product, but `orangeSoft` at 0.9 on Meal
  ([MealScreen.kt:93-99](../../app/src/main/kotlin/app/justthecarbs/ui/meal/MealScreen.kt#L93-L99)).
  Possibly deliberate to distinguish the meal context; if not, it is an inconsistency.
- **`settings_results` offers four options** but two of them (`settings_results_whole`,
  `settings_results_decimal`) appear unused given `ResultStyle` has two entries. Worth checking for
  dead strings.

---

## Suggested sequencing

**Before release** (presentation only, no schema, no navigation):
10 → 9 → 4 → 6 → 5 → 3 → 8 → 11

**After release** (structural, needs design decisions):
1 → 2 → 7

Items 1 and 2 are the ones that would change how the app feels to use daily. Items 3 and 4 are the
highest-value cheap wins. Item 10 should take two minutes.

---

## What I deliberately did not suggest

Recorded so the boundary is visible rather than silently enforced:

- **Insulin ratio / dose calculation.** Forbidden by §2 and the entire regulatory position. Not
  arguable.
- **Any history, log, trend, or "carbs today".** Would require adding a meal id, which the
  architecture deliberately makes absent.
- **Glucose meter or CGM integration.** Immediately converts the app into a medical device accessory.
- **A "hypo mode".** Considered and rejected: it names a medical event, and item 1 achieves the
  same speed benefit without the app ever knowing why the user is in a hurry.
- **Nutrition beyond carbohydrate** (protein, fat, fibre subtraction). Fibre subtraction in
  particular is a genuine carb-counting practice, but it is a dietary calculation method and would
  put the app in the position of endorsing one.
