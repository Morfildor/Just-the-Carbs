# Regulatory qualification assessment — CarbScan

**Status: DRAFT prepared for the owner. Not yet signed. Not a legal opinion.**

This document exists to satisfy brief §44, which requires that a qualification assessment be
*performed and documented* before publication — and which expressly forbids using a disclaimer to
avoid that assessment.

It sets out the owner's position that CarbScan is **not** a medical device under Regulation (EU)
2017/745 (MDR), together with the reasoning and the verifiable facts that position rests on. It is
written to be read by someone who disagrees with it.

**What this document is not:** it is not a legal opinion, not a regulatory determination by a
competent authority, and not a substitute for professional advice. It is the manufacturer's own
documented reasoning, which is what MDR expects a manufacturer to hold.

---

## 1. Intended purpose

> **CarbScan is a general-purpose calculator for the carbohydrate content of a portion of packaged
> food.**
>
> The user scans a barcode or enters a product manually. The app retrieves or accepts a
> carbohydrate-per-100 g (or per-100 ml) figure and multiplies it by a portion size the user
> enters, displaying the resulting carbohydrate quantity in grams.
>
> It is intended for anyone who wants to know the carbohydrate content of a portion of food —
> including people cooking, tracking nutrition for sport or training, managing their weight,
> following a low-carbohydrate diet, or with any other dietary interest.
>
> It contains no feature specific to any disease or condition, and no feature that any one group of
> users needs and others do not.
>
> **CarbScan does not calculate insulin or any other medication. It does not interpret blood
> glucose. It does not provide diagnostic, therapeutic, monitoring, prediction or prognostic
> information. It does not replace the information printed on the package.**

This statement is the input to everything below. It must appear, in substance, in the store listing
and in the app, and **the app must not be marketed in any way that contradicts it** (see §7).

## 2. What the software actually does

The entire calculation is one line of arithmetic:

```kotlin
val exact = carbsPer100.multiply(portion).movePointLeft(2)   // CarbCalculator.kt:45
```

That is `carbohydrate per 100 × portion ÷ 100`. There is exactly one such formula in the
application; countable portions ("2 slices") resolve to a weight *before* this step and never
constitute a second calculation path.

Three properties follow, and each is verifiable in the repository rather than merely asserted:

| Property | Verification |
|---|---|
| The result is arithmetic on a value the user supplies or confirms | `CarbCalculator`, 11 unit tests |
| The same figure is obtainable by a person reading the package and doing the multiplication | Inherent to the formula |
| No physiological model, patient parameter, or algorithm interprets the number | Absence of any such concept in `domain/` |

**Verified absence of dosing concepts.** A search of the entire production source tree for
`insulin`, `bolus`, `dose`, `glucose`, `CGM`, `correction factor` and `carb ratio` returns **no
implementation** — no field, function, parameter, stored value, or UI element (checked
2026-08-14). The only occurrences are explanatory comments, addressed honestly in §5.

## 3. Assessment against MDR Article 2(1)

MDR Article 2(1) defines a medical device by reference to the manufacturer's **intended purpose**
being one or more specific medical purposes: diagnosis, prevention, monitoring, prediction,
prognosis, treatment or alleviation of disease, injury or disability, and related purposes.

| Medical purpose in Art. 2(1) | Does CarbScan claim it? | Basis |
|---|---|---|
| Diagnosis | No | Produces no diagnostic output of any kind |
| Prevention | No | Makes no preventive claim; not marketed against any disease |
| Monitoring | No | Stores no physiological data; tracks no health parameter over time. Deliberately not a food diary: no daily totals, no dated meal history, no trends |
| Prediction / prognosis | No | Computes no forward-looking or risk output |
| Treatment or alleviation | No | Recommends no intervention. Suggests no portion, no food, and no dose |

**Conclusion on Article 2(1): CarbScan's intended purpose is not a medical purpose.** It reports a
property of a food product — the same property printed on the packaging — for a portion size the
user chose.

Nutritional information about packaged food is not, in itself, medical information. A carbohydrate
figure on a package is a food-labelling fact governed by food law (Regulation (EU) No 1169/2011),
not medical device law. Reading that figure and multiplying it does not convert it into one.

## 4. Assessment against MDCG 2019-11 rev.1

**Guidance version used: MDCG 2019-11 rev.1, published June 2025** — confirmed current against the
European Commission's MDCG guidance index on 2026-08-14. *(Note: this supersedes the 2019 original.
Confirm no further revision exists at the time of signing.)*

MDCG 2019-11 asks whether software performs **an action on data beyond storage, archival,
communication or simple search** and whether that action is **for the benefit of an individual
patient** for a medical purpose.

**On the action:** CarbScan performs a single multiplication and division by 100 on a value the
user supplied or confirmed. This is arithmetic on a food-label figure, not processing that creates
new medical information. It applies no algorithm, no model, no patient-specific parameter, and no
interpretation. The output contains no more information than the inputs.

**On the benefit to an individual patient for a medical purpose:** the intended purpose in §1
names no patient population and no disease. The app has **no diabetes-specific feature of any
kind** — a claim of intent that is corroborated by the implementation, not merely stated.

**On the revision-1 clarification:** rev.1 emphasises that software processing health information
falls under MDR *only if it directly serves a medical purpose*. CarbScan processes **food product
information**, not health information about a person. It holds no patient data whatsoever: no
identity, no account, no physiological measurement, no health history.

**Conclusion: CarbScan does not meet the MDSW qualification criteria.** It is closer in kind to a
kitchen scale or a unit converter than to clinical software.

## 5. The adjacency question — stated plainly, not omitted

**This is the strongest argument against the position above, so it is addressed directly rather
than left for someone else to find.**

Some users of a carbohydrate calculator will have type 1 diabetes and will type the resulting
figure into a separate bolus calculator. The repository does not pretend otherwise. Three source
comments and one design decision acknowledge this destination — most explicitly:

> `ResultFormatter.kt:62` — *"a bolus calculator expecting `31.3` would misread the Dutch `31,3`"*

The clipboard value is formatted with `Locale.ROOT` for that reason. Owner decision #2
(decimal-dominant results) rests on the same anticipated use.

**The origin of those design decisions, stated for completeness.** The developer is himself an
insulin pump user who uses a separate bolus calculator. CarbScan was written in the first instance
because he wanted a faster way to get a carbohydrate figure, and the decisions above — exact
decimals, `Locale.ROOT` formatting — come from that first-hand knowledge that such figures get
retyped and that a comma decimal breaks a machine expecting a point.

This is recorded rather than omitted, because it is the truthful explanation for the design and
because a document that concealed it would be weaker, not stronger. Its regulatory weight is
nevertheless **nil**:

- **Intended purpose is a property of the product, not of its author.** MDR qualification turns on
  the purpose the manufacturer assigns and markets, not on the manufacturer's own health or
  personal motivation. A developer with diabetes who builds a general-purpose calculator has built
  a general-purpose calculator.
- **The knowledge produced a quality decision, not a clinical one.** Knowing that decimals get
  transcribed led to formatting that resists transcription error. It did not lead to any dose
  logic, any patient parameter, or any diabetes-specific feature — and the source tree confirms
  none exists.
- **The product has no feature that a person with diabetes needs and a person tracking carbohydrate
  for sport or weight does not.** Every function serves both identically. There is no
  diabetes-specific code path to point at, because there is no diabetes-specific code.

**Why this does not make CarbScan a medical device:**

1. **Foreseeable downstream use by a third party is not intended purpose.** MDR qualification turns
   on the purpose the manufacturer assigns. A kitchen scale, a food label, and a pocket calculator
   are all used by people with diabetes in exactly this way, and none is a medical device for that
   reason. If foreseeable use by a patient were sufficient, food packaging itself would qualify.

2. **CarbScan performs no part of the dose calculation.** It does not apply an insulin-to-carb
   ratio, a correction factor, insulin-on-board, or any patient parameter. It has no interface to
   any dosing application: no API, no integration, no export, no automation. The transfer is a
   human being retyping a number into a different app they chose.

3. **The design decisions in question improve accuracy; they do not add clinical function.**
   Formatting a decimal so it cannot be misread, and showing the exact value rather than a rounded
   one, reduce transcription error. A decision that makes a number harder to get wrong is a quality
   measure, not the assumption of a medical purpose.

4. **The app never modifies, interprets, or acts on the number.** It reports a food fact. What the
   user does with it is outside both the software and its intended purpose.

**Honest statement of residual risk.** This is the element of the assessment on which a competent
authority could most plausibly take a different view — particularly if CarbScan were ever marketed
toward diabetes management. That is why §7 exists, and why the marketing constraints there are
binding rather than advisory. The owner accepts this residual uncertainty knowingly, having
identified it rather than having overlooked it.

## 6. Safeguards implemented, each pinned by a test

Independently of qualification, the safety-critical paths are conservative — implemented and
tested, not asserted. This is offered as evidence of a diligent manufacturer, **not** as a
substitute for the assessment.

| Safeguard | Enforced by |
|---|---|
| OCR output is never auto-accepted; every reading is confirmed by the user | `NutritionLabelParserTest` |
| Ambiguous label readings are never resolved by the app — it asks | `reports ambiguity when two different carbohydrate values appear` |
| The "of which sugars" sub-line is never read as total carbohydrate | `never mistakes the sugars sub-line for the total` |
| A user-verified value is never silently overwritten by remote data | 7 provenance regression tests |
| An open calculation is never changed by a background refresh | Session-immutability regression tests |
| No value is shown when confidence is insufficient — it reports unavailable | `NutritionValueValidatorTest` |
| Millilitres are never converted to grams; no density is assumed | `the basis never applies a density conversion` |
| The displayed decimal and whole gram are derived independently — never rounded twice | `CarbResult` tests |
| Values are stored as exact decimals, never as floating point | Room TEXT-column round-trip tests |

Totals at assessment date: **225 JVM unit tests, 89 instrumented tests, all passing; lint clean.**

## 7. Binding constraints on marketing and future development

**The conclusion in §8 is valid only while every constraint below holds.** Breaching any of them
reopens the assessment before release.

**Marketing — the app must not:**

- Be described as being for diabetes, diabetes management, or blood glucose control
- Be described using "recommend", "advise", "prescribe", "dose", "bolus", or "therapy"
- Claim to diagnose, treat, prevent, monitor, predict, or alleviate any disease
- Claim clinical validation, medical approval, or regulatory clearance
- Display a CE mark
- State or imply affiliation with any medical device manufacturer (CamDiab, Ypsomed, CamAPS FX,
  Abbott, Libre or any other)
- Use disease-related keywords in the store listing, including in ASO metadata

> **The developer's own story must stay out of the listing.** The developer is an insulin pump
> user, and the natural marketing instinct — *"as a pump user, I built this because…"* — is the
> single most likely way this assessment gets breached in practice. It would be true, sympathetic,
> and would tell every reader, in writing, on the page a regulator reads first, that the product is
> for diabetes dosing. **That one sentence would change the intended purpose.**
>
> This is not an instruction to conceal anything. It is that personal use is not a product claim and
> does not belong in the listing. The same applies to review replies, the app's About screen,
> release notes, screenshots, social posts, and anything else published under the app's name.
> "I built the app I wanted" is safe; "I built it for my bolus calculator" is not.

**Development — the following would require a fresh assessment before shipping:**

| Change | Why it reopens the question |
|---|---|
| Any insulin, dose, ratio, correction factor or IOB concept | Direct therapeutic function |
| Any glucose value, entry, import, or interpretation | Physiological data for a medical purpose |
| Integration with a pump, CGM, or dosing app (API, export, deep link, automation) | Removes the human step and makes the app part of a dosing chain |
| Recommending portions, foods, or meals | Advice rather than arithmetic |
| Any prediction, trend, or personalised algorithm | Prediction/prognosis under Art. 2(1) |
| Marketing that names diabetes as the purpose | Changes the intended purpose itself |

> **Note the asymmetry:** these changes are dangerous *even though* each might seem individually
> small. Qualification turns on intended purpose, and intended purpose is exactly what such
> features would change.

## 8. Conclusion and decision record

**Assessed conclusion: CarbScan is not a medical device within the meaning of MDR Article 2(1),
and is not an accessory to a medical device.**

Basis: its intended purpose is not a medical purpose (§3); it does not meet the MDSW qualification
criteria in MDCG 2019-11 rev.1 (§4); it performs arithmetic on a food-labelling figure rather than
processing patient health data; and the foreseeable use of its output by a third-party dosing tool
is not an intended purpose the manufacturer assigns (§5).

**This conclusion is conditional on the constraints in §7 continuing to hold.**

### Owner sign-off — required before publication

| Field | Value |
|---|---|
| Assessment performed by | *(owner's full name)* |
| Role | Manufacturer (natural person placing the software on the market) |
| Date | *(date)* |
| Intended purpose statement | As set out in §1 above, unmodified |
| Guidance version relied on | MDCG 2019-11 rev.1 (June 2025) — *confirm still current at signing* |
| Conclusion | ☐ Not a medical device ☐ Medical device ☐ Accessory to a medical device |
| Markets assessed | ☐ EU/NL only ☐ EU + UK ☐ Worldwide |
| Independent review obtained? | ☐ No ☐ Yes — *(name, firm, date)* |
| Signature | |

**Retain this document. If a competent authority, Google, or a user ever asks, this record — dated
and reasoned — is the deliverable. "We thought about it" is not.**

## 9. Outstanding items the owner must still close

1. **Markets are undecided.** This assessment covers **the EU only**. The UK (MHRA) and US (FDA)
   have diverged and require separate assessment. Until decided, **geo-restrict the Play listing to
   the EU**, which is a real control rather than a paper one.
2. **Confirm MDCG 2019-11 is still at rev.1** at the time of signing.
3. **Review MDCG 2025-4** — *Safe making available of medical device software apps on online
   platforms* (June 2025). It concerns app-store distribution and is directly relevant to a Play
   release, including for software concluded not to be a device.
4. **Google Play Health apps policy** — a separate gate with its own criteria. Play's definition of
   a health app is broader than MDR's definition of a device; being out of scope here does not
   settle the Play declaration.
5. **Consider an independent review.** One to two hours with an EU MDR software consultant
   (roughly €150–400/hour) would either confirm this position or correct it. Given the adjacency in
   §5, this is recommended rather than merely available — and the existence of this document
   should reduce the time required.
6. **Assess the intended-purpose wording against the final store listing** once written, to confirm
   §7 is not breached by marketing copy.

---

*Prepared 2026-08-14 as a working draft for the owner. Unsigned drafts carry no weight: this
becomes the record only when §8 is completed and dated.*
