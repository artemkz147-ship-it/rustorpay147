package ru.anime.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ru.rustore.sdk.review.RuStoreReviewManagerFactory
import ru.rustore.sdk.review.errors.RuStoreReviewExists
import ru.rustore.sdk.review.model.ReviewInfo

/** Success is flow completion, never proof of submitted rating or shown dialog. */
class ReviewController(context: Context) {
    private val manager = RuStoreReviewManagerFactory.create(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var info: ReviewInfo? = null
    private var preparedAt = 0L
    private var alreadyReviewed = false
    private var busy = false
    private var closed = false
    fun close() { closed = true; info = null }
    fun prepare() {
        if (closed || busy || alreadyReviewed || validInfo()) return
        busy = true
        manager.requestReviewFlow().addOnSuccessListener { result ->
            main.post { if (!closed) { info = result; preparedAt = SystemClock.elapsedRealtime(); busy = false } }
        }.addOnFailureListener { error ->
            main.post { if (!closed) { alreadyReviewed = error is RuStoreReviewExists; busy = false } }
        }
    }
    private fun validInfo() = info != null && SystemClock.elapsedRealtime() - preparedAt < 240_000
    fun start(onBonus: () -> Unit, onUnavailable: () -> Unit) {
        if (closed) return
        if (alreadyReviewed) { onBonus(); return }
        // Preparation might still be running. A new request is safe; UI prevents repeated user taps.
        if (validInfo()) { launch(requireNotNull(info), onBonus, onUnavailable); return }
        manager.requestReviewFlow().addOnSuccessListener { result ->
            main.post { if (!closed) launch(result, onBonus, onUnavailable) }
        }.addOnFailureListener { error ->
            main.post { if (!closed) { if (error is RuStoreReviewExists) onBonus() else onUnavailable() } }
        }
    }
    private fun launch(reviewInfo: ReviewInfo, onBonus: () -> Unit, onUnavailable: () -> Unit) {
        info = null
        manager.launchReviewFlow(reviewInfo).addOnSuccessListener {
            main.post { if (!closed) onBonus() }
        }.addOnFailureListener { error ->
            main.post { if (!closed) { if (error is RuStoreReviewExists) onBonus() else onUnavailable() } }
        }
    }
}
