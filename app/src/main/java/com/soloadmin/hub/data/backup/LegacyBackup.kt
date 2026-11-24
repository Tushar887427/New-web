package com.soloadmin.hub.data.backup

/**
 * Backup envelope constants shared by the encoder, the decoder and the import UI.
 *
 * Two dialects are accepted by the importer:
 *  1. The raw object the legacy HTML app writes to `localStorage['solo_gov_office_hub_v6']`
 *     (what its "Export Backup (JSON)" button downloads) — no envelope at all.
 *  2. This app's own backup envelope (`format` = [FORMAT_NATIVE], `version` = [NATIVE_VERSION]).
 *
 * The per-record shapes are read by [BackupCodec] with hand-written traversal rather than
 * generated serializers: partial or slightly malformed localStorage dumps are common, and one odd
 * field must never reject a whole file.
 */
object BackupPayload {

    /** Marker written by this app; absent in legacy exports. */
    const val FORMAT_NATIVE = "solo-admin-backup"

    /** Envelope version for native backups. */
    const val NATIVE_VERSION = 2

    /** Key used by the legacy app in localStorage; shown in the import instructions. */
    const val LEGACY_STORAGE_KEY = "solo_gov_office_hub_v6"
}
