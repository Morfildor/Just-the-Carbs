# Patch notes — Just the Carbs

Change list per version. **Newest first.** This file carries the version being worked on and the
most recent released one; every uploaded version is copied into
[`docs/version-history.md`](docs/version-history.md), the append-only archive that records each
artifact's hash, size and signer.

**Latest release: `1.0.2` / `versionCode 3`**, on closed testing since 2026-08-29.

## Versioning rule — a new version number per code change (owner, 2026-08-28)

**Every code change from now on gets its own version number.** This replaces the earlier
accumulate-into-one-open-version policy that 1.0.0 and 1.0.1 were produced under.

- The first code change after a release **opens a new version**: bump `brandVersionCode` and
  `brandVersionName` in `branding.gradle.kts`, and rename the **Unreleased** heading to that
  version.
- `versionCode` increments by one each time and is **never reused** — Play refuses a duplicate,
  including for a build that was rejected or never uploaded.
- Documentation-only changes do not open a version. A version number exists to identify an
  artifact, and prose that changes no code produces none.
- Test figures and the Play *What's new* text inside an unreleased section describe the work so far
  and must be re-checked before the build is made.
- A version moves to [`docs/version-history.md`](docs/version-history.md) only once **Play accepts
  the upload**. A build that never left the machine is not a release.

### How 1.0.0 and 1.0.1 were produced

Both accumulated several changes under one version number before being uploaded, which was the
policy at the time. They are left exactly as recorded; the rule above applies to work after
1.0.1.

## Conventions

- **Unreleased** holds the heading waiting for the next code change. Under the versioning rule
  above it is renamed to a real version as soon as that change lands, so it is normally empty
  between releases rather than a place work accumulates.
- A version heading is `## <versionName> (versionCode N) — <date> — <track>`, so a tester report
  quoting "1.0.1" matches exactly one artifact.
- Entries group by **Fixed / Changed / Added / Internal**, written for whoever reads them next: a
  defect line says what the user would have seen, not which function moved.
- `versionCode` is unique per upload and **never reused** — Play rejects a duplicate. It is bumped
  when a version section is opened, then left alone until that build ships.
- **`versionCode 1` and `2` are spent.** Both are on the closed track and neither is to be rebuilt
  or re-uploaded; the next number is `3`.
- When a version is uploaded, copy its section verbatim into `docs/version-history.md`. Nothing is
  rewritten on the way across, so the record of what shipped stays what it said at the time.
- Every version also carries a **Play Store release notes** block — the *What's new* text, written
  for users and far less granular than the list above it: ≤500 characters, small fixes grouped into
  one line, no health claim and no mention of diabetes (§44 §7.1 binds this field). Rules are at the
  top of [`docs/version-history.md`](docs/version-history.md).

---

## Unreleased

Nothing yet. `1.0.2` / `versionCode 3` shipped on 2026-08-29, so no version is open — the next code
change opens `1.0.3` / `versionCode 4` and bumps `branding.gradle.kts` in the same change.

## 1.0.2 (versionCode 3) — 2026-08-29 — Closed testing

Opened 2026-08-28 by the live-search change below, under the one-version-per-code-change rule, and
extended the same day by the Search-a-licious migration, the search-hardening and accuracy passes,
and on 2026-08-29 by the light/dark theme and system-UI fixes.

**Uploaded and accepted by Play on 2026-08-29**, built from `29a4f3d`. The artifact's hash, size and
signer are in [`docs/version-history.md`](docs/version-history.md), which is the authority for what
a tester is reporting against.

**Device verification: live search is confirmed on hardware (owner, 2026-08-29); the theme and
system-bar fixes are not.** That is the gap worth closing next — those defects were reported from a
device, so the fixes address symptoms no emulator run reproduced. See the archive entry for the
full split.

### Fixed — reproduced by a test that failed before the fix

- **Live search asked Open Food Facts far more often than it is allowed to, and the refusals looked
  like an outage.** Search-as-you-type sent a request after every half-second pause in typing, with
  nothing capping how many that added up to. Open Food Facts allows about 10 searches per minute per
  address — shared by everyone on the same connection — so a minute of ordinary typing could exceed
  it several times over, and the server started refusing. Those refusals were shown as *"The product
  database is unavailable"*, which is what produced the results → loading → error → loading → results
  flicker on a real phone: the app had earned the failure and then blamed the database for it.
  Typing is now answered instantly from results already on screen, while remote searches are paced
  by one shared budget across the whole app. **Later in this version** the primary provider changed
  (see *Product search now uses a dedicated search service* below), and that budget of at most 9 per
  minute now applies to the Open Food Facts fallback, which is the endpoint that imposes it. Typing
  and Search still coalesce to one request per settled query, and repeatedly pressing Search or Try
  again still adds none at all.
- **A momentary search failure wiped the results you were reading.** While live search refreshed a
  list, a single failed request replaced that whole list with the full error screen — losing results
  that were still perfectly good and still tappable, for a request nobody had asked for. Open Food
  Facts' search endpoint answers 503 while otherwise healthy often enough that this happened
  mid-word. A refresh that fails now leaves the results in place and says so in a thin line above
  them, with *Try again*; the full error screen is still shown when a search fails with nothing on
  screen to keep, which is the case where those recovery options are the whole point.

### Changed

- **Product search now uses a dedicated search service, with the previous one kept as a backup.**
  Text search runs against Open Food Facts' purpose-built search service; the older search endpoint
  remains in place and answers automatically if the new one cannot. The change is about whether
  search works at all: measured on 2026-08-28, at a deliberately polite pace well inside its own
  limits, the old endpoint refused **five of seven** ordinary queries — *chocolate*, *gouda*,
  *nutella*, *bread* and *hagelslag* all came back unavailable. The new service answered every one,
  on the phone, in about a tenth of a second. Switching between the two is invisible: a search that
  falls back looks like one search, not a failure followed by a retry, and an error is shown only
  once both have failed.
  - **One known trade, and it costs no accuracy.** The new service does not publish the field the
    app uses to tell grams from millilitres, so more results now show their name, brand, size and
    photo without a carbohydrate figure beside them. Nothing is guessed — the app has never shown a
    figure whose unit it could not establish, and tapping a result still fetches the full product
    and its real values exactly as before. Search only helps you find the product; the numbers you
    count with come from the product itself.

- **Product search now updates as you type.** Previously a query sat in the field doing nothing
  until Search or the keyboard's Search key was pressed — the results were there to be had and the
  app waited to be asked. Typing now runs the search by itself, about half a second after typing
  stops. Pressing Search still works and skips that wait.
- **Home's search shows when it is refreshing.** Home had no refresh indication at all: its only
  loading state was the centred spinner shown when there were no results, so a refresh over an
  existing list was completely silent. It now shows the same thin progress line and the same
  inline refresh notice as the search screen, so the two cannot drift on what a refresh looks like.
- **Typing now updates the visible results immediately, without waiting for the network.** When the
  new query extends the one the results came from — typing `chocolate` after `choc` — the list
  narrows on the spot while the remote refresh is still pending. A query that is *not* an extension
  (`chocolate` → `gouda`, or shortening back to `choc`) shows a neutral waiting state instead: those
  results are not a partial answer to the new query, so presenting them would be wrong rather than
  merely stale.
- **Waiting for the app's own request budget no longer looks like a failure.** It shows a quiet
  *Updating…* and resolves by itself; only the newest query is kept while it waits, so typing five
  more characters during a wait costs one request rather than five.
- **Being rate-limited is now handled as a wait rather than reported as a broken database**, with no
  *Try again* button while a server-imposed backoff is in force — offering one there invites exactly
  the repeated requests the backoff exists to stop. The queued search resumes automatically, once;
  a second refusal is then reported plainly instead of retrying forever.
- **The wait before a typed query is searched is 0.5 s.** It went 0.6 s → 1 s earlier in this
  version's development, while every search still went to Open Food Facts' rate-limited endpoint and
  a conservative delay avoided spending a scarce slot on a mid-word hesitation. It returned to 0.5 s
  when the dedicated search service became the primary provider (below): that service answers in
  about a tenth of a second and imposes no such budget, so the extra half-second bought nothing and
  the user paid it on every query.
- **Results no longer blank out between queries.** Editing a query keeps the previous results on
  screen, under a thin progress line, until the newer ones replace them in one step. The old
  behaviour cleared the list on every keystroke, so the screen flashed empty-then-spinner-then-
  results for each character typed. Results are still dropped at once when the query is cleared or
  shortened below the three-character minimum, where nothing is coming to replace them.
- **Typing a query that is still too short is no longer treated as a mistake.** The "type at least
  3 characters" notice now appears only when Search is actually pressed, not while someone is on
  their way to a longer word — where, as a screen-reader live region, it announced on every
  keystroke.

### Hardening

- **Search requests now use the provider's POST search API.** What someone types is sent in the
  request body instead of the web address. A web address is the part of a request that proxies and
  server logs routinely keep in plain text, and a search here is a food someone is about to eat.
- **A search reply that arrives damaged no longer looks like "no such product".** If the search
  service answers that it has matches but none of them can be read, the app now treats that as a
  failed search and asks the backup provider — instead of telling the user their product does not
  exist. Both look like an empty list on screen, which is why this could not be noticed in use.
- **Punctuation in a product name is now searched for literally.** The search service reads its
  input as a query language, so brackets, `+` and `:` were being treated as commands rather than as
  part of the name: *Kinder Bueno (White)* found nothing at all, and *milk -chocolate* quietly
  searched for milk **without** chocolate. Names are now searched exactly as typed. Nothing about
  what the search field shows or accepts changed.

### Improved

- **Going back to a search you just ran is now instant.** Looking at *chocolate*, then *gouda*, then
  *chocolate* again reuses the results the app already has instead of asking the network a second
  time — so the list appears immediately, with no spinner and nothing to wait for. Results are kept
  for a few minutes and only while the app is open; nothing is written to the device. A search that
  failed is never reused, so a momentary problem cannot get stuck on screen, and tapping a product
  still loads its full, current details exactly as before.
- **Search results are fetched slightly leaner.** One piece of data the app requested and never
  displayed is no longer asked for. Nothing shown on a result changes.
- **Seven controls were too small to tap reliably, and two of them are the grams/slices switch.**
  Measured on a device, not guessed: the *Grams* and *Slices* buttons on the calculator were 32dp
  tall, *Add portion unit* 40dp, the search and clear buttons inside both search boxes 40dp, Home's
  meal bar 40dp and Home's *Enter manually* 43dp — against the 48dp minimum the rest of the app
  already uses. The mode switch is the one that matters: it decides whether the number you type
  means grams or a count, so missing it changes what the answer is *of*, not just its size. All are
  now full-size. Nothing moved, nothing was restyled, and no spacing changed — the tappable area
  grew to meet the text already there.

### Internal

- New `TouchTargetSizeTest` (instrumented, 5 cases) asserts every clickable node on Home, Search and
  the calculator is at least `Space.minTouchTarget` on its short side, in **dp**, so the result does
  not depend on device density. Text fields are excluded by `IsEditable`, and zero-sized (scrolled
  out of view) nodes are skipped so an off-screen node cannot fail for the wrong reason.
  **Why a test rather than a review habit:** the convention was already established and applied
  everywhere else, and seven controls still shipped under it, because every place that misses it is
  a place where something *else* silently overrides the intent — an `IconButton` in a text field's
  decoration slot is measured by the field, not by its own 48dp default; Material 3's `FilterChip`
  is 32dp by default; and on Home's *Enter manually*, `.height(48.dp)` was written **before**
  `.padding(top = 4.dp)`, so the padding was applied *inside* the 48dp box and the button measured
  44. That last one is the case worth remembering: the line that looks like the fix **was** the
  defect, so reading the code argued the opposite of the truth. Fixed by ordering padding first;
  modifier order is load-bearing and silent when wrong.
  Negative control, run against the unfixed code: every case fails, reporting
  `Search = 40x40dp`, `Clear search = 40x40dp`, `Grams = 75x32dp`, `Slices = 73x32dp`,
  `+ Add portion unit = 137x40dp`, `Enter manually = 371x43dp`. At 1.8× font scale the chips
  measured 35dp, so a large font does **not** rescue them — which is why every fix is
  `heightIn(min = …)` rather than a fixed `height`, so the control still grows with its text.
- Live and explicit search share **one** request pipeline (`MutableStateFlow<SearchRequest>` →
  `flatMapLatest`), so a debounce and a keypress cannot issue two requests for the same query.
  Typing a nine-character word costs one request, not nine.
- Stale-response protection is a request-generation check at the single point where a result is
  written into state, deliberately **not** left to coroutine cancellation. A response for an
  abandoned query cannot reach the screen even if its transport ignores cancellation and completes
  anyway.
- Searches run in their own child coroutine rather than in the collector. `collectLatest` waits for
  the previous block to unwind before starting the next, so a transport slow to cancel stalled the
  pipeline and the *next* query was never sent — found by negative control, not by reading the code.
- `SearchUiState` gained one field, `refreshFailed`. It distinguishes the two failures by what the
  user stands to lose rather than by what went wrong — both carry the same `LookupError` — and is
  always false when there are no hits, so a renderer can treat the two as exclusive. Both screens
  now test `hits.isNotEmpty()` **before** `error != null`; the old ordering is why a refresh failure
  could take the region away from results that were still good.
- New `RemoteSearchGovernor` (pure Kotlin, `domain/`, injectable clock) owns the Open Food Facts
  search budget: `MIN_INTERVAL_MS = 7000`, giving `MAX_REQUESTS_PER_MINUTE = 9`. Held by
  `AppContainer` as a single instance because Home's inline search and the search screen are two
  separate `SearchViewModel`s — a per-ViewModel cooldown would let the two most-likely-consecutive
  screens spend the same budget twice.
- Coalescing is structural rather than a queue: `requests` is a `MutableStateFlow` holding at most
  one desired query, and `flatMapLatest` discards the settle wait *and* the governor wait whenever a
  newer query arrives. There is no data structure in which a backlog of old prefixes could form.
- An attempt is recorded when a request **starts**, for every outcome, and cancellation does not
  refund it — a request that has left the device has spent the quota whatever the app does with the
  answer.
- 429 was already classified as `RATE_LIMITED`; `Retry-After` was being discarded. New
  `RetryAfterHeader` parses both RFC 9110 forms and returns null (never zero) for anything
  unusable, so a malformed header falls back to a conservative 60 s rather than reading as "retry
  now". The governor takes the stricter of its own interval and the server's backoff, caps any
  server-named backoff at 5 minutes, and never lets a later laxer refusal shorten an earlier one.
- The automatic resume after a rate limit is bounded to **one** attempt. Written unbounded first,
  which produced request → 429 → re-arm → request forever; the test suite hung on it.
- `SearchUiState` gained `awaitingRemotePermit`, `rateLimited` and `narrowedLocally`. The remote
  result set is kept separately from the displayed list so narrowing is non-destructive and can
  widen again.
- Two defects found by writing the tests: `waitUntilPermittedMs` computed `Long.MIN_VALUE - now`
  for "no rule applies", which **underflows** to a ~292-million-year wait and parked every first
  search (invisible at a clock of 0, which is why the first governor test passed); and the
  explicit-search dedupe guard swallowed **Retry** entirely, because a finished search leaves the
  requested query set. Both are now pinned by their own tests.
- `LIVE_SEARCH_DEBOUNCE_MS` (600 → 500 earlier in 1.0.2) is replaced by `REMOTE_SEARCH_SETTLE_MS`
  (1000, then **500** once the primary provider changed — see below). The request rate is bounded by
  the governors, not by this constant.

#### Search-a-licious as the primary provider (later in 1.0.2)

- **The feasibility gate was measured before anything was wired.** Legacy `cgi/search.pl`, at 7 s
  spacing: **503 on 5 of 7** representative queries. `search.openfoodfacts.org/search`: twelve
  back-to-back requests, all 200, 136–202 ms, no throttling, no auth. Verified again end-to-end on
  the emulator through the production wiring: **7/7 queries, 20 hits each, 78–106 ms** after the
  first. Bench: `SearchALiciousLiveDiagnosticTest` (prints, asserts almost nothing — a network test
  that fails the build on a flaky connection teaches the team to ignore it).
- **`product_quantity_unit` is not in the Search-a-licious index** — 0 of 140 hits across seven
  queries, and requesting it by name returns nothing rather than erroring. It is
  `PackageBasisResolver`'s primary evidence, so the basis now resolves from free-text `quantity`
  alone on that path and resolves less often (`pasta`: 3/20 vs the legacy 18/20). **No resolver rule
  was weakened to compensate.** A hit with no basis shows no number, which is the existing §13 rule;
  the authoritative figure still comes from the canonical barcode lookup after selection.
- **`brands` is a JSON array here and a comma string on the legacy path** (137 of 140 hits).
  `FirstOfStringOrArray` reads either, scoped to that one field for the same reason
  `LooseNumericText` is — the carbohydrate values keep strict typing.
- `langs=nl,en` is load-bearing, not decoration: without it `product_name_nl` is absent from every
  hit, and Dutch recall collapses (`hagelslag` 449 matches with it, 26 without). This is **input
  recognition**, not localization — the UI stays English (owner decision 10).
- `FallbackProductSearch` (pure, `domain/`) is itself a `ProductSearchSource`, so no ViewModel or
  screen knows there are two providers — which is what makes the migration reversible: pointing
  `AppContainer.searchSource` at the legacy source alone restores the previous behaviour exactly.
  Fallback-eligible: `OFFLINE`, `TIMEOUT`, `SERVER`, `MALFORMED`. **Not** eligible: `RATE_LIMITED`
  (answering "you are asking too often" by asking elsewhere is the behaviour the limit exists to
  stop) and, crucially, a legitimate `NoMatches` — that is an answer, and falling back on it would
  double the cost of every search for something genuinely absent.
- **A cancelled query cannot spend a fallback request, and that needed an explicit guard.** Found by
  test, not by reading: a primary whose transport ignores cancellation returns an ordinary `Failed`,
  and `fallback.search` may then run to completion without ever suspending — so nothing would have
  thrown. `currentCoroutineContext().ensureActive()` before the fallback call is what closes it.
  `CancellationException` is never caught anywhere in the chain.
- **The governor moved out of the ViewModel and down to the provider it protects.** It sat above the
  provider boundary, so leaving it there would have made every primary query wait out an interval
  sized for a different service. `GovernedProductSearch` wraps the legacy source only, keeps
  `MIN_INTERVAL_MS = 7000` and the shared cross-screen budget, and **refuses immediately rather than
  waiting** — a 7 s delay behind an already-failed primary is the stacked wait this migration must
  not create. The primary has its own instance at `PRIMARY_MIN_INTERVAL_MS = 300`.
  `RemoteSearchGovernor`'s clock parameter had to stay **last**: existing callers use a trailing
  lambda, and adding the interval after it silently rebound every one (caught by the compiler).
- Debug-only `SearchProviderLog` / `LogcatSearchProviderLog` (`adb logcat -s JtcSearch`) answers the
  one question device testing cannot answer by looking: which provider served this query. **No query
  text is ever logged** — stage, provider and result count only.
- **A pre-existing ViewModel test was measuring the wrong budget** and is re-aimed rather than
  relaxed: it asserted OFF's 9/min ceiling against traffic that now goes to a provider without one.
  The legacy budget is asserted where it is now enforced, in `GovernedProductSearchTest`.
- **One integration test was found vacuous by negative control and fixed.** The stale-fallback case
  passed with the generation guard deleted — it was measuring `flatMapLatest`, not staleness. It now
  uses a `NonCancellable` fallback, the only fake that reproduces the hazard, and fails without the
  guard. Same trap as the Dutch header fixture and the soft-keyboard geometry test.
- **Search-a-licious moved to `POST /search`.** Identical `q` semantics to the GET form (verified
  against the service's own OpenAPI document), so this is a transport change only. `langs` and
  `fields` are JSON **arrays** in the POST schema where the query string took comma-joined strings.
- **`@EncodeDefault` on the request body is load-bearing.** kotlinx.serialization omits a property
  equal to its default, and the shared `Json` does not set `encodeDefaults` — so every request would
  have serialised to `{"q":"…"}` alone and the *server's* defaults would have applied: `page_size` 10
  instead of 20, `langs` `["en"]` instead of `["nl","en"]` (which is what makes `product_name_nl`
  appear at all, so Dutch recall would have collapsed), and no field filter, pulling ~13 KB per hit.
  Every request still succeeded, so the failure was invisible; caught by asserting the request body
  rather than the outcome.
- **`NoMatches` vs `MALFORMED` is now decided by whether the provider *claimed* matches**, never by
  the mapped list being empty — which is true in both cases and is what made the original bug
  invisible. `hits` non-empty **or** `count > 0` with nothing usable ⇒ `MALFORMED` (fallback-
  eligible); empty `hits` with no positive `count` ⇒ `NoMatches` (an answer, no fallback). A mix of
  valid and malformed records still returns the valid ones — one bad record never discards good ones.
- **`SearchALiciousQuery.escape` prefixes Lucene's reserved characters**, in the provider only. The
  wider rule was chosen over a narrower one on measurement: whether a character acts as an operator
  depends on **position**, not identity (`(` is inert inside a word, an operator around one), and
  escaping the full set changed **no** query that already worked — `M&M's`, `Ben & Jerry's`,
  `70% chocolate`, `Haagen-Dazs`, `7-Up`, `Lay's`, `Côte d'Or` all returned identical counts and
  identical top hits. Apostrophes, `%`, `.`, `,`, spaces and all non-ASCII are untouched. The legacy
  fallback receives the user's text verbatim — it has no query language, so the same escaping there
  would send literal backslashes into a search that would match nothing.
- Nine negative controls run and restored byte-for-byte (hash-verified): GET restored / query in the
  URL (3 fail), unusable-hits→`NoMatches` (4), `MALFORMED` made ineligible (2), `NoMatches` made
  eligible (3), `ensureActive` removed (1), escaping removed (15), `@EncodeDefault` removed (1),
  generation guard removed (7). None vacuous.
- No change to the search request pipeline's generation check, the dedupe rules, query
  normalization, local narrowing, the minimum query length, the calculation, the schema, migrations,
  the §10 lookup priority, barcode detection, any OCR rule, or the 30 s product-refresh window. The
  canonical product path still reads Open Food Facts' product API directly, not the search chain.
- `CachedProductSearch` is a `ProductSearchSource` decorator wrapping the **primary only**, inside
  the chain. Wrapping the primary rather than the whole chain is what keeps a cached result's
  provenance answerable — a legacy answer is never filed under the primary's name — and it makes
  "a cache hit does not reach the fallback" structural rather than a rule. `AppContainer` holds one
  instance, so Home's inline search and the search screen share it for the same reason they already
  share one governor. 20 entries, 5-minute TTL, access-ordered LRU, memory only.
- Only `Found` is cached. Every `Failed` and `NoMatches` is passed through untouched: a cached 503
  would outlive the outage it described, and a cached "no matches" would tell someone a product does
  not exist because it did not five minutes ago, in a database strangers edit continuously. A
  cancelled search writes nothing, because the delegate never returns.
- A future `storedAtMs` counts as expired rather than fresh — a backwards clock change would
  otherwise pin an entry until real time caught up. Same rule and same reasoning as the 30 s
  product-refresh window.
- **Phrase boosting was evaluated and is not available on this deployment.** `boost_phrase` does not
  exist in the service's OpenAPI document (zero occurrences of "boost" or "phrase") and sending it
  is accepted with HTTP 200 and changes nothing at all — the most misleading of the three possible
  answers, since it would have looked enabled. Free-text Lucene phrase syntax does not work either:
  `"nutella"` returns **0**, `(coca cola)` returns 0, `coca^2 cola` returns 0 and `coca OR cola`
  returns **HTTP 500**, while `brands:"coca-cola"` returns 3283 and the service's own documented
  example works — so quoting is honoured only as a field-filter value. Recorded as a re-runnable
  diagnostic in `SearchALiciousLiveDiagnosticTest`, and it independently re-confirms the escaping.
- `SEARCH_FIELDS` dropped `lang`, which was requested and read nowhere. Measured before removing:
  240 bytes per response (12 bytes × 20 hits, 2.3%) across five queries with the **mapped products
  identical** for every one. The request's `langs` is untouched — that is what makes
  `product_name_nl` arrive, and it is a different thing from the per-hit `lang` echo.
- The field-list assertion is now an exact-list comparison. The previous `contains` checks could not
  see a field being **added**, which is precisely how `lang` went unnoticed; proven by negative
  control, which the old assertions did not catch.
- `page_size` stays at **20** on evidence: across the 48-query benchmark, Top20 (37/39) exceeds
  Top10 (36/39) by exactly one query, so a larger page would enlarge every response to buy at most
  one position.

#### Light/dark theme and Android system UI (later in 1.0.2)

Reported from a physical device: status-bar icons disappeared in Light mode, and several Settings
and Home elements were unreadable in Dark mode. Presentation only — no calculation, schema,
migration, §10 lookup priority, barcode, OCR, search or navigation behaviour changed.

- **Status-bar icons were unreadable in Light mode.** `enableEdgeToEdge()` was called with no
  arguments, so the bars resolved their own light/dark from the **device** configuration while the
  app's colours followed the user's selection — two authorities for one question. Choosing Light on
  a dark phone produced light icons on the cream background, which is invisible. Bar appearance now
  follows the **selected app theme** in every combination: Light theme → dark icons, Dark theme →
  light icons.
- **Dark-mode text and icons were black on near-black.** The Settings back arrow, the Settings
  title, the *Haptic feedback* row and Home's gear icon all rendered black in Dark mode. One cause,
  not four: the screens paint themselves with `Modifier.background(colorScheme.background)`, which
  fills a colour but provides no `LocalContentColor` — Material3's `Surface` is what normally does
  both, and the app has one, inside a dialog. Everything that did not name a colour therefore
  inherited `LocalContentColor`'s default of `Color.Black`. `JustTheCarbsTheme` now provides
  `onBackground`, rather than wrapping every screen in a `Surface` that would double-paint
  backgrounds the screens already draw (and would fight the camera screens, which are deliberately
  black in both themes).
- **Navigation-bar treatment now matches the theme.** Icon appearance follows the effective theme,
  and API 29+ contrast enforcement is disabled — its translucent scrim read as a grey band matching
  neither theme with three-button navigation. Safe only because icon contrast comes from the
  appearance flag against a solid, known theme colour. Gesture and three-button navigation both
  remain usable; API 26–28 is unchanged, guarded by a version check.
- **Theme switching is unchanged and still immediate.** The manifest declares
  `configChanges="…|uiMode"`, so this Activity is never recreated — which is also why the one-shot
  `onCreate` call could never re-run. The bar flags are applied from inside the composition, so a
  preference change reapplies them without an Activity restart, a flash or a navigation reset.

### Internal — theme

- New `resolveDarkTheme(themeChoice, systemInDarkTheme)` is the single authority both the Material
  colour scheme and the system bars read, so the two cannot drift again. Pure and system-free — the
  device state is a parameter, which is what makes it JVM-testable without an emulator.
- New `ThemeResolutionTest` (JVM, 10 cases) pins the six device-theme × app-selection combinations
  plus the two properties the consumers rely on: an explicit choice must ignore the device entirely,
  and `SYSTEM` must follow it in both directions. Verified non-vacuous by negative control —
  reintroducing the device read into the `LIGHT` branch fails 2 cases, including the one that
  mirrors the reported defect. The instrumented `ThemeDefaultTest` is unchanged and still asserts
  the **rendered** background, which is the seam where a wrong default is visible.
- Only `Theme.kt`, `MainActivity.kt` and the new test changed. `Color.Black`/`Color.White` elsewhere
  was audited and deliberately left: the camera screens are intentionally black in both themes with
  explicitly tinted foregrounds, and onboarding's white sits on saturated brand backgrounds, with
  its one theme-background slide already using semantic tokens.

### Verification — 1.0.2 release candidate

- **JVM 1015/1015** (up from 1004), 0 failures, 0 errors, **0 skipped**, `--rerun-tasks`, counted
  from JUnit XML rather than a wrapper exit code.
- **Instrumented 248/248** in **one whole-suite run**, 0 failures, **0 ignored**, counted from
  instrumentation status codes. Taken **after** every change in this version, so the figure covers
  the release rather than predating part of it.
- **Real-image OCR corpus 37/37**, unchanged — `RealImageOcrTest` 15, `ProductionStillPipelineTest`
  8, `SelectedTableProductionTest` 6, `EvidencePipelineProductionTest` 8.
- **Room migrations 10/10** (`JustTheCarbsDatabaseMigrationTest`).
- **Lint exit 0 on both debug and release**, 0 errors, **41 advisories** — unchanged baseline, so
  this version's work added none.
- **Debug APK and release AAB both build from `clean`.**
- **OSV dependency scan: 226 resolved release-runtime artifacts, 0 known vulnerabilities**, control
  query positive.
- **R8 privacy barriers re-checked** on the release build's `mapping.txt`: `ScanEvidenceRecorder`
  and `OcrDiagnosticsLogger` → `R8$$REMOVED$$CLASS$$`; `ScanEvidenceExport`, `OcrDiagnosticsReport`
  and `ScanTrace` absent entirely; `UnitMarkerFilter`, `CandidateProvenance`, `CarbCandidate` and
  `PackageBasisResolver` retained as real classes. Release manifest: three disclosed permissions,
  one exported component of ours (`MainActivity`), **no `FileProvider`**.
- **Not verified on physical hardware.** Everything above is JVM and emulator. The artifact hash,
  size and signer are recorded in [`docs/version-history.md`](docs/version-history.md) only once
  Play accepts the upload.

### Play Store release notes

```
Product search now updates as you type, with faster and more reliable results. Returning to a
search you just ran is instant, and results stay on screen while the next ones load instead of
blanking out. Product names containing brackets, symbols or punctuation now find the right
products. Also fixes status bar icons being invisible in light mode, and text and icons being
hard to read in dark mode, with better light and dark theme integration throughout.
```

457 characters, within the 500 limit.

Internal notes, not for Play: the provider names, the request budgets, the settle delay, the
fallback architecture and the move to POST are engineering details with no user-facing wording —
the privacy improvement is real but describing it would need the reader to know the search text was
previously in the address, which is not something the app ever showed them. The fewer preview
figures on search cards are also deliberately unmentioned — tapping a result still fetches the full
product, so there is no user-visible loss to describe. The theme entries are worded as the two
things a tester can see and check — light-mode status bar, dark-mode readability — rather than by
mechanism; `LocalContentColor` and edge-to-edge mean nothing to a reader of this field.

---

## 1.0.1 (versionCode 2) — 2026-08-28 — Closed testing

The **first update of the closed beta**, uploaded and accepted by Play on 2026-08-28. Full entry,
including the artifact hash, size and signer, in
[`docs/version-history.md`](docs/version-history.md).

A small, conservative quality pass taken during the closed beta. No feature work, no schema change,
no migration, no new permission or dependency, no change to barcode scanning, and no change to the
calculation or to any OCR safety rule.

### Fixed — reproduced by a test that failed before the fix

- **Every new product was fetched from Open Food Facts twice.** Not an edge case and not a race:
  *every* first-time scan cost two requests. On a cache miss the lookup downloaded the product and
  saved it, and the background refresh that runs immediately afterwards then found that fresh row,
  decided it had something to refresh, and re-downloaded the same barcode microseconds later — for
  a budget of 15 reads per minute shared by everyone on the same connection. A freshly downloaded
  product is now stamped as synced, and a refresh is skipped for any product synced within the last
  30 seconds — which suppresses that immediate duplicate, and also means reopening the same product
  within half a minute does not re-check it. Anything cached longer ago than that still refreshes
  normally, which is the only way a reformulated product can be noticed. Measured before and after:
  a first-time lookup went from two requests to one.
- **A dragged crop rectangle moved a fraction of the distance your finger did.** Drag events arrive
  as small increments, and the handler recomputed each one from the rectangle as it stood when the
  gesture *began* rather than as it stood after the previous increment — so the increments replaced
  each other instead of adding up. Measured: a drag delivered as ten 10-pixel steps moved the
  rectangle 10 pixels instead of 100. Resizing from every corner was affected the same way, and a
  second drag restarted from the original rectangle, discarding the first.

- **Saving a manually entered product could fail with nothing on screen to say so.** If the write
  failed, the Save button simply became tappable again and the screen was otherwise unchanged — no
  message, no error, nothing. The only available reading was that the tap had not registered, so the
  natural response was to tap again and fail again. A failed save now says the product was not
  saved; the message clears as soon as you edit a field or the next save succeeds. A related case
  was also corrected: when a product carrying a scanned portion failed to save, the app moved on to
  the calculator for a product that did not exist. It now only does that when the product genuinely
  landed and it was the portion alone that failed — which is what the existing message about the
  portion has always described. The two writes are made as two separate steps so which of them
  failed is known from where the failure happened; an earlier draft of this fix inferred it
  afterwards by checking whether a product row existed, which gave the wrong answer whenever that
  barcode was already stored from an earlier scan — a failed product write was then reported as a
  portion failure and the calculator opened on the old record.

### Improved

- **Searching for a product name shorter than three characters did nothing at all.** The search is
  deliberately refused below three characters — two letters match thousands of products — but the
  refusal was silent: no request, no spinner, and the same "type a product name" prompt the screen
  already showed. The screen now says a longer query is needed, and announces it for screen readers.
  Nothing about when a search is actually sent changed.

### Changed

- **The scanner starts reading a captured label sooner.** Live camera frames stop being analysed
  the moment you tap capture, instead of finishing a full parse whose result was then thrown away.
  That parse sat directly between the shutter and the reading of the captured photo, so discarding
  it earlier shortens the wait. Nothing about what the scanner accepts, refuses or reports changed.
  The framing hint can also no longer appear over the capture screen after you have tapped capture.

### Hardening — no defect reproduced, kept as protection

- **The label scanner's autofocus fallback no longer runs after the screen closes.** Capture waits
  up to 1.2 s for autofocus and fires the shutter anyway if it never reports back; nothing cancelled
  that fallback, so closing the scanner within that window left it queued to run against a camera
  already torn down. That state is reachable by reading the code, and the fallback now does nothing
  once the screen is gone. **The resulting failure was never reproduced on a device**, so no
  specific crash is claimed — this is defensive, not a fixed crash report. The previous draft of
  this entry described it as a crash; that was not established and has been corrected.
- **The crop screen no longer shares its grabbed-corner state across the whole app.** Which corner a
  drag had hold of was kept in state shared by every scan in the process rather than per screen, and
  a drag interrupted by *Retake* could leave it set. It is now per screen, so a new capture cannot
  inherit it. **No misbehaviour was ever reproduced from this** — an earlier draft of this entry
  claimed the next scan's first drag would resize instead of move, which was not established and has
  been corrected. This is state isolation, not a fixed defect.

### Internal

- The lookup test's fake cache previously discarded everything written to it, which is precisely
  what hid the double fetch: with nothing stored, the refresh returned early and its request never
  appeared. It now persists, and four cases cover the fresh lookup, the cached refresh, overlapping
  lookups and cancellation. All four fail on the old code.
- Crop gesture state extracted to a pure class with 8 JVM tests covering accumulation, per-corner
  resizing, consecutive gestures, interrupted gestures and the bounds rules. Verified non-vacuous:
  reintroducing the old captured-value read fails exactly the accumulation cases.
- **The instrumented flake is root-caused and fixed.** It was the soft keyboard, not timing and not
  a layout defect: Gboard is a real 641-pixel window over the bottom of the screen, and the
  assertions that failed were on elements pinned there. Measured — the meal bar sits at a stable,
  fully on-screen position when its test runs alone, and with the keyboard disabled the class passed
  19/19 three times, while with it enabled exactly one arbitrary test failed per run. The tests now
  dismiss the keyboard after typing, as a user does. No retries, no `@Ignore`, no sleeps and no
  weakened assertions were used.
- Five repository tests pin the new refresh-freshness rule, including the two cases that decide
  whether it is safe: a never-synced product still refreshes, and a sync timestamp in the future
  (a device clock change) is not read as freshness.
- A lookup that fetches from the network now hands back the same record it caches. It saved a copy
  stamped with the sync time but returned the unstamped original, so the two disagreed about when
  the product was last synced. Nothing read that field off the returned value, so no user-visible
  behaviour changed; two tests now pin that the returned and cached records match, and that a cache
  hit is still returned untouched.
- Ten tests pin the two failure notices above: six on the manual-entry save (reported, cleared by an
  edit, cleared by a later success, plus the three failure-classification cases — product failure
  over an existing local row, portion failure after a successful product write, and full success
  still navigating) and four on the search refusal (reported, cleared by an edit, cleared by a
  longer submission, and **not** raised for a blank field — an empty box is not a refused search).
  Verified non-vacuous by negative control: removing each notice, and reinstating the discarded
  after-the-fact failure inference, each fails exactly its own tests and nothing else.
- `ProductRepository.saveProductWithPortionUnit` now has no production caller — manual entry makes
  the same two writes itself so it can tell which one failed. It is left in place with its existing
  repository tests rather than removed as part of this change.
- **JVM 801/801** (up from 791) and **instrumented 218/218**, both 0 failures, 0 errors and 0 skipped,
  counted from JUnit XML / instrumentation status codes rather than from a wrapper exit code. The
  37-test real-image OCR corpus is 37/37, unchanged. Lint exit 0, 0 errors, 41 advisories. OSV
  dependency scan: 226 resolved release-runtime artifacts, 0 known vulnerabilities, control query
  positive. The instrumented suite completed as **one whole-suite run** (218/218, 0 ignored,
  15m25s) taken **after** every change in this version, including the two that add a line to a
  rendered screen — so the figure covers this release rather than predating part of it.

### Play Store release notes

**As pasted into Play Console's *What's new* on upload.** 411 characters, within the 500 limit.

The label-scan change is worded as *reducing the wait after capture* rather than as "faster": the
work removed is real and measured in the code, but no before/after figure was taken on a physical
device, and the archive's rules forbid promising a fix that has not been verified on one.

```
Fixes a problem where dragging the crop box while scanning a nutrition label moved it far less than
expected. Searching for a very short product name now explains why it needs more letters instead of
appearing to do nothing, and a product that fails to save now says so instead of looking like the
button was missed. Uses less mobile data when looking up a product, and reduces the wait after
capturing a label.
```

---

## 1.0.0 (versionCode 1) — 2026-08-26 — Internal testing → Closed testing

First build delivered to testers via Google Play, and the version currently on a track. The **same
artifact** was promoted from internal to closed testing — one bundle, one hash, two tracks, not two
releases — and the closed-testing period is running with 12+ testers opted in. Full entry, including
the artifact hash, in [`docs/version-history.md`](docs/version-history.md).

### Added

- Scan a food barcode, enter a portion, read the carbohydrate grams. Open Food Facts lookup with a
  local cache, manual barcode entry, and search by product name as a fallback from a failed lookup.
- **Nutrition-label scanning.** Capture a label, confirm the table with a draggable rectangle, and
  read the per-100 figure. Where automatic reading is not confident the frozen photo stays on
  screen and you point at the value yourself, rather than the scan dead-ending.
- **Countable portions** — "2 slices" as well as "65 g", when a trustworthy per-item weight or a
  per-serving carbohydrate figure exists.
- **Temporary meal.** Add several calculated portions and read one total. One meal only; no history.
- **Usual portions.** Portions repeated for a specific product become one-tap shortcuts.
- Light/dark/system theme, onboarding, and a settings screen with the privacy policy and
  Open Food Facts attribution.

### Notes

- The app calculates carbohydrate amounts only. It does **not** calculate insulin, and it is not a
  diet tracker.
- English-only interface. Dutch, German and other label text is still recognised when scanning.
- No analytics, no accounts, no advertising. Data stays on the device apart from Open Food Facts
  lookups.
