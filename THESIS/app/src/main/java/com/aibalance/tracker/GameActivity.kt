package com.aibalance.tracker

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity() {

    data class GameWord(val word: String, val category: String, val clueTag: String, val clueText: String)

    private val wordBank = listOf(
        GameWord("ALERT", "Safety", "Definition:", "A warning signal"),
        GameWord("FOCUS", "Mindset", "Instruction:", "Pay close attention"),
        GameWord("PAUSE", "Action", "Status:", "Temporary stop"),
        GameWord("SMART", "Ability", "Synonym:", "Intelligent"),
        GameWord("BRAVE", "Trait", "Synonym:", "Courageous"),
        GameWord("PLANT", "Nature", "Definition:", "A living organism that grows in soil"),
        GameWord("STORM", "Weather", "Description:", "Heavy rain with thunder"),
        GameWord("TIGER", "Animal", "Description:", "Striped big cat from Asia"),
        GameWord("CHESS", "Games", "Definition:", "A strategy board game with kings and queens"),
        GameWord("BRUSH", "Tools", "Function:", "Used to apply paint or clean"),
        GameWord("FLAME", "Science", "Definition:", "Burning gas that produces light and heat"),
        GameWord("GLOBE", "Geography", "Definition:", "A spherical map of Earth"),
        GameWord("HONEY", "Food", "Source:", "Made by bees from flower nectar"),
        GameWord("INDEX", "Writing", "Definition:", "A list that helps you find information"),
        GameWord("JUDGE", "Law", "Role:", "Person who decides court cases"),
        GameWord("KNEEL", "Action", "Definition:", "To rest on one or both knees"),
        GameWord("LEMON", "Fruit", "Description:", "Sour yellow citrus fruit"),
        GameWord("MAGIC", "Concept", "Definition:", "Mysterious power to do impossible things"),
        GameWord("NERVE", "Biology", "Function:", "Carries signals in the body"),
        GameWord("OCEAN", "Nature", "Definition:", "A vast body of salt water"),
        GameWord("PEACH", "Fruit", "Description:", "Soft fuzzy sweet fruit"),
        GameWord("QUEEN", "Royalty", "Definition:", "Female ruler of a kingdom"),
        GameWord("RIVER", "Geography", "Definition:", "A large stream of flowing water"),
        GameWord("SHADE", "Environment", "Definition:", "Darkness caused by blocking light"),
        GameWord("TORCH", "Tools", "Function:", "Portable light source"),
        GameWord("UNITY", "Values", "Definition:", "The state of being joined together"),
        GameWord("VAULT", "Action", "Definition:", "To leap over something"),
        GameWord("WATER", "Nature", "Formula:", "H2O in liquid form"),
        GameWord("XEROX", "Office", "Function:", "A machine that makes copies"),
        GameWord("YACHT", "Transport", "Definition:", "A medium-sized sailing or motor boat"),
        GameWord("ZEBRA", "Animal", "Description:", "Black and white striped African animal"),
        GameWord("AMBER", "Color", "Description:", "Warm yellow-orange tone"),
        GameWord("BLAZE", "Action", "Definition:", "To burn fiercely or brightly"),
        GameWord("CHARM", "Quality", "Definition:", "Pleasing and attractive quality"),
        GameWord("DELVE", "Action", "Definition:", "To research deeply into something"),
        GameWord("EVADE", "Action", "Definition:", "To escape or avoid cleverly"),
        GameWord("FJORD", "Geography", "Description:", "Narrow sea inlet between cliffs"),
        GameWord("GRAZE", "Action", "Definition:", "To feed on growing grass"),
        GameWord("HELIX", "Shape", "Definition:", "A three-dimensional spiral curve"),
        GameWord("IRONY", "Language", "Definition:", "Saying the opposite of what you mean"),
        GameWord("JOUST", "History", "Definition:", "A fight between two knights on horseback"),
        GameWord("KARMA", "Belief", "Concept:", "The idea that actions have consequences"),
        GameWord("LUNAR", "Space", "Definition:", "Relating to the moon"),
        GameWord("MIRTH", "Emotion", "Definition:", "Amusement expressed through laughter"),
        GameWord("NOTCH", "Shape", "Definition:", "A V-shaped cut or indentation"),
        GameWord("ORBIT", "Space", "Definition:", "The curved path of a planet around a star"),
        GameWord("PIXEL", "Tech", "Definition:", "The smallest unit of a digital image"),
        GameWord("QUOTA", "Business", "Definition:", "A fixed share or limit that is set"),
        GameWord("RIDGE", "Geography", "Definition:", "A long narrow hilltop or mountain edge"),
        GameWord("STOIC", "Mindset", "Definition:", "Enduring hardship without complaint")
    )

    private lateinit var roundWords: List<GameWord>
    private var roundIndex = 0
    private var attempts = 0
    private var sessionSolved = 0

    private lateinit var tvGameSolved: TextView
    private lateinit var tvCategoryValue: TextView
    private lateinit var tvAttemptsValue: TextView
    private lateinit var tvClueTag: TextView
    private lateinit var tvClueText: TextView
    private lateinit var slotContainer: LinearLayout
    private lateinit var slotViews: List<TextView>
    private lateinit var tileViews: List<TextView>
    private lateinit var cvReward: View

    private var scrambledLetters = listOf<Char>()
    private val selectedTileIndices = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        roundWords = wordBank.shuffled().take(5)
        bindViews()
        setupTileListeners()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnBackspace).setOnClickListener { removeLastTile() }

        loadRound()
    }

    private fun bindViews() {
        tvGameSolved = findViewById(R.id.tvGameSolved)
        tvCategoryValue = findViewById(R.id.tvCategoryValue)
        tvAttemptsValue = findViewById(R.id.tvAttemptsValue)
        tvClueTag = findViewById(R.id.tvClueTag)
        tvClueText = findViewById(R.id.tvClueText)
        slotContainer = findViewById(R.id.slotContainer)
        cvReward = findViewById(R.id.cvReward)

        slotViews = listOf(
            findViewById(R.id.tvSlot1), findViewById(R.id.tvSlot2), findViewById(R.id.tvSlot3),
            findViewById(R.id.tvSlot4), findViewById(R.id.tvSlot5)
        )
        tileViews = listOf(
            findViewById(R.id.tile1), findViewById(R.id.tile2), findViewById(R.id.tile3),
            findViewById(R.id.tile4), findViewById(R.id.tile5)
        )
    }

    private fun setupTileListeners() {
        tileViews.forEachIndexed { index, tv ->
            tv.setOnClickListener { onTileTapped(index) }
        }
    }

    private fun loadRound() {
        if (roundIndex >= roundWords.size) return
        val current = roundWords[roundIndex]
        tvCategoryValue.text = current.category
        tvClueTag.text = current.clueTag
        tvClueText.text = current.clueText
        tvAttemptsValue.text = attempts.toString()
        val totalSolved = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            .getInt(SettingsActivity.KEY_WORDS_SOLVED, 0)
        tvGameSolved.text = "$totalSolved solved"

        // Shuffle letters, ensuring result differs from the original word
        var shuffled = current.word.toList().shuffled()
        var retries = 10
        while (shuffled.joinToString("") == current.word && retries-- > 0) {
            shuffled = current.word.toList().shuffled()
        }
        scrambledLetters = shuffled

        selectedTileIndices.clear()
        tileViews.forEachIndexed { i, tv ->
            tv.text = scrambledLetters[i].uppercaseChar().toString()
            tv.isEnabled = true
            tv.alpha = 1f
            tv.scaleX = 1f
            tv.scaleY = 1f
        }
        slotViews.forEach { it.text = ""; it.alpha = 1f; it.scaleX = 1f; it.scaleY = 1f }
    }

    private fun onTileTapped(tileIndex: Int) {
        if (selectedTileIndices.size >= 5 || !tileViews[tileIndex].isEnabled) return

        val tile = tileViews[tileIndex]
        // Scale bounce on tap
        tile.animate().scaleX(0.88f).scaleY(0.88f).setDuration(75).withEndAction {
            tile.animate().scaleX(1f).scaleY(1f).setDuration(75).start()
        }.start()

        tile.isEnabled = false
        tile.animate().alpha(0.25f).setDuration(150).start()

        val slotIndex = selectedTileIndices.size
        selectedTileIndices.add(tileIndex)

        // Slot fill: fade + scale in
        val slot = slotViews[slotIndex]
        slot.text = scrambledLetters[tileIndex].uppercaseChar().toString()
        slot.alpha = 0f; slot.scaleX = 0.6f; slot.scaleY = 0.6f
        slot.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(120).setInterpolator(AccelerateDecelerateInterpolator()).start()

        if (selectedTileIndices.size == 5) checkAnswer()
    }

    private fun removeLastTile() {
        if (selectedTileIndices.isEmpty()) return
        val lastSlot = selectedTileIndices.size - 1
        val tileIdx = selectedTileIndices.removeAt(lastSlot)
        tileViews[tileIdx].isEnabled = true
        tileViews[tileIdx].animate().alpha(1f).setDuration(150).start()
        slotViews[lastSlot].text = ""
    }

    private fun checkAnswer() {
        val answer = selectedTileIndices.map { scrambledLetters[it] }.joinToString("").uppercase()
        val target = roundWords[roundIndex].word.uppercase()
        if (answer == target) onCorrectAnswer() else onWrongAnswer()
    }

    private fun onCorrectAnswer() {
        sessionSolved++
        attempts = 0
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putInt(SettingsActivity.KEY_WORDS_SOLVED,
                prefs.getInt(SettingsActivity.KEY_WORDS_SOLVED, 0) + 1)
            .putInt(SettingsActivity.KEY_BONUS_MINUTES_EARNED,
                prefs.getInt(SettingsActivity.KEY_BONUS_MINUTES_EARNED, 0) + 3)
            .apply()

        showRewardBanner()
        Handler(Looper.getMainLooper()).postDelayed({
            hideRewardBanner()
            roundIndex++
            if (roundIndex >= roundWords.size) finishGame() else loadRound()
        }, 1800L)
    }

    private fun onWrongAnswer() {
        attempts++
        tvAttemptsValue.text = attempts.toString()
        shakeSlots()
        Handler(Looper.getMainLooper()).postDelayed({
            tileViews.forEach { it.isEnabled = true; it.animate().alpha(1f).setDuration(150).start() }
            selectedTileIndices.clear()
            slotViews.forEach { it.text = "" }
        }, 800L)
    }

    private fun shakeSlots() {
        ObjectAnimator.ofFloat(
            slotContainer, "translationX",
            0f, -18f, 18f, -14f, 14f, -10f, 10f, -5f, 5f, 0f
        ).apply {
            duration = 500L
            interpolator = LinearInterpolator()
        }.start()
    }

    private fun showRewardBanner() {
        val offsetPx = 300f * resources.displayMetrics.density
        cvReward.translationY = offsetPx
        cvReward.visibility = View.VISIBLE
        cvReward.animate().translationY(0f).setDuration(250)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun hideRewardBanner() {
        val offsetPx = 300f * resources.displayMetrics.density
        cvReward.animate().translationY(offsetPx).setDuration(200).withEndAction {
            cvReward.visibility = View.GONE
            cvReward.translationY = 0f
        }.start()
    }

    private fun finishGame() {
        // Bonus minutes already saved per-word in onCorrectAnswer(); send 0 to avoid double-counting
        setResult(RESULT_OK, Intent().putExtra(CooldownActivity.EXTRA_BONUS_MINUTES, 0))
        finish()
    }
}
