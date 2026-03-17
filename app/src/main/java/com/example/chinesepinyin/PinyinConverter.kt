package com.example.chinesepinyin

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * 中国語テキストをピンインに変換するユーティリティクラス
 */
object PinyinConverter {

    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITH_TONE_MARK  // 声調記号付き（ā á ǎ à）
        vCharType = HanyuPinyinVCharType.WITH_U_UNICODE  // ü を正しく表示
    }

    /**
     * テキスト全体をピンインに変換する
     * @param text 変換する中国語テキスト
     * @return ピンイン文字列（スペース区切り）
     */
    fun convertToPinyin(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            when {
                char.code in 0x4E00..0x9FFF || char.code in 0x3400..0x4DBF -> {
                    // 漢字の場合
                    try {
                        val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(char, format)
                        if (pinyinArray != null && pinyinArray.isNotEmpty()) {
                            sb.append(pinyinArray[0])
                            sb.append(' ')
                        } else {
                            sb.append(char)
                        }
                    } catch (e: Exception) {
                        sb.append(char)
                    }
                }
                char == '\n' || char == '\r' -> {
                    sb.append('\n')
                }
                char == ' ' -> {
                    // スペースはそのまま
                }
                else -> {
                    // 漢字以外（英数字、句読点など）
                    sb.append(char)
                }
            }
        }
        return sb.toString().trim()
    }

    /**
     * 各文字とそのピンインのペアリストを返す
     * @param text 変換する中国語テキスト
     * @return (漢字, ピンイン) のペアリスト
     */
    fun convertToCharPinyinPairs(text: String): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        for (char in text) {
            when {
                char.code in 0x4E00..0x9FFF || char.code in 0x3400..0x4DBF -> {
                    try {
                        val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(char, format)
                        val pinyin = if (pinyinArray != null && pinyinArray.isNotEmpty()) {
                            pinyinArray[0]
                        } else {
                            ""
                        }
                        pairs.add(Pair(char.toString(), pinyin))
                    } catch (e: Exception) {
                        pairs.add(Pair(char.toString(), ""))
                    }
                }
                char == '\n' -> {
                    pairs.add(Pair("\n", ""))
                }
                char != ' ' -> {
                    pairs.add(Pair(char.toString(), ""))
                }
            }
        }
        return pairs
    }

    /**
     * ピンインのみを抽出してスペース区切りで返す（コピー用）
     */
    fun extractPinyinOnly(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            if (char.code in 0x4E00..0x9FFF || char.code in 0x3400..0x4DBF) {
                try {
                    val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(char, format)
                    if (pinyinArray != null && pinyinArray.isNotEmpty()) {
                        sb.append(pinyinArray[0])
                        sb.append(' ')
                    }
                } catch (e: Exception) {
                    // skip
                }
            } else if (char == '\n') {
                sb.append('\n')
            }
        }
        return sb.toString().trim()
    }
}
