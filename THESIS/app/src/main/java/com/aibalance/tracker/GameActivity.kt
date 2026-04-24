package com.aibalance.tracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import kotlin.random.Random

class GameActivity : AppCompatActivity() {

    private val words = listOf("BALAI", "ALERT", "FOCUS", "PAUSE", "SMART")
    private var roundIndex = 0

    private lateinit var tileButtons: List<Button>
    private lateinit var slotViews: List<TextView>
    private lateinit var cardReward: MaterialCardView
    private lateinit var tvSubtitle: TextView

    private val selectedChars = mutableListOf<Char>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        tileButtons = listOf(
            findViewById(R.id.btnTile1),
            findViewById(R.id.btnTile2),
            findViewById(R.id.btnTile3),
            findViewById(R.id.btnTile4),
            findViewById(R.id.btnTile5)
        )
        slotViews = listOf(
            findViewById(R.id.tvSlot1),
            findViewById(R.id.tvSlot2),
            findViewById(R.id.tvSlot3),
            findViewById(R.id.tvSlot4),
            findViewById(R.id.tvSlot5)
        )

        cardReward = findViewById(R.id.cardReward)
        tvSubtitle = findViewById(R.id.tvGameSubtitle)

        val btnClear = findViewById<Button>(R.id.btnClear)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)

        btnClear.setOnClickListener { resetSelectionForCurrentWord() }
        btnSubmit.setOnClickListener { submitWord() }

        loadRound()
    }

    private fun loadRound() {
        val target = words[roundIndex]
        val shuffled = target.toList().shuffled(Random(System.currentTimeMillis()))

        tvSubtitle.text = getString(R.string.game_subtitle_format, roundIndex + 1)
        cardReward.visibility = android.view.View.GONE
        selectedChars.clear()
        updateSlots()

        tileButtons.forEachIndexed { index, button ->
            button.text = shuffled[index].toString()
            button.isEnabled = true
            button.setOnClickListener {
                if (selectedChars.size < 5) {
                    selectedChars.add(button.text.first())
                    button.isEnabled = false
                    updateSlots()
                }
            }
        }
    }

    private fun updateSlots() {
        slotViews.forEachIndexed { index, tv ->
            tv.text = if (index < selectedChars.size) selectedChars[index].toString() else "_"
        }
    }

    private fun resetSelectionForCurrentWord() {
        selectedChars.clear()
        updateSlots()
        tileButtons.forEach { it.isEnabled = true }
    }

    private fun submitWord() {
        if (selectedChars.size != 5) return

        val answer = selectedChars.joinToString("")
        val target = words[roundIndex]
        if (answer == target) {
            roundIndex += 1
            if (roundIndex >= words.size) {
                // Completing all rounds awards bonus time back to the user.
                cardReward.visibility = android.view.View.VISIBLE
                val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                val solved = prefs.getInt(SettingsActivity.KEY_WORDS_SOLVED, 0)
                val streak = prefs.getInt(SettingsActivity.KEY_STREAK_DAYS, 0)
                prefs.edit()
                    .putInt(SettingsActivity.KEY_WORDS_SOLVED, solved + words.size)
                    .putInt(SettingsActivity.KEY_STREAK_DAYS, streak + 1)
                    .apply()

                setResult(RESULT_OK, Intent().putExtra(CooldownActivity.EXTRA_BONUS_MINUTES, 3))
                finish()
            } else {
                loadRound()
            }
        } else {
            resetSelectionForCurrentWord()
        }
    }
}
