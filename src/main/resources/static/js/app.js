// AI Chat Application
class ChatApp {
    constructor() {
        this.messagesContainer = document.getElementById('messages');
        this.chatForm = document.getElementById('chatForm');
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
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
            // Send message to API
            const response = await this.sendMessage(message);

            // Hide loading indicator
            this.hideLoadingIndicator();

            // Add expert responses to chat - each expert as a separate message
            if (response.experts && Array.isArray(response.experts)) {
                response.experts.forEach(expert => {
                    this.addExpertMessage(expert);
                });
            } else {
                // Fallback if response format is unexpected
                this.addMessage(JSON.stringify(response), 'assistant');
            }
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

    async sendMessage(message) {
        const response = await fetch('/api/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ message })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Server error');
        }

        return await response.json();
    }

    addMessage(text, type) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        const label = type === 'user' ? 'Вы' : type === 'error' ? 'Ошибка' : 'Assistant';
        contentDiv.innerHTML = `<strong>${label}:</strong> ${this.escapeHtml(text)}`;

        messageDiv.appendChild(contentDiv);
        this.messagesContainer.appendChild(messageDiv);

        // Scroll to bottom
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }

    addExpertMessage(expert) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message expert';

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        const expertHeader = document.createElement('div');
        expertHeader.className = 'expert-header';
        expertHeader.innerHTML = `
            <strong class="expert-role">${this.escapeHtml(expert.expertRole)}</strong>
            <span class="expert-name">${this.escapeHtml(expert.expertName)}</span>
        `;

        const expertResponse = document.createElement('div');
        expertResponse.className = 'expert-response';
        expertResponse.textContent = expert.response;

        contentDiv.appendChild(expertHeader);
        contentDiv.appendChild(expertResponse);
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
