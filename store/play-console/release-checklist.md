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

## 2. Code Quality and Checks

- [ ] Run full test suite: `./scripts/test-kratkoza.sh`
- [ ] Run lint: `./scripts/lint-kratkoza.sh`
- [ ] Run store metadata validation: `./scripts/check-store-metadata.sh`
- [ ] Verify `versionCode` and `versionName` in `app/build.gradle.kts`
- [ ] Check git status is clean and create release tag `v<versionName>` (e.g. `v0.32.0`)

## 3. Build Release Bundle

- [ ] Run `./scripts/build-kratkoza-bundle.sh` with the release keystore credentials.
- [ ] Ensure the script verifies the JAR signature and rejects any debug key.
- [ ] Record the resulting `.aab` in `dist/`, along with its SHA-256 and size in `store/play-console/releases.md`.

## 4. Google Play Console Upload

- [ ] Navigate to **Release → Testing → Closed testing** (or Internal testing).
- [ ] Create a new release.
- [ ] Upload the generated `dist/kratkoza-<versionName>-<versionCode>.aab`.
- [ ] Enter release notes:
  - English: copy from `store/listing/en-US/release-notes/default.txt`.
  - Polish: copy from `store/listing/pl-PL/release-notes/default.txt`.
- [ ] Review release and roll out to closed testing testers.
