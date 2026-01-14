package org.example.support

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Репозиторий данных поддержки.
 * Хранит тикеты, пользователей и FAQ в JSON файле.
 */
class SupportRepository(
    private val dataFile: String = "support_data.json"
) {
    private val logger = LoggerFactory.getLogger(SupportRepository::class.java)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private var data: SupportData = loadOrCreateData()

    private fun loadOrCreateData(): SupportData {
        val file = File(dataFile)
        return if (file.exists()) {
            try {
                val content = file.readText()
                json.decodeFromString<SupportData>(content).also {
                    logger.info("Загружено ${it.tickets.size} тикетов, ${it.users.size} пользователей, ${it.faq.size} FAQ")
                }
            } catch (e: Exception) {
                logger.error("Ошибка загрузки данных поддержки: ${e.message}")
                createSampleData()
            }
        } else {
            logger.info("Файл данных не найден, создаём тестовые данные")
            createSampleData()
        }
    }

    private fun save() {
        try {
            File(dataFile).writeText(json.encodeToString(data))
        } catch (e: Exception) {
            logger.error("Ошибка сохранения данных: ${e.message}")
        }
    }

    // === Тикеты ===

    fun getTicket(id: String): Ticket? = data.tickets.find { it.id == id }

    fun getTicketsByUser(userId: String): List<Ticket> =
        data.tickets.filter { it.userId == userId }

    fun getTicketsByStatus(status: TicketStatus): List<Ticket> =
        data.tickets.filter { it.status == status }

    fun searchTickets(query: String): List<Ticket> {
        val queryLower = query.lowercase()
        return data.tickets.filter { ticket ->
            ticket.subject.lowercase().contains(queryLower) ||
            ticket.description.lowercase().contains(queryLower) ||
            ticket.messages.any { it.content.lowercase().contains(queryLower) }
        }
    }

    fun createTicket(
        userId: String,
        subject: String,
        description: String,
        category: TicketCategory = TicketCategory.GENERAL,
        priority: TicketPriority = TicketPriority.MEDIUM
    ): Ticket {
        val now = Instant.now().toString()
        val ticket = Ticket(
            id = "TKT-${UUID.randomUUID().toString().take(8).uppercase()}",
            userId = userId,
            subject = subject,
            description = description,
            category = category,
            priority = priority,
            status = TicketStatus.OPEN,
            createdAt = now,
            updatedAt = now
        )
        data = data.copy(tickets = data.tickets + ticket)
        save()
        logger.info("Создан тикет ${ticket.id}")
        return ticket
    }

    fun updateTicketStatus(id: String, status: TicketStatus): Ticket? {
        val ticket = getTicket(id) ?: return null
        val updated = ticket.copy(
            status = status,
            updatedAt = Instant.now().toString()
        )
        data = data.copy(tickets = data.tickets.map { if (it.id == id) updated else it })
        save()
        logger.info("Тикет $id обновлён: статус = $status")
        return updated
    }

    fun addMessageToTicket(ticketId: String, author: String, content: String): Ticket? {
        val ticket = getTicket(ticketId) ?: return null
        val message = TicketMessage(
            id = UUID.randomUUID().toString().take(8),
            author = author,
            content = content,
            createdAt = Instant.now().toString()
        )
        val updated = ticket.copy(
            messages = ticket.messages + message,
            updatedAt = Instant.now().toString()
        )
        data = data.copy(tickets = data.tickets.map { if (it.id == ticketId) updated else it })
        save()
        return updated
    }

    fun getAllTickets(): List<Ticket> = data.tickets

    fun getOpenTicketsCount(): Int = data.tickets.count { it.status == TicketStatus.OPEN }

    // === Пользователи ===

    fun getUser(id: String): SupportUser? = data.users.find { it.id == id }

    fun getUserByEmail(email: String): SupportUser? =
        data.users.find { it.email.equals(email, ignoreCase = true) }

    fun getAllUsers(): List<SupportUser> = data.users

    // === FAQ ===

    fun getAllFaq(): List<FaqEntry> = data.faq

    fun getFaqByCategory(category: TicketCategory): List<FaqEntry> =
        data.faq.filter { it.category == category }

    fun searchFaq(query: String): List<FaqEntry> {
        val queryLower = query.lowercase()
        return data.faq.filter { faq ->
            faq.question.lowercase().contains(queryLower) ||
            faq.answer.lowercase().contains(queryLower) ||
            faq.keywords.any { it.lowercase().contains(queryLower) }
        }
    }

    // === Статистика ===

    fun getStats(): Map<String, Any> = mapOf(
        "totalTickets" to data.tickets.size,
        "openTickets" to data.tickets.count { it.status == TicketStatus.OPEN },
        "inProgressTickets" to data.tickets.count { it.status == TicketStatus.IN_PROGRESS },
        "resolvedTickets" to data.tickets.count { it.status == TicketStatus.RESOLVED },
        "totalUsers" to data.users.size,
        "faqCount" to data.faq.size
    )

    // === Тестовые данные ===

    private fun createSampleData(): SupportData {
        val now = Instant.now()

        val users = listOf(
            SupportUser(
                id = "USR-001",
                name = "Иван Петров",
                email = "ivan@example.com",
                plan = UserPlan.PRO,
                registeredAt = now.minusSeconds(86400 * 30).toString(),
                lastLoginAt = now.minusSeconds(3600).toString(),
                ticketCount = 3
            ),
            SupportUser(
                id = "USR-002",
                name = "Мария Сидорова",
                email = "maria@example.com",
                plan = UserPlan.BASIC,
                registeredAt = now.minusSeconds(86400 * 60).toString(),
                lastLoginAt = now.minusSeconds(86400).toString(),
                ticketCount = 1
            ),
            SupportUser(
                id = "USR-003",
                name = "Алексей Козлов",
                email = "alexey@company.com",
                plan = UserPlan.ENTERPRISE,
                registeredAt = now.minusSeconds(86400 * 90).toString(),
                lastLoginAt = now.minusSeconds(7200).toString(),
                ticketCount = 2
            )
        )

        val tickets = listOf(
            Ticket(
                id = "TKT-001",
                userId = "USR-001",
                subject = "Не могу войти в аккаунт",
                description = "После обновления пароля не могу авторизоваться. Пишет 'Неверный пароль', хотя я точно ввожу правильный.",
                category = TicketCategory.AUTH,
                status = TicketStatus.OPEN,
                priority = TicketPriority.HIGH,
                messages = listOf(
                    TicketMessage(
                        id = "MSG-001",
                        author = "USR-001",
                        content = "Пробовал сбросить пароль через email, но письмо не приходит уже 30 минут.",
                        createdAt = now.minusSeconds(1800).toString()
                    )
                ),
                createdAt = now.minusSeconds(3600).toString(),
                updatedAt = now.minusSeconds(1800).toString()
            ),
            Ticket(
                id = "TKT-002",
                userId = "USR-001",
                subject = "Ошибка при оплате подписки",
                description = "При попытке оплатить PRO подписку выдаёт ошибку 'Payment failed'. Карта рабочая, проверял.",
                category = TicketCategory.BILLING,
                status = TicketStatus.IN_PROGRESS,
                priority = TicketPriority.MEDIUM,
                messages = listOf(
                    TicketMessage(
                        id = "MSG-002",
                        author = "support",
                        content = "Добрый день! Проверяем логи платёжной системы. Уточните, пожалуйста, какой банк выпустил карту?",
                        createdAt = now.minusSeconds(7200).toString()
                    ),
                    TicketMessage(
                        id = "MSG-003",
                        author = "USR-001",
                        content = "Сбербанк, карта Visa.",
                        createdAt = now.minusSeconds(3600).toString()
                    )
                ),
                createdAt = now.minusSeconds(86400).toString(),
                updatedAt = now.minusSeconds(3600).toString()
            ),
            Ticket(
                id = "TKT-003",
                userId = "USR-002",
                subject = "Как экспортировать данные?",
                description = "Подскажите, как экспортировать историю чатов в JSON формате?",
                category = TicketCategory.GENERAL,
                status = TicketStatus.RESOLVED,
                priority = TicketPriority.LOW,
                messages = listOf(
                    TicketMessage(
                        id = "MSG-004",
                        author = "support",
                        content = "Перейдите в Настройки → Экспорт → выберите JSON формат. Подробная инструкция в документации: docs/export.md",
                        createdAt = now.minusSeconds(172800).toString()
                    )
                ),
                createdAt = now.minusSeconds(259200).toString(),
                updatedAt = now.minusSeconds(172800).toString()
            ),
            Ticket(
                id = "TKT-004",
                userId = "USR-003",
                subject = "API возвращает 500 ошибку",
                description = "При вызове POST /api/chat периодически получаем Internal Server Error. Происходит примерно в 10% запросов.",
                category = TicketCategory.TECHNICAL,
                status = TicketStatus.OPEN,
                priority = TicketPriority.CRITICAL,
                messages = listOf(
                    TicketMessage(
                        id = "MSG-005",
                        author = "USR-003",
                        content = "Прикрепляю логи с request_id для отладки: req-abc123, req-def456, req-ghi789",
                        createdAt = now.minusSeconds(1800).toString()
                    )
                ),
                createdAt = now.minusSeconds(3600).toString(),
                updatedAt = now.minusSeconds(1800).toString()
            ),
            Ticket(
                id = "TKT-005",
                userId = "USR-003",
                subject = "Запрос на интеграцию с Slack",
                description = "Планируется ли интеграция с Slack для получения уведомлений о новых сообщениях?",
                category = TicketCategory.FEATURE_REQUEST,
                status = TicketStatus.WAITING,
                priority = TicketPriority.LOW,
                messages = listOf(
                    TicketMessage(
                        id = "MSG-006",
                        author = "support",
                        content = "Спасибо за предложение! Передали в команду разработки. Slack интеграция запланирована на Q2 2025.",
                        createdAt = now.minusSeconds(604800).toString()
                    )
                ),
                createdAt = now.minusSeconds(1209600).toString(),
                updatedAt = now.minusSeconds(604800).toString()
            )
        )

        val faq = listOf(
            FaqEntry(
                id = "FAQ-001",
                question = "Как сбросить пароль?",
                answer = "Для сброса пароля: 1) Нажмите 'Забыли пароль?' на странице входа. 2) Введите email, привязанный к аккаунту. 3) Проверьте почту (включая папку Спам). 4) Перейдите по ссылке из письма. 5) Задайте новый пароль. Если письмо не приходит более 15 минут, обратитесь в поддержку.",
                category = TicketCategory.AUTH,
                keywords = listOf("пароль", "сброс", "забыл", "восстановление", "логин", "вход")
            ),
            FaqEntry(
                id = "FAQ-002",
                question = "Почему не работает авторизация?",
                answer = "Частые причины проблем с авторизацией: 1) Неверный пароль — попробуйте сбросить. 2) Caps Lock включён. 3) Аккаунт заблокирован — обратитесь в поддержку. 4) Cookies отключены в браузере. 5) VPN может блокировать доступ. Попробуйте очистить кэш браузера и войти заново.",
                category = TicketCategory.AUTH,
                keywords = listOf("авторизация", "логин", "вход", "не работает", "ошибка", "войти")
            ),
            FaqEntry(
                id = "FAQ-003",
                question = "Как оплатить подписку?",
                answer = "Для оплаты подписки: 1) Перейдите в Настройки → Подписка. 2) Выберите тарифный план. 3) Нажмите 'Оплатить'. 4) Введите данные карты. Принимаем Visa, MasterCard, МИР. Для Enterprise плана доступна оплата по счёту — напишите на billing@example.com.",
                category = TicketCategory.BILLING,
                keywords = listOf("оплата", "подписка", "тариф", "план", "карта", "платёж")
            ),
            FaqEntry(
                id = "FAQ-004",
                question = "Какие есть тарифные планы?",
                answer = "Доступные планы: 1) FREE — базовые функции, 100 запросов/день. 2) BASIC ($9/мес) — 1000 запросов/день, история чатов. 3) PRO ($29/мес) — безлимит запросов, приоритетная поддержка, API доступ. 4) ENTERPRISE (по запросу) — выделенный сервер, SLA, интеграции.",
                category = TicketCategory.BILLING,
                keywords = listOf("тариф", "план", "цена", "стоимость", "free", "pro", "enterprise")
            ),
            FaqEntry(
                id = "FAQ-005",
                question = "Как экспортировать данные?",
                answer = "Экспорт данных: 1) Откройте Настройки → Данные → Экспорт. 2) Выберите формат (JSON, CSV, Markdown). 3) Выберите период или 'Все данные'. 4) Нажмите 'Экспортировать'. Файл скачается автоматически. Для PRO и Enterprise доступен экспорт через API.",
                category = TicketCategory.GENERAL,
                keywords = listOf("экспорт", "данные", "скачать", "json", "csv", "backup")
            ),
            FaqEntry(
                id = "FAQ-006",
                question = "API возвращает ошибку 500",
                answer = "Ошибка 500 (Internal Server Error) может возникать по причинам: 1) Временные проблемы сервера — повторите запрос через минуту. 2) Слишком большой запрос — уменьшите размер. 3) Невалидный JSON в теле запроса. 4) Превышен лимит запросов. Если ошибка повторяется, сообщите request_id в поддержку.",
                category = TicketCategory.TECHNICAL,
                keywords = listOf("500", "ошибка", "api", "сервер", "internal", "error")
            ),
            FaqEntry(
                id = "FAQ-007",
                question = "Как подключить API?",
                answer = "Для подключения API: 1) Получите API ключ в Настройки → API. 2) Добавьте заголовок Authorization: Bearer YOUR_API_KEY. 3) Базовый URL: https://api.example.com/v1. 4) Документация: docs.example.com/api. Лимиты зависят от тарифа. Пример запроса в документации.",
                category = TicketCategory.TECHNICAL,
                keywords = listOf("api", "ключ", "интеграция", "подключение", "token", "bearer")
            ),
            FaqEntry(
                id = "FAQ-008",
                question = "Как удалить аккаунт?",
                answer = "Для удаления аккаунта: 1) Перейдите в Настройки → Аккаунт → Удаление. 2) Введите пароль для подтверждения. 3) Нажмите 'Удалить аккаунт'. Внимание: все данные будут удалены безвозвратно. Перед удалением рекомендуем экспортировать данные.",
                category = TicketCategory.GENERAL,
                keywords = listOf("удалить", "аккаунт", "удаление", "закрыть", "деактивация")
            )
        )

        val sampleData = SupportData(
            tickets = tickets,
            users = users,
            faq = faq
        )

        // Сохраняем тестовые данные
        try {
            File(dataFile).writeText(json.encodeToString(sampleData))
            logger.info("Созданы тестовые данные: ${tickets.size} тикетов, ${users.size} пользователей, ${faq.size} FAQ")
        } catch (e: Exception) {
            logger.error("Ошибка сохранения тестовых данных: ${e.message}")
        }

        return sampleData
    }

    /**
     * Пересоздать тестовые данные.
     */
    fun resetToSampleData() {
        data = createSampleData()
        logger.info("Данные поддержки сброшены к тестовым")
    }
}
