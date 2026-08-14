# Privacy policy — CarbScan

**Last updated:** 2026-08-13
**Contact:** REPLACE_ME@example.com *(owner must replace before publication)*

This describes what the app actually does, verified against the source code (§48).

---

## In one paragraph

CarbScan has no account, no advertising, and no analytics we added. Your products, portions,
favourites and verified values stay on your device. **It is not true that no data leaves your
device:** when you scan a barcode the app has never seen, it sends that barcode to Open Food Facts
to look up the product, and — when that product has a photo — requests the photo too. Those two
requests are the only ones CarbScan itself makes.

## What stays on your device

Stored locally in an app-private database, readable by no other app:

- Barcodes and product names you have scanned or entered
- Carbohydrate values, measurement basis, package sizes
- Your verified values, and the online value they replaced
- Countable portion units (e.g. "1 slice = 36 g"), whether suggested by Open Food Facts or
  entered by you, and whether you have checked them against the package
- The last portion or countable-unit count you used per product, and when you last used it
- Favourites, and your settings (theme, result style, haptics)

There is no login, no cloud profile, and no synchronisation. Deleting the app deletes all of it.

## What leaves your device

**Two things: a barcode lookup, and — only when the product has one — its photo.**

| Field | Value |
|---|---|
| Recipient | Open Food Facts, for product data (`world.openfoodfacts.org`) and product photos (`images.openfoodfacts.org`) |
| When | Product data: only when you scan or enter a barcode not already saved on your device. Photos: only when that lookup returns a product that has a photo, and only from Open Food Facts' own image host — the app checks this and will not load an image from any other address |
| What is sent | The barcode number and a User-Agent identifying the app and version (product lookup); a standard image request with no additional data attached (photo) |
| What is *not* sent | Any identifier for you or your device, your portions, your results, your history, your verified values |
| Transport | HTTPS only. Cleartext traffic is disabled at the platform level |

Open Food Facts is an independent organisation and will receive your IP address as an unavoidable
part of any internet request. Their handling of that is governed by their own privacy policy.

A cached product — and an already-loaded photo — is served without a new network request, so
re-using a product typically sends nothing.

## Camera

The camera is used for two things: reading barcodes, and reading nutrition labels.

- Processing is **on-device**. Frames are analysed in memory and discarded immediately.
- **No photograph is saved.** No image is uploaded to us or anyone else.
- The app requests no storage or photo-library permission, so it cannot see your gallery.
- Camera access is asked for when you first open the scanner, not at launch, and you can decline —
  manual entry always remains available.

## What CarbScan does not contain

No advertising SDK. No advertising ID. No analytics SDK we added. No crash-reporting SDK. No
third-party tracker. No social login. No Health Connect. No Bluetooth. No location access. Your data
is never sold, and never shared for advertising.

## One thing we want to be precise about

CarbScan uses **Google ML Kit** for barcode and label recognition. ML Kit runs its recognition
on-device, but it ships with a Google data-transport component (`com.google.android.datatransport`)
that we did not add, do not control, and **cannot remove without breaking barcode scanning entirely** (we tested this), and which adds the `ACCESS_NETWORK_STATE` permission. Google
may receive diagnostic information about ML Kit usage through it. We disclose this rather than claim
a purity we cannot demonstrate. See
[google-play-data-safety.md](google-play-data-safety.md).

## Backup

Android backup is **disabled** (`allowBackup="false"`). Your food history is never copied to your
Google account. The trade-off: your saved products do not transfer to a new phone.

## Your control

| You want to | Do this |
|---|---|
| Remove usage history, keep verified products | Settings → Clear recent history |
| Delete everything the app has stored | Settings → Clear saved products |
| Remove all data permanently | Uninstall the app |
| Stop all network requests | Use the app offline; saved products keep working |

There is no account to delete, because there is no account.

## Children

CarbScan is not directed at children and collects nothing that could identify anyone, of any age.

## Changes

Material changes to this policy will be reflected here with an updated date, published alongside the
app release that introduces them.

## Contact

**REPLACE_ME@example.com** *(owner must replace before publication)*

---

*Product data is provided by Open Food Facts and used under the Open Database License (ODbL).
Product photos are provided by Open Food Facts under the Creative Commons Attribution-ShareAlike
licence (CC BY-SA) — a separate licence from the database itself; see
[third-party-notices.md](third-party-notices.md). CarbScan is not affiliated with, endorsed by, or
connected to any medical device manufacturer.*
