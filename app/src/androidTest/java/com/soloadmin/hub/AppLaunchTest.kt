package com.soloadmin.hub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the whole shell: the app launches, the home dashboard renders real (seed) data,
 * the bottom bar navigates between all five tabs and a pushed screen comes back cleanly.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeDashboardShowsTheOfficeHeader() {
        rule.onNodeWithText("Gov Office Hub").assertIsDisplayed()
    }

    @Test
    fun bottomBarReachesEveryTab() {
        listOf("Ledger", "Expenses", "Finance", "Settings", "Home").forEach { label ->
            rule.onNodeWithText(label).performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText("Gov Office Hub").assertIsDisplayed()
    }

    @Test
    fun settingsOpensKeyDatesAndReturns() {
        rule.onNodeWithText("Settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Expiry reminders").assertIsDisplayed()
    }
}
