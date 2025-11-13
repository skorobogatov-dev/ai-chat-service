// AI Chat Application
class ChatApp {
    constructor() {
        this.messagesContainer = document.getElementById('messages');
        this.chatForm = document.getElementById('chatForm');
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.modelSelect = document.getElementById('modelSelect');
        this.viewHistoryButton = document.getElementById('viewHistoryButton');
        this.historyModal = document.getElementById('historyModal');
        this.closeModalButton = document.getElementById('closeModalButton');
        this.loadingIndicator = null;
        this.sessionId = null; // Current session ID

        this.init();
    }

    init() {
        this.chatForm.addEventListener('submit', (e) => this.handleSubmit(e));
        this.viewHistoryButton.addEventListener('click', () => this.viewHistory());
        this.closeModalButton.addEventListener('click', () => this.closeModal());
        this.historyModal.addEventListener('click', (e) => {
            if (e.target === this.historyModal) {
                this.closeModal();
            }
        });
        this.messageInput.focus();

        // Disable history button initially
        this.updateHistoryButton();
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
                responseTimeMs: response.responseTimeMs,
                historyCompressed: response.historyCompressed
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
        // Send sessionId if we have one
        if (this.sessionId) {
            requestBody.sessionId = this.sessionId;
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

        const data = await response.json();

        // Store sessionId from response
        if (data.sessionId) {
            this.sessionId = data.sessionId;
            this.updateHistoryButton();
        }

        return data;
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
            let statsHtml = `
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

            // Add compression indicator if history was compressed
            if (stats.historyCompressed) {
                statsHtml += `
                    <div class="stats-item compressed">
                        <span class="stats-label">🗜️ История сжата</span>
                    </div>
                `;
            }

            statsDiv.innerHTML = statsHtml;
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

    updateHistoryButton() {
        // Enable/disable history button based on sessionId
        this.viewHistoryButton.disabled = !this.sessionId;
        if (this.sessionId) {
            this.viewHistoryButton.title = 'Просмотр истории диалога';
        } else {
            this.viewHistoryButton.title = 'Начните диалог, чтобы просмотреть историю';
        }
    }

    async viewHistory() {
        if (!this.sessionId) {
            return;
        }

        try {
            const response = await fetch(`/api/chat/history/${this.sessionId}`);

            if (!response.ok) {
                throw new Error('Failed to load history');
            }

            const historyData = await response.json();
            this.displayHistory(historyData);
            this.openModal();
        } catch (error) {
            console.error('Error loading history:', error);
            alert('Не удалось загрузить историю диалога');
        }
    }

    displayHistory(historyData) {
        const historyInfo = document.getElementById('historyInfo');
        const historyMessages = document.getElementById('historyMessages');

        // Display session info
        historyInfo.innerHTML = `
            <div class="history-stats">
                <div class="history-stat-item">
                    <span class="label">Session ID:</span>
                    <span class="value">${historyData.sessionId}</span>
                </div>
                <div class="history-stat-item">
                    <span class="label">Всего сообщений:</span>
                    <span class="value">${historyData.messageCount}</span>
                </div>
                <div class="history-stat-item">
                    <span class="label">Пар диалогов:</span>
                    <span class="value">${historyData.pairsCount}</span>
                </div>
            </div>
        `;

        // Display messages
        historyMessages.innerHTML = '';

        historyData.messages.forEach((msg, index) => {
            const msgDiv = document.createElement('div');
            msgDiv.className = `history-message history-message-${msg.type.toLowerCase()}`;

            let typeLabel = '';
            let icon = '';

            switch(msg.type) {
                case 'USER':
                    typeLabel = 'Вы';
                    icon = '👤';
                    break;
                case 'ASSISTANT':
                    typeLabel = 'Assistant';
                    icon = '🤖';
                    break;
                case 'SUMMARY':
                    typeLabel = 'Summary (сжатие истории)';
                    icon = '📝';
                    break;
            }

            const date = new Date(msg.timestamp);
            const timeStr = date.toLocaleTimeString('ru-RU');

            msgDiv.innerHTML = `
                <div class="history-message-header">
                    <span class="history-message-icon">${icon}</span>
                    <span class="history-message-type">${typeLabel}</span>
                    <span class="history-message-time">${timeStr}</span>
                </div>
                <div class="history-message-content">${this.escapeHtml(msg.content)}</div>
            `;

            historyMessages.appendChild(msgDiv);
        });
    }

    openModal() {
        this.historyModal.style.display = 'block';
        document.body.style.overflow = 'hidden'; // Prevent background scrolling
    }

    closeModal() {
        this.historyModal.style.display = 'none';
        document.body.style.overflow = 'auto';
    }
}

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new ChatApp();
});
