package org.ollu.mini.skills

import androidx.compose.ui.graphics.Color

/**
 * The skill vocabulary shared by the catalog, the learning mode and the progress
 * screens. Skills are grouped into families, and a family fixes both the label
 * palette and the colour, so a skill reads the same wherever it appears.
 */
enum class SkillFamily(val color: Color) {
    ATTENTION(Color(0xFF5C9BD6)),
    MOTOR(Color(0xFFFF8A65)),
    THINKING(Color(0xFF9C7BB8)),
    PERCEPTION(Color(0xFF26C6DA)),
    SPEECH(Color(0xFF66BB6A)),
    LITERACY(Color(0xFFEF5350)),
    SELF_CONTROL(Color(0xFFFFB300))
}

data class Skill(
    val id:     String,
    val label:  String,
    val family: SkillFamily
) {
    val color: Color get() = family.color
}

private val ALL_SKILLS: List<Skill> = listOf(
    Skill("attention",             "Внимание",             SkillFamily.ATTENTION),
    Skill("visual_attention",      "Зрит. внимание",       SkillFamily.ATTENTION),
    Skill("selective_attention",   "Избир. внимание",      SkillFamily.ATTENTION),
    Skill("sustained_attention",   "Устойч. внимание",     SkillFamily.ATTENTION),

    Skill("fine_motor",            "Мелкая моторика",      SkillFamily.MOTOR),
    Skill("hand_eye_coordination", "Глаз-рука",            SkillFamily.MOTOR),
    Skill("gesture_control",       "Жесты",                SkillFamily.MOTOR),
    Skill("trace_control",         "Обводка",              SkillFamily.MOTOR),

    Skill("matching",              "Соответствие",         SkillFamily.THINKING),
    Skill("categorization",        "Категоризация",        SkillFamily.THINKING),
    Skill("sequencing",            "Последовательность",   SkillFamily.THINKING),
    Skill("memory",                "Память",               SkillFamily.THINKING),
    Skill("working_memory",        "Рабочая память",       SkillFamily.THINKING),
    Skill("problem_solving",       "Решение задач",        SkillFamily.THINKING),
    Skill("visual_planning",       "Планирование",         SkillFamily.THINKING),

    Skill("visual_memory",         "Зрит. память",         SkillFamily.PERCEPTION),
    Skill("color_recognition",     "Цвета",                SkillFamily.PERCEPTION),
    Skill("shape_recognition",     "Формы",                SkillFamily.PERCEPTION),
    Skill("size_comparison",       "Размеры",              SkillFamily.PERCEPTION),
    Skill("pattern_recognition",   "Паттерны",             SkillFamily.PERCEPTION),

    Skill("associative_memory",    "Ассоц. память",        SkillFamily.SPEECH),
    Skill("receptive_speech",      "Понимание речи",       SkillFamily.SPEECH),
    Skill("expressive_speech",     "Активная речь",        SkillFamily.SPEECH),
    Skill("word_association",      "Ассоциации",           SkillFamily.SPEECH),
    Skill("phonemic_awareness",    "Фонем. слух",          SkillFamily.SPEECH),
    // What speech games build, for a child who is not talking yet: using the
    // voice at all, copying a sound, asking, moving the mouth on purpose, and
    // a voice loud and long enough to be heard.
    Skill("vocal_initiation",      "Голос",                SkillFamily.SPEECH),
    Skill("sound_imitation",       "Звукоподражание",      SkillFamily.SPEECH),
    Skill("requesting",            "Просьба",              SkillFamily.SPEECH),
    Skill("speech_motor",          "Моторика речи",        SkillFamily.SPEECH),
    Skill("voice_loudness",        "Сила голоса",          SkillFamily.SPEECH),

    Skill("sequential_memory",     "Послед. память",       SkillFamily.LITERACY),
    Skill("numbers",               "Цифры",                SkillFamily.LITERACY),
    Skill("counting",              "Счёт",                 SkillFamily.LITERACY),
    Skill("letters",               "Буквы",                SkillFamily.LITERACY),
    Skill("reading_readiness",     "Подгот. к чтению",     SkillFamily.LITERACY),
    Skill("reaction_speed",        "Реакция",              SkillFamily.LITERACY),

    Skill("task_completion",       "Завершение",           SkillFamily.SELF_CONTROL),
    Skill("turn_taking",           "Очерёдность",          SkillFamily.SELF_CONTROL),
    Skill("patience",              "Терпение",             SkillFamily.SELF_CONTROL),
    Skill("impulse_control",       "Контроль импульсов",   SkillFamily.SELF_CONTROL),
    Skill("inhibitory_control",    "Торможение",           SkillFamily.SELF_CONTROL)
)

private val BY_ID: Map<String, Skill> = ALL_SKILLS.associateBy { it.id }

/** Unknown ids fall back to the raw id and a neutral colour rather than disappearing. */
private val UNKNOWN_COLOR = Color(0xFF90A4AE)

fun skillLabelOf(id: String): String = BY_ID[id]?.label ?: id

fun skillColorOf(id: String): Color = BY_ID[id]?.color ?: UNKNOWN_COLOR

fun knownSkills(): List<Skill> = ALL_SKILLS
