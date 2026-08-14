# Privacy policy — CarbScan

**Last updated:** 2026-08-14
**Contact:** albinogorillassupport@gmail.com

This describes what the app actually does, verified against the source code (§48).

---

## In one paragraph

CarbScan has no account, no advertising, and no developer-configured analytics service. Your products, portions,
favourites and verified values stay on your device. **It is not true that no data leaves your
device:** when you scan a barcode the app has never seen, it sends that barcode to Open Food Facts
to look up the product, and — when that product has a photo — requests the photo too. If you use
**Search by name**, the words you type are also sent to Open Food Facts, because that is how the
search works. Those three requests are the only ones CarbScan itself makes.

## What stays on your device

Stored locally in an app-private database, readable by no other app:

- Barcodes and product names you have scanned or entered
- Carbohydrate values, measurement basis, package sizes
- Your verified values, and the online value they replaced
- Countable portion units (e.g. "1 slice = 36 g"), whether suggested by Open Food Facts or
  entered by you, and whether you have checked them against the package
- The last portion or countable-unit count you used per product, and when you last used it
- Portions you have used more than once for a product, so they can be offered as shortcuts. These
  are kept per product; the app has no way to assemble them into a picture of what you eat overall
- The items in your **current meal**, if you are using that feature. There is only ever one meal
  and it has no name and no date. It stays until you clear it — including across a restart, so you
  do not lose a half-built plate by switching apps — but the app has no way to store a *past* meal,
  so it holds no record of meals you have eaten
- Favourites, and your settings (theme, result style, haptics)

There is no login, no cloud profile, and no synchronisation. Deleting the app deletes all of it.

## What leaves your device

**Three things: a barcode lookup, a product photo, and — only if you use it — a search you typed.**

| Field | Value |
|---|---|
| Recipient | Open Food Facts, for product data/search (`world.openfoodfacts.org`) and product photos (`images.openfoodfacts.org` or `static.openfoodfacts.org`) |
| When | Product data: only when you scan or enter a barcode not already saved on your device. Photos: only when that lookup returns a product that has a photo, and only from Open Food Facts' own image host — the app checks this and will not load an image from any other address. Search text: only while you are typing on the search screen, which is reached from a failed lookup and never opened on its own |
| What is sent | The barcode number and a User-Agent identifying the app and version (product lookup); a standard image request with no additional data attached (photo); the search words themselves (search) |
| What CarbScan does not attach to these requests | An account/user ID, advertising ID, your portions, results, history, or verified values. The recipient still receives normal network metadata such as IP address |
| Transport | HTTPS only. Cleartext traffic is disabled at the platform level |

**About the search text specifically.** Unlike a barcode, this is text you typed, so it deserves
naming rather than folding into "product lookups". It is sent as you type (after a short pause) so
results can appear live, it is sent with no app-supplied user identifier, and CarbScan does not
store it on your device or on a CarbScan server — the app keeps no search history and has no server.
Open Food Facts' own retention of search requests was not established by a primary source in the
2026-08-14 review. If you do not use Search by name, nothing of this kind is sent by CarbScan.

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

No advertising SDK. No advertising ID. No developer-configured analytics or crash-reporting
service. No social login. No Health Connect. No Bluetooth. No location permission or location
feature. CarbScan does not sell data or share it for advertising. ML Kit's SDK metrics are described
separately below.

## One thing we want to be precise about

CarbScan uses **Google ML Kit** for barcode and label recognition. Google states that camera input
and recognition results are processed on-device and are not sent to Google. Google also states that
the SDK collects device and app information, per-installation identifiers, performance metrics,
API configuration, feature events and errors for diagnostics and usage analytics. Google says this
data is encrypted in transit and not transferred to third parties. See Google's
[ML Kit disclosure](https://developers.google.com/ml-kit/android-data-disclosure) and
[Terms & Privacy](https://developers.google.com/ml-kit/terms), checked 2026-08-14.

The SDK's `com.google.android.datatransport` component cannot be removed without breaking barcode
scanning (verified in an emulator experiment) and adds `ACCESS_NETWORK_STATE`. CarbScan does not
control Google's retention of those metrics. The proposed Play declarations are in
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

CarbScan is not directed at children. It has no child-oriented design, account, advertising, or
public user content. The same Open Food Facts requests and ML Kit collection described above apply
regardless of a user's age; no separate claim of zero collection is made for children.

## Changes

Material changes to this policy will be reflected here with an updated date, published alongside the
app release that introduces them.

## Contact

**albinogorillassupport@gmail.com**

---

*Product data is provided by Open Food Facts and used under the Open Database License (ODbL).
Product photos are provided by Open Food Facts under the Creative Commons Attribution-ShareAlike
licence (CC BY-SA) — a separate licence from the database itself; see
[third-party-notices.md](third-party-notices.md). CarbScan is not affiliated with, endorsed by, or
connected to any medical device manufacturer.*
