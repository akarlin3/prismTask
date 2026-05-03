package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.preferences.CognitiveLoadCustomKeywords
import com.averycorp.prismtask.domain.model.CognitiveLoad
import org.junit.Assert.assertEquals
import org.junit.Test

class CognitiveLoadClassifierTest {
    private val classifier = CognitiveLoadClassifier()

    @Test
    fun `empty input is uncategorized`() {
        assertEquals(CognitiveLoad.UNCATEGORIZED, classifier.classify(""))
        assertEquals(CognitiveLoad.UNCATEGORIZED, classifier.classify("   "))
    }

    @Test
    fun `easy keyword classifies as easy`() {
        assertEquals(CognitiveLoad.EASY, classifier.classify("Quick reply to mom"))
        assertEquals(CognitiveLoad.EASY, classifier.classify("Archive yesterday's drafts"))
    }

    @Test
    fun `medium keyword classifies as medium`() {
        assertEquals(CognitiveLoad.MEDIUM, classifier.classify("Review the PR comments"))
        assertEquals(CognitiveLoad.MEDIUM, classifier.classify("Compose the standup notes"))
    }

    @Test
    fun `hard keyword classifies as hard`() {
        assertEquals(CognitiveLoad.HARD, classifier.classify("Start the recommendation letter"))
        assertEquals(CognitiveLoad.HARD, classifier.classify("Debug the Firestore listener leak"))
    }

    @Test
    fun `unrelated text is uncategorized`() {
        assertEquals(CognitiveLoad.UNCATEGORIZED, classifier.classify("Random unrelated string"))
    }

    @Test
    fun `tie breaks toward easy then medium then hard`() {
        // "quick" + "review" both match — tie-break wins for EASY (over MEDIUM).
        assertEquals(CognitiveLoad.EASY, classifier.classify("quick review"))
        // "review" + "start" both match — tie-break wins for MEDIUM (over HARD).
        assertEquals(CognitiveLoad.MEDIUM, classifier.classify("review and start"))
    }

    @Test
    fun `description text is also scanned`() {
        assertEquals(
            CognitiveLoad.HARD,
            classifier.classify(title = "Tomorrow's blocker", description = "investigate the regression")
        )
    }

    @Test
    fun `custom keywords augment defaults`() {
        val classifier = CognitiveLoadClassifier.withCustomKeywords(
            CognitiveLoadCustomKeywords(easy = "ack, ok", hard = "spike")
        )
        assertEquals(CognitiveLoad.EASY, classifier.classify("Ack the email"))
        assertEquals(CognitiveLoad.HARD, classifier.classify("Spike on the new framework"))
    }

    @Test
    fun `case insensitive`() {
        assertEquals(CognitiveLoad.MEDIUM, classifier.classify("REVIEW the PR"))
    }
}
