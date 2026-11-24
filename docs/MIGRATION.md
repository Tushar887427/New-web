# Moving data from the HTML app

Your existing records live in the browser's `localStorage` under the key
`solo_gov_office_hub_v6`. The native app reads exactly that shape, so no manual reformatting is needed.

## Step 1 — export the JSON from the HTML app

Open the old HTML page in the browser you used for it, then use its **Export Backup (JSON)** button.
The browser downloads a file named `gov_office_hub_backup_YYYY-MM-DD.json`.

If that button is gone or you no longer have the page, you can still get the data out by hand:

1. Open the HTML file in the same browser (as a local file it keeps the same storage for that path).
2. Open DevTools (F12) → **Console**.
3. Paste this and press Enter — it downloads the raw stored JSON:

```js
(() => {
  const raw = localStorage.getItem('solo_gov_office_hub_v6') || '{}';
  const blob = new Blob([raw], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'gov_office_hub_backup.json';
  a.click();
  URL.revokeObjectURL(a.href);
})();
```

> If the console prints `undefined` or the downloaded file is `{}`, the browser profile is not the one
> the app used — try the other browser or profile first. Nothing is lost until that file exists.

## Step 2 — get the file onto the phone

Any of these work: email it to yourself, put it in Google Drive, or copy it over USB. Keep it as a
`.json` file.

## Step 3 — import it in the native app

1. Open **Settings → Import from the old HTML app** (or **Backup & restore → Import legacy HTML backup**).
2. Pick the JSON file.
3. The app shows **Review import** with three counts: *Import*, *Update*, *Skip*, plus a list of why
   anything was skipped (invalid dates, invalid amounts, already present, …).
4. Choose how conflicts are handled:
   * **Keep existing** (default) — records already in the app are left untouched. Use this when importing
     twice by accident.
   * **Overwrite** — the incoming version replaces the stored one.
5. Tap **Import now**. Everything is written in a single transaction; if anything fails, nothing is
   changed.

You can also just tap the JSON file in the Files app — the app registers for `application/json` and
opens straight to the import preview.

## What is preserved

| Legacy field | Native destination |
| --- | --- |
| `profile{name,bio,photo,weeklyOff,shiftStart,info}` | Profile record; photo becomes a real file |
| `attendance{date:{status,inTime,outTime,note,leaveType}}` | Attendance table, one row per date |
| `outOnWorkLogs[]` | Official duty table + linked attendance row (`Outstation`) |
| `expenses[]` | Expenses table, `owner` keeps headmaster/personal, `recovered` kept |
| `finances[]` | Pay records; the original `type` string is preserved for export |
| `reminders[]` | Key dates table |
| `tasks[]` | Daily tasks, `done` state kept |
| `colleagues[]` | Colleagues incl. `info` label/value pairs and photos |
| `session` / `workSessions` | Live punch state for the day |

Dates keep their `YYYY-MM-DD` form, amounts keep their exact paise value, and the attendance ↔
official-duty link is rebuilt by date.

**Photos:** the HTML app stored images as base64 data URLs. On import each one is decoded, re-encoded
and written to app-private storage, so deleting the original from your gallery cannot break a record.
The import summary reports how many images were restored.

## Import rules worth knowing

* **No duplicates.** Every record keeps its legacy `id` (or its date, for attendance), so importing the
  same file twice reports the second pass as *already present* instead of doubling your data.
* **Invalid records are skipped, not guessed.** A row with an impossible date or an unreadable amount is
  reported in the skip list rather than being silently changed.
* **Unknown statuses** stay as their legacy text; they are counted as leave only when the type matches
  Casual / Earned / Commuted.
* **Nothing is overwritten without your choice** — the confirm step always asks first.

## Native backups after that

From **Settings → Create JSON backup** you can export a single file containing everything, and restore it
on any device with **Backup & restore → Import native backup**. That format is the same JSON with a
`format` marker so the app can tell the two dialects apart.
