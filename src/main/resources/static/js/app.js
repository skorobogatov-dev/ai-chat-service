// AI Chat Application
class ChatApp {
    constructor() {
        this.messagesContainer = document.getElementById('messages');
        this.chatForm = document.getElementById('chatForm');
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.temperatureSlider = document.getElementById('temperatureSlider');
        this.temperatureValue = document.getElementById('temperatureValue');
        this.loadingIndicator = null;

        this.init();
    }

    init() {
        this.chatForm.addEventListener('submit', (e) => this.handleSubmit(e));
        this.temperatureSlider.addEventListener('input', (e) => this.updateTemperatureDisplay(e));
        this.messageInput.focus();
    }

    updateTemperatureDisplay(event) {
        this.temperatureValue.textContent = parseFloat(event.target.value).toFixed(1);
    }

    async handleSubmit(event) {
        event.preventDefault();

        const message = this.messageInput.value.trim();
        if (!message) return;

        // Disable input while processing
        this.setInputState(false);

        // Clear input
        this.messageInput.value = '';

        // Show loading indicator
        this.showLoadingIndicator();

        try {
            // Get temperature value
            const temperature = parseFloat(this.temperatureSlider.value);

            // Add user message
            this.addMessage(message, 'user');

            // Send message to API
            const response = await this.sendMessage(message, temperature);

            // Hide loading indicator
            this.hideLoadingIndicator();

            // Add assistant response with temperature badge
            this.addMessage(response.response, 'assistant', temperature);
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

    async sendMessage(message, temperature) {
        const requestBody = {
            message,
            systemPrompt: "Отвечай обычным текстом, без JSON форматирования. Будь естественным и дружелюбным."
        };

        // Only include temperature if it's not the default value
        if (temperature !== undefined && temperature !== null) {
            requestBody.temperature = temperature;
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

    createTemperatureBadge(temperature) {
        let tempClass = 'temp-medium';
        let tempLabel = 'Сбалансированная';

        if (temperature <= 0.3) {
            tempClass = 'temp-low';
            tempLabel = 'Точная';
        } else if (temperature >= 0.7) {
            tempClass = 'temp-high';
            tempLabel = 'Креативная';
        }

        return `<span class="temperature-badge ${tempClass}" title="Temperature: ${temperature}">${tempLabel} (${temperature})</span>`;
    }

    addMessage(text, type, temperature = null) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        const label = type === 'user' ? 'Вы' : type === 'error' ? 'Ошибка' : 'Assistant';

        // Format text with line breaks
        const formattedText = this.escapeHtml(text).replace(/\n/g, '<br>');

        // Add temperature badge only for assistant messages
        const tempBadge = (type === 'assistant' && temperature !== null)
            ? this.createTemperatureBadge(temperature)
            : '';

        contentDiv.innerHTML = `
            <div class="message-header">
                <strong>${label}</strong>
                ${tempBadge}
            </div>
            <div class="message-text">${formattedText}</div>
        `;

        messageDiv.appendChild(contentDiv);
        this.messagesContainer.appendChild(messageDiv);

        // Scroll to bottom
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
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
