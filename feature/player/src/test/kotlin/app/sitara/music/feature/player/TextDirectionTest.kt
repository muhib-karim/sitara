package app.sitara.music.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A lyric line reads right to left when its first letter with a direction does: Urdu and Arabic, not Bangla or Hindi. */
class TextDirectionTest {
    @Test fun urduAndArabicReadRightToLeft() {
        assertTrue(isRightToLeft("ہزاروں خواہشیں ایسی کہ ہر خواہش پہ دم نکلے"))
        assertTrue(isRightToLeft("«حبيبي» 2024"))
    }

    @Test fun banglaHindiAndLatinReadLeftToRight() {
        assertFalse(isRightToLeft("আমার সোনার বাংলা, আমি তোমায় ভালোবাসি।"))
        assertFalse(isRightToLeft("पोथी पढ़ि पढ़ि जग मुआ, पंडित भया न कोय।"))
        assertFalse(isRightToLeft("(1, 2) Paper lanterns"))
    }

    @Test fun aLineWithNoLettersReadsLeftToRight() = assertFalse(isRightToLeft("… 123 …"))
}
