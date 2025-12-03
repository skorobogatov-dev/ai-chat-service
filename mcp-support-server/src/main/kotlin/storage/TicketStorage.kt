package dev.skorobogatov.mcp.support.storage

import dev.skorobogatov.mcp.support.models.Ticket
import dev.skorobogatov.mcp.support.models.TicketStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

@Serializable
data class TicketsData(val tickets: List<Ticket>)

class TicketStorage(private val dataFilePath: String = "mcp-support-server/src/main/resources/data/tickets.json") {
    private val log = LoggerFactory.getLogger(TicketStorage::class.java)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var tickets: MutableList<Ticket> = mutableListOf()

    init {
        loadTickets()
    }

    private fun loadTickets() {
        val file = File(dataFilePath)
        if (file.exists()) {
            try {
                val content = file.readText()
                val data = json.decodeFromString<TicketsData>(content)
                tickets = data.tickets.toMutableList()
                log.info("Loaded ${tickets.size} tickets from $dataFilePath")
            } catch (e: Exception) {
                log.error("Failed to load tickets: ${e.message}")
                initializeDemoTickets()
            }
        } else {
            log.info("Tickets file not found, initializing with demo data")
            initializeDemoTickets()
        }
    }

    private fun initializeDemoTickets() {
        tickets = mutableListOf(
            Ticket(
                id = "ticket-001",
                userId = "user-001",
                subject = "Не работает авторизация через Google",
                description = "При попытке войти через Google появляется ошибка 'OAuth failed'",
                status = TicketStatus.RESOLVED,
                priority = dev.skorobogatov.mcp.support.models.Priority.HIGH,
                createdAt = "2024-11-10T10:00:00",
                updatedAt = "2024-11-11T14:30:00",
                assignedTo = "Support Team",
                messages = listOf(
                    dev.skorobogatov.mcp.support.models.TicketMessage(
                        id = "msg-001",
                        author = "Алексей Петров",
                        content = "При попытке войти через Google появляется ошибка 'OAuth failed'",
                        timestamp = "2024-11-10T10:00:00",
                        isFromSupport = false
                    ),
                    dev.skorobogatov.mcp.support.models.TicketMessage(
                        id = "msg-002",
                        author = "Support Agent",
                        content = "Проверьте разрешения в настройках Google аккаунта. Также очистите кэш браузера.",
                        timestamp = "2024-11-10T12:15:00",
                        isFromSupport = true
                    ),
                    dev.skorobogatov.mcp.support.models.TicketMessage(
                        id = "msg-003",
                        author = "Алексей Петров",
                        content = "Помогло! Спасибо!",
                        timestamp = "2024-11-11T14:30:00",
                        isFromSupport = false
                    )
                )
            ),
            Ticket(
                id = "ticket-002",
                userId = "user-002",
                subject = "Как экспортировать задачи в CSV?",
                description = "Не могу найти кнопку экспорта в интерфейсе",
                status = TicketStatus.OPEN,
                priority = dev.skorobogatov.mcp.support.models.Priority.MEDIUM,
                createdAt = "2024-11-15T09:30:00",
                updatedAt = "2024-11-15T09:30:00",
                messages = listOf(
                    dev.skorobogatov.mcp.support.models.TicketMessage(
                        id = "msg-004",
                        author = "Мария Иванова",
                        content = "Не могу найти кнопку экспорта в интерфейсе",
                        timestamp = "2024-11-15T09:30:00",
                        isFromSupport = false
                    )
                )
            ),
            Ticket(
                id = "ticket-003",
                userId = "user-001",
                subject = "Ошибка при создании подзадачи",
                description = "При попытке создать подзадачу появляется ошибка 500",
                status = TicketStatus.IN_PROGRESS,
                priority = dev.skorobogatov.mcp.support.models.Priority.HIGH,
                createdAt = "2024-11-18T11:00:00",
                updatedAt = "2024-11-18T15:45:00",
                assignedTo = "Dev Team",
                messages = listOf(
                    dev.skorobogatov.mcp.support.models.TicketMessage(
                        id = "msg-005",
                        author = "Алексей Петров",
                        content = "При попытке создать подзадачу появляется ошибка 500",
                        timestamp = "2024-11-18T11:00:00",
                        isFromSupport = false
                    ),
                    dev.skorobogatov.mcp.support.models.TicketMessage(
                        id = "msg-006",
                        author = "Support Agent",
                        content = "Передали баг в разработку. Разбираемся.",
                        timestamp = "2024-11-18T15:45:00",
                        isFromSupport = true
                    )
                )
            ),
            Ticket(
                id = "ticket-004",
                userId = "user-003",
                subject = "Не приходят email-уведомления",
                description = "Настроил уведомления, но письма не приходят",
                status = TicketStatus.WAITING_USER,
                priority = dev.skorobogatov.mcp.support.models.Priority.MEDIUM,
                createdAt = "2024-11-19T14:20:00",
                updatedAt = "2024-11-19T16:10:00",
                assignedTo = "Support Team",
                messages = listOf(
                    dev.skorobogatov.mcp.support.models.TicketMessage(
                        id = "msg-007",
                        author = "Дмитрий Сидоров",
                        content = "Настроил уведомления, но письма не приходят",
                        timestamp = "2024-11-19T14:20:00",
                        isFromSupport = false
                    ),
                    dev.skorobogatov.mcp.support.models.TicketMessage(
                        id = "msg-008",
                        author = "Support Agent",
                        content = "Проверьте папку Спам. Также убедитесь, что email указан правильно в профиле.",
                        timestamp = "2024-11-19T16:10:00",
                        isFromSupport = true
                    )
                )
            ),
            Ticket(
                id = "ticket-005",
                userId = "user-004",
                subject = "Проблема с синхронизацией календаря",
                description = "Задачи не появляются в Google Calendar",
                status = TicketStatus.OPEN,
                priority = dev.skorobogatov.mcp.support.models.Priority.LOW,
                createdAt = "2024-11-20T10:15:00",
                updatedAt = "2024-11-20T10:15:00",
                messages = listOf(
                    dev.skorobogatov.mcp.support.models.TicketMessage(
                        id = "msg-009",
                        author = "Елена Кузнецова",
                        content = "Задачи не появляются в Google Calendar",
                        timestamp = "2024-11-20T10:15:00",
                        isFromSupport = false
                    )
                )
            )
        )
        log.info("Initialized ${tickets.size} demo tickets")
    }

    fun saveTickets() {
        val file = File(dataFilePath)
        file.parentFile.mkdirs()
        try {
            val data = TicketsData(tickets)
            file.writeText(json.encodeToString(data))
            log.info("Saved ${tickets.size} tickets to $dataFilePath")
        } catch (e: Exception) {
            log.error("Failed to save tickets: ${e.message}")
        }
    }

    fun getTicket(ticketId: String): Ticket? {
        return tickets.firstOrNull { it.id == ticketId }
    }

    fun getAllTickets(): List<Ticket> {
        return tickets.toList()
    }

    fun getUserTickets(userId: String, status: TicketStatus? = null): List<Ticket> {
        return tickets.filter { ticket ->
            ticket.userId == userId && (status == null || ticket.status == status)
        }
    }

    fun searchTickets(query: String, status: TicketStatus? = null, userId: String? = null): List<Ticket> {
        val lowerQuery = query.lowercase()
        return tickets.filter { ticket ->
            val matchesQuery = ticket.subject.lowercase().contains(lowerQuery) ||
                               ticket.description.lowercase().contains(lowerQuery) ||
                               ticket.id.lowercase().contains(lowerQuery)
            val matchesStatus = status == null || ticket.status == status
            val matchesUserId = userId == null || ticket.userId == userId

            matchesQuery && matchesStatus && matchesUserId
        }
    }

    fun addTicket(ticket: Ticket) {
        tickets.add(ticket)
        saveTickets()
    }

    fun updateTicket(ticket: Ticket) {
        val index = tickets.indexOfFirst { it.id == ticket.id }
        if (index != -1) {
            tickets[index] = ticket
            saveTickets()
        }
    }

    fun deleteTicket(ticketId: String) {
        tickets.removeIf { it.id == ticketId }
        saveTickets()
    }
}
