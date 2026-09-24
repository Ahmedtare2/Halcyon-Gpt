import com.ella.music.data.parser.LrcParser

fun main() {
    val result = LrcParser.parse(
        """
        <tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\">
          <body>
            <div>
              <p begin=\"00:01.000\" end=\"00:03.000\">
                <span begin=\"00:01.000\" end=\"00:02.000\">To get respect from</span>
                <span ttm:role=\"x-translation\">他人的尊重</span>
                <span ttm:role=\"x-bg\" begin=\"00:02.000\" end=\"00:03.000\">
                  <span begin=\"00:02.000\" end=\"00:03.000\">(Baby</span>
                  <span ttm:role=\"x-translation\">宝贝</span>
                </span>
              </p>
              <p begin=\"00:03.000\" end=\"00:04.000\">
                <span ttm:role=\"x-bg\" begin=\"00:03.000\" end=\"00:04.000\">
                  <span begin=\"00:03.000\" end=\"00:04.000\">(Yeah</span>
                </span>
              </p>
            </div>
          </body>
        </tt>
        """.trimIndent()
    )
    println("size=" + result.lyrics.size)
    result.lyrics.forEachIndexed { index, line ->
        println("[$index] text='${line.text}' trans='${line.translation}' bg='${line.backgroundText}' bgTrans='${line.backgroundTranslation}' bgWords=${line.backgroundWords.map { it.text }}")
    }
}
