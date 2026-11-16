package net.millida.launcher.core.hud

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

import net.millida.launcher.core.i18n.tr
import net.millida.launcher.core.i18n.trKey

@Serializable
enum class HudKind {
    STICK,
    KEY,
    MOUSE,
    ACTION,
    SCROLL,
    FLOAT_STICK,
    JUMP,
    HOTBAR_MORE,
}

@Serializable
enum class HudAction {
    POINTER_MODE,
    KEYBOARD,
    MENU,
    NONE,
    CHAT,
}

@Serializable
enum class HudVisibility {
    LOOK,
    CURSOR,
    ALWAYS,
    FLYING,
    WALKING,
    ;

    fun shown(lookMode: Boolean, flying: Boolean): Boolean = when (this) {
        ALWAYS -> true
        LOOK -> lookMode
        CURSOR -> !lookMode
        FLYING -> lookMode && flying
        WALKING -> lookMode && !flying
    }
}

@Serializable
data class HudControl(
    val id: String,
    val label: String,
    val kind: HudKind,
    val code: Int = 0,
    val action: HudAction = HudAction.NONE,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val width: Float = 64f,
    val height: Float = 46f,
    val toggle: Boolean = false,
    val visibility: HudVisibility = HudVisibility.LOOK,
    val opacity: Float? = null,
    val hidden: Boolean = false,
    val codes: List<Int> = emptyList(),
    val icon: String? = null,
    val latch: Boolean = false,
) {
    val keys: List<Int>
        get() = codes.ifEmpty { listOf(code) }

    val rebindable: Boolean
        get() = kind == HudKind.KEY || kind == HudKind.JUMP || kind == HudKind.HOTBAR_MORE

    val anchored: Boolean
        get() = kind == HudKind.HOTBAR_MORE

    fun withPosition(nextX: Float, nextY: Float): HudControl =
        copy(x = nextX.coerceIn(0.02f, 0.98f), y = nextY.coerceIn(0.02f, 0.98f))

    /** Centre in dp on a screen of the given dp size, put on the editor grid. */
    fun snappedTo(centreX: Float, centreY: Float, screenWidth: Float, screenHeight: Float, grid: Float = SNAP_GRID): HudControl {
        if (screenWidth <= 0f || screenHeight <= 0f) return this
        val snappedX = (centreX / grid).roundToInt() * grid
        val snappedY = (centreY / grid).roundToInt() * grid
        return withPosition(snappedX / screenWidth, snappedY / screenHeight)
    }

    fun withSize(factor: Float): HudControl {
        val nextWidth = base.width * factor.coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = if (width > 0f) height / width else base.height / base.width
        return copy(width = nextWidth, height = nextWidth * ratio)
    }

    fun withOpacity(value: Float?): HudControl = copy(opacity = value?.coerceIn(HudLayout.MIN_OPACITY, 1f))

    fun shownOpacity(global: Float): Float = (opacity ?: global).coerceIn(HudLayout.MIN_OPACITY, 1f)

    fun withKeys(next: List<Int>): HudControl {
        val clean = next.filter { HudKeys.isBindable(it) }.distinct().take(MAX_KEYS)
        if (clean.isEmpty()) return this
        return copy(code = clean.first(), codes = if (clean.size == 1) emptyList() else clean)
    }

    /** New keys, and a label that only named the old keys names the new ones; a label the player chose stays. */
    fun rebound(next: List<Int>): HudControl {
        val bound = withKeys(next)
        if (bound.keys == keys) return bound
        return if (HudKeys.isAutoLabel(label, keys)) bound.copy(label = HudKeys.autoLabel(bound.keys)) else bound
    }

    fun withLabel(next: String): HudControl = copy(label = next.filterNot { it.isISOControl() }.take(MAX_LABEL))

    fun withIcon(next: String?): HudControl = copy(icon = next?.takeIf { it in HudIcons.all })

    private val base: HudControl
        get() = HudPresets.base(kind)

    val scale: Float
        get() = width / base.width

    companion object {
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 2.2f
        const val MAX_KEYS = 3
        const val MAX_LABEL = 24
        const val SNAP_GRID = 8f
    }
}

/**
 * How the screen itself is used, as in Bedrock's three schemes. Touch: a tap places or
 * uses, holding hits and breaks. Crosshair: a tap is a click at the aim point (a hit),
 * placing has its own button. Buttons: the stick and the buttons do everything and a tap
 * hits.
 */
@Serializable
enum class ControlScheme {
    TOUCH,
    CROSSHAIR,
    BUTTONS,
}

@Serializable
data class HudLayout(
    val name: String = trKey("Своя раскладка"),
    val controls: List<HudControl> = emptyList(),
    val opacity: Float = DEFAULT_OPACITY,
    val lookTap: Int = HudKeys.MOUSE_LEFT,
    val holdDelay: Int = DEFAULT_HOLD_MS,
    /** Absent in layouts saved before the schemes: [shownScheme] works it out from what they hold. */
    val scheme: ControlScheme? = null,
    /** Every button and stick drawn this many times its own size. */
    val sizeScale: Float = 1f,
    val invertY: Boolean = false,
    /** The preset a renamed layout came from: [reset] still finds its buttons. Absent while the name is the preset's own. */
    val template: String? = null,
) {
    fun replace(control: HudControl): HudLayout =
        copy(controls = controls.map { if (it.id == control.id) control else it })

    fun remove(id: String): HudLayout = copy(controls = controls.filterNot { it.id == id })

    fun add(control: HudControl): HudLayout {
        var id = control.id
        var suffix = 2
        while (controls.any { it.id == id }) id = "${control.id}-${suffix++}"
        return copy(controls = controls + control.copy(id = id))
    }

    fun withOpacity(value: Float): HudLayout = copy(opacity = value.coerceIn(MIN_OPACITY, 1f))

    fun withLookTap(button: Int): HudLayout =
        copy(lookTap = if (button == HudKeys.MOUSE_RIGHT) HudKeys.MOUSE_RIGHT else HudKeys.MOUSE_LEFT)

    fun withHoldDelay(ms: Int): HudLayout = copy(holdDelay = ms.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS))

    /** A scheme's own preset brings the tap it is built for; the others keep the player's. */
    fun switchTo(preset: HudLayout): HudLayout = preset.copy(
        opacity = opacity,
        lookTap = if (preset.scheme == ControlScheme.TOUCH || preset.scheme == ControlScheme.CROSSHAIR) preset.lookTap else lookTap,
        holdDelay = holdDelay,
        sizeScale = sizeScale,
        invertY = invertY,
    )

    fun withSizeScale(value: Float): HudLayout = copy(sizeScale = value.takeIf { it.isFinite() }?.coerceIn(MIN_SIZE, MAX_SIZE) ?: 1f)

    fun withInvertY(on: Boolean): HudLayout = copy(invertY = on)

    /**
     * Takes the scheme's own arrangement and what a tap does with it, and keeps the
     * player's look-and-feel settings: transparency, size, holding time, inversion.
     */
    fun withScheme(next: ControlScheme): HudLayout {
        val preset = HudPresets.forScheme(next)
        return preset.copy(
            opacity = opacity,
            holdDelay = holdDelay,
            sizeScale = sizeScale,
            invertY = invertY,
        )
    }

    /** The stored scheme, or for an older layout the one its stick and tap point at. */
    val shownScheme: ControlScheme
        get() = scheme ?: when {
            controls.none { it.kind == HudKind.FLOAT_STICK } -> ControlScheme.BUTTONS
            lookTap == HudKeys.MOUSE_RIGHT -> ControlScheme.TOUCH
            else -> ControlScheme.CROSSHAIR
        }

    val shownSizeScale: Float
        get() = sizeScale.takeIf { it.isFinite() }?.coerceIn(MIN_SIZE, MAX_SIZE) ?: 1f

    /** Puts a control back the way the layout's template has it; a control the template lacks stays. */
    fun reset(id: String): HudLayout {
        val original = (preset ?: HudPresets.bedrock()).controls.firstOrNull { it.id == id }
            ?: return this
        return replace(original)
    }

    /** The preset this layout started from, by its own name or the one kept through a rename. */
    val preset: HudLayout?
        get() = HudPresets.byName(template ?: name) ?: template?.let { HudPresets.byName(name) }

    /** The same layout under the player's name; the preset it came from is remembered for [reset]. */
    fun named(next: String): HudLayout {
        if (next == name) return this
        val from = template ?: name.takeIf { HudPresets.byName(it) != null }
        return copy(name = next, template = from?.let { HudPresets.byName(it)?.name })
    }

    val shownOpacity: Float
        get() = opacity.coerceIn(MIN_OPACITY, 1f)

    val shownHoldDelay: Int
        get() = holdDelay.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)

    val tapHits: Boolean
        get() = lookTap == HudKeys.MOUSE_LEFT

    companion object {
        const val DEFAULT_OPACITY = 0.85f
        const val MIN_OPACITY = 0.3f
        const val MIN_SIZE = 0.6f
        const val MAX_SIZE = 1.6f
        const val DEFAULT_HOLD_MS = 250
        const val MIN_HOLD_MS = 150
        const val MAX_HOLD_MS = 500
    }
}

object HudKeys {
    const val SPACE = 32
    const val APOSTROPHE = 39
    const val COMMA = 44
    const val MINUS = 45
    const val PERIOD = 46
    const val SLASH = 47
    const val ZERO = 48
    const val ONE = 49
    const val TWO = 50
    const val THREE = 51
    const val FOUR = 52
    const val FIVE = 53
    const val SIX = 54
    const val SEVEN = 55
    const val EIGHT = 56
    const val NINE = 57
    const val SEMICOLON = 59
    const val EQUALS = 61
    const val A = 65
    const val D = 68
    const val E = 69
    const val F = 70
    const val Q = 81
    const val S = 83
    const val T = 84
    const val W = 87
    const val Z = 90
    const val LEFT_BRACKET = 91
    const val BACKSLASH = 92
    const val RIGHT_BRACKET = 93
    const val GRAVE = 96
    const val ESCAPE = 256
    const val ENTER = 257
    const val TAB = 258
    const val BACKSPACE = 259
    const val INSERT = 260
    const val DELETE = 261
    const val RIGHT = 262
    const val LEFT = 263
    const val DOWN = 264
    const val UP = 265
    const val PAGE_UP = 266
    const val PAGE_DOWN = 267
    const val HOME = 268
    const val END = 269
    const val CAPS_LOCK = 280
    const val F1 = 290
    const val F2 = 291
    const val F3 = 292
    const val F5 = 294
    const val F12 = 301
    const val KP_0 = 320
    const val KP_9 = 329
    const val KP_DECIMAL = 330
    const val KP_DIVIDE = 331
    const val KP_MULTIPLY = 332
    const val KP_SUBTRACT = 333
    const val KP_ADD = 334
    const val KP_ENTER = 335
    const val KP_EQUAL = 336
    const val LEFT_SHIFT = 340
    const val LEFT_CONTROL = 341
    const val LEFT_ALT = 342
    const val RIGHT_SHIFT = 344
    const val RIGHT_CONTROL = 345
    const val RIGHT_ALT = 346

    const val MOUSE_LEFT = 0
    const val MOUSE_RIGHT = 1
    const val MOUSE_MIDDLE = 2

    const val SCROLL_UP = 1
    const val SCROLL_DOWN = -1

    fun slot(index: Int): Int = ONE + index.coerceIn(0, 8)

    // The game acts on every repeat, so keys that open or switch something must not repeat.
    fun repeats(code: Int): Boolean = code !in NO_REPEAT

    private val NO_REPEAT: Set<Int> = setOf(
        E, T, TAB, ESCAPE, ENTER, KP_ENTER, CAPS_LOCK,
        LEFT_SHIFT, LEFT_CONTROL, LEFT_ALT, RIGHT_SHIFT, RIGHT_CONTROL, RIGHT_ALT,
        ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE,
    ) + (F1..F12)

    /** A row of the key picker: [title] is an untranslated key, shown through tr(). */
    data class Group(val title: String, val codes: List<Int>)

    /**
     * Keys a player can put on a button: the whole desk keyboard a mod may bind,
     * without the system keys (Win, Print Screen) the phone keeps for itself.
     * A name is an untranslated key where it is a word, so a label made from it
     * follows the language.
     */
    private val sources: List<Pair<Int, String>> = listOf(
        SPACE to trKey("Пробел"),
        ESCAPE to "Esc",
        ENTER to "Enter",
        TAB to "Tab",
        BACKSPACE to "Backspace",
        LEFT_SHIFT to "Shift",
        LEFT_CONTROL to "Ctrl",
        LEFT_ALT to "Alt",
        RIGHT_SHIFT to "RShift",
        RIGHT_CONTROL to "RCtrl",
        RIGHT_ALT to "RAlt",
        CAPS_LOCK to "Caps Lock",
        UP to trKey("Вверх"),
        DOWN to trKey("Вниз"),
        LEFT to trKey("Влево"),
        RIGHT to trKey("Вправо"),
        INSERT to "Insert",
        DELETE to "Delete",
        HOME to "Home",
        END to "End",
        PAGE_UP to "PgUp",
        PAGE_DOWN to "PgDn",
        MINUS to "-",
        EQUALS to "=",
        LEFT_BRACKET to "[",
        RIGHT_BRACKET to "]",
        BACKSLASH to "\\",
        SEMICOLON to ";",
        APOSTROPHE to "'",
        COMMA to ",",
        PERIOD to ".",
        SLASH to "/",
        GRAVE to "`",
        KP_DIVIDE to "Num /",
        KP_MULTIPLY to "Num *",
        KP_SUBTRACT to "Num -",
        KP_ADD to "Num +",
        KP_DECIMAL to "Num .",
        KP_ENTER to "Num Enter",
        KP_EQUAL to "Num =",
    ) +
        (A..Z).map { it to it.toChar().toString() } +
        (ZERO..NINE).map { it to it.toChar().toString() } +
        (F1..F12).map { it to "F${it - F1 + 1}" } +
        (KP_0..KP_9).map { it to "Num ${it - KP_0}" }

    val groups: List<Group> = listOf(
        Group(trKey("Основные"), listOf(SPACE, ESCAPE, ENTER, TAB, BACKSPACE)),
        Group(trKey("Буквы"), (A..Z).toList()),
        Group(trKey("Цифры"), (ZERO..NINE).toList()),
        Group(trKey("F-клавиши"), (F1..F12).toList()),
        Group(trKey("Модификаторы"), listOf(LEFT_SHIFT, LEFT_CONTROL, LEFT_ALT, RIGHT_SHIFT, RIGHT_CONTROL, RIGHT_ALT, CAPS_LOCK)),
        Group(trKey("Навигация"), listOf(UP, DOWN, LEFT, RIGHT, INSERT, DELETE, HOME, END, PAGE_UP, PAGE_DOWN)),
        Group(trKey("Символы"), listOf(MINUS, EQUALS, LEFT_BRACKET, RIGHT_BRACKET, BACKSLASH, SEMICOLON, APOSTROPHE, COMMA, PERIOD, SLASH, GRAVE)),
        Group(
            trKey("Цифровой блок"),
            (KP_0..KP_9).toList() + listOf(KP_DIVIDE, KP_MULTIPLY, KP_SUBTRACT, KP_ADD, KP_DECIMAL, KP_ENTER, KP_EQUAL),
        ),
    )

    private val sourceNames: Map<Int, String> = sources.toMap()

    /** Every bindable key with its name in the current language. */
    val bindable: List<Pair<Int, String>> get() = sources.map { (code, name) -> code to tr(name) }

    fun isBindable(code: Int): Boolean = code in sourceNames

    /** The name to show now. */
    fun name(code: Int): String = sourceNames[code]?.let { tr(it) } ?: "#$code"

    /** The name to store in a label: untranslated, so the label follows the language. */
    fun sourceName(code: Int): String = sourceNames[code] ?: "#$code"

    /** The label a button gets from its keys, as stored. */
    fun autoLabel(keys: List<Int>): String =
        keys.joinToString("+") { sourceName(it) }.take(HudControl.MAX_LABEL)

    /**
     * Whether [label] only names keys (its own, or any one key as preset buttons
     * such as «W» or «F5» do), so a new key may rename it. A name the player
     * typed stays.
     */
    fun isAutoLabel(label: String, keys: List<Int>): Boolean {
        val clean = label.trim()
        if (clean.isEmpty()) return true
        if (clean == autoLabel(keys) || clean == keys.joinToString("+") { name(it) }.take(HudControl.MAX_LABEL)) return true
        return sources.any { (code, source) -> clean == source || clean == name(code) }
    }
}

object Hotbar {
    const val WIDTH = 182
    const val HEIGHT = 22
    const val SLOT = 20
    const val SLOTS = 9

    private const val REACH = 6

    fun scale(width: Int, height: Int, wanted: Int, forceUnicode: Boolean = false): Int {
        if (width <= 0 || height <= 0) return 1
        var scale = 1
        while (scale != wanted && scale < width && scale < height &&
            width / (scale + 1) >= 320 && height / (scale + 1) >= 240
        ) {
            scale++
        }
        if (forceUnicode && scale % 2 != 0) scale++
        return scale
    }

    private fun guiSize(pixels: Int, scale: Int): Int = (pixels + scale - 1) / scale

    fun left(width: Int, scale: Int): Float = ((guiSize(width, scale) / 2 - WIDTH / 2) * scale).toFloat()

    fun top(height: Int, scale: Int): Float = ((guiSize(height, scale) - HEIGHT) * scale).toFloat()

    fun right(width: Int, scale: Int): Float = left(width, scale) + WIDTH * scale

    /** The topmost row a finger still counts as the hotbar. */
    fun reachTop(height: Int, scale: Int): Float = top(height, scale) - REACH * scale

    fun contains(x: Float, y: Float, width: Int, height: Int, scale: Int): Boolean {
        val left = left(width, scale)
        return x >= left && x < left + WIDTH * scale && y >= reachTop(height, scale) && y <= height
    }

    fun slotAt(x: Float, y: Float, width: Int, height: Int, scale: Int): Int? {
        if (!contains(x, y, width, height, scale)) return null
        val inside = (x - left(width, scale)) / scale - 1f
        return (inside / SLOT).toInt().coerceIn(0, SLOTS - 1)
    }

    fun slotPixels(scale: Int): Float = (SLOT * scale).toFloat()

    fun readOptions(text: String): Pair<Int, Boolean> {
        var scale = 0
        var unicode = false
        text.lineSequence().forEach { line ->
            when {
                line.startsWith("guiScale:") -> scale = line.substringAfter(':').trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
                line.startsWith("forceUnicodeFont:") -> unicode = line.substringAfter(':').trim() == "true"
            }
        }
        return scale to unicode
    }
}

object HudIcons {
    const val PAUSE = "pause"
    const val CHAT = "chat"
    const val CAMERA = "camera"
    const val PLAYERS = "players"
    const val MORE = "more"
    const val JUMP = "jump"
    const val SNEAK = "sneak"
    const val UP = "up"
    const val DOWN = "down"
    const val CLOSE = "close"
    const val DROP = "drop"
    const val USE = "use"

    val all: Set<String> = setOf(PAUSE, CHAT, CAMERA, PLAYERS, MORE, JUMP, SNEAK, UP, DOWN, CLOSE, DROP, USE)
}

object HudPresets {

    // Places are shares of the smallest target screen: on a bigger one the gaps only grow.
    const val NOMINAL_WIDTH = 640f
    const val NOMINAL_HEIGHT = 360f

    val BEDROCK: String = trKey("Как в Bedrock")
    val CROSSHAIR: String = trKey("Прицел")
    val STICK: String = trKey("Джойстик")
    val BUTTONS: String = trKey("Кнопки")
    val MINIMAL: String = trKey("Только обзор")
    val MODS: String = trKey("Моды/PvP")

    fun base(kind: HudKind): HudControl = when (kind) {
        HudKind.STICK -> HudControl("stick", trKey("Ходьба"), HudKind.STICK, width = 176f, height = 176f)
        HudKind.FLOAT_STICK -> HudControl("stick", trKey("Ходьба"), HudKind.FLOAT_STICK, width = 120f, height = 120f)
        HudKind.KEY -> HudControl("key", trKey("Клавиша"), HudKind.KEY, width = 64f, height = 46f)
        HudKind.MOUSE -> HudControl("mouse", trKey("Кнопка мыши"), HudKind.MOUSE, width = 104f, height = 46f)
        HudKind.ACTION -> HudControl("action", trKey("Действие"), HudKind.ACTION, width = 104f, height = 44f)
        HudKind.SCROLL -> HudControl("scroll", trKey("Колесо"), HudKind.SCROLL, width = 64f, height = 46f)
        HudKind.JUMP -> HudControl("jump", trKey("Прыжок"), HudKind.JUMP, code = HudKeys.SPACE, width = 64f, height = 64f)
        HudKind.HOTBAR_MORE -> HudControl("more", trKey("Инвентарь"), HudKind.HOTBAR_MORE, code = HudKeys.E, width = 32f, height = 32f)
    }

    private fun at(control: HudControl, centreX: Float, centreY: Float): HudControl =
        control.copy(x = centreX / NOMINAL_WIDTH, y = centreY / NOMINAL_HEIGHT)

    private fun key(
        id: String,
        label: String,
        code: Int,
        width: Float = 64f,
        toggle: Boolean = false,
        visibility: HudVisibility = HudVisibility.LOOK,
    ) = HudControl(
        id,
        label,
        HudKind.KEY,
        code = code,
        width = width,
        height = 46f,
        toggle = toggle,
        visibility = visibility,
    )

    private fun square(
        id: String,
        label: String,
        code: Int,
        side: Float,
        icon: String,
        visibility: HudVisibility = HudVisibility.LOOK,
        latch: Boolean = false,
        hidden: Boolean = false,
        mouse: Boolean = false,
    ) = HudControl(
        id,
        label,
        if (mouse) HudKind.MOUSE else HudKind.KEY,
        code = code,
        width = side,
        height = side,
        visibility = visibility,
        icon = icon,
        latch = latch,
        hidden = hidden,
    )

    private fun gameKeys(camera: Boolean): List<HudControl> = listOfNotNull(
        at(key("escape", "Esc", HudKeys.ESCAPE, visibility = HudVisibility.ALWAYS), 40f, 28f),
        if (camera) at(key("camera", "F5", HudKeys.F5), 112f, 28f) else null,
    )

    private fun mouse(id: String, label: String, code: Int) =
        HudControl(id, label, HudKind.MOUSE, code = code, width = 104f, height = 46f)

    private fun action(id: String, label: String, what: HudAction, width: Float) =
        HudControl(
            id,
            label,
            HudKind.ACTION,
            action = what,
            width = width,
            height = 44f,
            visibility = HudVisibility.ALWAYS,
        )

    private fun actions(): List<HudControl> = listOf(
        at(action("mode", trKey("Курсор"), HudAction.POINTER_MODE, 104f), 308f, 28f),
        at(action("keyboard", trKey("Клавиатура"), HudAction.KEYBOARD, 140f), 446f, 28f),
        at(action("menu", trKey("Меню"), HudAction.MENU, 96f), 580f, 28f),
    )

    private fun rightHand(): List<HudControl> = listOf(
        at(key("sneak", trKey("Присесть"), HudKeys.LEFT_SHIFT, width = 104f, toggle = true), 452f, 190f),
        at(key("jump", trKey("Прыжок"), HudKeys.SPACE, width = 104f), 572f, 190f),
        at(mouse("hit", trKey("Удар"), HudKeys.MOUSE_LEFT), 452f, 250f),
        at(mouse("use", trKey("Действие"), HudKeys.MOUSE_RIGHT), 572f, 250f),
        at(key("inventory", trKey("Инвентарь"), HudKeys.E, width = 104f), 452f, 310f),
        at(key("chat", trKey("Чат"), HudKeys.T, width = 104f), 572f, 310f),
    )

    private fun iconAction(id: String, label: String, what: HudAction, icon: String, visibility: HudVisibility) =
        HudControl(
            id,
            label,
            HudKind.ACTION,
            action = what,
            width = 36f,
            height = 36f,
            visibility = visibility,
            icon = icon,
        )

    /**
     * Tap places, holding breaks, the left thumb walks wherever it lands. Esc for
     * closing a game screen takes the chat's place while a screen is open: the
     * two are never needed at once.
     */
    fun bedrock(): HudLayout = HudLayout(
        name = BEDROCK,
        lookTap = HudKeys.MOUSE_RIGHT,
        scheme = ControlScheme.TOUCH,
        controls = listOf(
            at(base(HudKind.FLOAT_STICK), 88f, 270f),
            at(base(HudKind.JUMP).copy(icon = HudIcons.JUMP), 584f, 280f),
            at(square("sneak", trKey("Присесть"), HudKeys.LEFT_SHIFT, 52f, HudIcons.SNEAK, HudVisibility.WALKING, latch = true), 520f, 312f),
            at(square("fly-down", trKey("Вниз"), HudKeys.LEFT_SHIFT, 52f, HudIcons.DOWN, HudVisibility.FLYING), 520f, 312f),
            at(iconAction("menu", trKey("Меню"), HudAction.MENU, HudIcons.PAUSE, HudVisibility.ALWAYS), 616f, 22f),
            at(iconAction("chat", trKey("Чат"), HudAction.CHAT, HudIcons.CHAT, HudVisibility.LOOK), 576f, 22f),
            at(square("escape", "Esc", HudKeys.ESCAPE, 36f, HudIcons.CLOSE, HudVisibility.CURSOR), 576f, 22f),
            at(square("camera", trKey("Вид"), HudKeys.F5, 36f, HudIcons.CAMERA), 536f, 22f),
            at(square("players", trKey("Игроки"), HudKeys.TAB, 36f, HudIcons.PLAYERS, hidden = true), 496f, 22f),
            at(square("drop", trKey("Бросить"), HudKeys.Q, 36f, HudIcons.DROP, hidden = true), 456f, 22f),
            base(HudKind.HOTBAR_MORE).copy(icon = HudIcons.MORE, x = 0.5f, y = 0.95f),
        ),
    )

    /**
     * Bedrock's Crosshair: a tap anywhere is a click at the aim point, so fighting is
     * tapping at the enemy; placing a block or using an item is the button under the
     * right thumb.
     */
    fun crosshair(): HudLayout = HudLayout(
        name = CROSSHAIR,
        lookTap = HudKeys.MOUSE_LEFT,
        scheme = ControlScheme.CROSSHAIR,
        controls = listOf(
            at(base(HudKind.FLOAT_STICK), 88f, 270f),
            at(base(HudKind.JUMP).copy(icon = HudIcons.JUMP), 584f, 280f),
            at(square("use", trKey("Действие"), HudKeys.MOUSE_RIGHT, 56f, HudIcons.USE, mouse = true), 520f, 232f),
            at(square("sneak", trKey("Присесть"), HudKeys.LEFT_SHIFT, 52f, HudIcons.SNEAK, HudVisibility.WALKING, latch = true), 520f, 312f),
            at(square("fly-down", trKey("Вниз"), HudKeys.LEFT_SHIFT, 52f, HudIcons.DOWN, HudVisibility.FLYING), 520f, 312f),
            at(iconAction("menu", trKey("Меню"), HudAction.MENU, HudIcons.PAUSE, HudVisibility.ALWAYS), 616f, 22f),
            at(iconAction("chat", trKey("Чат"), HudAction.CHAT, HudIcons.CHAT, HudVisibility.LOOK), 576f, 22f),
            at(square("escape", "Esc", HudKeys.ESCAPE, 36f, HudIcons.CLOSE, HudVisibility.CURSOR), 576f, 22f),
            at(square("camera", trKey("Вид"), HudKeys.F5, 36f, HudIcons.CAMERA), 536f, 22f),
            at(square("players", trKey("Игроки"), HudKeys.TAB, 36f, HudIcons.PLAYERS, hidden = true), 496f, 22f),
            at(square("drop", trKey("Бросить"), HudKeys.Q, 36f, HudIcons.DROP, hidden = true), 456f, 22f),
            base(HudKind.HOTBAR_MORE).copy(icon = HudIcons.MORE, x = 0.5f, y = 0.95f),
        ),
    )

    fun stick(): HudLayout = HudLayout(
        name = STICK,
        scheme = ControlScheme.BUTTONS,
        controls = listOf(
            at(HudControl("stick", trKey("Ходьба"), HudKind.STICK, width = 176f, height = 176f), 104f, 252f),
        ) + rightHand() + gameKeys(camera = true) + actions(),
    )

    fun buttons(): HudLayout = HudLayout(
        name = BUTTONS,
        controls = listOf(
            at(key("forward", "W", HudKeys.W, width = 56f), 116f, 168f),
            at(key("left", "A", HudKeys.A, width = 56f), 56f, 222f),
            at(key("back", "S", HudKeys.S, width = 56f), 116f, 222f),
            at(key("right", "D", HudKeys.D, width = 56f), 176f, 222f),
            at(key("sprint", trKey("Бег"), HudKeys.LEFT_CONTROL, width = 104f, toggle = true), 68f, 282f),
        ) + rightHand() + gameKeys(camera = true) + actions(),
    )

    fun minimal(): HudLayout = HudLayout(
        name = MINIMAL,
        controls = listOf(
            at(HudControl("stick", trKey("Ходьба"), HudKind.STICK, width = 150f, height = 150f), 92f, 262f),
            at(key("jump", trKey("Прыжок"), HudKeys.SPACE, width = 104f), 572f, 250f),
        ) + gameKeys(camera = false) + actions(),
    )

    /**
     * For modded servers and PvP: Bedrock's Crosshair, so a tap hits, plus what a
     * phone without a desk keyboard lacks there: F3, the wheel for the hotbar,
     * right Shift that opens the Millida mod menu, and the middle button.
     */
    fun mods(): HudLayout {
        val base = crosshair()
        return base.copy(
            name = MODS,
            controls = base.controls + listOf(
                at(key("debug", "F3", HudKeys.F3), 40f, 28f),
                at(key("mod-menu", trKey("Меню Millida"), HudKeys.RIGHT_SHIFT, width = 120f, visibility = HudVisibility.ALWAYS), 148f, 28f),
                at(wheel("slot-prev", trKey("Слот −"), HudKeys.SCROLL_UP), 584f, 140f),
                at(wheel("slot-next", trKey("Слот +"), HudKeys.SCROLL_DOWN), 584f, 196f),
                at(mouse("middle", trKey("Копировать"), HudKeys.MOUSE_MIDDLE), 456f, 140f),
            ),
        )
    }

    private fun wheel(id: String, label: String, notches: Int) =
        HudControl(id, label, HudKind.SCROLL, code = notches, width = 64f, height = 46f)

    fun all(): List<HudLayout> = listOf(bedrock(), crosshair(), mods(), stick(), buttons(), minimal())

    fun forScheme(scheme: ControlScheme): HudLayout = when (scheme) {
        ControlScheme.TOUCH -> bedrock()
        ControlScheme.CROSSHAIR -> crosshair()
        ControlScheme.BUTTONS -> stick()
    }

    /** Matches the stored key and, for layouts saved before names stayed untranslated, the name in the current language. */
    fun byName(name: String): HudLayout? = all().firstOrNull { it.name == name } ?: all().firstOrNull { tr(it.name) == name }
}
