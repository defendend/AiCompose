package org.example.tools.support

import kotlinx.serialization.json.*
import org.example.support.*
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool

/**
 * Глобальный репозиторий поддержки.
 * Инициализируется при первом использовании.
 */
object SupportRepositoryHolder {
    val repository: SupportRepository by lazy { SupportRepository() }
}

/**
 * Получить информацию о тикете по ID.
 */
@Tool(
    name = "support_get_ticket",
    description = "Получить детальную информацию о тикете поддержки по ID"
)
@Param(name = "ticket_id", description = "ID тикета (например TKT-001)", type = "string", required = true)
object SupportGetTicketTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val ticketId = json["ticket_id"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: не указан ticket_id"

        val ticket = SupportRepositoryHolder.repository.getTicket(ticketId)
            ?: return "❌ Тикет $ticketId не найден"

        val user = SupportRepositoryHolder.repository.getUser(ticket.userId)

        return buildString {
            appendLine("📋 Тикет: ${ticket.id}")
            appendLine("━".repeat(40))
            appendLine("📌 Тема: ${ticket.subject}")
            appendLine("📝 Описание: ${ticket.description}")
            appendLine()
            appendLine("📊 Статус: ${formatStatus(ticket.status)}")
            appendLine("🏷️ Категория: ${formatCategory(ticket.category)}")
            appendLine("⚡ Приоритет: ${formatPriority(ticket.priority)}")
            appendLine()
            appendLine("👤 Пользователь: ${user?.name ?: ticket.userId}")
            user?.let {
                appendLine("   📧 Email: ${it.email}")
                appendLine("   💳 Тариф: ${it.plan}")
            }
            appendLine()
            appendLine("📅 Создан: ${ticket.createdAt}")
            appendLine("🔄 Обновлён: ${ticket.updatedAt}")

            if (ticket.messages.isNotEmpty()) {
                appendLine()
                appendLine("💬 История сообщений (${ticket.messages.size}):")
                appendLine("─".repeat(40))
                ticket.messages.forEach { msg ->
                    val authorLabel = if (msg.author == "support") "🛟 Поддержка" else "👤 ${user?.name ?: msg.author}"
                    appendLine()
                    appendLine("$authorLabel (${msg.createdAt.take(16)}):")
                    appendLine("   ${msg.content}")
                }
            }
        }
    }

    private fun formatStatus(status: TicketStatus): String = when (status) {
        TicketStatus.OPEN -> "🔴 Открыт"
        TicketStatus.IN_PROGRESS -> "🟡 В работе"
        TicketStatus.WAITING -> "🟠 Ожидает ответа"
        TicketStatus.RESOLVED -> "🟢 Решён"
        TicketStatus.CLOSED -> "⚫ Закрыт"
    }

    private fun formatCategory(category: TicketCategory): String = when (category) {
        TicketCategory.AUTH -> "🔐 Авторизация"
        TicketCategory.BILLING -> "💳 Оплата"
        TicketCategory.TECHNICAL -> "🔧 Технический"
        TicketCategory.FEATURE_REQUEST -> "💡 Запрос функции"
        TicketCategory.BUG -> "🐛 Баг"
        TicketCategory.GENERAL -> "📝 Общий"
    }

    private fun formatPriority(priority: TicketPriority): String = when (priority) {
        TicketPriority.LOW -> "🔵 Низкий"
        TicketPriority.MEDIUM -> "🟡 Средний"
        TicketPriority.HIGH -> "🟠 Высокий"
        TicketPriority.CRITICAL -> "🔴 Критический"
    }
}

/**
 * Поиск тикетов.
 */
@Tool(
    name = "support_search_tickets",
    description = "Поиск тикетов по ключевым словам или статусу"
)
@Param(name = "query", description = "Поисковый запрос (ключевые слова)", type = "string", required = false)
@Param(name = "status", description = "Фильтр по статусу: OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED", type = "string", required = false)
@Param(name = "user_id", description = "Фильтр по ID пользователя", type = "string", required = false)
object SupportSearchTicketsTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val query = json["query"]?.jsonPrimitive?.content
        val statusStr = json["status"]?.jsonPrimitive?.content
        val userId = json["user_id"]?.jsonPrimitive?.content

        var tickets = SupportRepositoryHolder.repository.getAllTickets()

        // Фильтруем по статусу
        statusStr?.let { s ->
            val status = try { TicketStatus.valueOf(s.uppercase()) } catch (e: Exception) { null }
            status?.let { tickets = tickets.filter { it.status == status } }
        }

        // Фильтруем по пользователю
        userId?.let { uid ->
            tickets = tickets.filter { it.userId == uid }
        }

        // Ищем по запросу
        query?.let { q ->
            val qLower = q.lowercase()
            tickets = tickets.filter { ticket ->
                ticket.subject.lowercase().contains(qLower) ||
                ticket.description.lowercase().contains(qLower)
            }
        }

        if (tickets.isEmpty()) {
            return "📭 Тикеты не найдены"
        }

        return buildString {
            appendLine("🔍 Найдено тикетов: ${tickets.size}")
            appendLine("━".repeat(40))
            tickets.take(10).forEach { ticket ->
                val statusIcon = when (ticket.status) {
                    TicketStatus.OPEN -> "🔴"
                    TicketStatus.IN_PROGRESS -> "🟡"
                    TicketStatus.WAITING -> "🟠"
                    TicketStatus.RESOLVED -> "🟢"
                    TicketStatus.CLOSED -> "⚫"
                }
                appendLine()
                appendLine("$statusIcon ${ticket.id}: ${ticket.subject}")
                appendLine("   Категория: ${ticket.category} | Приоритет: ${ticket.priority}")
            }
            if (tickets.size > 10) {
                appendLine()
                appendLine("... и ещё ${tickets.size - 10} тикетов")
            }
        }
    }
}

/**
 * Получить информацию о пользователе.
 */
@Tool(
    name = "support_get_user",
    description = "Получить информацию о пользователе по ID или email"
)
@Param(name = "user_id", description = "ID пользователя", type = "string", required = false)
@Param(name = "email", description = "Email пользователя", type = "string", required = false)
object SupportGetUserTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val userId = json["user_id"]?.jsonPrimitive?.content
        val email = json["email"]?.jsonPrimitive?.content

        if (userId == null && email == null) {
            return "❌ Ошибка: укажите user_id или email"
        }

        val user = userId?.let { SupportRepositoryHolder.repository.getUser(it) }
            ?: email?.let { SupportRepositoryHolder.repository.getUserByEmail(it) }
            ?: return "❌ Пользователь не найден"

        val tickets = SupportRepositoryHolder.repository.getTicketsByUser(user.id)
        val openTickets = tickets.count { it.status == TicketStatus.OPEN || it.status == TicketStatus.IN_PROGRESS }

        return buildString {
            appendLine("👤 Пользователь: ${user.name}")
            appendLine("━".repeat(40))
            appendLine("🆔 ID: ${user.id}")
            appendLine("📧 Email: ${user.email}")
            appendLine("💳 Тариф: ${formatPlan(user.plan)}")
            appendLine()
            appendLine("📅 Зарегистрирован: ${user.registeredAt.take(10)}")
            user.lastLoginAt?.let { appendLine("🕐 Последний вход: ${it.take(16)}") }
            appendLine()
            appendLine("📋 Тикетов всего: ${tickets.size}")
            appendLine("   🔴 Открытых: $openTickets")
        }
    }

    private fun formatPlan(plan: UserPlan): String = when (plan) {
        UserPlan.FREE -> "🆓 Free"
        UserPlan.BASIC -> "📘 Basic"
        UserPlan.PRO -> "⭐ Pro"
        UserPlan.ENTERPRISE -> "🏢 Enterprise"
    }
}

/**
 * Создать новый тикет.
 */
@Tool(
    name = "support_create_ticket",
    description = "Создать новый тикет поддержки"
)
@Param(name = "user_id", description = "ID пользователя", type = "string", required = true)
@Param(name = "subject", description = "Тема тикета", type = "string", required = true)
@Param(name = "description", description = "Описание проблемы", type = "string", required = true)
@Param(name = "category", description = "Категория: AUTH, BILLING, TECHNICAL, FEATURE_REQUEST, BUG, GENERAL", type = "string", required = false)
@Param(name = "priority", description = "Приоритет: LOW, MEDIUM, HIGH, CRITICAL", type = "string", required = false)
object SupportCreateTicketTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val userId = json["user_id"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: не указан user_id"
        val subject = json["subject"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: не указана тема (subject)"
        val description = json["description"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: не указано описание (description)"

        val category = json["category"]?.jsonPrimitive?.content?.let {
            try { TicketCategory.valueOf(it.uppercase()) } catch (e: Exception) { TicketCategory.GENERAL }
        } ?: TicketCategory.GENERAL

        val priority = json["priority"]?.jsonPrimitive?.content?.let {
            try { TicketPriority.valueOf(it.uppercase()) } catch (e: Exception) { TicketPriority.MEDIUM }
        } ?: TicketPriority.MEDIUM

        val ticket = SupportRepositoryHolder.repository.createTicket(
            userId = userId,
            subject = subject,
            description = description,
            category = category,
            priority = priority
        )

        return buildString {
            appendLine("✅ Тикет создан!")
            appendLine("━".repeat(40))
            appendLine("🆔 ID: ${ticket.id}")
            appendLine("📌 Тема: ${ticket.subject}")
            appendLine("🏷️ Категория: $category")
            appendLine("⚡ Приоритет: $priority")
            appendLine("📊 Статус: OPEN")
        }
    }
}

/**
 * Обновить статус тикета.
 */
@Tool(
    name = "support_update_ticket",
    description = "Обновить статус тикета или добавить сообщение"
)
@Param(name = "ticket_id", description = "ID тикета", type = "string", required = true)
@Param(name = "status", description = "Новый статус: OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED", type = "string", required = false)
@Param(name = "message", description = "Добавить сообщение в тикет", type = "string", required = false)
object SupportUpdateTicketTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val ticketId = json["ticket_id"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: не указан ticket_id"
        val statusStr = json["status"]?.jsonPrimitive?.content
        val message = json["message"]?.jsonPrimitive?.content

        if (statusStr == null && message == null) {
            return "❌ Ошибка: укажите status или message"
        }

        val updates = mutableListOf<String>()

        // Обновляем статус
        statusStr?.let { s ->
            val status = try { TicketStatus.valueOf(s.uppercase()) } catch (e: Exception) { null }
            if (status != null) {
                SupportRepositoryHolder.repository.updateTicketStatus(ticketId, status)
                updates.add("📊 Статус изменён на: $status")
            } else {
                updates.add("⚠️ Неизвестный статус: $s")
            }
        }

        // Добавляем сообщение
        message?.let { msg ->
            SupportRepositoryHolder.repository.addMessageToTicket(ticketId, "support", msg)
            updates.add("💬 Добавлено сообщение от поддержки")
        }

        val ticket = SupportRepositoryHolder.repository.getTicket(ticketId)
            ?: return "❌ Тикет $ticketId не найден"

        return buildString {
            appendLine("✅ Тикет ${ticket.id} обновлён")
            appendLine("━".repeat(40))
            updates.forEach { appendLine(it) }
        }
    }
}

/**
 * Получить FAQ.
 */
@Tool(
    name = "support_get_faq",
    description = "Получить FAQ (часто задаваемые вопросы) по категории или поиску"
)
@Param(name = "query", description = "Поисковый запрос", type = "string", required = false)
@Param(name = "category", description = "Категория: AUTH, BILLING, TECHNICAL, FEATURE_REQUEST, BUG, GENERAL", type = "string", required = false)
object SupportGetFaqTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val query = json["query"]?.jsonPrimitive?.content
        val categoryStr = json["category"]?.jsonPrimitive?.content

        var faq = SupportRepositoryHolder.repository.getAllFaq()

        // Фильтруем по категории
        categoryStr?.let { c ->
            val category = try { TicketCategory.valueOf(c.uppercase()) } catch (e: Exception) { null }
            category?.let { faq = faq.filter { it.category == category } }
        }

        // Ищем по запросу
        query?.let { q ->
            faq = SupportRepositoryHolder.repository.searchFaq(q)
        }

        if (faq.isEmpty()) {
            return "📭 FAQ не найдены"
        }

        return buildString {
            appendLine("📚 FAQ (${faq.size} записей)")
            appendLine("━".repeat(50))
            faq.forEach { entry ->
                appendLine()
                appendLine("❓ ${entry.question}")
                appendLine("─".repeat(40))
                appendLine("💡 ${entry.answer}")
                appendLine()
                appendLine("🏷️ Категория: ${entry.category}")
            }
        }
    }
}

/**
 * Получить статистику поддержки.
 */
@Tool(
    name = "support_get_stats",
    description = "Получить общую статистику поддержки: количество тикетов, пользователей и т.д."
)
object SupportGetStatsTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val stats = SupportRepositoryHolder.repository.getStats()

        return buildString {
            appendLine("📊 Статистика поддержки")
            appendLine("━".repeat(40))
            appendLine()
            appendLine("📋 Тикеты:")
            appendLine("   📁 Всего: ${stats["totalTickets"]}")
            appendLine("   🔴 Открыто: ${stats["openTickets"]}")
            appendLine("   🟡 В работе: ${stats["inProgressTickets"]}")
            appendLine("   🟢 Решено: ${stats["resolvedTickets"]}")
            appendLine()
            appendLine("👥 Пользователей: ${stats["totalUsers"]}")
            appendLine("📚 FAQ записей: ${stats["faqCount"]}")
        }
    }
}
