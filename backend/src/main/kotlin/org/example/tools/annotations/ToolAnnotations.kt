package org.example.tools.annotations

/**
 * Аннотация для маркировки класса как инструмента агента.
 * Позволяет декларативно определять метаданные инструмента.
 *
 * @param name Уникальное имя инструмента (snake_case)
 * @param description Описание инструмента для LLM
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tool(
    val name: String,
    val description: String
)

/**
 * Аннотация для определения параметра инструмента.
 * Используется для генерации JSON Schema параметров.
 *
 * @param name Имя параметра в JSON
 * @param description Описание параметра для LLM
 * @param type Тип параметра (string, integer, number, boolean, array, object)
 * @param required Обязательность параметра
 * @param enumValues Возможные значения для enum параметров
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class Param(
    val name: String,
    val description: String,
    val type: String = "string",
    val required: Boolean = true,
    val enumValues: Array<String> = []
)

/**
 * Контейнер для множественных @Param аннотаций.
 * Kotlin автоматически использует его для @Repeatable.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Params(val value: Array<Param>)
