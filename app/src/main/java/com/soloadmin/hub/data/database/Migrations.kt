package com.soloadmin.hub.data.database

import androidx.room.migration.Migration

/**
 * Version 1 is the initial schema. Future schema changes must be added here as explicit
 * [Migration] objects — destructive fallbacks are deliberately not used for upgrades, so a
 * user's records can never be dropped by a routine app update.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
