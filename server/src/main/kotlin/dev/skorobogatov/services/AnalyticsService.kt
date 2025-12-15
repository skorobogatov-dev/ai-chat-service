package dev.skorobogatov.services

import dev.skorobogatov.models.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Сервис аналитики тикетов с RAG-поиском
 * Генерирует тикеты при старте и векторизует их для семантического поиска
 */
class AnalyticsService(
    private val ollamaService: OllamaService,
    private val ollamaChatService: OllamaChatService,
    private val ticketCount: Int = 500
) {
    private val logger = LoggerFactory.getLogger(AnalyticsService::class.java)

    private val tickets = mutableListOf<Ticket>()
    private val ticketsWithEmbeddings = mutableListOf<TicketWithEmbedding>()
    private var embeddingDimension = 0
    private var initialized = false

    /**
     * Инициализация: генерация тикетов и векторизация
     */
    suspend fun initialize() {
        logger.info("Initializing AnalyticsService with $ticketCount tickets...")
        val startTime = System.currentTimeMillis()

        // Генерируем тикеты
        generateTickets()
        logger.info("Generated ${tickets.size} tickets")

        // Векторизуем тикеты
        vectorizeTickets()

        val totalTime = System.currentTimeMillis() - startTime
        logger.info("AnalyticsService initialized in ${totalTime}ms. ${ticketsWithEmbeddings.size} tickets vectorized.")
        initialized = true
    }

    /**
     * Генерация тестовых тикетов
     */
    private fun generateTickets() {
        val categories = listOf("auth", "payment", "ui", "performance", "integration", "billing", "account", "feature_request", "bug", "data")
        val priorities = listOf("low", "medium", "high", "critical")
        val statuses = listOf("open", "in_progress", "resolved", "closed", "waiting_for_user")
        val userSegments = listOf("free", "basic", "premium", "enterprise")
        val funnelStages = listOf("registration", "onboarding", "login", "dashboard", "checkout", "settings", "profile", "reports", "integrations", "billing")
        val browsers = listOf("Chrome", "Firefox", "Safari", "Edge", "Opera", null)
        val platforms = listOf("Windows", "macOS", "Linux", "iOS", "Android", null)

        val ticketTemplates = mapOf(
            "auth" to listOf(
                Triple("Не могу войти в систему", "При попытке входа получаю ошибку авторизации. Пароль точно правильный.", "AUTH_001"),
                Triple("Двухфакторная аутентификация не работает", "Код из приложения не принимается, хотя время синхронизировано.", "AUTH_002"),
                Triple("Сброс пароля не приходит на почту", "Запрашиваю сброс пароля, но письмо не приходит уже 30 минут.", "AUTH_003"),
                Triple("Сессия истекает слишком быстро", "Каждые 5 минут приходится заново входить в систему.", "AUTH_004"),
                Triple("Ошибка при входе через Google", "OAuth авторизация через Google возвращает ошибку.", "AUTH_005"),
                Triple("Заблокирован аккаунт после неудачных попыток", "Ввел пароль неправильно 3 раза, теперь аккаунт заблокирован.", "AUTH_006"),
                Triple("Не могу выйти из системы", "Кнопка выхода не работает, сессия не закрывается.", "AUTH_007"),
                Triple("SSO не работает с корпоративным провайдером", "SAML интеграция возвращает ошибку валидации.", "AUTH_008")
            ),
            "payment" to listOf(
                Triple("Ошибка при оплате картой", "При попытке оплаты появляется ошибка 'Payment failed'. Карта валидна.", "PAY_001"),
                Triple("Двойное списание средств", "С карты списали деньги дважды за одну подписку.", "PAY_002"),
                Triple("Не применяется промокод", "Ввожу промокод SAVE20, но скидка не применяется.", "PAY_003"),
                Triple("Ошибка 3D Secure", "Банк отклоняет платеж на этапе 3D Secure верификации.", "PAY_004"),
                Triple("PayPal интеграция не работает", "При выборе PayPal происходит редирект на пустую страницу.", "PAY_005"),
                Triple("Не могу обновить платежные данные", "Форма обновления карты выдает ошибку валидации.", "PAY_006"),
                Triple("Автопродление не сработало", "Подписка должна была продлиться автоматически, но не продлилась.", "PAY_007"),
                Triple("Неверная сумма в счете", "В инвойсе указана сумма больше, чем должна быть по тарифу.", "PAY_008")
            ),
            "ui" to listOf(
                Triple("Кнопка не реагирует на клики", "Кнопка 'Сохранить' в настройках не работает.", "UI_001"),
                Triple("Страница не загружается полностью", "Dashboard показывает вечный спиннер загрузки.", "UI_002"),
                Triple("Элементы наезжают друг на друга", "На мобильном устройстве меню перекрывает контент.", "UI_003"),
                Triple("Темная тема отображается некорректно", "При включении темной темы текст становится нечитаемым.", "UI_004"),
                Triple("Модальное окно не закрывается", "Попап с подтверждением не реагирует на кнопку закрытия.", "UI_005"),
                Triple("Таблица не помещается на экран", "Горизонтальный скролл не работает в таблице отчетов.", "UI_006"),
                Triple("Иконки не отображаются", "Вместо иконок показываются пустые квадраты.", "UI_007"),
                Triple("Drag and drop не работает", "Не могу перетащить задачи между колонками.", "UI_008")
            ),
            "performance" to listOf(
                Triple("Страница загружается очень медленно", "Dashboard грузится более 10 секунд.", "PERF_001"),
                Triple("Приложение зависает при большом количестве данных", "При открытии отчета с 10000 записей браузер зависает.", "PERF_002"),
                Triple("Поиск работает слишком долго", "Поиск по документам занимает более минуты.", "PERF_003"),
                Triple("Экспорт данных таймаутит", "При экспорте в Excel получаю таймаут ошибку.", "PERF_004"),
                Triple("API отвечает с большой задержкой", "Запросы к API занимают 5-10 секунд.", "PERF_005"),
                Triple("Память браузера переполняется", "После часа работы Chrome съедает 4GB RAM.", "PERF_006"),
                Triple("Графики рендерятся с задержкой", "Построение графиков занимает 30+ секунд.", "PERF_007"),
                Triple("Пагинация не работает эффективно", "При переходе на следующую страницу данные загружаются заново.", "PERF_008")
            ),
            "integration" to listOf(
                Triple("Slack интеграция перестала работать", "Уведомления в Slack больше не приходят.", "INT_001"),
                Triple("Webhook не вызывается", "Настроенный webhook не получает события.", "INT_002"),
                Triple("Jira синхронизация сломалась", "Задачи из Jira не импортируются в систему.", "INT_003"),
                Triple("Google Calendar не синхронизируется", "События не появляются в подключенном календаре.", "INT_004"),
                Triple("API ключ не работает", "Запросы с API ключом возвращают 401 ошибку.", "INT_005"),
                Triple("Zapier триггер не срабатывает", "Автоматизации через Zapier перестали работать.", "INT_006"),
                Triple("GitHub интеграция отключилась", "Коммиты больше не связываются с задачами.", "INT_007"),
                Triple("Email forwarding не работает", "Письма на служебный адрес не создают тикеты.", "INT_008")
            ),
            "billing" to listOf(
                Triple("Не могу скачать инвойс", "Кнопка скачивания инвойса не работает.", "BILL_001"),
                Triple("Неверный период в счете", "В инвойсе указан неправильный расчетный период.", "BILL_002"),
                Triple("Хочу изменить тариф", "Как перейти с Basic на Premium план?", "BILL_003"),
                Triple("Возврат средств", "Хочу вернуть деньги за последний месяц подписки.", "BILL_004"),
                Triple("НДС неправильно рассчитан", "В счете указан НДС 20%, но для нашей страны должен быть 0%.", "BILL_005"),
                Triple("Не пришел чек об оплате", "После успешной оплаты не получил чек на email.", "BILL_006"),
                Triple("Хочу оплатить за год", "Как переключиться на годовую оплату со скидкой?", "BILL_007"),
                Triple("Не могу отменить подписку", "Не нахожу где отменить автопродление.", "BILL_008")
            ),
            "account" to listOf(
                Triple("Хочу удалить аккаунт", "Как полностью удалить свой аккаунт и все данные?", "ACC_001"),
                Triple("Не могу изменить email", "При смене email получаю ошибку валидации.", "ACC_002"),
                Triple("Аватар не загружается", "Пытаюсь загрузить фото профиля, но ничего не происходит.", "ACC_003"),
                Triple("Хочу экспортировать свои данные", "Как скачать все мои данные из системы?", "ACC_004"),
                Triple("Не могу пригласить пользователя в команду", "Инвайт не отправляется, ошибка при добавлении.", "ACC_005"),
                Triple("Роли пользователей не применяются", "Назначил роль Admin, но права не изменились.", "ACC_006"),
                Triple("Уведомления приходят на старый email", "Изменил email, но уведомления идут на старый адрес.", "ACC_007"),
                Triple("Не вижу историю активности", "Страница Activity Log пустая.", "ACC_008")
            ),
            "feature_request" to listOf(
                Triple("Хочу bulk import из Excel", "Нужна возможность массово импортировать данные из Excel.", null),
                Triple("Добавьте темную тему", "Хотелось бы иметь возможность включить темную тему.", null),
                Triple("Нужно мобильное приложение", "Планируется ли выпуск мобильного приложения?", null),
                Triple("Хочу кастомные поля", "Нужна возможность добавлять свои поля к задачам.", null),
                Triple("Добавьте экспорт в PDF", "Хочу экспортировать отчеты в PDF формате.", null),
                Triple("Нужна интеграция с Notion", "Хотелось бы синхронизацию с Notion.", null),
                Triple("Добавьте повторяющиеся задачи", "Нужна возможность создавать recurring tasks.", null),
                Triple("Хочу видеть timeline проекта", "Нужен Gantt chart для визуализации проекта.", null)
            ),
            "bug" to listOf(
                Triple("Данные не сохраняются", "Заполняю форму, нажимаю сохранить, но данные пропадают.", "BUG_001"),
                Triple("Фильтры сбрасываются", "После обновления страницы все фильтры сбрасываются.", "BUG_002"),
                Triple("Дубликаты в списке", "В списке задач появляются дубликаты записей.", "BUG_003"),
                Triple("Неправильная сортировка", "Сортировка по дате работает неправильно.", "BUG_004"),
                Triple("Уведомления не отмечаются прочитанными", "Кликаю на уведомление, но оно остается непрочитанным.", "BUG_005"),
                Triple("Копирование не работает", "Ctrl+C не копирует текст из полей.", "BUG_006"),
                Triple("Автосохранение не работает", "Потерял данные из-за того, что автосохранение не сработало.", "BUG_007"),
                Triple("Локализация отображается неправильно", "Часть интерфейса на английском, часть на русском.", "BUG_008")
            ),
            "data" to listOf(
                Triple("Пропали все мои данные", "Зашел в аккаунт, а все проекты и задачи исчезли.", "DATA_001"),
                Triple("Импорт данных не работает", "При импорте CSV файла получаю ошибку парсинга.", "DATA_002"),
                Triple("Экспорт выдает пустой файл", "Скачиваю экспорт, но файл пустой.", "DATA_003"),
                Triple("Данные отображаются некорректно", "В отчете цифры не сходятся с реальными данными.", "DATA_004"),
                Triple("Backup не создается", "Автоматический бэкап не создался по расписанию.", "DATA_005"),
                Triple("Не могу восстановить удаленные данные", "Случайно удалил проект, как восстановить?", "DATA_006"),
                Triple("Синхронизация данных зависла", "Прогресс синхронизации застрял на 50%.", "DATA_007"),
                Triple("Дата создания неправильная", "У задачи дата создания показывает 1970 год.", "DATA_008")
            )
        )

        val resolutions = mapOf(
            "auth" to listOf("Сброшены сессии пользователя", "Обновлен токен 2FA", "Письмо было в спаме", "Увеличено время сессии", "Исправлена конфигурация OAuth", "Аккаунт разблокирован", "Исправлен logout", "Обновлена конфигурация SAML"),
            "payment" to listOf("Повторная оплата прошла успешно", "Оформлен возврат", "Выдан новый промокод", "Помогли пройти верификацию", "Исправлена интеграция PayPal", "Обновлены данные карты", "Подписка продлена вручную", "Инвойс скорректирован"),
            "ui" to listOf("Исправлен CSS баг", "Оптимизирована загрузка", "Исправлены мобильные стили", "Обновлены цвета темы", "Исправлен z-index", "Добавлен скролл", "Исправлен шрифт иконок", "Исправлен drag and drop"),
            "performance" to listOf("Оптимизированы запросы к БД", "Добавлена пагинация", "Улучшен индекс поиска", "Увеличен таймаут", "Добавлено кэширование", "Исправлена утечка памяти", "Оптимизирован рендеринг", "Реализована серверная пагинация"),
            "integration" to listOf("Переподключена интеграция", "Исправлен URL webhook", "Обновлены учетные данные", "Переавторизован доступ", "Сгенерирован новый API ключ", "Обновлена конфигурация", "Переподключен репозиторий", "Настроен forwarding"),
            "billing" to listOf("Инвойс отправлен вручную", "Период скорректирован", "Помогли сменить тариф", "Оформлен возврат", "Исправлена ставка НДС", "Чек переотправлен", "Переключен на годовой план", "Подписка отменена"),
            "account" to listOf("Аккаунт удален", "Email изменен через поддержку", "Исправлен баг загрузки", "Архив данных отправлен", "Исправлена система приглашений", "Права применены", "Email для уведомлений обновлен", "Activity Log исправлен"),
            "feature_request" to listOf("Добавлено в roadmap", "Уже в разработке", "Передано продуктовой команде", "Реализовано в обновлении"),
            "bug" to listOf("Баг сохранения исправлен", "Исправлено сохранение фильтров", "Исправлено дублирование", "Исправлена сортировка", "Исправлена отметка уведомлений", "Исправлен clipboard", "Исправлено автосохранение", "Исправлена локализация"),
            "data" to listOf("Данные восстановлены", "Парсер исправлен", "Генератор экспорта исправлен", "Расчет исправлен", "Scheduler исправлен", "Проект восстановлен", "Синхронизация перезапущена", "Обработка дат исправлена")
        )

        val tagsByCategory = mapOf(
            "auth" to listOf("авторизация", "логин", "пароль", "безопасность", "сессия", "oauth", "sso", "2fa"),
            "payment" to listOf("оплата", "карта", "биллинг", "подписка", "возврат", "paypal", "платеж"),
            "ui" to listOf("интерфейс", "дизайн", "верстка", "css", "мобильный", "отображение", "кнопка"),
            "performance" to listOf("производительность", "скорость", "оптимизация", "загрузка", "память", "таймаут"),
            "integration" to listOf("интеграция", "api", "webhook", "синхронизация", "slack", "jira", "github"),
            "billing" to listOf("биллинг", "счет", "инвойс", "тариф", "ндс", "оплата", "подписка"),
            "account" to listOf("аккаунт", "профиль", "настройки", "пользователь", "команда", "роли"),
            "feature_request" to listOf("фича", "улучшение", "предложение", "функционал", "roadmap"),
            "bug" to listOf("баг", "ошибка", "сбой", "проблема", "исправление", "дефект"),
            "data" to listOf("данные", "импорт", "экспорт", "бэкап", "восстановление", "синхронизация")
        )

        val random = Random(42)
        val baseDate = LocalDateTime.of(2024, 1, 1, 0, 0)
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        repeat(ticketCount) { i ->
            val ticketNum = i + 1
            val id = "TKT-${ticketNum.toString().padStart(5, '0')}"

            val daysOffset = random.nextLong(0, 365)
            val hoursOffset = random.nextLong(0, 24)
            val createdAt = baseDate.plusDays(daysOffset).plusHours(hoursOffset)

            val category = categories[random.nextInt(categories.size)]
            val priority = priorities[random.nextInt(priorities.size)]
            val status = statuses[random.nextInt(statuses.size)]
            val userSegment = userSegments[random.nextInt(userSegments.size)]
            val funnelStage = funnelStages[random.nextInt(funnelStages.size)]
            val browser = browsers[random.nextInt(browsers.size)]
            val platform = platforms[random.nextInt(platforms.size)]
            val userId = "USR-${random.nextInt(1000, 9999)}"

            val templates = ticketTemplates[category] ?: ticketTemplates["bug"]!!
            val template = templates[random.nextInt(templates.size)]

            val titleVariations = listOf("", " - срочно", " (повторно)", "")
            val descVariations = listOf("", " Проблема возникла сегодня.", " Раньше все работало.", " Пробовал в разных браузерах.", "")

            val title = template.first + titleVariations[random.nextInt(titleVariations.size)]
            val description = template.second + descVariations[random.nextInt(descVariations.size)]
            val errorCode = template.third

            val resolution = if (status in listOf("resolved", "closed")) {
                val categoryResolutions = resolutions[category] ?: resolutions["bug"]!!
                categoryResolutions[random.nextInt(categoryResolutions.size)]
            } else null

            val resolutionTimeHours = if (resolution != null) {
                when (priority) {
                    "critical" -> random.nextInt(1, 4)
                    "high" -> random.nextInt(2, 12)
                    "medium" -> random.nextInt(4, 48)
                    else -> random.nextInt(12, 168)
                }
            } else null

            val categoryTags = tagsByCategory[category] ?: tagsByCategory["bug"]!!
            val ticketTags = categoryTags.shuffled(random).take(random.nextInt(2, 5))

            tickets.add(Ticket(
                id = id,
                createdAt = createdAt.format(formatter),
                category = category,
                priority = priority,
                status = status,
                title = title,
                description = description,
                errorCode = errorCode,
                resolution = resolution,
                resolutionTimeHours = resolutionTimeHours,
                userSegment = userSegment,
                funnelStage = funnelStage,
                browser = browser,
                platform = platform,
                userId = userId,
                tags = ticketTags
            ))
        }
    }

    /**
     * Векторизация всех тикетов
     */
    private suspend fun vectorizeTickets() {
        logger.info("Starting vectorization of ${tickets.size} tickets...")

        // Создаем searchable text для каждого тикета
        val searchableTexts = tickets.map { ticket ->
            buildSearchableText(ticket)
        }

        // Векторизуем батчами по 50 для эффективности
        val batchSize = 50
        var processedCount = 0

        searchableTexts.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            try {
                val response = ollamaService.getBatchEmbeddings(batch)

                if (embeddingDimension == 0) {
                    embeddingDimension = response.dimension
                }

                response.embeddings.forEachIndexed { index, embedding ->
                    val ticketIndex = batchIndex * batchSize + index
                    ticketsWithEmbeddings.add(TicketWithEmbedding(
                        ticket = tickets[ticketIndex],
                        embedding = embedding,
                        searchableText = searchableTexts[ticketIndex]
                    ))
                }

                processedCount += batch.size
                if (processedCount % 100 == 0) {
                    logger.info("Vectorized $processedCount/${tickets.size} tickets...")
                }
            } catch (e: Exception) {
                logger.error("Error vectorizing batch $batchIndex: ${e.message}")
            }
        }

        logger.info("Vectorization complete: ${ticketsWithEmbeddings.size} tickets vectorized")
    }

    /**
     * Создание searchable текста из тикета
     */
    private fun buildSearchableText(ticket: Ticket): String {
        return buildString {
            append("[${ticket.id}] ")
            append("[${ticket.category}] ")
            append("[${ticket.priority}] ")
            append("[${ticket.status}] ")
            append(ticket.title)
            append(". ")
            append(ticket.description)
            ticket.errorCode?.let { append(" Код ошибки: $it.") }
            ticket.resolution?.let { append(" Решение: $it.") }
            append(" Сегмент: ${ticket.userSegment}.")
            append(" Этап: ${ticket.funnelStage}.")
            append(" Теги: ${ticket.tags.joinToString(", ")}.")
        }
    }

    /**
     * Поиск похожих тикетов по запросу
     */
    suspend fun searchTickets(query: String, topK: Int = 10, minSimilarity: Double = 0.3): List<TicketSearchResult> {
        if (!initialized) {
            throw IllegalStateException("AnalyticsService not initialized")
        }

        val queryEmbedding = ollamaService.getEmbedding(query).embedding

        return ticketsWithEmbeddings
            .map { ticketWithEmb ->
                val similarity = cosineSimilarity(queryEmbedding, ticketWithEmb.embedding)
                TicketSearchResult(ticketWithEmb.ticket, similarity)
            }
            .filter { it.similarity >= minSimilarity }
            .sortedByDescending { it.similarity }
            .take(topK)
    }

    /**
     * Аналитический запрос с использованием LLM
     */
    suspend fun analyzeQuery(request: AnalyticsQueryRequest): AnalyticsQueryResponse {
        val startTime = System.currentTimeMillis()

        // Ищем релевантные тикеты
        val relevantTickets = searchTickets(request.query, request.topK, request.minSimilarity)

        // Формируем контекст для LLM
        val context = buildAnalyticsContext(relevantTickets, request.query)

        // Получаем ответ от LLM
        val systemPrompt = """
            Ты - аналитик службы технической поддержки.
            Анализируй предоставленные тикеты и отвечай на вопросы пользователя.
            Давай конкретные ответы с цифрами и примерами из данных.
            Если нужно посчитать - посчитай на основе предоставленных тикетов.
            Отвечай на русском языке.
        """.trimIndent()

        val answer = try {
            val userMessage = ClaudeMessage(role = "user", content = context)
            ollamaChatService.sendMessage(
                messages = listOf(userMessage),
                systemPrompt = systemPrompt,
                requestModel = null
            ).response
        } catch (e: Exception) {
            logger.error("Error getting LLM response: ${e.message}")
            "Не удалось получить ответ от модели: ${e.message}"
        }

        val processingTime = System.currentTimeMillis() - startTime

        return AnalyticsQueryResponse(
            query = request.query,
            answer = answer,
            relevantTickets = relevantTickets,
            totalTicketsAnalyzed = tickets.size,
            processingTimeMs = processingTime
        )
    }

    /**
     * Формирование контекста для аналитического запроса
     */
    private fun buildAnalyticsContext(relevantTickets: List<TicketSearchResult>, query: String): String {
        val ticketsContext = relevantTickets.joinToString("\n\n") { result ->
            val t = result.ticket
            """
            Тикет ${t.id} (релевантность: ${String.format("%.2f", result.similarity)}):
            - Категория: ${t.category}, Приоритет: ${t.priority}, Статус: ${t.status}
            - Заголовок: ${t.title}
            - Описание: ${t.description}
            - Код ошибки: ${t.errorCode ?: "нет"}
            - Решение: ${t.resolution ?: "не решен"}
            - Время решения: ${t.resolutionTimeHours?.let { "${it}ч" } ?: "н/д"}
            - Сегмент пользователя: ${t.userSegment}
            - Этап воронки: ${t.funnelStage}
            - Теги: ${t.tags.joinToString(", ")}
            """.trimIndent()
        }

        // Добавляем общую статистику
        val stats = getStats()
        val statsContext = """
            ОБЩАЯ СТАТИСТИКА ПО ВСЕМ ${stats.totalTickets} ТИКЕТАМ:
            По категориям: ${stats.byCategory.entries.joinToString(", ") { "${it.key}: ${it.value}" }}
            По приоритету: ${stats.byPriority.entries.joinToString(", ") { "${it.key}: ${it.value}" }}
            По статусу: ${stats.byStatus.entries.joinToString(", ") { "${it.key}: ${it.value}" }}
            Топ ошибок: ${stats.topErrorCodes.take(5).joinToString(", ") { "${it.errorCode}: ${it.count}" }}
            По этапу воронки: ${stats.byFunnelStage.entries.joinToString(", ") { "${it.key}: ${it.value}" }}
        """.trimIndent()

        return """
            Вопрос пользователя: $query

            $statsContext

            НАЙДЕННЫЕ РЕЛЕВАНТНЫЕ ТИКЕТЫ (${relevantTickets.size} из ${tickets.size}):

            $ticketsContext

            На основе этих данных ответь на вопрос пользователя.
        """.trimIndent()
    }

    /**
     * Получение статистики по тикетам
     */
    fun getStats(): TicketStats {
        val byCategory = tickets.groupingBy { it.category }.eachCount()
        val byPriority = tickets.groupingBy { it.priority }.eachCount()
        val byStatus = tickets.groupingBy { it.status }.eachCount()
        val byUserSegment = tickets.groupingBy { it.userSegment }.eachCount()
        val byFunnelStage = tickets.groupingBy { it.funnelStage }.eachCount()

        val topErrorCodes = tickets
            .mapNotNull { it.errorCode }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { ErrorCodeCount(it.key, it.value) }

        val avgResolutionTime = tickets
            .filter { it.resolutionTimeHours != null }
            .groupBy { it.priority }
            .mapValues { (_, ticketList) ->
                ticketList.mapNotNull { it.resolutionTimeHours }.average()
            }

        return TicketStats(
            totalTickets = tickets.size,
            byCategory = byCategory,
            byPriority = byPriority,
            byStatus = byStatus,
            byUserSegment = byUserSegment,
            byFunnelStage = byFunnelStage,
            topErrorCodes = topErrorCodes,
            avgResolutionTimeByPriority = avgResolutionTime
        )
    }

    /**
     * Получение статуса системы
     */
    fun getStatus(): AnalyticsStatus {
        return AnalyticsStatus(
            initialized = initialized,
            totalTickets = tickets.size,
            vectorizedTickets = ticketsWithEmbeddings.size,
            embeddingDimension = embeddingDimension,
            categories = tickets.groupingBy { it.category }.eachCount(),
            priorities = tickets.groupingBy { it.priority }.eachCount(),
            statuses = tickets.groupingBy { it.status }.eachCount()
        )
    }

    /**
     * Cosine similarity между двумя векторами
     */
    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size) return 0.0

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0.0) 0.0 else dotProduct / denominator
    }
}
