# UX and usefulness review, and a ranked plan (2026-09-23)

Review at `c31818a` (`calculator-refinement-2026-09-23`, 1.0.8 / versionCode 9 open, not merged).
Method: code read of every UI flow plus the product brief (`PRODUCT.md`, `docs/MASTER-PROMPT.md`
§2/§3/§6), the two earlier reviews (`2026-08-16-t1d-ux-review.md`,
`design-audit-2026-09-22/REPORT.md`), the post-beta backlog and the closed-beta tester report.
**Nothing was run on a device for this review, and no code changed.** Every "the app does not…"
claim below was checked by search, and the file is cited.

The visual layer has had four passes in two days (hierarchy, refinement, hero redesign, photo
timeouts). This review deliberately does **not** re-open it. It asks a different question:
*which jobs does a carb counter bring to this app that it still makes slow, error-prone or
impossible?*

---

## Status (2026-09-23, later): F1, F2 and F3 built, with two corrections to this plan

Built on branch `meal-search-patch-2026-09-23` (uncommitted when written). Checked against the
code first, two proposals below were wrong and were not built as written:

- **F1's rule is not "oldest item older than N h".** Built as *time since the most recent
  addition* ≥ 2 h (`MealStaleness`). With the oldest-item rule, answering "add to this meal"
  would re-ask on every later add, and a long dinner with a late dessert would be flagged. The
  question is asked only at add time, and there is no age line on the meal screen.
- **F2 is not a DAO `LIKE` per keystroke.** Built as an in-memory match over a Room projection
  flow (`ProductDao.observeSearchable`), using `SearchQueryMatcher` so folding matches the remote
  ranking. A per-keystroke query would have needed its own debounce/cancel/generation. Stored
  matches are a separate `SearchUiState.savedHits` field, so the remote pipeline's state is
  unchanged. Only full matches (every typed word) are listed.
- **F3's counted-line wording:** a direct-carb line stores no unit word, so an edited count reads
  `3 × 14.2 g carbs` rather than guessing a plural.

## 0. What I understood (correct me here first)

**Stated by the brief:** one number, fast, one-handed; SCAN → PORTION → CARBS and
RECENT → PORTION → CARBS; every feature must make one of those faster, safer, clearer or more
reliable (§2). No insulin, no diary, no history, no daily totals, English UI, never market for
diabetes, never invent a number.

**Assumed (not stated anywhere I could find):**

- The typical user counts carbs **several times a day, for years**. Repeat use matters more than
  the first run, which already has a carousel, a tutorial and a Home reminder.
- A large share of users are **carers** (parents of a child who counts), not the eater. Carers
  weigh plates, split dishes and deal with leftovers.
- Success means **fewer taps and fewer silent wrong totals** on the second-to-hundredth use, not
  new screens.

---

## 1. What is already strong (the baseline to protect)

- The safety core: provenance kept separate from verification, the calculation-session immutability
  rule, no invented numbers, the OCR refusal rules. All of it is structural, not a convention.
- The decimal answer first, copy puts only the number on the clipboard, and the result is announced
  by TalkBack.
- Repeat use is already fast: Quick Add on Home (1.0.8), Usual portions, pack shortcuts, launcher
  shortcuts (`res/xml/shortcuts.xml`: scan barcode, scan label), Add & scan next.
- Most of the closed-beta report's suggestions **already exist**: a walkthrough (carousel plus
  coach-mark tutorial), *Rate on Google Play* and *Send feedback* in Settings, the package-check
  hint, and working offline for cached products. Only its ASO point and "report wrong data" are
  still open.

The remaining gaps are about **workflow shape**, not correctness. They cluster around three places:
the **meal**, **your own foods**, and **the kitchen reality of weighing**.

---

## 2. Findings

### F1. A meal from yesterday silently adds to today's total — safety-adjacent, highest priority

The meal persists across restarts by design, so a half-built plate survives an app switch. But
nothing ever tells the user how old it is. `MealItem.addedAt` is stored
(`domain/MealItem.kt:44`, `data/local/MealItemEntity.kt:44`) and **read by no UI code** (verified:
no reference under `ui/`). If a user forgets *Clear meal* after dinner, the next morning's first
Quick Add appends to it. The Home meal bar then reads `Meal · 4 items · 96.3 g carbs` where the user
expects about 30 g. That number is transcribed into another calculator.

This does not break scope. It needs no history and no meal id, only the timestamps already stored.

**Proposal.** When the oldest item is older than a threshold (proposal: 4 h; owner to choose), the
next add from anywhere (calculator, Quick Add, *Add & scan next*) asks once: *"Your meal has items
from 7 hours ago. Start a new meal / Add to it"*. The meal screen shows a quiet age line
(`Started 7 h ago`). No automatic clearing, because deleting a plate without being asked is its own
failure.

Effort S–M. It is pure logic (`MealStaleness`, JVM-testable) plus one dialog, and **no schema
change**.

### F2. Your own foods are unreachable once they leave Recents

Products entered by hand with no barcode get the key `local:<uuid>`
(`ui/manual/ManualEntryViewModel.kt:208`). They can only be reached from Home's Recents, which is
capped at **25** (`HomeViewModel.kt:238`), or by being a favourite. Search never looks at the local
database. `SearchViewModel.localHitsFor` (`:430`) only narrows the remote hits it already has. So:

- a homemade bread entered in August disappears after 25 other products, and can only be found
  again by typing it all in again;
- offline, typing the name of a product that **is cached on the device** returns *offline* rather
  than the product;
- values the user **verified** against the package are shown only when that product is scanned
  again.

**Proposal.** Search the local products table first, by name and brand with the existing
`SearchQueryMatcher` folding, and show those hits in a *Saved* group above the online results. They
appear with no network and no request budget. Tapping one runs the ordinary lookup, so the §10
priority is unchanged. This strengthens RECENT → PORTION → CARBS instead of adding a surface. It is
not a food diary: it lists products, not eating events.

Effort M. It needs a DAO `LIKE` query or FTS table; FTS would mean a migration, so start with
`LIKE` over `name`/`brand` and measure.

### F3. A meal line cannot be corrected, only deleted

`MealStore.update` and `ProductRepository.updateMealItem` (`data/ProductRepository.kt:589`) exist
with **no caller**. A meal row offers only the remove × (`ui/meal/MealScreen.kt:301`). Typing 350
instead of 35 means deleting the line, going back to the product and re-entering it.

**Proposal.** Tap a meal row to open a small sheet with that line's portion field. The
recalculation goes through `CarbCalculator`/`DirectCarbCalculator` from the line's **own stored**
per-100 or per-unit figure, never from the current product, which keeps the "a meal line never
silently follows a later product change" rule. Undo applies, as it does for remove.

Effort S–M.

### F4. The kitchen arithmetic happens in the user's head

People weigh with the bowl, weigh leftovers, and cook with dry-weight nutrition data. The portion
field accepts one number (`domain/PortionParser.kt:18`).

- **Portion expressions** (proposed first; small; scope-safe). Let the field accept `+`, `−` and
  `×`: `412-298` (plate minus bowl), `2x35`, `250-40` (served minus leftovers). The parsed result
  appears under the field (`= 114 g`) before it is used. It is pure domain code that TDD covers
  cleanly, and the answer is still one `CarbCalculator` call.
- **Container weights** (maybe later). A remembered bowl or plate weight to subtract. It is
  superseded by expressions for most users; build it only if people ask.
- **Eaten fraction of the meal** (carers). *"Ate ¾ of it"* on the meal total: ½, ¾ and a typed
  fraction, shown as `¾ of 48.2 g = 36.2 g` so the user sees the multiplication. It uses no new data
  and the meal still has no id.
- **Batch / pot cooking** (owner decision; see §4). Sum the carbs of the ingredients, weigh the
  finished pot, then portion by weight. This is how carb counting of home cooking is actually done,
  and it avoids the dry-versus-cooked pasta error, which is off by about 2.5× and the most common
  one there is. It uses only the user's own measurements, so nothing is invented. It rubs against
  §2's "no recipes": a saved pot looks like a recipe. The safe version is a **one-shot** conversion:
  meal total ÷ weighed pot = a temporary per-100 g, fed into Quick calculation, with nothing saved.

### F5. A meal total does not say how trustworthy each input was (carried over from 2026-08-16 #7)

The calculator now says *Online value · not checked against the package*, but a meal line drops
that fact, so a total built from three verified foods and one unverified one looks identical to an
all-verified total. **Proposal:** show the same quiet marker on the line (`Online value`), with no
colour and no warning wording. Needs `MealItem.dataSource/verification` → **Room migration 6→7**
(additive columns, labelled for existing rows, never inferred).

Effort M, because of the migration.

### F6. Wrong data has no way back to the source

The beta report asked for "report inaccuracies". The app lets you fix a value **locally** (Verify)
but gives no route to fix it for everyone. **Proposal:** on an Open Food Facts product, add an
overflow item *Edit on Open Food Facts* that opens
`https://world.openfoodfacts.org/product/<barcode>` in the browser. It is an outbound link only, so
no account, no API write and no licensing change. On *Product not found*, a matching *Add it to Open
Food Facts* link does the same. Effort S.

### F7. How old a verified value is stays hidden

`docs/known-limitations.md` promises the verification date is recorded "so staleness can be
surfaced later". It never was. **Proposal:** in the identity row, show `Checked on the package ·
Mar 2025` in place of the plain badge, with no nagging and no expiry. It helps the user decide when
to re-scan the label after a suspected reformulation. The date is already stored
(`Product.verifiedAt`, `domain/Product.kt:102`) and no UI file reads it. Effort S, no schema.

### F8. Nothing on the store listing says what the app is for (beta report #1)

The listing has to stay inside §44 §7.1 (no diabetes, no health claims), which is why it is thin.
Room remains for plain functional keywords: *carb counter, carbohydrate calculator, barcode
scanner, nutrition label scanner, grams of carbs per portion, meal carbs, offline*. This is owner
copywriting, not code, and it is worth an hour before 1.0.8 ships.

### F9. The gate behind all of this: almost nothing since 1.0.7 has been seen on a phone

The four calculator passes, Quick Add, the hero redesign and the photo timeouts are emulator-only.
Every item above shares the problem, so this plan puts a **device QA session first**, reusing
`docs/manual-qa.md`, before new features pile onto an unobserved calculator.

---

## 3. The plan, in waves

Each item gets its own bounded design and approval before any code, per the repo's working
agreements. Waves are ordered by (user value × safety) ÷ (effort + regulatory risk).

**Wave 0: see it on hardware (owner, about 1 h)**

1. Run the 1.0.8 branch on a physical phone: calculator at rest, typing and after Done; Quick Add;
   Remove from Recent; product photos on a slow connection. File what is wrong before adding more.
2. Decide whether `calculator-refinement-2026-09-23` merges to `main` as is.

**Wave 1: small, scope-safe, high value (fits 1.0.8 or opens 1.0.9)**

| # | Item | Effort | Schema | Why now |
|---|---|---|---|---|
| F1 | Stale-meal guard | S–M | none | the only item that prevents a silently wrong total |
| F3 | Edit a meal line | S–M | none | dead code already exists for it; delete-and-redo is error-prone |
| F4a | Portion expressions (`412-298`) | S | none | removes mental arithmetic from the most common kitchen case |
| F6 | Edit on / Add to Open Food Facts link | S | none | answers the beta report; zero licensing change |
| F8 | Store listing keywords | owner | — | free discoverability |

**Wave 2: structural but still inside scope**

| # | Item | Effort | Schema |
|---|---|---|---|
| F2 | Search your saved products first (offline too) | M | none (LIKE) → maybe FTS later |
| F4c | Eaten fraction of the meal total | S–M | none |
| F7 | Show the verification date | S | none (already stored) |
| F5 | Provenance on meal lines | M | migration 6→7 |

**Wave 3: owner decisions first (see §4)**

- F4d one-shot batch/pot conversion
- a home-screen widget or Quick Settings tile for *Scan barcode* (launcher shortcuts already exist;
  a widget is the step beyond them)
- export/import of *your own* products when changing phone (`allowBackup="false"` means a new phone
  starts empty today)

---

## 4. Owner decisions needed

1. **Stale-meal threshold** (F1): 4 h, 6 h, or "different calendar day"? Only the prompt's timing
   changes.
2. **Batch/pot conversion** (F4d): is a one-shot "divide the meal total by the pot's weight" inside
   §2 or is it "recipes"? I believe it is inside, because nothing is saved or named, but it is your
   call.
3. **Export of your own products:** does ODbL share-alike bite if the export contains only
   user-authored and user-verified values and not OFF data? `CLAUDE.md` flags export as the feature
   most likely to break ODbL, so this needs your review before any design.
4. **Widget/tile:** P3.3 (launcher shortcuts) was skipped once and later built. Is a widget wanted
   or unwanted clutter?

---

## 5. Considered and rejected

- **Nutrients other than carbs, fibre subtraction, net carbs**: dietary method, §2.
- **"Carbs today", history, any trend**: needs a meal id, which the architecture forbids on purpose.
- **A named "hypo" favourite section or mode**: medical framing. Quick Add already gives the speed
  without the app knowing why.
- **AI food-photo recognition / plate estimation**: invents numbers.
- **Community forum** (beta report): off-product, and a moderation liability for health-adjacent
  talk.
- **Voice input for portions**: plausible for one-handed use, but low demand evidence and a new
  permission. Revisit only if users ask.
- **Re-opening the visual system**: four passes landed this week; wait for device evidence (Wave 0).
