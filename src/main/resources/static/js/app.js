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
        this.newChatButton = document.getElementById('newChatButton');
        this.conversationsList = document.getElementById('conversationsList');
        this.chatTitle = document.getElementById('chatTitle');
        this.loadingIndicator = null;
        this.sessionId = null; // Current session ID
        this.conversations = []; // List of all conversations

        this.init();
    }

    async init() {
        this.chatForm.addEventListener('submit', (e) => this.handleSubmit(e));
        this.viewHistoryButton.addEventListener('click', () => this.viewHistory());
        this.closeModalButton.addEventListener('click', () => this.closeModal());
        this.newChatButton.addEventListener('click', () => this.createNewConversation());
        this.historyModal.addEventListener('click', (e) => {
            if (e.target === this.historyModal) {
                this.closeModal();
            }
        });

        // Load conversations list
        await this.loadConversations();

        // Disable history button initially
        this.updateHistoryButton();
    }

    async loadConversations() {
        try {
            const response = await fetch('/api/chat/conversations');
            if (!response.ok) {
                throw new Error('Failed to load conversations');
            }

            const data = await response.json();
            this.conversations = data.conversations;
            this.displayConversations();

            // If there are conversations, ask user to select one
            if (this.conversations.length === 0) {
                this.conversationsList.innerHTML = '<div class="loading">Нет диалогов. Создайте новый!</div>';
            }
        } catch (error) {
            console.error('Error loading conversations:', error);
            this.conversationsList.innerHTML = '<div class="loading">Ошибка загрузки диалогов</div>';
        }
    }

    displayConversations() {
        this.conversationsList.innerHTML = '';

        this.conversations.forEach(conv => {
            const convItem = document.createElement('div');
            convItem.className = 'conversation-item';
            if (conv.sessionId === this.sessionId) {
                convItem.classList.add('active');
            }

            const title = conv.title || 'Новый диалог';
            const date = new Date(conv.lastAccessedAt);
            const dateStr = this.formatDate(date);

            convItem.innerHTML = `
                <div class="conversation-title">${this.escapeHtml(title)}</div>
                <div class="conversation-meta">
                    <span>${conv.messageCount} сообщений</span>
                    <span>${dateStr}</span>
                </div>
            `;

            convItem.addEventListener('click', () => this.switchConversation(conv.sessionId));
            this.conversationsList.appendChild(convItem);
        });
    }

    async switchConversation(sessionId) {
        if (sessionId === this.sessionId) {
            return; // Already on this conversation
        }

        this.sessionId = sessionId;

        // Clear current messages
        this.messagesContainer.innerHTML = '';

        // Load history for this conversation
        try {
            const response = await fetch(`/api/chat/history/${sessionId}`);
            if (!response.ok) {
                throw new Error('Failed to load conversation history');
            }

            const historyData = await response.json();

            // Update title
            const conv = this.conversations.find(c => c.sessionId === sessionId);
            if (conv && conv.title) {
                this.chatTitle.textContent = conv.title;
            } else {
                this.chatTitle.textContent = 'AI Chat Assistant';
            }

            // Display all messages
            historyData.messages.forEach(msg => {
                const type = msg.type === 'USER' ? 'user' : 'assistant';
                this.addMessage(msg.content, type, null, null, false);
            });

            // Update UI
            this.displayConversations();
            this.updateHistoryButton();
            this.messageInput.focus();
        } catch (error) {
            console.error('Error switching conversation:', error);
            alert('Не удалось загрузить диалог');
        }
    }

    createNewConversation() {
        // Clear session ID to start new conversation
        this.sessionId = null;
        this.chatTitle.textContent = 'AI Chat Assistant';

        // Clear messages
        this.messagesContainer.innerHTML = `
            <div class="message assistant">
                <div class="message-content">
                    <strong>Assistant:</strong> Привет! Я готов помочь вам. Задайте любой вопрос.
                </div>
            </div>
        `;

        // Update UI
        this.displayConversations();
        this.updateHistoryButton();
        this.messageInput.focus();
    }

    formatDate(date) {
        const now = new Date();
        const diff = now - date;
        const minutes = Math.floor(diff / 60000);
        const hours = Math.floor(diff / 3600000);
        const days = Math.floor(diff / 86400000);

        if (minutes < 1) return 'только что';
        if (minutes < 60) return `${minutes} мин назад`;
        if (hours < 24) return `${hours} ч назад`;
        if (days < 7) return `${days} дн назад`;

        return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
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

            // Reload conversations list to update it
            await this.loadConversations();

            // Update title if it's a new conversation
            const conv = this.conversations.find(c => c.sessionId === this.sessionId);
            if (conv && conv.title) {
                this.chatTitle.textContent = conv.title;
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

    addMessage(text, type, model = null, stats = null, scrollToBottom = true) {
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
        if (scrollToBottom) {
            this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
        }
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

// MCP Manager Class
class MCPManager {
    constructor() {
        this.mcpConnectForm = document.getElementById('mcpConnectForm');
        this.mcpServerUrl = document.getElementById('mcpServerUrl');
        this.connectButton = document.getElementById('connectButton');
        this.disconnectButton = document.getElementById('disconnectButton');
        this.refreshToolsButton = document.getElementById('refreshToolsButton');
        this.connectionStatus = document.getElementById('connectionStatus');
        this.mcpToolsList = document.getElementById('mcpToolsList');
        this.toolCallSection = document.getElementById('toolCallSection');
        this.toolCallResult = document.getElementById('toolCallResult');
        this.isConnected = false;
        this.tools = [];

        this.init();
    }

    init() {
        // Tab navigation
        document.querySelectorAll('.tab-button').forEach(button => {
            button.addEventListener('click', () => this.switchTab(button.dataset.tab));
        });

        // MCP form handlers
        this.mcpConnectForm.addEventListener('submit', (e) => this.handleConnect(e));
        this.disconnectButton.addEventListener('click', () => this.handleDisconnect());
        this.refreshToolsButton.addEventListener('click', () => this.loadTools());

        // Check initial connection status
        this.checkConnectionStatus();
    }

    switchTab(tabName) {
        // Update tab buttons
        document.querySelectorAll('.tab-button').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });

        // Update tab content
        document.querySelectorAll('.tab-content').forEach(content => {
            content.classList.remove('active');
        });

        const targetTab = document.getElementById(tabName + 'Tab');
        if (targetTab) {
            targetTab.classList.add('active');
        }
    }

    async handleConnect(event) {
        event.preventDefault();

        const serverUrl = this.mcpServerUrl.value.trim();
        if (!serverUrl) {
            alert('Введите URL сервера');
            return;
        }

        this.connectButton.disabled = true;
        this.connectButton.textContent = 'Подключение...';

        try {
            const response = await fetch('/api/mcp/connect', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    serverUrl: serverUrl,
                    transportType: 'websocket'
                })
            });

            const data = await response.json();

            if (response.ok && data.connected) {
                this.isConnected = true;
                this.updateConnectionUI(true, serverUrl);
                await this.loadTools();
            } else {
                throw new Error(data.error || 'Не удалось подключиться');
            }
        } catch (error) {
            console.error('Connection error:', error);
            alert(`Ошибка подключения: ${error.message}`);
            this.updateConnectionUI(false);
        } finally {
            this.connectButton.disabled = false;
            this.connectButton.textContent = 'Подключиться';
        }
    }

    async handleDisconnect() {
        try {
            await fetch('/api/mcp/disconnect', { method: 'POST' });
            this.isConnected = false;
            this.updateConnectionUI(false);
            this.tools = [];
            this.displayTools();
        } catch (error) {
            console.error('Disconnect error:', error);
        }
    }

    async checkConnectionStatus() {
        try {
            const response = await fetch('/api/mcp/status');
            const data = await response.json();

            if (data.connected) {
                this.isConnected = true;
                this.updateConnectionUI(true, data.serverUrl);
                await this.loadTools();
            }
        } catch (error) {
            console.error('Status check error:', error);
        }
    }

    async loadTools() {
        if (!this.isConnected) {
            return;
        }

        this.refreshToolsButton.disabled = true;
        this.refreshToolsButton.textContent = '🔄 Загрузка...';

        try {
            const response = await fetch('/api/mcp/tools');
            const data = await response.json();

            if (response.ok) {
                this.tools = data.tools || [];
                this.displayTools();
            } else {
                throw new Error('Не удалось загрузить инструменты');
            }
        } catch (error) {
            console.error('Load tools error:', error);
            this.mcpToolsList.innerHTML = `<p class="mcp-placeholder" style="color: #dc3545;">Ошибка: ${error.message}</p>`;
        } finally {
            this.refreshToolsButton.disabled = false;
            this.refreshToolsButton.textContent = '🔄 Обновить список';
        }
    }

    displayTools() {
        if (this.tools.length === 0) {
            this.mcpToolsList.innerHTML = '<p class="mcp-placeholder">Нет доступных инструментов</p>';
            return;
        }

        this.mcpToolsList.innerHTML = '';

        this.tools.forEach(tool => {
            const toolItem = document.createElement('div');
            toolItem.className = 'tool-item';

            const schemaHtml = tool.inputSchema
                ? `<div class="tool-schema">${this.escapeHtml(JSON.stringify(tool.inputSchema, null, 2))}</div>`
                : '';

            toolItem.innerHTML = `
                <div class="tool-header">
                    <span class="tool-name">🔧 ${this.escapeHtml(tool.name)}</span>
                </div>
                <div class="tool-description">${this.escapeHtml(tool.description || 'Нет описания')}</div>
                ${schemaHtml}
                <div class="tool-actions">
                    <button class="btn-small call-tool-btn" data-tool-name="${this.escapeHtml(tool.name)}">
                        ▶️ Вызвать
                    </button>
                </div>
            `;

            const callButton = toolItem.querySelector('.call-tool-btn');
            callButton.addEventListener('click', () => this.showToolCallForm(tool));

            this.mcpToolsList.appendChild(toolItem);
        });
    }

    showToolCallForm(tool) {
        const form = document.createElement('div');
        form.className = 'tool-call-modal';
        form.innerHTML = `
            <h4>Вызов инструмента: ${this.escapeHtml(tool.name)}</h4>
            <form class="tool-call-form">
                <div class="argument-input">
                    <input type="text" name="argKey" placeholder="Ключ (например: text)" />
                    <input type="text" name="argValue" placeholder="Значение" />
                </div>
                <div class="form-actions">
                    <button type="submit" class="btn-primary">Выполнить</button>
                    <button type="button" class="btn-secondary cancel-btn">Отмена</button>
                </div>
            </form>
        `;

        // Find tool item
        const toolItems = Array.from(document.querySelectorAll('.tool-item'));
        const toolItem = toolItems.find(item =>
            item.querySelector('.tool-name').textContent.includes(tool.name)
        );

        if (!toolItem) return;

        // Remove any existing forms
        const existingForm = toolItem.querySelector('.tool-call-modal');
        if (existingForm) {
            existingForm.remove();
            return;
        }

        toolItem.appendChild(form);

        // Handle form submission
        const formElement = form.querySelector('.tool-call-form');
        formElement.addEventListener('submit', async (e) => {
            e.preventDefault();
            const argKey = formElement.querySelector('[name="argKey"]').value.trim();
            const argValue = formElement.querySelector('[name="argValue"]').value.trim();

            const toolArgs = {};
            if (argKey && argValue) {
                toolArgs[argKey] = argValue;
            }

            await this.callTool(tool.name, toolArgs);
            form.remove();
        });

        // Handle cancel
        form.querySelector('.cancel-btn').addEventListener('click', () => {
            form.remove();
        });
    }

    async callTool(toolName, toolArgs) {
        try {
            const response = await fetch('/api/mcp/tools/call', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ toolName, arguments: toolArgs })
            });

            const data = await response.json();

            // Show result
            this.toolCallSection.style.display = 'block';
            this.toolCallResult.className = 'tool-call-result ' + (data.success ? 'result-success' : 'result-error');
            this.toolCallResult.innerHTML = `
                <div class="result-header">
                    ${data.success ? '✅ Успешно' : '❌ Ошибка'}: ${this.escapeHtml(toolName)}
                </div>
                <div class="result-content">${this.escapeHtml(data.result || data.error || 'Нет результата')}</div>
            `;

            // Scroll to result
            this.toolCallSection.scrollIntoView({ behavior: 'smooth' });
        } catch (error) {
            console.error('Call tool error:', error);
            alert(`Ошибка вызова инструмента: ${error.message}`);
        }
    }

    updateConnectionUI(connected, serverUrl = null) {
        this.isConnected = connected;

        if (connected) {
            this.connectionStatus.textContent = '🟢 Подключено' + (serverUrl ? `: ${serverUrl}` : '');
            this.connectionStatus.className = 'connection-status connected';
            this.disconnectButton.disabled = false;
            this.refreshToolsButton.disabled = false;
            if (serverUrl) {
                this.mcpServerUrl.value = serverUrl;
            }
        } else {
            this.connectionStatus.textContent = '⚫ Не подключено';
            this.connectionStatus.className = 'connection-status disconnected';
            this.disconnectButton.disabled = true;
            this.refreshToolsButton.disabled = true;
            this.mcpToolsList.innerHTML = '<p class="mcp-placeholder">Подключитесь к MCP серверу для просмотра инструментов</p>';
        }
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new ChatApp();
    new MCPManager();
});
