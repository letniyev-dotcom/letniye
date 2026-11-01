package app.letniye

enum class PostType { PHOTO, CIRCLE, VOICE, ART, MUSIC }

/**
 * r — высота/ширина у фото, d — длительность, pr — доля проигранного голосового,
 * cv — у статьи есть обложка, k/m/h/p — рубрика, время чтения, заголовок, текст,
 * ti/ar — название и исполнитель.
 */
data class Post(
    val t: PostType,
    val n: String,
    val r: Float = 1f,
    val d: String = "",
    val pr: Float = 0f,
    val cv: Boolean = false,
    val k: String = "",
    val m: String = "",
    val h: String = "",
    val p: String = "",
    val ti: String = "",
    val ar: String = "",
)

/** false — убрать имена под постами совсем. */
const val SHOW_NAMES = false

val FEED = listOf(
    Post(PostType.PHOTO, "Алина", r = 1.4f),
    Post(PostType.PHOTO, "Ярослав", r = .75f),
    Post(PostType.CIRCLE, "Илья", d = "0:23"),
    Post(PostType.VOICE, "Мария", d = "0:48", pr = .4f),
    Post(PostType.ART, "Дарья", cv = true, k = "Мода", m = "4 мин", h = "Как выбрать плащ на межсезонье", p = "Три силуэта, которые работают с любой обувью."),
    Post(PostType.PHOTO, "Тимур", r = 1f),
    Post(PostType.PHOTO, "Вера", r = 1.6f),
    Post(PostType.CIRCLE, "Соня", d = "0:58"),
    Post(PostType.VOICE, "Артём", d = "0:36", pr = 0f),
    Post(PostType.ART, "Полина", k = "Интерьер", m = "6 мин", h = "Свет в маленькой квартире", p = "Тёплые лампы, зеркала и тканевые абажуры: как добавить объём без ремонта."),
    Post(PostType.MUSIC, "Мира Лен", ti = "Тихий свет", ar = "Мира Лен"),
    Post(PostType.PHOTO, "Глеб", r = .7f),
    Post(PostType.PHOTO, "Ника", r = 1.25f),
    Post(PostType.CIRCLE, "Катя", d = "0:41"),
    Post(PostType.VOICE, "Оля", d = "0:52", pr = .2f),
    Post(PostType.PHOTO, "Рома", r = 1.5f),
    Post(PostType.ART, "Егор", cv = true, k = "Графика", m = "8 мин", h = "Шрифт как настроение", p = "Плакаты, где вся идея держится на одной гарнитуре."),
    Post(PostType.MUSIC, "Лето в сети", ti = "Дождь в июле", ar = "Лето в сети"),
    Post(PostType.PHOTO, "Лиза", r = .8f),
    Post(PostType.CIRCLE, "Дима", d = "0:33"),
    Post(PostType.PHOTO, "Сева", r = 1.1f),
    Post(PostType.MUSIC, "Пятый этаж", ti = "Ночной рейс", ar = "Пятый этаж"),
    Post(PostType.PHOTO, "Марта", r = 1.3f),
)
