# 10x Analysis: CarbScan
Session 1 | Date: 2026-08-14

## Current Value

CarbScan compresses a safety-sensitive packaged-food task into `scan -> portion -> carbs`. Its
strongest product qualities are speed, offline/cache-first behavior, explicit provenance, and a
single auditable calculation path. The newest capability lets users calculate by countable units
such as slices when a trustworthy per-item weight exists.

The product is feature-complete enough that the next highest-leverage work is confidence and
release validation, not breadth. Evidence: `CLAUDE.md` lists unresolved publication gates and says
countable portions have not been exercised on physical hardware or against a real Open Food Facts
`serving_size`; `docs/ux-critique-countable-portions.md` identifies the missing correction flow.

## The Question

What should CarbScan focus on first to create the most user and release value?

---

## Massive Opportunities

### 1. Trusted personal product memory

**What**: Make every scan improve a private, on-device library of verified products and portions,
with effortless re-verification when packaging changes.

**Why 10x**: First use is a lookup tool; repeated use becomes nearly instant and more trustworthy
than a generic food database because it remembers what the user personally checked.

**Unlocks**: A compounding advantage without accounts, cloud storage, or health-platform scope.

**Effort**: High

**Risk**: Stale packaging data must remain visible and reversible; it must never become silent sync.

**Score**: Strong, but the existing foundations already cover much of this and should be validated
before expanding them.

### 2. Broader food-intake or insulin features

**What**: Add diaries, meal history, insulin calculations, pump/CGM integrations, or nutritional
recommendations.

**Why 10x**: It would expand the apparent market and number of use cases.

**Risk**: It destroys the product's narrow advantage, expands regulatory exposure, and conflicts
with the explicit scope in `docs/MASTER-PROMPT.md`.

**Effort**: Very high

**Score**: Pass.

---

## Medium Opportunities

### 1. Close the countable-portion confidence loop

**What**: Validate countable portions against real Open Food Facts data and physical devices, then
let users correct an existing remote-suggested unit in place. The repository already supports
`verifyPortionUnit(unitId, confirmedAmountPerUnit)`; the missing piece is the UI path.

**Why 10x**: "2 slices" is materially faster and more natural than weighing common foods, but only
if users can trust and correct the mapping without creating confusing duplicate units.

**Impact**: Makes the newest differentiating feature genuinely shippable and trustworthy.

**Effort**: Medium

**Score**: Must do.

### 2. Release-confidence program

**What**: Resolve the regulatory assessment, run a small physical-device matrix, produce a signed
AAB, add dependency vulnerability scanning, and close attribution/contact/licence placeholders.

**Why 10x**: It converts a capable repository into a product that can actually be distributed.

**Impact**: Removes every known publication gate.

**Effort**: Medium, with owner/legal dependencies.

**Score**: Must do if launch is the goal.

### 3. Scan-to-answer observability

**What**: During structured QA, record anonymous/local timings and failure reasons for each stage:
barcode acquisition, cache hit, remote lookup, recovery route, and first result.

**Why 10x**: The core promise is speed. Measuring the funnel exposes where real users lose time or
confidence instead of optimizing from intuition.

**Impact**: Guides future work toward measurable reductions in time-to-carbs.

**Effort**: Medium

**Score**: Strong, but design it carefully to preserve the current privacy posture.

---

## Small Gems

### 1. Edit a suggested portion directly

**What**: Change `1 slice = 36 g` in place and mark it verified.

**Why powerful**: Removes the clearest known UX dead end and uses an already-implemented repository
operation.

**Effort**: Low

**Score**: Must do.

### 2. Remove the known test flake and compiler warnings

**What**: Stabilize the order-dependent Compose test, opt into or replace `FlowPreview`, and migrate
the deprecated clipboard/lifecycle APIs.

**Why powerful**: Improves signal quality before release work; failures and warnings become
meaningful again.

**Effort**: Low

**Score**: Strong.

### 3. Reconcile verification documentation

**What**: Make `CLAUDE.md` consistent about whether live Open Food Facts lookup has been verified.

**Why powerful**: Prevents future work from repeating validation or falsely assuming it occurred.

**Effort**: Very low

**Score**: Strong.

---

## Recommended Priority

### Do Now

1. Run the countable-portion workflow on a physical device with several real products whose Open
   Food Facts `serving_size` values cover success, absence, ambiguity, and incorrect data.
2. Add the in-place correction/verification UI for a suggested portion unit.
3. Stabilize the flaky instrumented test and add coverage for correcting a suggested unit.

### Do Next

1. Resolve the regulatory qualification, because it is the hard publication gate and determines
   store wording and release requirements.
2. Complete signing/AAB, vulnerability scanning, attribution, contact, licence, and device-matrix
   work as one release-readiness milestone.
3. Measure scan-to-answer time and recovery frequency during structured QA.

### Explore

1. Strengthen the private verified-product memory into the product's compounding advantage, while
   retaining local-only data and explicit re-verification.

### Backlog

1. Additional food databases, only after real lookup failures show meaningful coverage gaps.
2. New portion kinds, only when observed packaging data requires them.
3. Any diary, insulin, pump, CGM, or recommendation feature: out of scope.

---

## Questions

### Answered

- **What is the first engineering focus?** Validate and complete countable portions end to end.
- **What is the first business/release focus?** Resolve the regulatory qualification.
- **Should another major feature be started now?** No; confidence and distributability have higher
  leverage than feature breadth.

### Blockers

- The owner must perform or commission the regulatory qualification; code cannot settle it.
- Physical-device and real-product validation requires representative packaging and hardware.

## Next Steps

- [ ] Select 5-10 real products with diverse `serving_size` data.
- [ ] Execute and record the countable-portion manual QA matrix.
- [ ] Specify the smallest in-place correction interaction.
- [ ] Implement and test that interaction in a separate engineering task.
- [ ] Assign an owner and evidence source for the regulatory assessment.
