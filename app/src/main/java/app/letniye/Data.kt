package app.letniye

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PostType { PHOTO, CIRCLE, VOICE, ART, MUSIC }

/** Цвет заметки: красит объёмное стекло карточки. */
enum class NoteColor(val c: Color) {
    LIME(Color(0xFF8CCB3C)),
    PEACH(Color(0xFFF2A065)),
    SKY(Color(0xFF6FB1E8)),
    LILAC(Color(0xFFA98BE0)),
    ROSE(Color(0xFFEE8FA6)),
    SAND(Color(0xFFD9BE7A)),
}

/**
 * r — высота/ширина у фото, d — длительность, pr — доля проигранного голосового,
 * cv — у заметки есть фото, col — цвет заметки, h/p — заголовок и текст,
 * ti/ar — название и исполнитель.
 */
data class Post(
    val t: PostType,
    val n: String,
    val r: Float = 1f,
    val d: String = "",
    val pr: Float = 0f,
    val cv: Boolean = false,
    val col: NoteColor = NoteColor.LIME,
    val h: String = "",
    val p: String = "",
    val ti: String = "",
    val ar: String = "",
    /** id — ключ в ленте, k — номер для картинки и даты; vid — свой кружок: mp4 и рамка (cx, cy, cd — доли кадра). */
    val id: Int = 0,
    val k: Int = 0,
    val vid: String = "",
    val vw: Int = 0,
    val vh: Int = 0,
    val cx: Float = .5f,
    val cy: Float = .5f,
    val cd: Float = 1f,
    val mir: Boolean = false,
)

/** false — убрать имена под постами совсем. */
const val SHOW_NAMES = false

/** false — убрать даты под карточками. */
const val SHOW_DATES = true

/** Дата под карточкой. Пока демо: чем ниже в ленте, тем старше. Потом брать из поста. */
fun feedDate(i: Int): String {
    val n = (i * .7).toInt()
    if (n == 0) return "сегодня"
    if (n == 1) return "вчера"
    return LocalDate.now().minusDays(n.toLong())
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")))
        .replace(".", "")
}

val FEED = listOf(
    Post(PostType.PHOTO, "Алина", r = 1.4f),
    Post(PostType.PHOTO, "Ярослав", r = .75f),
    Post(PostType.CIRCLE, "Илья", d = "0:23"),
    Post(PostType.VOICE, "Мария", d = "0:48", pr = .4f),
    Post(PostType.ART, "Дарья", cv = true, col = NoteColor.LIME, h = "Как выбрать плащ на межсезонье", p = "Три силуэта, которые работают с любой обувью."),
    Post(PostType.PHOTO, "Тимур", r = 1f),
    Post(PostType.PHOTO, "Вера", r = 1.6f),
    Post(PostType.CIRCLE, "Соня", d = "0:58"),
    Post(PostType.VOICE, "Артём", d = "0:36", pr = 0f),
    Post(PostType.ART, "Полина", col = NoteColor.SAND, h = "Свет в маленькой квартире", p = "Тёплые лампы, зеркала и тканевые абажуры: как добавить объём без ремонта."),
    Post(PostType.MUSIC, "Мира Лен", ti = "Тихий свет", ar = "Мира Лен"),
    Post(PostType.PHOTO, "Глеб", r = .7f),
    Post(PostType.PHOTO, "Ника", r = 1.25f),
    Post(PostType.CIRCLE, "Катя", d = "0:41"),
    Post(PostType.VOICE, "Оля", d = "0:52", pr = .2f),
    Post(PostType.PHOTO, "Рома", r = 1.5f),
    Post(PostType.ART, "Егор", cv = true, col = NoteColor.LILAC, h = "Шрифт как настроение", p = "Плакаты, где вся идея держится на одной гарнитуре."),
    Post(PostType.MUSIC, "Лето в сети", ti = "Дождь в июле", ar = "Лето в сети"),
    Post(PostType.PHOTO, "Лиза", r = .8f),
    Post(PostType.CIRCLE, "Дима", d = "0:33"),
    Post(PostType.PHOTO, "Сева", r = 1.1f),
    Post(PostType.MUSIC, "Пятый этаж", ti = "Ночной рейс", ar = "Пятый этаж"),
    Post(PostType.ART, "Алла", cv = true, col = NoteColor.ROSE, h = "Свет в маленькой квартире", p = "Тёплые лампы и зеркала."),
    Post(PostType.ART, "Кирилл", col = NoteColor.SKY, h = "Дождь в Таллине", p = "Куда пойти, когда небо серое: места с окнами в пол."),
    Post(PostType.ART, "Вика", cv = true, col = NoteColor.PEACH, h = "Абрикосовый пирог", p = "Без миксера, за сорок минут."),
    Post(PostType.PHOTO, "Марта", r = 1.3f),
)
