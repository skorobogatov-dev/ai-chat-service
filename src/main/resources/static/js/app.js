// AI Chat Application
class ChatApp {
    constructor() {
        this.messagesContainer = document.getElementById('messages');
        this.chatForm = document.getElementById('chatForm');
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.modelSelect = document.getElementById('modelSelect');
        this.loadingIndicator = null;

        // Predefined questions with different token requirements
        this.quickQuestions = {
            short: "Что такое Kotlin?",
            medium: "Объясни основные принципы ООП (инкапсуляция, наследование, полиморфизм, абстракция) с примерами кода на Kotlin для каждого принципа",
            long: "Напиши максимально подробное и исчерпывающее руководство по созданию enterprise-grade микросервисной архитектуры на Kotlin с использованием Spring Boot и Ktor. Включи ВСЕ следующие разделы с полными примерами кода:\n\n1) Детальная настройка multi-module Gradle проекта со всеми зависимостями, версиями и плагинами для микросервисов (auth-service, user-service, order-service, payment-service, notification-service)\n\n2) Полная структура каждого микросервиса с применением Clean Architecture (presentation, domain, data layers) и объяснением каждого слоя\n\n3) Создание domain моделей, entities, DTOs, mappers с валидацией через Bean Validation и собственные аннотации\n\n4) Реализация REST API endpoints для CRUD операций с пагинацией, сортировкой, фильтрацией и поиском\n\n5) Подключение к PostgreSQL через Spring Data JPA и R2DBC для reactive подхода, настройка connection pools, транзакций\n\n6) Полная реализация репозиториев (JPA repositories, custom queries, specifications) и сервисов с бизнес-логикой\n\n7) Детальная реализация аутентификации и авторизации: JWT tokens (access + refresh), OAuth2, Spring Security configuration, role-based access control, permission system\n\n8) Comprehensive валидация на всех уровнях: request validation, business rules validation, cross-field validation с кастомными аннотациями\n\n9) Продвинутая обработка ошибок: глобальный exception handler, кастомные исключения, structured error responses, logging стратегии\n\n10) Межсервисная коммуникация: REST clients (RestTemplate, WebClient), message queues (RabbitMQ/Kafka), service discovery (Eureka/Consul)\n\n11) API Gateway реализация с маршрутизацией, rate limiting, circuit breaker паттерном (Resilience4j)\n\n12) Distributed tracing (Zipkin/Jaeger), централизованное логирование (ELK stack), метрики (Prometheus + Grafana)\n\n13) Comprehensive testing: unit tests (JUnit 5, MockK), integration tests (Testcontainers), contract testing (Spring Cloud Contract), E2E tests\n\n14) Полная Dockerизация всех сервисов, Docker Compose для локальной разработки, multi-stage builds\n\n15) Kubernetes deployment manifests (deployments, services, ingress, configmaps, secrets), helm charts\n\n16) CI/CD pipeline на GitLab CI/Jenkins: сборка, тестирование, security scanning, deployment в разные окружения\n\n17) Database migration стратегия (Flyway/Liquibase), versioning, rollback procedures\n\n18) Caching стратегии (Redis, Spring Cache), distributed cache\n\n19) Event sourcing и CQRS паттерны для критичных сервисов\n\n20) Monitoring, alerting, health checks, graceful shutdown\n\nДля КАЖДОГО раздела предоставь: полный рабочий код с подробными комментариями, объяснение архитектурных решений, best practices, anti-patterns которых следует избегать, примеры конфигурации, curl/Postman примеры для API, примеры тестов. Объясни как все компоненты взаимодействуют между собой и почему выбраны именно эти решения."
        };

        this.init();
    }

    init() {
        this.chatForm.addEventListener('submit', (e) => this.handleSubmit(e));
        this.messageInput.focus();
        this.initQuickQuestionButtons();
    }

    initQuickQuestionButtons() {
        const quickQuestionButtons = document.querySelectorAll('.quick-question-btn');
        quickQuestionButtons.forEach(button => {
            button.addEventListener('click', () => {
                const size = button.dataset.size;
                const question = this.quickQuestions[size];
                if (question) {
                    this.messageInput.value = question;
                    this.messageInput.focus();
                    // Scroll to input
                    this.messageInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            });
        });
    }

    async handleSubmit(event) {
        event.preventDefault();

        const message = this.messageInput.value.trim();
        if (!message) return;

        // Disable input while processing
        this.setInputState(false);

        // Add user message to chat
        this.addMessage(message, 'user');

        // Clear input
        this.messageInput.value = '';

        // Show loading indicator
        this.showLoadingIndicator();

        try {
            // Get selected model
            const selectedModel = this.modelSelect.value;

            // Send message to API
            const response = await this.sendMessage(message, selectedModel);

            // Hide loading indicator
            this.hideLoadingIndicator();

            // Add assistant response to chat with model info and stats
            this.addMessage(response.response, 'assistant', response.model, {
                inputTokens: response.inputTokens,
                outputTokens: response.outputTokens,
                totalTokens: response.totalTokens,
                responseTimeMs: response.responseTimeMs
            });
        } catch (error) {
            // Hide loading indicator
            this.hideLoadingIndicator();

            console.error('Error:', error);
            this.addMessage(
                `Ошибка: ${error.message || 'Не удалось получить ответ'}`,
                'error'
            );
        } finally {
            // Re-enable input
            this.setInputState(true);
            this.messageInput.focus();
        }
    }

    async sendMessage(message, model) {
        const requestBody = { message };
        if (model) {
            requestBody.model = model;
        }

        const response = await fetch('/api/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(requestBody)
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Server error');
        }

        return await response.json();
    }

    addMessage(text, type, model = null, stats = null) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        const label = type === 'user' ? 'Вы' : type === 'error' ? 'Ошибка' : 'Assistant';
        let modelInfo = '';
        if (model && type === 'assistant') {
            const modelName = this.getModelDisplayName(model);
            modelInfo = ` <span class="model-badge">${modelName}</span>`;
        }
        contentDiv.innerHTML = `<strong>${label}:${modelInfo}</strong> ${this.escapeHtml(text)}`;

        messageDiv.appendChild(contentDiv);

        // Add stats if available
        if (stats && type === 'assistant') {
            const statsDiv = document.createElement('div');
            statsDiv.className = 'message-stats';
            statsDiv.innerHTML = `
                <div class="stats-item">
                    <span class="stats-label">Вход:</span>
                    <span class="stats-value">${stats.inputTokens}</span>
                </div>
                <div class="stats-item">
                    <span class="stats-label">Выход:</span>
                    <span class="stats-value">${stats.outputTokens}</span>
                </div>
                <div class="stats-item">
                    <span class="stats-label">Всего:</span>
                    <span class="stats-value">${stats.totalTokens}</span>
                </div>
                <div class="stats-item">
                    <span class="stats-label">Время:</span>
                    <span class="stats-value">${(stats.responseTimeMs / 1000).toFixed(2)}с</span>
                </div>
            `;
            messageDiv.appendChild(statsDiv);
        }

        this.messagesContainer.appendChild(messageDiv);

        // Scroll to bottom
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }

    getModelDisplayName(modelId) {
        const modelNames = {
            'claude-3-haiku-20240307': 'Haiku',
            'claude-sonnet-4-20250514': 'Sonnet 4'
        };
        return modelNames[modelId] || modelId;
    }

    setInputState(enabled) {
        this.messageInput.disabled = !enabled;
        this.sendButton.disabled = !enabled;
        this.sendButton.textContent = enabled ? 'Отправить' : 'Отправка...';
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    showLoadingIndicator() {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message assistant loading';
        messageDiv.id = 'loading-indicator';

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        contentDiv.innerHTML = `
            <strong>Assistant:</strong>
            <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
            </div>
        `;

        messageDiv.appendChild(contentDiv);
        this.messagesContainer.appendChild(messageDiv);
        this.loadingIndicator = messageDiv;

        // Scroll to bottom
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }

    hideLoadingIndicator() {
        if (this.loadingIndicator) {
            this.loadingIndicator.remove();
            this.loadingIndicator = null;
        }
    }
}

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new ChatApp();
});
