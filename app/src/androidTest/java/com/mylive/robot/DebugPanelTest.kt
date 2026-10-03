package com.mylive.robot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test

class DebugPanelTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun twoFingers(holdMs: Long, thirdFinger: Boolean = false) {
        rule.onNodeWithTag("robot-root").performTouchInput {
            down(0, Offset(200f, 200f))
            down(1, Offset(400f, 200f))
            if (thirdFinger) down(2, Offset(300f, 400f))
            advanceEventTime(holdMs)
            move()
            up(0)
            up(1)
            if (thirdFinger) up(2)
        }
    }

    @Test fun twoFingerHoldOfOneSecondOpensThePanel() {
        rule.mainClock.autoAdvance = false
        twoFingers(1300)
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag("debug-panel").assertIsDisplayed()
    }

    @Test fun shortTwoFingerTouchDoesNotOpenIt() {
        rule.mainClock.autoAdvance = false
        twoFingers(400)
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithTag("debug-panel").assertDoesNotExist()
    }

    @Test fun threeFingersDoNotOpenIt() {
        rule.mainClock.autoAdvance = false
        twoFingers(1300, thirdFinger = true)
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithTag("debug-panel").assertDoesNotExist()
    }

    @Test fun panelButtonsWorkAndCloseHidesIt() {
        rule.mainClock.autoAdvance = false
        twoFingers(1300)
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("Sad").performClick()
        rule.onNodeWithText("Yawn").performClick()
        rule.onNodeWithText("Hide panel").performClick()
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag("debug-panel").assertDoesNotExist()
    }
}
