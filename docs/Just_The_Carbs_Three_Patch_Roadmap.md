# Just The Carbs — Three-Patch Product Roadmap

**Roadmap baseline:** Production submission `1.0.7` / versionCode `8`  
**Next releases:** `1.0.8` / vc9 → `1.0.9` / vc10 → `1.0.10` / vc11  
**Repository:** `Morfildor/Just-the-Carbs`  
**Prepared:** 17 September 2026

---

## 1. Purpose of this roadmap

This roadmap defines the next three feature patches for **Just The Carbs** after the 1.0.7 Production submission.

The goal is not to make the app larger for the sake of having more features. The goal is to make its existing core job materially faster, more reliable, and more flexible:

> **Find food → choose amount → read carbs → done.**

Every feature in these three patches must strengthen that loop.

The roadmap deliberately avoids turning the app into:

- a diet tracker;
- a food diary;
- a calorie or macro tracker;
- a medical workflow;
- an insulin or bolus calculator;
- a CGM or pump companion;
- an account-based cloud service;
- a dashboard full of statistics;
- a general-purpose nutrition app.

The three patches form a progression:

1. **1.0.8 — Faster Every Day**  
   Remove friction from workflows that already exist.
2. **1.0.9 — Capture Anywhere**  
   Let users bring products and labels into the app from photos, screenshots, barcodes, and Android sharing.
3. **1.0.10 — Trust Your Data**  
   Give users better ownership of locally saved products, corrections, aliases, portions, refreshes, and offline behavior.

The sequence is intentional. Patch 1 changes the least risky parts of the product and should ship first. Patch 2 expands inputs while reusing existing recognition logic. Patch 3 adds stronger local ownership only after the daily workflows and capture paths are stable.

---

# 2. Global engineering rules

These rules apply to all three patches.

## 2.1 Release branch discipline

`release/1.0.7` is frozen.

Do not merge `main` back into it.  
Do not use it as the development branch for these patches.

The first actual code change after the 1.0.7 submission opens:

- **1.0.8**
- **versionCode 9**

Subsequent patches use:

- **1.0.9 / versionCode 10**
- **1.0.10 / versionCode 11**

Documentation-only changes do not need to open a new release.

---

## 2.2 Calculation safety boundary

The arithmetic and existing calculation semantics are not feature-development playgrounds.

A feature may change:

- how the user reaches a product;
- how the user enters a portion;
- how a previous portion is reused;
- how local product metadata is managed;
- how a photo or barcode enters the existing pipeline;
- how the current meal is edited.

A feature must not silently change:

- carbohydrate basis interpretation;
- rounding semantics;
- verified-vs-unverified meaning;
- source-vs-verification meaning;
- label candidate selection rules;
- background refresh immutability;
- conflict handling;
- the rule that a number must never be invented.

If a feature requires changing calculation semantics, it leaves this roadmap and requires a separate design review.

---

## 2.3 Scope boundary

Do not add:

- meal history;
- meal IDs;
- dates attached to completed meals;
- daily totals;
- trends;
- streaks;
- nutrition targets;
- insulin;
- glucose;
- medication;
- health recommendations;
- food intake analytics;
- account creation;
- cloud sync.

`MealStore` should remain a current-session/current-meal concept rather than becoming a diary.

`PortionUsageStore` should remain per-product convenience data rather than becoming an all-food behavioral history system.

---

## 2.4 UX principles

Every new feature should answer at least one of these questions:

1. Does this remove taps?
2. Does this reduce retyping?
3. Does this make a failed scan recover faster?
4. Does this let the user reuse information they already verified?
5. Does this help the app work when the network or camera is inconvenient?
6. Does this make the source of a carb value easier to understand?
7. Does this help users correct bad crowd-sourced data locally without introducing ambiguity?

If not, it probably does not belong in these three patches.

---

## 2.5 Visual principles

Preserve the current design system.

- Result remains visually dominant.
- Blue remains the primary interaction color.
- The result color remains reserved for the carbohydrate result rather than decorative UI.
- Do not create dashboard cards merely because a new feature exists.
- Do not add bottom navigation.
- Do not add a permanent toolbar full of secondary actions.
- Prefer progressive disclosure.
- Prefer existing component primitives.
- Preserve large-font behavior.
- Preserve dark-mode quality.
- Preserve one-handed usability.

---

## 2.6 Testing philosophy

Every patch requires four levels of verification:

### A. JVM tests
Domain behavior, state transitions, formatters, ranking, local persistence, undo state, parser contracts.

### B. Instrumented Compose tests
Actual user flows, text scaling, focus behavior, keyboard behavior, accessibility semantics, dark/light mode, navigation.

### C. Emulator/device manual QA
At minimum:

- API 36 reference emulator;
- one physical Android device;
- light;
- dark;
- default font;
- large font;
- online;
- offline or simulated network failure.

### D. Regression comparison
When OCR/search behavior is touched, replay the existing committed fixture corpus and compare against the previous released baseline.

No patch should trade a convenience feature for a regression in a previously hardened scan path.

---

# 3. PATCH ONE — 1.0.8 / vc9

# Faster Every Day

## 3.1 Patch objective

Make the workflows users already perform noticeably faster without introducing a new major data source or touching the high-risk OCR interpretation core.

This is the safest and most immediately useful patch.

The patch should feel like:

> “Everything I already do takes fewer taps.”

It should not feel like:

> “The app suddenly became a different product.”

---

## 3.2 Primary scope

### Feature 1 — Quick Add to Meal from Recent and Favorites

**Priority:** P0  
**Impact:** Very high  
**Risk:** Low–medium

#### Problem

A repeated product is already visible on Home, often with its remembered portion/result. If the user is building a meal, reopening the product and confirming the same portion creates unnecessary friction.

#### Desired behavior

When a meal is currently in progress, eligible Recent/Favorite rows may expose a compact **Add** action.

The action should:

1. use the product’s valid remembered portion;
2. calculate using the same normal domain calculation path;
3. add a normal immutable `MealItem`;
4. provide immediate success feedback;
5. update the running meal total;
6. avoid navigating away from Home.

If the remembered portion is missing, stale, invalid, or no longer compatible with the product:

- do not guess;
- open the product normally instead.

#### UX rules

- Do not turn every Recent card into a button farm.
- The row itself still opens the product.
- Favorite toggle remains available.
- Quick Add should be visually secondary.
- It should be shown only when the feature can complete safely.
- A user should understand what portion is about to be added.

Potential label examples:

- `Add 2 slices`
- `Add 35 g`
- or a small `Add` action adjacent to the already-visible remembered portion.

#### Acceptance criteria

- Quick Add produces exactly the same carb result as opening the product and using the same remembered portion manually.
- No Quick Add appears if the remembered state is incomplete.
- Repeated tapping cannot create accidental duplicate adds through a stale enabled state.
- Meal total updates immediately.
- Home remains on Home.
- TalkBack exposes the product and portion in the action description.

---

### Feature 2 — Edit Meal Item Portion In Place

**Priority:** P0  
**Impact:** Very high  
**Risk:** Low–medium

#### Problem

A meal item is often correct in product identity but wrong in amount. Deleting and recreating the item is needless friction.

#### Desired behavior

Tapping an item in the current meal opens a lightweight edit flow using the same portion-entry logic used by the normal product calculator.

The user may change:

- grams;
- serving count;
- countable-unit amount;
- other already-supported portion input modes.

The user should not need to:

- rescan;
- research;
- reselect the product;
- delete and recreate the item.

#### Data rule

The edited meal item remains a calculation snapshot. Editing the meal item must not silently rewrite the saved product’s global remembered portion unless the product’s existing architecture already treats this action as a normal use.

Define this explicitly during implementation.

Preferred behavior:

- meal edit changes the meal item;
- explicit product-use actions control remembered product usage.

#### Acceptance criteria

- Edit recalculates using the same calculation engine as the product screen.
- Cancel leaves the original item unchanged.
- Confirm atomically replaces the old item.
- Meal total updates exactly once.
- No transient double-counting.
- Back navigation safely discards unconfirmed edits.

---

### Feature 3 — Remove One Product from Recent + Undo

**Priority:** P0  
**Impact:** High  
**Risk:** Very low

#### Problem

The app supports clearing recent history globally, but not cleaning one item from Recent.

#### Desired behavior

A user may remove an individual product’s usage history.

For a non-favorite:

- it disappears from Recent.

For a favorite:

- it remains in Favorites;
- its usage/remembered-portion history is cleared;
- Favorite status remains intact.

#### What must be cleared

For the selected product:

- `lastUsedAt`;
- `lastPortion`;
- remembered input mode;
- remembered selected unit;
- remembered count;
- corresponding portion-usage aggregates used by `Usual`.

#### What must survive

- product identity;
- barcode;
- name;
- carb value;
- source;
- verification state;
- local corrections;
- favorite flag.

#### Undo

Use a transient undo mechanism.

Undo must restore the cleared usage state exactly enough that the UI returns to the pre-removal state.

If exact restoration would create unsafe persistence complexity, the implementation may use a short-lived in-memory snapshot while the snackbar/action is available.

#### Acceptance criteria

- Removing one item does not affect any other product.
- Removing a favorite does not unfavorite it.
- “Usual” portion state does not survive a confirmed removal.
- Undo restores expected Home state.
- Global Clear Recent continues to work unchanged.

---

### Feature 4 — Local Saved Products First in Search

**Priority:** P0  
**Impact:** High  
**Risk:** Low

#### Problem

A user may already have a locally saved or verified product, but search still primarily feels network-oriented.

#### Desired behavior

Search should consult local products immediately and merge useful local matches into the result experience before remote results arrive.

Preferred hierarchy:

1. strong local Favorite match;
2. strong locally saved/recent match;
3. remote ranked results;
4. weaker local matches only when useful.

Do not simply force every local product above every better remote result.

#### Requirements

- Local matching returns instantly.
- No network is required for local results.
- Duplicate local/remote products with the same barcode are merged.
- Local user-owned/corrected values remain clearly identified.
- Existing remote ranking behavior remains intact for the remote portion.

#### Acceptance criteria

- Saved product can be found while offline.
- Same product is not rendered twice.
- Favorite exact match appears rapidly.
- Search cancellation/generation behavior remains correct.
- Existing benchmark suite does not regress materially.

---

### Feature 5 — Smarter Portion Controls

**Priority:** P1  
**Impact:** High  
**Risk:** Low

#### Goal

Reduce repeated keyboard editing for common amount adjustments.

#### Candidate controls

For weight:

- `½`
- `×2`
- `−10 g`
- `+10 g`

For countable portions:

- `−1`
- `+1`
- `½`
- `×2`

Exact controls should adapt to input mode rather than showing irrelevant operations.

#### Rules

- Never allow negative values.
- Zero behavior must be explicitly defined.
- Decimal results must use the existing locale-aware formatting.
- Controls should not crowd the primary input.
- Large font must not cause clipping.

#### Acceptance criteria

- Buttons are fast enough to replace manual editing.
- Keyboard entry remains fully supported.
- All operations use exact decimal arithmetic already established in the domain layer.
- No float conversion is introduced.

---

### Feature 6 — Search Launcher Shortcut

**Priority:** P1  
**Impact:** Medium  
**Risk:** Very low

Search is now a first-class Home entry point. Launcher shortcuts should reflect that.

Add:

- Scan barcode
- Scan nutrition label
- Search products

Search shortcut behavior:

1. launch app;
2. respect first-run onboarding;
3. land on Home;
4. focus Search;
5. show keyboard.

Do not create a separate search architecture just for the shortcut.

---

### Feature 7 — Manual / Paste Barcode

**Priority:** P1  
**Impact:** Medium  
**Risk:** Very low

Provide a fallback when:

- camera cannot focus;
- barcode is damaged;
- barcode is available as text;
- user copied it from a webpage or message.

Entry should accept:

- normal numeric barcode input;
- paste from clipboard through standard text-field behavior.

Validate supported barcode shapes before lookup.

Do not silently modify the number.

---

### Feature 8 — Small Companion Wins

Include only if the patch remains stable:

- Copy carbs directly from Recent/Favorites.
- Copy individual meal-item carbs.
- Clear meal + Undo.
- Persist torch state while moving between consecutive scans in the same meal workflow.

These are secondary to the P0/P1 features above.

---

## 3.3 Explicit non-goals for 1.0.8

Do not include:

- gallery OCR;
- barcode-from-photo;
- custom product creation;
- product correction architecture;
- share sheet;
- OCR parser expansion;
- new languages;
- new database concepts unrelated to the above;
- major search-provider rewrite;
- visual redesign.

---

## 3.4 Technical workstreams

### Home
- Extend Recent/Favorite presentation without overcrowding cards.
- Add per-product remove flow.
- Add Quick Add eligibility/state.

### Meal
- Add edit-item navigation/state.
- Add atomic replacement operation if needed.
- Add undo for clear if included.

### Search
- Add local matching source.
- Define merge/deduplication.
- Preserve current remote ranking pipeline.
- Test offline behavior.

### Product/portion
- Extract reusable portion adjustment operations.
- Ensure all controls operate on the domain representation, not formatted strings.

### Startup
- Add Search launcher action.
- Reuse existing `StartupDestination` first-run logic.

---

## 3.5 Patch 1 regression gates

Required before release:

- Full JVM suite green.
- No new lint errors.
- Instrumented release gate green.
- Search benchmark comparison recorded.
- Existing barcode flow unchanged.
- Existing label OCR fixture corpus unchanged.
- Meal total arithmetic equivalence tests.
- Light/dark QA.
- 1.0x and large-font QA.
- cold start;
- Home;
- Search;
- barcode;
- label scan;
- manual entry;
- Recents;
- Favorites;
- meal;
- offline local search;
- Clear Recent;
- delete local products.

---

## 3.6 Patch 1 success criteria

The patch is successful if:

- repeat-product meal building requires materially fewer taps;
- a mistaken meal portion can be fixed without recreation;
- local saved products appear instantly;
- Recent can be cleaned product-by-product;
- no core scan/calculation reliability metric worsens.

---

# 4. PATCH TWO — 1.0.9 / vc10

# Capture Anywhere

## 4.1 Patch objective

Remove the assumption that the physical package must be directly in front of the live camera.

Users should be able to use:

- live camera;
- an existing barcode image;
- a nutrition-label photo;
- a screenshot;
- an image shared from another Android app.

The key architectural rule:

> **New input source, same interpretation pipeline.**

This patch must not create a second, weaker OCR engine.

---

## 4.2 Primary scope

### Feature 1 — Scan Nutrition Label from Photo

**Priority:** P0  
**Impact:** Very high  
**Risk:** Medium

#### Entry points

Possible entry:

`Scan nutrition label`

then:

- `Camera`
- `Choose photo`

or a secondary gallery action on the scanner screen.

Avoid a modal chooser on every scan if it slows the primary live-camera workflow.

#### Pipeline

The chosen image should enter the existing label recognition pipeline as close as possible to the same stage as a captured camera frame.

Reuse:

- image normalization;
- OCR;
- geometry parsing;
- candidate extraction;
- basis detection;
- conflict checks;
- verification eligibility;
- correction/review UI.

Do not implement a simplified “gallery parser.”

#### Image handling

Use the modern Android Photo Picker where supported.

Requirements:

- no broad media-library permission if Photo Picker avoids it;
- temporary URI access only as needed;
- correct EXIF/orientation handling;
- image downsampling bounded for memory safety;
- no permanent local image retention unless already required by an explicit diagnostics action;
- do not upload the image to a new service.

#### Acceptance criteria

- A supported photo reaches the same review flow as live capture.
- Same image produces deterministic output across repeated runs.
- Rotation/orientation is correct.
- Large images do not OOM.
- Cancel returns safely.
- Unsupported/ambiguous images offer recovery.
- No photo is silently retained.

---

### Feature 2 — Barcode from Photo

**Priority:** P0  
**Impact:** High  
**Risk:** Low–medium

The user may select an existing image containing a product barcode.

Use the existing ML Kit barcode stack where practical.

#### Multiple barcodes

If exactly one valid food barcode is found:

- proceed normally.

If multiple valid barcodes are found:

- do not guess;
- display a compact selection UI or request a crop.

If none:

- offer retry / manual barcode / nutrition-label scan / manual entry.

#### Acceptance criteria

- selected barcode enters the same lookup flow as a live scan;
- no duplicate product path exists;
- manual barcode remains available as fallback.

---

### Feature 3 — Share Image to Just The Carbs

**Priority:** P0/P1  
**Impact:** High  
**Risk:** Medium

Allow Android `ACTION_SEND` for supported image MIME types.

Example:

1. user receives a label photo in WhatsApp;
2. taps Share;
3. chooses Just The Carbs;
4. app opens an import interpretation screen;
5. user selects or app detects:
   - Barcode
   - Nutrition label

Prefer detection with a safe confirmation rather than requiring the user to know which pipeline to choose before the image is inspected.

#### Startup/onboarding rule

If the user has never completed onboarding:

- preserve the shared input temporarily;
- complete onboarding;
- resume the intended import.

Do not lose the shared intent.

---

### Feature 4 — OCR Correction UX Upgrade

**Priority:** P0  
**Impact:** Very high  
**Risk:** Low–medium

#### Problem

Even a strong OCR pipeline occasionally misreads a digit. The fastest recovery should be correcting that digit, not abandoning the scan.

#### Desired behavior

Review UI should make uncertain or conflicting extracted fields easy to correct.

Examples:

- tap detected carb value;
- edit `12.8`;
- confirm;
- retain the recognized basis/serving context when still valid.

Do not expose internal terms such as confidence score, OCR candidate, basis enum, or parser stage.

#### Optional enhancement

Visually emphasize only the field that requires attention rather than painting the whole result as unsafe.

#### Acceptance criteria

- one-digit correction requires minimal taps;
- correcting the value never bypasses required basis validation;
- user-edited value is distinguishable internally as user-confirmed;
- correction survives navigation to calculation;
- semantics are accessible.

---

### Feature 5 — Crop / Reframe Before OCR

**Priority:** P1  
**Impact:** High for difficult labels  
**Risk:** Medium

This should support gallery images first.

Users may crop a large photo around the nutrition table before recognition.

Keep the crop tool simple:

- rectangular crop;
- rotate 90°;
- reset;
- continue.

Do not build a full image editor.

If Android/platform facilities or a small reliable library can handle this cleanly, use them rather than creating custom gesture-heavy code.

---

### Feature 6 — Continuous Meal Capture

**Priority:** P1  
**Impact:** High  
**Risk:** Medium

A meal workflow can be accelerated:

> Scan → portion → Add to meal → Scan next

The app already has pieces of this workflow. Patch 2 should ensure the capture loop feels continuous across all supported input paths.

After `Add & scan next`:

- return directly to the previous scan mode where sensible;
- preserve session torch state;
- preserve meal bar/meal state;
- avoid Home round-trips.

For gallery import, “scan next” may return to the import choice rather than reopening Photo Picker automatically.

---

### Feature 7 — Scanner usability polish

Include if verified useful:

- zoom control;
- tap-to-focus;
- persistent torch within the active capture session;
- clearer scanner success feedback;
- separate haptic patterns for accepted vs needs-review states.

These must not visually clutter the camera.

---

## 4.3 Explicit non-goals for 1.0.9

Do not add:

- automatic background processing of the entire photo gallery;
- image storage library inside the app;
- cloud photo upload;
- generative AI nutrition extraction;
- camera roll indexing;
- meal history;
- universal object recognition;
- automatic food recognition from meal photos;
- calorie estimation from images.

This patch recognizes **printed product information**, not food itself.

---

## 4.4 Architecture requirement — one recognition engine

Before implementation, draw the current recognition path.

Target architecture conceptually:

```text
Live camera frame --------\
                           \
Photo Picker image ----------> Normalized input image
                                -> ML Kit text/barcode recognition
Android shared image --------/
                                -> existing parsing / decision engine
                                -> existing verification/review
                                -> existing product/calculator flow
```

Do not allow this:

```text
Camera -> hardened parser A
Gallery -> new parser B
Share -> shortcut parser C
```

That would triple the trust surface.

---

## 4.5 Patch 2 test matrix

Test real or committed images containing:

- clear nutrition table;
- rotated table;
- photographed at angle;
- dim light;
- glare;
- comma decimal;
- dot decimal;
- multiple numeric columns;
- per-100-g and per-serving columns;
- percentages next to gram values;
- long multilingual nutrient names;
- child nutrient rows;
- multiple barcodes;
- QR code plus EAN;
- screenshot rather than camera photo;
- very high-resolution image;
- low-resolution messaging-app image.

Languages should cover the currently supported Latin-script recognition set and Turkish.

Do not broaden language support merely because the source is now a photo.

---

## 4.6 Patch 2 regression gates

Before release:

- current live-camera OCR corpus remains equal or improves;
- current barcode scanner remains unchanged in success rate;
- Photo Picker does not require unnecessary broad storage permission;
- shared URI permission is handled safely;
- process recreation does not lose an active import where reasonably recoverable;
- no new crash on large images;
- no image appears in app-private persistent storage unless explicitly expected;
- two failed exploratory harness tests, if still intentionally excluded, remain documented rather than accidentally counted as release regressions.

---

## 4.7 Patch 2 success criteria

The patch succeeds if a user can receive a photo of a nutrition label, share it into Just The Carbs, correct one OCR digit if necessary, choose the portion, and obtain the normal result without needing the physical package.

That is the flagship Patch 2 workflow.

---

# 5. PATCH THREE — 1.0.10 / vc11

# Trust Your Data

## 5.1 Patch objective

Give the user more control over products they repeatedly use, especially when crowd-sourced product data is incomplete, ugly, outdated, or wrong.

Patch 3 should answer:

> “When I know this product better than the database does, can the app remember my verified local information without becoming confusing?”

This patch is about **local ownership and transparency**.

---

## 5.2 Primary scope

### Feature 1 — Local Product Alias

**Priority:** P0  
**Impact:** High  
**Risk:** Low

Allow a user to give a saved product a local display name.

Examples:

- `Breakfast bread`
- `Melike's cereal`
- `AH protein wrap`

#### Rules

Store separately from the source product name.

Do not overwrite the original imported name.

UI may show:

**Breakfast bread**  
*Original product name*

or expose source name in details.

Search should match both alias and original product name.

Clearing local products should remove aliases.

Refreshing remote data must not erase the alias.

---

### Feature 2 — Custom Reusable Product

**Priority:** P0  
**Impact:** Very high  
**Risk:** Medium

Allow a user to create a local product when search/barcode/OCR cannot provide useful data.

Minimum fields:

- product name;
- carbs basis already supported by the app;
- carbohydrate value;
- optional barcode;
- optional serving/portion information.

#### Important constraint

Do not create new calculation semantics.

A custom product must map onto the same existing domain structures used by normal products.

#### Optional barcode assignment

If a barcode is entered or scanned:

- future scans may resolve to the local custom product first;
- UI must make the local nature clear;
- user can later unlink it.

#### Acceptance criteria

- custom product behaves like a normal reusable saved product;
- it can be favorited;
- it can appear in local search;
- it can be used offline;
- it can be deleted;
- its provenance is not falsely presented as Open Food Facts.

---

### Feature 3 — Local Carb Correction / Override

**Priority:** P0  
**Impact:** Very high  
**Risk:** Medium

#### Problem

Crowd-sourced product data may be wrong, but a user may have the physical label in hand.

#### Desired behavior

Allow an explicit local correction while preserving:

1. original remote value;
2. local corrected value;
3. provenance;
4. verification status;
5. ability to revert.

Do not simply overwrite the only copy of the original value.

#### UI concept

A corrected product might expose:

- current value being used;
- `Saved by you` / similarly neutral local indicator;
- source details elsewhere;
- action: `Restore online value`.

Avoid alarmist warnings.

#### Refresh behavior

If remote value changes later:

- background refresh must not silently replace the active local correction;
- the app may inform the user that a newer online value exists;
- user chooses whether to switch.

This follows the existing principle that background refresh never mutates a live calculation.

---

### Feature 4 — Restore Online Value

**Priority:** P0  
**Impact:** Medium  
**Risk:** Low

Every local override needs an escape hatch.

`Restore online value` should:

1. remove the local correction;
2. retain normal product identity and favorite state;
3. use the freshest available remote value;
4. refresh if needed and network is available;
5. handle offline state cleanly.

Never leave the user trapped in a local edit.

---

### Feature 5 — Manual Product Refresh

**Priority:** P1  
**Impact:** Medium–high  
**Risk:** Low

Provide a clear `Check for updated product data` action in product details.

Requirements:

- no change to active calculator without confirmation;
- communicate no-change vs new-data vs unavailable;
- preserve local alias;
- preserve local correction unless user explicitly replaces it;
- update cached product source metadata.

---

### Feature 6 — Custom Preferred Portions

**Priority:** P0/P1  
**Impact:** High  
**Risk:** Medium

The current learned `Usual` mechanism is behavior-derived. Patch 3 should optionally allow an explicit user-defined shortcut.

Examples:

- `My bowl — 42 g`
- `1 scoop — 28 g`
- `2 slices`
- `Half pack — 75 g`

#### Distinction

**Usual** = inferred from repeated use.  
**Saved portion** = intentionally created by the user.

Do not merge the two concepts invisibly.

#### Requirements

- create;
- rename;
- edit;
- delete;
- tap to apply;
- maximum sensible number per product to keep the UI compact.

Do not let saved portions become recipes or meal templates.

---

### Feature 7 — Saved Product Offline Search

Patch 1 introduces local-first search. Patch 3 completes the experience by ensuring all local-owned data participates:

- aliases;
- custom products;
- local corrections;
- favorites;
- normal saved products.

The app should remain genuinely useful offline for products the user has already established locally.

---

### Feature 8 — Clear Local Data Semantics Review

Because Patch 3 introduces more user-owned data, Settings actions must be explicit.

Review:

#### Clear Recent History
Should remove usage facts only.

It should not remove:

- favorites;
- aliases;
- custom product definitions;
- corrections;
- explicit saved portion definitions unless they are semantically “history.”

#### Clear Locally Saved Products
Likely removes:

- saved product rows;
- aliases;
- custom products;
- local corrections;
- product-linked explicit portions;
- usage aggregates.

Current meal should remain independent unless wording explicitly says otherwise.

Tests must prove these promises.

---

## 5.3 Optional Patch 3 features

Only include after P0 behavior is solid.

### Product source details

Compact details:

- saved/local;
- online source;
- last fetched;
- corrected locally;
- verification status.

Keep this behind progressive disclosure.

### Open Food Facts contribution shortcut

If a remote product is wrong/missing, offer a link to improve it upstream.

This must remain optional and external.

### Export local products

A simple local JSON export may become useful after custom products/corrections exist.

However, export/import should be deferred if it threatens Patch 3 quality.

Do not build accounts or cloud sync to solve backup.

---

## 5.4 Data model expectations

Patch 3 is the most likely patch to require a Room migration.

Requirements:

- forward migration covered by automated tests;
- existing production databases migrate without destructive fallback;
- all 1.0.7/1.0.8/1.0.9 user data preserved;
- corrected/local values remain distinguishable from fetched values;
- alias is distinct from source name;
- custom product source is explicit;
- saved portion ownership is explicit.

Before implementation, produce an entity/schema proposal.

Do not start by adding random nullable columns until the state model is clear.

---

## 5.5 Patch 3 trust-state model

At minimum, the product layer must be able to distinguish cases conceptually similar to:

```text
Remote product
Remote product + local alias
Remote product + local correction
Remote product + local alias + local correction
Custom local product
Custom local product + barcode
```

The UI should never require the user to understand those internal states, but the data layer must.

---

## 5.6 Patch 3 acceptance criteria

A user can:

1. search or scan a product;
2. discover bad or inconvenient data;
3. correct the value locally;
4. rename it locally;
5. create a saved portion;
6. favorite it;
7. find it instantly later while offline;
8. see that the value is locally owned/corrected;
9. restore the online value if desired.

No step should imply that the locally edited value came from the online database.

---

# 6. Cross-patch dependency map

```text
1.0.8
│
├── Local-first search
│   └── becomes the discovery layer for aliases/custom products in 1.0.10
│
├── Faster meal editing
│   └── remains unchanged while capture methods expand in 1.0.9
│
├── Quick Add
│   └── later benefits from explicit saved portions in 1.0.10
│
└── Manual barcode
    └── becomes fallback for photo/share barcode paths in 1.0.9


1.0.9
│
├── Unified image-input pipeline
│   └── can later create/correct products in 1.0.10
│
├── OCR correction UX
│   └── provides natural input to a local correction flow in 1.0.10
│
└── Share-to-app
    └── remains only an input mechanism; no history is created


1.0.10
│
├── Aliases
├── Custom products
├── Local corrections
├── Explicit saved portions
└── Offline ownership
```

The patches should therefore be implemented in order.

---

# 7. Master priority matrix

| Feature | Patch | Impact | Complexity | Regression risk | Priority |
|---|---:|---:|---:|---:|---|
| Quick Add from Recent/Favorites | 1.0.8 | Very high | Low–medium | Low | P0 |
| Edit meal portion in place | 1.0.8 | Very high | Medium | Low | P0 |
| Remove one Recent + Undo | 1.0.8 | High | Low | Very low | P0 |
| Local-first saved-product search | 1.0.8 | High | Medium | Low | P0 |
| Smarter portion controls | 1.0.8 | High | Low | Very low | P1 |
| Search launcher shortcut | 1.0.8 | Medium | Very low | Very low | P1 |
| Manual/paste barcode | 1.0.8 | Medium | Low | Very low | P1 |
| Label from photo | 1.0.9 | Very high | Medium | Medium | P0 |
| Barcode from photo | 1.0.9 | High | Low–medium | Low | P0 |
| Share image to app | 1.0.9 | High | Medium | Medium | P0/P1 |
| OCR correction UX | 1.0.9 | Very high | Medium | Low–medium | P0 |
| Crop/rotate import | 1.0.9 | High | Medium | Low | P1 |
| Continuous meal capture | 1.0.9 | High | Medium | Medium | P1 |
| Local alias | 1.0.10 | High | Low | Very low | P0 |
| Custom reusable product | 1.0.10 | Very high | Medium | Medium | P0 |
| Local carb correction | 1.0.10 | Very high | Medium | Medium | P0 |
| Restore online value | 1.0.10 | Medium | Low | Low | P0 |
| Explicit saved portions | 1.0.10 | High | Medium | Low–medium | P0/P1 |
| Manual refresh | 1.0.10 | Medium | Low | Low | P1 |

---

# 8. Suggested release notes

These are directionally useful, not final Play listing copy.

## 1.0.8 — Faster Every Day

- Add familiar products to your current meal more quickly.
- Edit meal portions without starting over.
- Remove individual recent items with Undo.
- Find locally saved products faster, including offline.
- Faster portion adjustments.
- Search directly from the app shortcut menu.
- Enter or paste a barcode when scanning is inconvenient.

---

## 1.0.9 — Capture Anywhere

- Read nutrition labels from existing photos.
- Scan barcodes from photos and screenshots.
- Share product images directly to Just The Carbs.
- Faster correction when a label digit is misread.
- Improved crop, rotation, focus, and multi-item scanning workflows.

---

## 1.0.10 — Trust Your Data

- Rename saved products locally.
- Create reusable products when online data is missing.
- Save your own corrected carb value when a package label differs.
- Restore the online value at any time.
- Create named portion shortcuts for products you use often.
- Improved offline access to locally saved products.

---

# 9. Release gates by patch

## 1.0.8 may ship when

- all P0 features are complete;
- no calculation semantics changed;
- no OCR corpus regression;
- search benchmark is stable;
- meal edit/Quick Add equivalence is proven;
- local search works offline;
- manual QA passes on physical device.

## 1.0.9 may ship when

- camera and gallery use the same downstream interpretation logic;
- photo imports do not require unnecessary storage permission;
- large images are memory-safe;
- shared images survive intended navigation;
- live OCR regression corpus is unchanged or improved;
- correction flow cannot bypass basis/conflict safeguards.

## 1.0.10 may ship when

- Room migrations are proven from actual production schema;
- source name and alias cannot overwrite one another accidentally;
- local correction and remote value remain separately represented;
- refresh cannot silently destroy a local correction;
- Settings clear actions have precise tested semantics;
- custom products remain distinguishable from remote data.

---

# 10. What should be deliberately postponed beyond these three patches

The following may be interesting later but should not distract from this roadmap:

- Greek/Bulgarian OCR expansion;
- large-scale UI localization;
- import/export backup;
- home-screen widgets;
- Quick Settings tile;
- advanced manual Favorite ordering;
- product folders;
- complex search history;
- generalized image recognition;
- account sync;
- cloud backup.

And the following should remain outside product scope unless the product itself is intentionally redefined:

- completed meal history;
- daily intake tracking;
- nutrition goals;
- calorie/macronutrient dashboards;
- CGM integration;
- pump integration;
- insulin calculations;
- medical recommendations.

---

# 11. Recommended execution strategy

Do not implement all three patches in one branch.

For each patch:

1. cut the release scope;
2. create a short technical design for anything touching persistence/navigation;
3. implement one vertical feature at a time;
4. add tests with each feature rather than at the end;
5. run the full gate after each major vertical slice;
6. perform device QA;
7. freeze the release branch;
8. build the signed bundle from the frozen commit;
9. record bundle hash/version/source commit;
10. submit;
11. return `main` to the next patch only after the release source is safely frozen.

Avoid opportunistic refactors unless they are directly required by a patch feature.

A useful feature patch is better than a patch that combines ten unrelated cleanup projects.

---

# 12. Final roadmap summary

## Patch 1 — 1.0.8: Faster Every Day

**Theme:** remove friction.

Ship:

- Quick Add from Recent/Favorites;
- edit meal item portion;
- remove individual Recent + Undo;
- local-first/offline saved-product search;
- smarter portion controls;
- Search launcher shortcut;
- manual/paste barcode;
- a few safe copy/undo/session conveniences if stable.

**Risk profile:** Low.  
**User-visible impact:** High.  
**This should be the next release.**

---

## Patch 2 — 1.0.9: Capture Anywhere

**Theme:** remove the physical-camera constraint.

Ship:

- nutrition label from photo;
- barcode from photo;
- Android image sharing;
- OCR correction improvements;
- crop/rotate;
- continuous meal capture improvements;
- scanner usability refinements.

**Risk profile:** Medium.  
**User-visible impact:** Very high.  
**Primary engineering principle:** new input source, same hardened interpretation pipeline.

---

## Patch 3 — 1.0.10: Trust Your Data

**Theme:** make repeated products truly belong to the user locally.

Ship:

- local aliases;
- custom reusable products;
- explicit local carb corrections;
- restore online value;
- manual refresh;
- explicit saved portions;
- full offline discovery of local-owned products;
- precise local-data clearing behavior.

**Risk profile:** Medium.  
**User-visible impact:** Very high for long-term users.  
**Primary engineering principle:** preserve provenance, never hide whether a value is remote or user-owned.

---

# 13. Product outcome after all three patches

After these releases, Just The Carbs should support four extremely strong workflows without becoming bloated.

### Fast repeat use

> Open app → favorite/recent → Add → done.

### Physical package

> Scan barcode/label → portion → carbs → done.

### Remote/photo package

> Share/select photo → recognize → correct if needed → portion → carbs → done.

### Known personal product

> Search local alias/custom product → saved portion → carbs → done.

That is a substantial product expansion without compromising the original discipline of Just The Carbs.

The app remains focused on one job.

It simply becomes much harder for real-world friction to prevent the user from completing that job.
