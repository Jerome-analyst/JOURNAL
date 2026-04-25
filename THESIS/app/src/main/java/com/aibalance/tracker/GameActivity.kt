package com.aibalance.tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class GameActivity : AppCompatActivity() {

    data class GameWord(val word: String, val category: String, val clueTag: String, val clueText: String)

    private val gameData = listOf(
        GameWord("MULAN", "Movies", "Sounds Like:", "Mow Land"),
        GameWord("ALERT", "System", "Definition:", "A warning signal"),
        GameWord("FOCUS", "Mindset", "Instruction:", "Pay close attention"),
        GameWord("PAUSE", "Action", "Status:", "Temporary stop"),
        GameWord("SMART", "Ability", "Synonym:", "Intelligent")
    )
    
    private var roundIndex = 0
    private var attempts = 0
    private var coins = 360

    private lateinit var slotViews: List<TextView>
    private lateinit var tvLevel: TextView
    private lateinit var tvCoins: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvAttempts: TextView
    private lateinit var tvClueTag: TextView
    private lateinit var tvClueText: TextView

    private val selectedChars = mutableListOf<Char>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        bindViews()
        setupKeyboard()
        
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnBackspace).setOnClickListener {
            if (selectedChars.isNotEmpty()) {
                selectedChars.removeAt(selectedChars.size - 1)
                updateSlots()
            }
        }

        loadRound()
    }

    private fun bindViews() {
        tvLevel = findViewById(R.id.tvLevel)
        tvCoins = findViewById(R.id.tvCoins)
        tvCategory = findViewById(R.id.tvCategoryValue)
        tvAttempts = findViewById(R.id.tvAttemptsValue)
        tvClueTag = findViewById(R.id.tvClueTag)
        tvClueText = findViewById(R.id.tvClueText)

        slotViews = listOf(
            findViewById(R.id.tvSlot1),
            findViewById(R.id.tvSlot2),
            findViewById(R.id.tvSlot3),
            findViewById(R.id.tvSlot4),
            findViewById(R.id.tvSlot5)
        )
    }

    private fun setupKeyboard() {
        val keyboardLayout = findViewById<ViewGroup>(R.id.layoutKeyboard)
        setupKeyboardRecursive(keyboardLayout)
    }

    private fun setupKeyboardRecursive(view: View) {
        if (view is Button && view.id != R.id.btnBackspace) {
            view.setOnClickListener {
                if (selectedChars.size < 5) {
                    selectedChars.add(view.text.first().uppercaseChar())
                    updateSlots()
                    if (selectedChars.size == 5) {
                        checkAnswer()
                    }
                }
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setupKeyboardRecursive(view.getChildAt(i))
            }
        }
    }

    private fun loadRound() {
        val current = gameData[roundIndex]
        tvLevel.text = "Level ${roundIndex + 1}"
        tvCategory.text = current.category
        tvClueTag.text = current.clueTag
        tvClueText.text = current.clueText
        tvAttempts.text = attempts.toString()
        tvCoins.text = coins.toString()
        
        selectedChars.clear()
        updateSlots()
    }

    private fun updateSlots() {
        slotViews.forEachIndexed { index, tv ->
            tv.text = if (index < selectedChars.size) selectedChars[index].toString() else ""
        }
    }

    private fun checkAnswer() {
        val answer = selectedChars.joinToString("")
        val target = gameData[roundIndex].word
        
        if (answer.uppercase() == target.uppercase()) {
            roundIndex++
            coins += 20
            if (roundIndex >= gameData.size) {
                finishGame()
            } else {
                loadRound()
            }
        } else {
            attempts++
            tvAttempts.text = attempts.toString()
            // Clear slots on wrong answer
            selectedChars.clear()
            updateSlots()
        }
    }

    private fun finishGame() {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val solved = prefs.getInt(SettingsActivity.KEY_WORDS_SOLVED, 0)
        val streak = prefs.getInt(SettingsActivity.KEY_STREAK_DAYS, 0)
        prefs.edit()
            .putInt(SettingsActivity.KEY_WORDS_SOLVED, solved + gameData.size)
            .putInt(SettingsActivity.KEY_STREAK_DAYS, streak + 1)
            .apply()

        val resultIntent = Intent()
        resultIntent.putExtra(CooldownActivity.EXTRA_BONUS_MINUTES, 3)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
