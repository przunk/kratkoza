# Google Play release checklist

Follow these steps in order when preparing and publishing a release to Google Play.

## 1. Prerequisites and Store Presence

- [ ] Confirm developer identity and public contact details in **Play Console → Settings → Developer account**.
- [ ] Confirm `com.przunk.kratkoza` package registration.
- [ ] In **Grow users → Store presence → Store settings**:
  - Category: Games → Puzzle
  - Support email entered
- [ ] In **Grow users → Store presence → Main store listing**:
  - English (`en-US`): paste from `store/listing/en-US/` (`title.txt`, `short-description.txt`, `full-description.txt`).
  - Polish (`pl-PL`): add language and paste from `store/listing/pl-PL/`.
  - App icon: upload `store/graphics/play-store-icon-512.png`.
  - Feature graphic: upload `store/graphics/feature-graphic-1024x500.png`.
  - Phone screenshots: upload `store/graphics/*-store.png` (padded to 1080px wide).
- [ ] Complete **Policy and programs → App content**:
  - Target audience (13+)
  - Content rating questionnaire (IARC)
  - Data safety form (see `data-safety.md`)
  - Privacy policy URL entered (pointing to hosted `store/privacy-policy.md`)

## 2. Checks, version, tag and bundle — `release.sh`

Steps 2 and 3 of the old checklist are one command now (see `docs/BUILD.md`, "Wydanie"):

- [ ] On `develop` (or `hotfix/<name>`), with a clean tree: `./scripts/release.sh X.Y.Z --check`.
- [ ] Then `./scripts/release.sh X.Y.Z`. It runs the full suite and lint, sets `versionName`,
      fast-forwards `master`, tags `vX.Y.Z`, builds the bundle from the tag (asking for the keystore
      passwords), and records size, SHA-256 and signer in `store/play-console/releases.md`.
- [ ] If it stops, read what it printed: completed steps are not rolled back.

## 3. Bundle

- [ ] Use the `.aab` path printed at the end of `release.sh`; the SHA-256 Play shows must match.

## 4. Google Play Console Upload

- [ ] Navigate to **Release → Testing → Closed testing** (or Internal testing).
- [ ] Create a new release.
- [ ] Upload the generated `dist/kratkoza-<versionName>-<versionCode>-<sha>.aab`.
- [ ] Enter release notes:
  - English: copy from `store/listing/en-US/release-notes/default.txt`.
  - Polish: copy from `store/listing/pl-PL/release-notes/default.txt`.
- [ ] Review release and roll out to closed testing testers.
