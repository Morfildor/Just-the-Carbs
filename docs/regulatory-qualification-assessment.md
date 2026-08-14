# Regulatory Qualification Assessment

## Just the Carbs — carbohydrate calculator for packaged food

**Manufacturer's assessment of medical device qualification under Regulation (EU) 2017/745**

| | |
|---|---|
| **Product** | Just the Carbs (application identifier `app.justthecarbs`) |
| **Version assessed** | 1.0.0 (version code 1) |
| **Manufacturer** | *(full name)* |
| **Contact** | albinogorillassupport@gmail.com |
| **Assessment date** | *(date)* |
| **Guidance relied upon** | MDCG 2019-11 rev.1 (June 2025) |
| **Markets assessed** | European Union |
| **Conclusion** | **Not a medical device** — see Section 8 |
| **Document status** | ☐ Draft ☐ Signed and in force |

---

## Purpose of this document

This is the manufacturer's documented assessment of whether Just the Carbs qualifies as a medical device
under Regulation (EU) 2017/745 (the Medical Device Regulation, "MDR").

It records the intended purpose assigned to the product, the assessment performed against MDR
Article 2(1) and the applicable MDCG guidance, the facts relied upon, and the conclusion reached.
It also records, deliberately, the strongest argument against that conclusion (Section 5) and the
conditions under which the conclusion would cease to be valid (Section 7).

This document is not a legal opinion and is not a determination by a competent authority. It is the
manufacturer's own reasoned assessment, retained as a record.

---

## 1. Intended purpose

**Just the Carbs is a general-purpose calculator for the carbohydrate content of a portion of packaged
food.**

The user scans a product barcode or enters a product manually. The application retrieves or accepts
a carbohydrate-per-100 g (or per-100 ml) figure and multiplies it by a portion size the user
enters, displaying the resulting carbohydrate quantity in grams.

Just the Carbs is intended for anyone who wants to know the carbohydrate content of a portion of food,
including people cooking, tracking nutrition for sport or training, managing their weight,
following a low-carbohydrate diet, or with any other dietary interest.

Just the Carbs contains no feature specific to any disease or condition, and no feature that any one
group of users requires and others do not.

**Just the Carbs does not calculate insulin or any other medication. It does not interpret blood glucose.
It does not provide diagnostic, therapeutic, monitoring, prediction or prognostic information. It
does not replace the information printed on the product packaging.**

This statement of intended purpose is the basis of the assessment that follows. It appears in
substance in the application and in its store listing, and the product is not marketed in any
manner that contradicts it.

---

## 2. Description of the software

### 2.1 The calculation

The complete calculation performed by the application is:

> **carbohydrate per 100 units × portion size ÷ 100**

This single arithmetic operation is the only carbohydrate calculation in the application.
Count-based portions (for example, "2 slices") are resolved to a weight before this step and do not
constitute a second calculation path.

### 2.2 Properties of the calculation

| Property | Basis |
|---|---|
| The result is arithmetic performed on a value the user has supplied or confirmed | Design of the calculation component, covered by automated tests |
| The same figure is obtainable by a person reading the package and performing the multiplication | Inherent to the operation |
| No physiological model, patient parameter, or interpretive algorithm is applied | Verified absence in the source code |

### 2.3 Verified absence of dose-related functionality

A search of the entire production source code for the terms *insulin*, *bolus*, *dose*, *glucose*,
*CGM*, *correction factor* and *carbohydrate ratio* returns no implementation: no data field, no
function, no parameter, no stored value, and no user-interface element. The only occurrences are
explanatory comments, which are addressed in Section 5.

### 2.4 Data held

Just the Carbs stores product information and user preferences on the device only. It holds no patient
data of any kind: no identity, no account, no physiological measurement, no health history, and no
record of what the user has eaten. There is no account system, no cloud synchronisation, and device
backup is disabled.

---

## 3. Assessment against MDR Article 2(1)

MDR Article 2(1) defines a medical device by reference to the manufacturer's intended purpose being
one or more specified medical purposes.

| Medical purpose under Article 2(1) | Claimed by Just the Carbs? | Basis |
|---|---|---|
| Diagnosis | No | The application produces no diagnostic output |
| Prevention | No | No preventive claim is made; the product is not marketed against any disease |
| Monitoring | No | No physiological data is stored and no health parameter is tracked over time. The application is deliberately not a food diary: it provides no daily totals, no dated meal history and no trends |
| Prediction or prognosis | No | No forward-looking or risk-related output is computed |
| Treatment or alleviation | No | No intervention is recommended. No portion, food or dose is suggested |

**Assessment.** The intended purpose stated in Section 1 is not a medical purpose within the
meaning of Article 2(1). Just the Carbs reports a property of a food product — the same property printed
on the packaging — for a portion size selected by the user.

Nutritional information concerning packaged food is not, of itself, medical information. The
carbohydrate content declared on a food package is a food-labelling particular governed by
Regulation (EU) No 1169/2011 on the provision of food information to consumers. Reading that
declared figure and multiplying it by a portion size does not transform it into medical
information.

---

## 4. Assessment against MDCG 2019-11 rev.1

The guidance relied upon is **MDCG 2019-11 rev.1, *Guidance on Qualification and Classification of
Software in Regulation (EU) 2017/745 – MDR and Regulation (EU) 2017/746 – IVDR*, published June
2025**, confirmed as the current revision at the date of this assessment.

MDCG 2019-11 asks, in substance, whether software performs an action on data beyond storage,
archival, communication or simple search, and whether that action is for the benefit of an
individual patient for a medical purpose.

**On the action performed.** Just the Carbs performs a single multiplication and a division by one
hundred, applied to a value the user has supplied or confirmed. This is arithmetic on a
food-labelling figure. The application applies no algorithm, no model, no patient-specific
parameter and no interpretation. The output contains no information not already present in the
inputs.

**On benefit to an individual patient for a medical purpose.** The intended purpose in Section 1
identifies no patient population and no disease. The application contains no feature specific to
any condition — a statement of intent corroborated by the implementation (Section 2.3), not merely
asserted.

**On the clarification introduced by revision 1.** Revision 1 clarifies that software processing
health information falls within the scope of the MDR only where it directly serves a medical
purpose. Just the Carbs processes food product information rather than health information relating to a
person, and holds no patient data at all (Section 2.4).

**Assessment.** Just the Carbs does not meet the qualification criteria for medical device software. In
function it is comparable to a kitchen scale or a unit converter rather than to clinical software.

---

## 5. Adjacency to insulin dosing

This section states the strongest argument against the conclusion of this assessment, and the
manufacturer's response to it.

### 5.1 The argument

Some users of a carbohydrate calculator have type 1 diabetes and will enter the resulting figure
into a separate bolus calculator. The application's source code acknowledges this destination in
explanatory comments, and two design decisions were made with it in mind: carbohydrate values are
held as exact decimals rather than floating-point numbers, and the value placed on the clipboard is
formatted with a decimal point rather than the local decimal separator, so that an application
expecting `31.3` does not misread the Dutch `31,3`.

### 5.2 Origin of those design decisions

The manufacturer is himself an insulin pump user who uses a separate bolus calculator, and
developed Just the Carbs in the first instance because he wanted a faster way to obtain a carbohydrate
figure. The design decisions described above derive from that first-hand knowledge: such figures
are frequently transcribed by hand, and a comma decimal separator is misread by software expecting
a decimal point.

This is recorded rather than omitted, because it is the truthful explanation for those decisions.
Its regulatory significance is nevertheless nil, for the following reasons.

**Intended purpose is a property of the product, not of its author.** Qualification under the MDR
turns on the purpose the manufacturer assigns to the product and by which it is marketed, not on
the manufacturer's own health or personal motivation for creating it. A developer with diabetes who
builds a general-purpose calculator has built a general-purpose calculator.

**The knowledge produced a quality decision, not a clinical one.** Awareness that figures are
transcribed by hand led to formatting that resists transcription error. It did not lead to any dose
logic, any patient parameter, or any feature specific to diabetes; the source code confirms that
none exists.

**No feature serves one group of users and not another.** The application contains nothing that a
person with diabetes requires and a person tracking carbohydrate intake for sport or weight
management does not. Every function serves both identically, and there is no condition-specific
code path.

### 5.3 Response on the adjacency itself

**Foreseeable downstream use by a third party is not intended purpose.** Qualification turns on the
purpose assigned by the manufacturer. Kitchen scales, food packaging and pocket calculators are all
used by people with diabetes in precisely this way, and none qualifies as a medical device for that
reason. Were foreseeable use by a patient sufficient to qualify a product, food packaging itself
would qualify.

**Just the Carbs performs no part of a dose calculation.** It applies no insulin-to-carbohydrate ratio,
no correction factor, no insulin-on-board calculation and no patient parameter. It provides no
interface to any dosing application: no programming interface, no integration, no data export and
no automation. Any transfer of a figure is performed by a person, by hand, into a separate
application of that person's choosing.

**The design decisions concerned improve accuracy; they do not add clinical function.** Formatting
a number so that it cannot be misread, and displaying an exact value rather than a rounded one,
reduce transcription error. A decision that makes a figure harder to get wrong is a quality
measure, not the assumption of a medical purpose.

**The application neither modifies nor interprets the figure.** It reports a food fact. What the
user subsequently does with that figure lies outside both the software and its intended purpose.

### 5.4 Residual uncertainty

The manufacturer records that this is the element of the assessment on which a competent authority
could most plausibly reach a different view, particularly were Just the Carbs to be marketed toward
diabetes management. The constraints in Section 7 exist for that reason and are binding. This
residual uncertainty is accepted knowingly, having been identified rather than overlooked.

---

## 6. Risk-reduction measures implemented

Independently of qualification, the safety-relevant paths of the application are conservative.
These measures are implemented and covered by automated tests. They are recorded as evidence of
diligent development and do not substitute for the assessment in Sections 3 to 5.

| Measure | Effect |
|---|---|
| Text recognised from a nutrition label is never accepted automatically | Every reading is confirmed by the user before use |
| Ambiguous label readings are never resolved by the application | Where two plausible values are found, the user is asked |
| The "of which sugars" sub-line is never read as total carbohydrate | Prevents systematic under-reporting |
| A user-verified value is never silently overwritten by remote data | Verified values persist |
| An open calculation is never altered by a background data refresh | The figure being worked with cannot change beneath the user |
| No value is displayed where confidence is insufficient | The application reports the value as unavailable |
| Millilitres are never converted to grams | No density is assumed for any product |
| The displayed decimal and the whole-gram figure are derived independently | No double rounding |
| Carbohydrate values are stored as exact decimals, never as floating-point numbers | No accumulated representation error |

Verification status at the date of assessment: 225 automated unit tests and 89 automated
instrumented tests, all passing; static analysis clean.

---

## 7. Conditions on which this conclusion depends

**The conclusion in Section 8 is valid only while every condition in this section holds. A breach
of any condition requires this assessment to be repeated before further distribution.**

### 7.1 Constraints on marketing

Just the Carbs must not:

- be described as being for diabetes, diabetes management, or blood glucose control;
- be described using the words *recommend*, *advise*, *prescribe*, *dose*, *bolus*, or *therapy*;
- claim to diagnose, treat, prevent, monitor, predict or alleviate any disease;
- claim clinical validation, medical approval, or regulatory clearance;
- bear a CE mark;
- state or imply affiliation with any manufacturer of medical devices;
- use disease-related keywords in store-listing metadata.

**The manufacturer's personal circumstances must not appear in published material.** The
manufacturer is an insulin pump user. A statement to the effect of *"as a pump user, I built this
because…"* would be true and would be the most natural thing to write, but it would inform every
reader, in writing and in the first place a regulator would look, that the product is intended for
diabetes dosing. Such a statement would alter the intended purpose recorded in Section 1.

This is not a requirement to conceal anything. It is that personal use is not a product claim and
has no place in the store listing, in replies to user reviews, in the application's About screen,
in release notes, in screenshots, or in any other material published in the product's name.

### 7.2 Changes requiring reassessment

| Change | Reason |
|---|---|
| Any insulin, dose, ratio, correction factor or insulin-on-board concept | Direct therapeutic function |
| Any blood glucose value, entry, import or interpretation | Physiological data processed for a medical purpose |
| Integration with a pump, continuous glucose monitor, or dosing application, by any means | Removes the human step and makes the application part of a dosing chain |
| Recommending portions, foods or meals | Advice rather than arithmetic |
| Any prediction, trend, or personalised algorithm | Prediction or prognosis under Article 2(1) |
| Marketing that identifies diabetes as the product's purpose | Alters the intended purpose itself |

Each of these changes would alter the intended purpose, which is the basis on which qualification
turns. Their apparent individual modesty is not a reason to treat them as minor.

---

## 8. Conclusion

**Just the Carbs, in the version identified on the first page of this document, is not a medical device
within the meaning of Article 2(1) of Regulation (EU) 2017/745, and is not an accessory to a
medical device.**

This conclusion is reached on the following basis:

1. The intended purpose assigned to the product is not a medical purpose (Section 3).
2. The product does not meet the qualification criteria for medical device software set out in
   MDCG 2019-11 rev.1 (Section 4).
3. The product performs arithmetic on a food-labelling figure and processes no patient health data
   (Sections 2 and 4).
4. Foreseeable use of the product's output by a third-party dosing application is not an intended
   purpose assigned by the manufacturer (Section 5).

**This conclusion is conditional upon the constraints in Section 7 continuing to be observed.**

---

## 9. Declaration

I confirm that I have performed the assessment recorded in this document, that the intended purpose
stated in Section 1 is the purpose I assign to the product and by which it will be marketed, and
that the facts recorded in Sections 2 and 6 are accurate to the best of my knowledge.

I understand that this conclusion depends on the conditions in Section 7, and that a change to the
product's function or marketing may require this assessment to be repeated.

<br>

| | |
|---|---|
| **Name** | ............................................................ |
| **Role** | Manufacturer |
| **Date** | ............................................................ |
| **Signature** | ............................................................ |

<br>

**Independent review** *(complete if obtained)*

| | |
|---|---|
| Reviewed by | ............................................................ |
| Firm | ............................................................ |
| Date | ............................................................ |
| Outcome | ☐ Conclusion confirmed ☐ Conclusion qualified ☐ Conclusion not supported |

---

## Annex A — Matters outstanding at the date of signature

The following are recorded as outstanding. They do not affect the conclusion in Section 8 in
respect of the European Union, but must be addressed before the corresponding step is taken.

| # | Matter | Required before |
|---|---|---|
| A.1 | **Markets outside the European Union have not been assessed.** The United Kingdom (MHRA) and the United States (FDA) apply diverging requirements and require separate assessment. Distribution should be restricted to the European Union until this is completed | Distribution outside the EU |
| A.2 | Confirmation that MDCG 2019-11 remains at revision 1 | Signature |
| A.3 | Review of MDCG 2025-4, *Safe making available of medical device software apps on online platforms* (June 2025) | Publication to an app store |
| A.4 | Assessment against the Google Play Health Apps policy, whose definition of a health application is broader than the MDR definition of a medical device and is not settled by this assessment | Publication to Google Play |
| A.5 | Review of the final store listing against the constraints in Section 7.1 | Publication |
| A.6 | Independent review by a consultant specialising in medical device software under the MDR, recommended in view of the matter discussed in Section 5 | Recommended before publication |

---

*End of document.*
