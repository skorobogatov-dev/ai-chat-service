package dev.skorobogatov.mcp.support.storage

import dev.skorobogatov.mcp.support.models.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

@Serializable
data class UsersData(val users: List<User>)

class UserStorage(private val dataFilePath: String = "mcp-support-server/src/main/resources/data/users.json") {
    private val log = LoggerFactory.getLogger(UserStorage::class.java)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var users: MutableList<User> = mutableListOf()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        val file = File(dataFilePath)
        if (file.exists()) {
            try {
                val content = file.readText()
                val data = json.decodeFromString<UsersData>(content)
                users = data.users.toMutableList()
                log.info("Loaded ${users.size} users from $dataFilePath")
            } catch (e: Exception) {
                log.error("Failed to load users: ${e.message}")
                initializeDemoUsers()
            }
        } else {
            log.info("Users file not found, initializing with demo data")
            initializeDemoUsers()
        }
    }

    private fun initializeDemoUsers() {
        users = mutableListOf(
            User(
                userId = "user-001",
                name = "Алексей Петров",
                email = "alex@example.com",
                role = "premium",
                registeredAt = "2024-01-15T10:30:00"
            ),
            User(
                userId = "user-002",
                name = "Мария Иванова",
                email = "maria@example.com",
                role = "basic",
                registeredAt = "2024-03-20T14:45:00"
            ),
            User(
                userId = "user-003",
                name = "Дмитрий Сидоров",
                email = "dmitry@example.com",
                role = "enterprise",
                registeredAt = "2024-02-10T09:15:00"
            ),
            User(
                userId = "user-004",
                name = "Елена Кузнецова",
                email = "elena@example.com",
                role = "premium",
                registeredAt = "2024-04-05T11:20:00"
            ),
            User(
                userId = "user-005",
                name = "Иван Смирнов",
                email = "ivan@example.com",
                role = "basic",
                registeredAt = "2024-05-12T16:30:00"
            )
        )
        log.info("Initialized ${users.size} demo users")
    }

    fun saveUsers() {
        val file = File(dataFilePath)
        file.parentFile.mkdirs()
        try {
            val data = UsersData(users)
            file.writeText(json.encodeToString(data))
            log.info("Saved ${users.size} users to $dataFilePath")
        } catch (e: Exception) {
            log.error("Failed to save users: ${e.message}")
        }
    }

    fun getUser(userId: String): User? {
        return users.firstOrNull { it.userId == userId }
    }

    fun getAllUsers(): List<User> {
        return users.toList()
    }

    fun searchUsers(query: String): List<User> {
        val lowerQuery = query.lowercase()
        return users.filter {
            it.name.lowercase().contains(lowerQuery) ||
            it.email.lowercase().contains(lowerQuery) ||
            it.userId.lowercase().contains(lowerQuery)
        }
    }

    fun addUser(user: User) {
        users.add(user)
        saveUsers()
    }

    fun updateUser(user: User) {
        val index = users.indexOfFirst { it.userId == user.userId }
        if (index != -1) {
            users[index] = user
            saveUsers()
        }
    }

    fun deleteUser(userId: String) {
        users.removeIf { it.userId == userId }
        saveUsers()
    }
}
