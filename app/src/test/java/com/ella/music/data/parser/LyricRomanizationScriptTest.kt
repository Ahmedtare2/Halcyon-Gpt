package com.ella.music.data.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricRomanizationScriptTest {

    @Test
    fun vietnameseLettersAreNotRomanizationLetters() {
        listOf(
            "Khi màn đêm vừa buông",
            "Nơi vực sâu không tên",
            "Sương còn vương hàng trăm",
            "Em vẫn cứ uống"
        ).forEach { line ->
            assertFalse(line, line.usesOnlyRomanizationLetters())
            assertFalse(line, line.looksLikeCjkReading())
        }
    }

    @Test
    fun tonedPinyinIsARomanizationLetterSet() {
        assertTrue("chūn xiāo yǔ zhì míng".usesOnlyRomanizationLetters())
        assertTrue("chūn xiāo yǔ zhì míng".looksLikeCjkReading())
        assertTrue("nǐ hǎo shì jiè".looksLikeCjkReading())
    }

    @Test
    fun untonedPinyinAndRomajiStillRead() {
        assertTrue("kaze ga kawattemo".looksLikeCjkReading())
        assertTrue("wo men de gu shi".looksLikeCjkReading())
        assertTrue("kimi no na wa".looksLikeCjkReading())
    }

    @Test
    fun latinLyricsWithoutCjkSyllableShapeDoNotRead() {
        // ASCII-only Vietnamese: the script gate passes, so the syllable check has to reject it.
        assertFalse("Noi vuc sau khong ten".looksLikeCjkReading())
        assertFalse("Khi man dem vua buong".looksLikeCjkReading())
        assertFalse("Even when the wind changes".looksLikeCjkReading())
    }

    @Test
    fun labialMoraicNasalIsStillARomajiSyllable() {
        // Traditional Hepburn spells syllabic ん as "m" before b/m/p — 新聞 as "shimbun", not
        // "shinbun". Both must read as valid romaji, and a plain "m" onset (も/み/む/め/も) must
        // not be mistaken for the nasal just because it precedes those letters coincidentally.
        assertTrue("shimbun".isRomajiToken())
        assertTrue("sempai".isRomajiToken())
        assertTrue("moshimoshi".isRomajiToken())
    }

    @Test
    fun revisedRomanizationKoreanReads() {
        assertTrue("annyeonghaseyo".looksLikeCjkReading())
        assertTrue("saranghae".looksLikeCjkReading())
        assertTrue("neol saranghae naege wajwo".looksLikeCjkReading())
    }

    @Test
    fun shortEnglishWordsDoNotFalsePositiveAsKorean() {
        // Individual short words can coincidentally parse as one or two plain CV syllables
        // ("no" -> no, "ten" -> ten) — the line-level 70% threshold is what actually protects
        // against this, so check it holds for ordinary English sentences.
        assertFalse("You and I are the same".looksLikeCjkReading())
        assertFalse("Even when the wind changes".looksLikeCjkReading())
    }

    @Test
    fun onlyAnnotatableScriptsTakeRuby() {
        assertTrue("風が変わっても".needsPhoneticAnnotation())
        assertTrue("当夜幕降临".needsPhoneticAnnotation())
        assertTrue("사랑".needsPhoneticAnnotation())
        assertFalse("Khi màn đêm vừa buông".needsPhoneticAnnotation())
        assertFalse("When I got on stage".needsPhoneticAnnotation())
        // Arabic, Cyrillic and Thai rows keep their per-word transliteration: only a Latin-script
        // lyric must never be pushed into the ruby row.
        assertTrue("حبيبي أنا قلبي".needsPhoneticAnnotation())
        assertTrue("Прощай".needsPhoneticAnnotation())
    }
}
