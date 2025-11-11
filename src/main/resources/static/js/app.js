// AI Chat Application
class ChatApp {
    constructor() {
        this.messagesContainer = document.getElementById('messages');
        this.chatForm = document.getElementById('chatForm');
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.modelSelect = document.getElementById('modelSelect');
        this.loadingIndicator = null;

        this.init();
    }

    init() {
        this.chatForm.addEventListener('submit', (e) => this.handleSubmit(e));
        this.messageInput.focus();
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
