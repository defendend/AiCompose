package org.example.model

import kotlinx.serialization.Serializable

/**
 * Режим сбора данных — определяет какую информацию модель должна собрать
 * и когда она должна выдать финальный результат
 */
@Serializable
enum class CollectionMode {
    /** Обычный режим — без ограничений */
    NONE,
    /** Сбор требований для технического задания */
    TECHNICAL_SPEC,
    /** Сбор информации для брифа дизайна */
    DESIGN_BRIEF,
    /** Сбор информации для резюме проекта */
    PROJECT_SUMMARY,
    /** Пользовательский режим */
    CUSTOM
}

/**
 * Настройки для режима сбора данных
 */
@Serializable
data class CollectionSettings(
    val mode: CollectionMode = CollectionMode.NONE,
    /** Пользовательское описание для режима CUSTOM */
    val customPrompt: String = "",
    /** Название результата (например, "Техническое задание") */
    val resultTitle: String = "",
    /** Включён ли режим сбора */
    val enabled: Boolean = false
)
