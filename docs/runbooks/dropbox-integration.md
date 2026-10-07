# Runbook: Shipping the Dropbox storage integration

_Last updated: 2026-10-06 · Covers `sar-srv` PR #88, `sar-kmp` PR #24, and the `sar-infra` follow-up._

The code is written and unit-tested, but **nothing has run against real Dropbox yet** — there is no
Dropbox app, so no app key or secret exists. This runbook is the path from here to production. Each
step says who does it: **You** (needs your accounts, your decisions, or a device in your hand) or
**Claude** (code, Terraform, docs — ask for it by step number).

Background: [contract](../../../research/dropbox-backend-contract.md) ·
[setup, security & legal notes](../../../research/dropbox-integration-and-legal.md) (both in the
top-level `sar/research/` directory).

## Checklist

- [x] **Phase 1 — Decisions** (You)
  - [x] 1.1 Pick the app name — `sar01`
  - [x] 1.2 Pick the default recipe subfolder (or none) — `_save_a_recipe`
- [ ] **Phase 2 — Register the Dropbox app** (You)
  - [x] 2.1 Create the app (Scoped access, App folder)
  - [ ] 2.2 Enable the two file scopes — _confirm_
  - [ ] 2.3 Register the redirect URI — _confirm_
  - [x] 2.4 Copy the app key and app secret
- [ ] **Phase 3 — Wire the values in**
  - [ ] 3.1 Store the app secret in Secret Manager (You) — **must be done before the `sar-infra` PR is applied**
  - [x] 3.2 Give Claude the non-secret values (You)
  - [x] 3.3 Terraform: secret + Cloud Run env vars in `sar-infra` (Claude) — PR open, not merged
  - [x] 3.4 Set `DROPBOX_APP_KEY` in the KMP build config (Claude) — in `sar-kmp` #24
- [ ] **Phase 4 — Merge and deploy**
  - [ ] 4.1 Review and merge `sar-infra`, then `sar-srv` #88 (You)
  - [ ] 4.2 Confirm the native image starts on Cloud Run (Claude, via logs)
  - [ ] 4.3 Merge `sar-kmp` #24 and build a dev APK (You)
- [ ] **Phase 5 — End-to-end verification on a real device** (You drive, Claude reads logs)
- [ ] **Phase 6 — Before real users**
  - [ ] 6.1 Amend the privacy policy (You; Claude can draft)
  - [ ] 6.2 Add Dropbox to incident response / sub-processor list (You)
  - [ ] 6.3 Apply for Dropbox production status (You)

---

## Phase 1 — Decisions (You)

### 1.1 App name
This is not cosmetic. With App-folder access Dropbox creates `/Apps/<app name>/` in each user's
Dropbox, shows the name on the consent screen, and it cannot be changed casually later.

- Must not contain "Dropbox", must not start with "drop", must not imply partnership.
- **Decided: `sar01`.** Users will see "sar01" on the Dropbox consent screen and as the folder
  `/Apps/sar01` in their Dropbox. The KMP picker copy (`StorageProviderRegistry.kt`) and the backend
  link setting (`COOKBOOK_DROPBOX_APP_FOLDER_NAME`) both use this value and must match it exactly.

### 1.2 Default recipe subfolder
`COOKBOOK_DROPBOX_FOLDER_NAME`. Empty (the default) puts recipes directly in the app folder,
`/Apps/<app name>/pasta.yaml`. A value such as `recipes` gives `/Apps/<app name>/recipes/pasta.yaml`.
Allowed characters: `A-Z a-z 0-9 . _ -`.

**Decided: `_save_a_recipe`** — recipes are stored at `/Apps/sar01/_save_a_recipe/<name>.yaml`.

## Phase 2 — Register the Dropbox app (You)

Use a **dedicated project Dropbox account**, not your personal one: the account owns the app, the
production-review relationship, and the breach-reporting obligation.

1. **2.1** <https://www.dropbox.com/developers/apps> → **Create app** → *Scoped access* →
   **App folder** (not Full Dropbox — the code assumes app-folder-relative paths) → name from 1.1.
2. **2.2** *Permissions* tab → enable `files.content.write` and `files.content.read` → **Submit**.
   Nothing else is needed. (Scope changes only apply to tokens issued afterwards.)
3. **2.3** *Settings* tab → *OAuth 2 → Redirect URIs* → add exactly `sar://dropbox-callback`.
   It is compared character-for-character at both the authorize and the token-exchange step.
4. **2.4** *Settings* tab → copy the **App key** (public) and **App secret** (private).
   Leave "Allow public clients (Implicit Grant & PKCE)" at its default.

## Phase 3 — Wire the values in

### 3.1 Store the app secret (You)
The secret must exist *with a value* before any Terraform or deploy references it — Cloud Run
refuses to start a revision whose secret has no version, which would break production deploys.

```bash
# macOS: copy the app secret to the clipboard first. This keeps it out of shell history
# and avoids a trailing newline ending up in the secret.
printf '%s' "$(pbpaste)" | gcloud secrets create dropbox-app-secret \
  --replication-policy=automatic --data-file=-
```

Never paste the app secret into a chat, a commit, or the KMP client.

### 3.2 Give Claude the non-secret values (You)
- the **App key**
- the **exact app name** (1.1)
- the subfolder decision (1.2)

### 3.3 Terraform in `sar-infra` (Claude)
Mirrors the existing Google OAuth secret in `cloudrun.tf`:
- declare `dropbox-app-secret` (with an `import` block so it adopts the secret from 3.1) and expose it
  to Cloud Run as `COOKBOOK_DROPBOX_APP_SECRET`;
- plain env vars `COOKBOOK_DROPBOX_APP_KEY`, `COOKBOOK_DROPBOX_APP_FOLDER_NAME` (= app name) and, if
  you chose one, `COOKBOOK_DROPBOX_FOLDER_NAME`;
- update `sar-infra` README/STATUS and `docs/firestore-schema.md` (`storage.type: "dropbox"`).

Until this lands the backend runs with placeholder credentials and
`POST /v1/storage/dropbox/connect` answers 400 — harmless, nothing else is affected.

### 3.4 KMP build config (Claude)
Done in `sar-kmp` #24: `DROPBOX_APP_KEY` is set once in `defaultConfigs` of
`shared/build.gradle.kts` (all flavors use the same Dropbox app), and the picker copy says
`/Apps/sar01`. A blank key makes the app show "Dropbox isn't set up in this build yet".

## Phase 4 — Merge and deploy

1. **4.1 (You)** Merge the `sar-infra` PR first, then `sar-srv` #88. The `sar-srv` deploy workflow
   triggers the Terraform apply.
2. **4.2 (Claude)** Check Cloud Run logs for a clean start. This is the first run of the Dropbox
   classes in the GraalVM native image; no new reflection config is expected, but it is unverified.
3. **4.3 (You)** Merge `sar-kmp` #24 and build:
   `./gradlew :composeApp:assembleDebug -Pbuildkonfig.flavor=dev`

## Phase 5 — End-to-end verification (You drive, Claude reads logs)

Use a **test Dropbox account** on a real Android device. These are the things unit tests could not
prove; tick each one off, and tell Claude which step failed if one does.

| # | Do this | Expect | What it proves |
|---|---------|--------|----------------|
| 1 | Onboarding → Dropbox → approve | "Dropbox connected"; `/Apps/<name>/` appears in Dropbox | Token exchange accepts `client_secret` **and** the PKCE `code_verifier` together |
| 2 | Save a recipe from the app | A `.yaml` file appears in the app folder | Upload, file naming |
| 3 | Save a recipe from the browser extension, click its link | Opens that file on dropbox.com | `driveFileUrl` is a real link |
| 4 | Pull to refresh, open a recipe | List and detail load | `list_folder`, download by `id:` |
| 5 | Delete a file on dropbox.com, open that recipe in the app | "Not found", not a server error | Dropbox 409 `path/not_found` → 404 |
| 6 | Leave it 5+ hours, open the app | Still works with no prompt | Refresh-token flow |
| 7 | Settings → Disconnect | Dropbox *Connected apps* no longer lists the app | Revoke on disconnect |
| 8 | Connect Dropbox, then switch to Google Drive | Dropbox no longer lists the app; Drive recipes show | Revoke on switch |
| 9 | Connect Google Drive, then switch to Dropbox | Google account permissions no longer list the app | Google revoke on switch |
| 10 | Connect Dropbox, then connect Dropbox again | Still works afterwards | Revoking the old Dropbox grant does not kill the new one |
| 11 | Remove the app on dropbox.com, wait 5+ hours, open the app | Recipe calls return 428 and status says not connected — not a 503 (what the app shows depends on its 428 handling) | `invalid_grant` → "not connected" |
| 12 | Tap Dropbox, then close the Dropbox page with the back button | Back on the chooser, no error | Cancel handling |
| 13 | `adb shell am start -a android.intent.action.VIEW -d "sar://dropbox-callback?code=x\&state="` | App comes to the front, nothing else happens | Unsolicited redirects are ignored |

Rows 1, 9 and 10 rest on provider behaviour taken from documentation rather than observed:
that Dropbox accepts a client secret together with a PKCE verifier, that revoking a Google refresh
token removes the grant, and that Dropbox revokes per refresh token. If row 1 fails, the fix is to
drop `client_secret` from the exchange when a verifier is present (`DropboxAuthClient`).

## Phase 6 — Before real users

- **6.1 Privacy policy (You; Claude can draft the wording).** Dropbox's developer terms require one.
  Add that when a user connects Dropbox, recipes are stored in *their* Dropbox at their direction and
  OAuth tokens are stored encrypted to make that possible. It must be live before 6.3.
- **6.2 Compliance (You).** Add Dropbox to the incident-response process (their terms require prompt
  breach reporting) and, for EU users, list Dropbox as a sub-processor.
- **6.3 Production status (You).** A new app is in *Development* mode: 500 linked users at most, and
  once it reaches 50 you have two weeks to be approved or new links are frozen. Apply from the App
  Console (app icon + a description of how the API is used) well before launch.

## Known gaps (not blocking Android)

- **iOS / Web have no Dropbox sign-in launcher.** The picker shows Dropbox as "Not on this device
  yet" there. Separately, the `shared` module does not currently compile for iOS at all
  (`System`/`java.*` used in `commonMain`, e.g. `LocalRecipeDataSource.kt`), which predates this work.
- **Rate limiting is basic.** The Dropbox client retries a 429 twice, waiting up to 5s. Listing
  recipes still downloads each file separately, so a very large library can be slow.
- **A revoked grant is reported, not cleaned up.** The stored connection stays in Firestore until the
  user reconnects or disconnects, so Settings can still say "Dropbox connected" while recipe calls
  return 428.
