// AI Chat Application
class ChatApp {
    constructor() {
        this.messagesContainer = document.getElementById('messages');
        this.chatForm = document.getElementById('chatForm');
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.modelSelect = document.getElementById('modelSelect');
        this.ollamaStatus = document.getElementById('ollamaStatus');
        this.viewHistoryButton = document.getElementById('viewHistoryButton');
        this.historyModal = document.getElementById('historyModal');
        this.closeModalButton = document.getElementById('closeModalButton');
        this.newChatButton = document.getElementById('newChatButton');
        this.conversationsList = document.getElementById('conversationsList');
        this.chatTitle = document.getElementById('chatTitle');
        this.loadingIndicator = null;
        this.sessionId = null; // Current session ID
        this.conversations = []; // List of all conversations
        this.pollingInterval = null; // Interval for polling conversations
        this.currentProvider = 'ollama'; // Always use Ollama
        this.ollamaModels = []; // Cached Ollama models

        // Pending requests per session for parallel chat support
        this.pendingRequests = new Map(); // sessionId -> { abortController, message }
        this.chatMessages = new Map(); // sessionId -> Array of messages (cache)

        // Settings modal elements
        this.settingsModal = document.getElementById('settingsModal');
        this.openSettingsButton = document.getElementById('openSettingsButton');
        this.closeSettingsButton = document.getElementById('closeSettingsButton');
        this.saveSettingsButton = document.getElementById('saveSettingsButton');
        this.resetSettingsButton = document.getElementById('resetSettingsButton');
        this.currentModelDisplay = document.getElementById('currentModelDisplay');
        this.currentPresetDisplay = document.getElementById('currentPresetDisplay');

        // Generation settings with defaults
        this.generationSettings = {
            temperature: 0.7,
            topP: 0.9,
            topK: 40,
            repeatPenalty: 1.1,
            maxTokens: 2048,
            numCtx: 4096
        };

        // Presets
        this.presets = {
            standard: {
                temperature: 0.7,
                topP: 0.9,
                topK: 40,
                repeatPenalty: 1.1,
                maxTokens: 2048,
                numCtx: 4096,
                codingMode: false
            },
            coding: {
                temperature: 0.1,
                topP: 0.95,
                topK: 20,
                repeatPenalty: 1.15,
                maxTokens: 4096,
                numCtx: 8192,
                codingMode: true
            }
        };

        this.currentPreset = 'standard';
        this.codingMode = false;
        this.settingsExpanded = false;

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

        // Initialize settings panel
        this.initSettingsPanel();

        // Load saved settings from localStorage
        this.loadSettings();

        // Load conversations list
        await this.loadConversations();

        // Load Ollama models
        await this.loadOllamaModels();

        // Disable history button initially
        this.updateHistoryButton();

        // Start polling for conversation updates every 3 seconds
        this.startPolling();
    }

    /**
     * Initialize settings modal
     */
    initSettingsPanel() {
        // Open settings modal
        if (this.openSettingsButton) {
            this.openSettingsButton.addEventListener('click', () => this.openSettingsModal());
        }

        // Close settings modal
        if (this.closeSettingsButton) {
            this.closeSettingsButton.addEventListener('click', () => this.closeSettingsModal());
        }

        // Close on backdrop click
        if (this.settingsModal) {
            this.settingsModal.addEventListener('click', (e) => {
                if (e.target === this.settingsModal) {
                    this.closeSettingsModal();
                }
            });
        }

        // Save button
        if (this.saveSettingsButton) {
            this.saveSettingsButton.addEventListener('click', () => {
                this.saveSettings();
                this.closeSettingsModal();
            });
        }

        // Reset button
        if (this.resetSettingsButton) {
            this.resetSettingsButton.addEventListener('click', () => this.applyPreset('standard'));
        }

        // Preset buttons
        document.querySelectorAll('.preset-btn').forEach(btn => {
            btn.addEventListener('click', () => this.applyPreset(btn.dataset.preset));
        });

        // Model select change
        if (this.modelSelect) {
            this.modelSelect.addEventListener('change', () => {
                this.updateDisplays();
            });
        }

        // Sliders
        const sliders = [
            { id: 'temperatureSlider', key: 'temperature', valueId: 'temperatureValue' },
            { id: 'topPSlider', key: 'topP', valueId: 'topPValue' },
            { id: 'topKSlider', key: 'topK', valueId: 'topKValue' },
            { id: 'repeatPenaltySlider', key: 'repeatPenalty', valueId: 'repeatPenaltyValue' },
            { id: 'maxTokensSlider', key: 'maxTokens', valueId: 'maxTokensValue' },
            { id: 'numCtxSlider', key: 'numCtx', valueId: 'numCtxValue' }
        ];

        sliders.forEach(({ id, key, valueId }) => {
            const slider = document.getElementById(id);
            const valueDisplay = document.getElementById(valueId);
            if (slider && valueDisplay) {
                slider.addEventListener('input', () => {
                    const value = parseFloat(slider.value);
                    valueDisplay.textContent = value;
                    this.generationSettings[key] = value;
                    // Mark as custom preset when user changes sliders
                    this.markCustomPreset();
                });
            }
        });
    }

    /**
     * Open settings modal
     */
    openSettingsModal() {
        if (this.settingsModal) {
            this.settingsModal.classList.add('active');
            document.body.style.overflow = 'hidden';
        }
    }

    /**
     * Close settings modal
     */
    closeSettingsModal() {
        if (this.settingsModal) {
            this.settingsModal.classList.remove('active');
            document.body.style.overflow = '';
        }
    }

    /**
     * Apply preset
     */
    applyPreset(presetName) {
        const preset = this.presets[presetName];
        if (!preset) return;

        this.currentPreset = presetName;
        this.codingMode = preset.codingMode;
        this.generationSettings = { ...preset };
        delete this.generationSettings.codingMode;

        // Update sliders
        this.updateSliders();

        // Update preset buttons
        document.querySelectorAll('.preset-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.preset === presetName);
        });

        // Update displays
        this.updateDisplays();
    }

    /**
     * Mark custom preset when user changes sliders
     */
    markCustomPreset() {
        document.querySelectorAll('.preset-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        this.currentPreset = 'custom';
        this.updateDisplays();
    }

    /**
     * Update display elements in the input area
     */
    updateDisplays() {
        // Update model display
        if (this.currentModelDisplay && this.modelSelect) {
            this.currentModelDisplay.textContent = this.modelSelect.value;
        }

        // Update preset display
        if (this.currentPresetDisplay) {
            const presetNames = {
                standard: '📝 Стандартный',
                coding: '💻 Программист',
                custom: '🔧 Свой'
            };
            this.currentPresetDisplay.textContent = presetNames[this.currentPreset] || this.currentPreset;
        }
    }

    /**
     * Update sliders to match current settings
     */
    updateSliders() {
        const sliders = [
            { id: 'temperatureSlider', key: 'temperature', valueId: 'temperatureValue' },
            { id: 'topPSlider', key: 'topP', valueId: 'topPValue' },
            { id: 'topKSlider', key: 'topK', valueId: 'topKValue' },
            { id: 'repeatPenaltySlider', key: 'repeatPenalty', valueId: 'repeatPenaltyValue' },
            { id: 'maxTokensSlider', key: 'maxTokens', valueId: 'maxTokensValue' },
            { id: 'numCtxSlider', key: 'numCtx', valueId: 'numCtxValue' }
        ];

        sliders.forEach(({ id, key, valueId }) => {
            const slider = document.getElementById(id);
            const valueDisplay = document.getElementById(valueId);
            if (slider && valueDisplay && this.generationSettings[key] !== undefined) {
                slider.value = this.generationSettings[key];
                valueDisplay.textContent = this.generationSettings[key];
            }
        });
    }

    /**
     * Save settings to localStorage
     */
    saveSettings() {
        const settings = {
            generationSettings: this.generationSettings,
            currentPreset: this.currentPreset,
            codingMode: this.codingMode,
            model: this.modelSelect?.value
        };
        localStorage.setItem('chatSettings', JSON.stringify(settings));
        this.updateDisplays();
    }

    /**
     * Load settings from localStorage
     */
    loadSettings() {
        try {
            const saved = localStorage.getItem('chatSettings');
            if (saved) {
                const settings = JSON.parse(saved);
                this.generationSettings = settings.generationSettings || this.generationSettings;
                this.currentPreset = settings.currentPreset || 'standard';
                this.codingMode = settings.codingMode || false;

                // Load saved model
                if (settings.model && this.modelSelect) {
                    this.modelSelect.value = settings.model;
                }

                this.updateSliders();

                // Update preset buttons
                document.querySelectorAll('.preset-btn').forEach(btn => {
                    btn.classList.toggle('active', btn.dataset.preset === this.currentPreset);
                });

                // Update displays
                this.updateDisplays();
            }
        } catch (e) {
            console.error('Error loading settings:', e);
        }
    }

    startPolling() {
        // Clear any existing polling interval
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
        }

        // Poll every 3 seconds
        this.pollingInterval = setInterval(async () => {
            await this.loadConversations();
        }, 3000);
    }

    stopPolling() {
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
            this.pollingInterval = null;
        }
    }

    /**
     * Загрузить список моделей Ollama
     */
    async loadOllamaModels() {
        try {
            const response = await fetch('/api/ollama/models');
            if (!response.ok) {
                throw new Error('Failed to load Ollama models');
            }
            const data = await response.json();
            this.ollamaModels = data.models.map(m => ({
                value: m.name,
                name: m.name
            }));

            // Update models dropdown
            this.updateModelsDropdown();

            // Update status
            if (this.ollamaStatus) {
                this.ollamaStatus.textContent = `Ollama: ${data.count} моделей`;
                this.ollamaStatus.className = 'ollama-status available';
            }
        } catch (error) {
            console.error('Error loading Ollama models:', error);
            this.ollamaModels = [];
            if (this.ollamaStatus) {
                this.ollamaStatus.textContent = 'Ollama: недоступен';
                this.ollamaStatus.className = 'ollama-status unavailable';
            }
        }
    }

    /**
     * Обновить список моделей в выпадающем списке
     */
    updateModelsDropdown() {
        const models = this.ollamaModels;

        // Clear current options
        this.modelSelect.innerHTML = '';

        if (models.length === 0) {
            const option = document.createElement('option');
            option.value = 'qwen2.5:0.5b';
            option.textContent = 'qwen2.5:0.5b (default)';
            option.selected = true;
            this.modelSelect.appendChild(option);
            return;
        }

        // Add models, prioritize qwen
        const sortedModels = [...models].sort((a, b) => {
            if (a.value.includes('qwen')) return -1;
            if (b.value.includes('qwen')) return 1;
            return 0;
        });

        sortedModels.forEach((model, index) => {
            const option = document.createElement('option');
            option.value = model.value;
            option.textContent = model.name;
            if (index === 0) {
                option.selected = true;
            }
            this.modelSelect.appendChild(option);
        });
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
            convItem.setAttribute('data-session-id', conv.sessionId);

            if (conv.sessionId === this.sessionId) {
                convItem.classList.add('active');
            }

            // Check if this chat has pending request
            const isPending = this.pendingRequests.has(conv.sessionId);
            if (isPending) {
                convItem.classList.add('loading');
            }

            const title = conv.title || 'Новый диалог';
            const date = new Date(conv.lastAccessedAt);
            const dateStr = this.formatDate(date);

            // Add unread badge if there are unread messages
            const unreadBadge = conv.unreadCount > 0
                ? `<span class="unread-badge">${conv.unreadCount}</span>`
                : '';

            // Add loading indicator
            const loadingIndicator = isPending
                ? '<span class="chat-loading-indicator" title="Генерация ответа...">⏳</span>'
                : '';

            convItem.innerHTML = `
                <div class="conversation-content">
                    <div class="conversation-title">
                        ${loadingIndicator}
                        ${this.escapeHtml(title)}
                        ${unreadBadge}
                    </div>
                    <div class="conversation-meta">
                        <span>${conv.messageCount} сообщений</span>
                        <span>${dateStr}</span>
                    </div>
                </div>
                <button class="delete-conversation-btn" data-session-id="${conv.sessionId}" title="Удалить">✕</button>
            `;

            // Click on conversation (but not delete button) to switch
            const contentDiv = convItem.querySelector('.conversation-content');
            contentDiv.addEventListener('click', () => this.switchConversation(conv.sessionId));

            // Click on delete button
            const deleteBtn = convItem.querySelector('.delete-conversation-btn');
            deleteBtn.addEventListener('click', (e) => {
                e.stopPropagation(); // Prevent switching conversation
                this.deleteConversation(conv.sessionId);
            });

            this.conversationsList.appendChild(convItem);
        });
    }

    async switchConversation(sessionId) {
        if (sessionId === this.sessionId) {
            return; // Already on this conversation
        }

        // Hide loading indicator from previous chat if any
        this.hideLoadingIndicator();

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

            // Show loading indicator if this chat has pending request
            if (this.pendingRequests.has(sessionId)) {
                this.showLoadingIndicator();
            }

            // Load session settings if available
            if (historyData.settings) {
                this.applySessionSettings(historyData.settings);
            }

            // Mark all messages as read
            try {
                await fetch(`/api/chat/mark-read/${sessionId}`, {
                    method: 'POST'
                });
                // Reload conversations to update unread count
                await this.loadConversations();
            } catch (markReadError) {
                console.error('Error marking messages as read:', markReadError);
            }

            // Update UI
            this.displayConversations();
            this.updateHistoryButton();
            this.messageInput.focus();
        } catch (error) {
            console.error('Error switching conversation:', error);
            alert('Не удалось загрузить диалог');
        }
    }

    /**
     * Apply settings from session to current UI
     */
    applySessionSettings(settings) {
        // Apply generation settings
        if (settings.temperature !== null && settings.temperature !== undefined) {
            this.generationSettings.temperature = settings.temperature;
        }
        if (settings.topP !== null && settings.topP !== undefined) {
            this.generationSettings.topP = settings.topP;
        }
        if (settings.topK !== null && settings.topK !== undefined) {
            this.generationSettings.topK = settings.topK;
        }
        if (settings.repeatPenalty !== null && settings.repeatPenalty !== undefined) {
            this.generationSettings.repeatPenalty = settings.repeatPenalty;
        }
        if (settings.maxTokens !== null && settings.maxTokens !== undefined) {
            this.generationSettings.maxTokens = settings.maxTokens;
        }
        if (settings.numCtx !== null && settings.numCtx !== undefined) {
            this.generationSettings.numCtx = settings.numCtx;
        }

        // Apply model
        if (settings.model && this.modelSelect) {
            // Check if model exists in dropdown
            const modelOption = Array.from(this.modelSelect.options).find(opt => opt.value === settings.model);
            if (modelOption) {
                this.modelSelect.value = settings.model;
            }
        }

        // Apply preset and coding mode
        if (settings.preset) {
            this.currentPreset = settings.preset;
        }
        if (settings.codingMode !== null && settings.codingMode !== undefined) {
            this.codingMode = settings.codingMode;
        }

        // Update UI
        this.updateSliders();
        document.querySelectorAll('.preset-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.preset === this.currentPreset);
        });
        this.updateDisplays();
    }

    async deleteConversation(sessionId) {
        if (!confirm('Вы уверены, что хотите удалить этот диалог?')) {
            return;
        }

        try {
            const response = await fetch(`/api/chat/conversations/${sessionId}`, {
                method: 'DELETE'
            });

            if (!response.ok) {
                throw new Error('Failed to delete conversation');
            }

            // If we deleted the current conversation, start a new one
            if (sessionId === this.sessionId) {
                this.createNewConversation();
            }

            // Reload conversations list
            await this.loadConversations();
        } catch (error) {
            console.error('Error deleting conversation:', error);
            alert('Не удалось удалить диалог');
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

        // Check for special commands
        if (message.startsWith('/')) {
            await this.handleCommand(message);
            return;
        }

        // Capture current session context for this request
        const requestSessionId = this.sessionId; // May be null for new chat
        const selectedModel = this.modelSelect.value;
        const requestSettings = { ...this.generationSettings };
        const requestPreset = this.currentPreset; // "standard" or "coding"

        // Add user message to current chat view
        this.addMessage(message, 'user');

        // Clear input immediately (don't block user)
        this.messageInput.value = '';

        // Show loading indicator for current chat
        this.showLoadingIndicator();

        // Mark this session as having pending request
        const tempSessionKey = requestSessionId || `new_${Date.now()}`;
        this.pendingRequests.set(tempSessionKey, { message, startTime: Date.now() });
        this.updateConversationLoadingState();

        try {
            // Send message to API (async, don't block)
            const response = await this.sendMessageAsync(message, selectedModel, requestSessionId, requestSettings, requestPreset);

            // Get the actual session ID from response
            const responseSessionId = response.sessionId;

            // Remove from pending
            this.pendingRequests.delete(tempSessionKey);
            if (responseSessionId !== tempSessionKey) {
                this.pendingRequests.delete(responseSessionId);
            }

            // Check if user is still viewing the same chat
            const isCurrentChat = (requestSessionId === null && this.sessionId === responseSessionId) ||
                                  (requestSessionId === this.sessionId);

            if (isCurrentChat) {
                // User is still in the same chat - update UI directly
                this.hideLoadingIndicator();
                this.addMessage(response.response, 'assistant', response.model, {
                    inputTokens: response.inputTokens,
                    outputTokens: response.outputTokens,
                    totalTokens: response.totalTokens,
                    responseTimeMs: response.responseTimeMs,
                    historyCompressed: response.historyCompressed
                });

                // Update sessionId if this was a new chat
                if (requestSessionId === null) {
                    this.sessionId = responseSessionId;
                    this.updateHistoryButton();
                }
            } else {
                // User switched to different chat - response goes to the original chat
                // Just update the conversations list to show it has new messages
                console.log(`Response received for chat ${responseSessionId}, but user is in ${this.sessionId}`);
            }

            // Reload conversations list to update titles and unread counts
            await this.loadConversations();

            // Update title if viewing the response chat
            if (isCurrentChat) {
                const conv = this.conversations.find(c => c.sessionId === responseSessionId);
                if (conv && conv.title) {
                    this.chatTitle.textContent = conv.title;
                }
            }
        } catch (error) {
            // Remove from pending
            this.pendingRequests.delete(tempSessionKey);

            // Only show error if user is still in the same chat
            const isCurrentChat = requestSessionId === this.sessionId ||
                                  (requestSessionId === null && !this.sessionId);

            if (isCurrentChat) {
                this.hideLoadingIndicator();
                console.error('Error:', error);
                this.addMessage(
                    `Ошибка: ${error.message || 'Не удалось получить ответ'}`,
                    'error'
                );
            } else {
                console.error(`Error in background chat ${requestSessionId}:`, error);
            }
        } finally {
            this.updateConversationLoadingState();
        }
    }

    /**
     * Send message asynchronously with isolated context
     */
    async sendMessageAsync(message, model, sessionId, settings, preset) {
        const requestBody = {
            message,
            model,
            provider: this.currentProvider,
            preset,  // "standard" или "coding"
            options: {
                temperature: settings.temperature,
                topP: settings.topP,
                topK: settings.topK,
                repeatPenalty: settings.repeatPenalty,
                maxTokens: settings.maxTokens,
                numCtx: settings.numCtx
            }
        };

        if (sessionId) {
            requestBody.sessionId = sessionId;
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

    /**
     * Update loading indicators in conversation list
     */
    updateConversationLoadingState() {
        this.conversations.forEach(conv => {
            const convItem = document.querySelector(`[data-session-id="${conv.sessionId}"]`);
            if (convItem) {
                const isPending = this.pendingRequests.has(conv.sessionId);
                convItem.classList.toggle('loading', isPending);
            }
        });
    }

    async handleCommand(commandText) {
        const parts = commandText.split(' ');
        const command = parts[0].toLowerCase();
        const args = parts.slice(1);

        // Add command message to chat
        this.addMessage(commandText, 'user');

        // Clear input
        this.messageInput.value = '';

        switch (command) {
            case '/review':
                await this.handleReviewCommand(args);
                break;
            case '/dev':
                await this.handleDevCommand(args);
                break;
            case '/analytics':
            case '/a':
                await this.handleAnalyticsCommand(args);
                break;
            case '/help':
                this.showHelpMessage();
                break;
            default:
                this.addMessage(
                    `Неизвестная команда: ${command}. Используйте /help для списка команд.`,
                    'error'
                );
                this.setInputState(true);
                this.messageInput.focus();
        }
    }

    /**
     * Обработка команды /dev для создания веб-приложений
     */
    async handleDevCommand(args) {
        // Disable input while processing
        this.setInputState(false);

        const fullCommand = '/dev ' + args.join(' ');

        // Show loading indicator
        this.showLoadingIndicator('Создаю приложение...');

        try {
            // Отправляем команду на сервер
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    message: fullCommand,
                    sessionId: this.sessionId,
                    model: this.modelSelect.value
                })
            });

            if (!response.ok) {
                throw new Error('Failed to process /dev command');
            }

            const data = await response.json();
            this.sessionId = data.sessionId;

            // Скрываем индикатор загрузки
            this.hideLoadingIndicator();

            // Парсим ответ и добавляем кнопки
            const responseHtml = this.formatDevResponse(data.response);
            this.addMessage(responseHtml, 'assistant', true);

            // Update title if new session
            if (data.conversationTitle) {
                this.chatTitle.textContent = data.conversationTitle;
            }

            this.updateHistoryButton();

        } catch (error) {
            console.error('Error processing /dev command:', error);
            this.hideLoadingIndicator();
            this.addMessage(`Ошибка: ${error.message}`, 'error');
        } finally {
            this.setInputState(true);
            this.messageInput.focus();
        }
    }

    /**
     * Форматирование ответа /dev с кнопками
     */
    formatDevResponse(response) {
        // Ищем URL приложения в ответе
        const urlMatch = response.match(/URL:\s*\/apps\/([a-zA-Z0-9_-]+)\/?/);
        const appName = urlMatch ? urlMatch[1] : null;

        let html = this.formatMessage(response);

        // Добавляем кнопки если нашли имя приложения
        if (appName) {
            html += `
                <div class="dev-actions">
                    <button class="dev-button dev-button-open" onclick="window.open('/apps/${appName}/', '_blank')">
                        ▶️ Открыть приложение
                    </button>
                    <button class="dev-button dev-button-preview" onclick="chatApp.showAppPreview('${appName}')">
                        🖼️ Предпросмотр
                    </button>
                </div>
            `;
        }

        return html;
    }

    /**
     * Показать предпросмотр приложения в модальном окне
     */
    showAppPreview(appName) {
        // Создаем модальное окно с iframe
        const modal = document.createElement('div');
        modal.className = 'app-preview-modal';
        modal.innerHTML = `
            <div class="app-preview-content">
                <div class="app-preview-header">
                    <h3>${appName}</h3>
                    <div class="app-preview-actions">
                        <button onclick="window.open('/apps/${appName}/', '_blank')">↗️ Новая вкладка</button>
                        <button onclick="this.closest('.app-preview-modal').remove()">✕</button>
                    </div>
                </div>
                <iframe src="/apps/${appName}/" frameborder="0"></iframe>
            </div>
        `;

        // Закрытие по клику на фон
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.remove();
            }
        });

        // Закрытие по Escape
        const escHandler = (e) => {
            if (e.key === 'Escape') {
                modal.remove();
                document.removeEventListener('keydown', escHandler);
            }
        };
        document.addEventListener('keydown', escHandler);

        document.body.appendChild(modal);
    }

    /**
     * Обработка команды /analytics для аналитики тикетов
     */
    async handleAnalyticsCommand(args) {
        this.setInputState(false);

        const fullCommand = args.length > 0 ? '/analytics ' + args.join(' ') : '/analytics';

        const loadingMessage = args.length > 0 ? 'Анализирую данные...' : 'Загружаю справку...';
        this.showLoadingIndicator(loadingMessage);

        try {
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    message: fullCommand,
                    sessionId: this.currentSessionId
                })
            });

            this.hideLoadingIndicator();

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();

            if (data.sessionId && !this.currentSessionId) {
                this.currentSessionId = data.sessionId;
            }

            this.addMessage(data.response, 'assistant');
        } catch (error) {
            this.hideLoadingIndicator();
            this.addMessage(`Ошибка аналитики: ${error.message}`, 'error');
        } finally {
            this.setInputState(true);
            this.messageInput.focus();
        }
    }

    async handleReviewCommand(args) {
        // Disable input while processing
        this.setInputState(false);

        // Show loading indicator
        this.showLoadingIndicator();

        try {
            const target = args.length > 0 ? args.join(' ') : null;

            // First, get list of changed files to check if we need to split
            const statusCheck = await this.sendMessage(
                'Используй get_git_status() и верни ТОЛЬКО список измененных файлов, по одному на строку, без дополнительного текста.',
                this.modelSelect.value,
                'Ты - помощник. Вызови get_git_status() и верни только пути к файлам, по одному на строку. Никакого другого текста.'
            );

            // Parse files from response
            const changedFiles = statusCheck.response
                .split('\n')
                .filter(line => line.trim().length > 0 && !line.includes(':') && !line.includes('Статус'))
                .map(line => line.trim().replace(/^[📝📄➕➖❓🔄]\s*/, '').trim())
                .filter(file => file.endsWith('.kt') || file.endsWith('.js') || file.endsWith('.md'));

            // If more than 3 files, do file-by-file analysis
            if (changedFiles.length > 3) {
                await this.handleMultiFileReview(changedFiles);
                return;
            }

            // Otherwise, do regular review
            let reviewMessage;
            if (target) {
                // Review specific branch or commit
                reviewMessage = `Сделай детальный code review для ${target}. Используй git инструменты для получения информации об изменениях. Проанализируй код на наличие:
- Потенциальных багов и ошибок
- Security уязвимостей (SQL injection, XSS, CSRF и т.д.)
- Performance issues
- Code smells и anti-patterns
- Нарушений best practices
Дай конкретные рекомендации по улучшению.`;
            } else {
                // Review current changes
                reviewMessage = `Сделай code review текущих изменений:

1. Используй get_git_status() - узнай какие файлы изменены
2. Используй get_git_diff() - получи детали изменений
3. Проанализируй на: баги, security, performance, best practices
4. Дай структурированный отчет

Отвечай в формате из системного промпта.`;
            }

            // Send review request with special system prompt
            const systemPrompt = `Ты code reviewer. Используй MCP инструменты get_git_status и get_git_diff для анализа.

Формат ответа (СТРОГО следуй структуре):

## 📊 Code Review

### 📁 Файлы
[список из git status]

### ✅ Хорошо
[3-5 пунктов]

### ⚠️ Проблемы
[конкретные проблемы с номерами строк]

### 💡 Рекомендации
[конкретные советы]

ВАЖНО:
- НЕ копируй код из diff, только анализируй
- Указывай конкретные файлы и строки
- Будь кратким и конкретным`;

            const response = await this.sendMessage(reviewMessage, this.modelSelect.value, systemPrompt);

            // Hide loading indicator
            this.hideLoadingIndicator();

            // Add review result to chat
            this.addMessage(response.response, 'assistant', response.model, {
                inputTokens: response.inputTokens,
                outputTokens: response.outputTokens,
                totalTokens: response.totalTokens,
                responseTimeMs: response.responseTimeMs,
                historyCompressed: response.historyCompressed
            });

            // Reload conversations list
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

            // Check if it's a rate limit error
            let errorMessage = error.message || 'Не удалось получить ответ';
            if (errorMessage.includes('rate_limit') || errorMessage.includes('429')) {
                errorMessage = `⏱️ Rate Limit: Превышен лимит запросов к Claude API.

Решения:
• Подождите 1 минуту и попробуйте снова
• Используйте модель Haiku (она быстрее и экономичнее)
• Проверьте конкретный файл вместо всех изменений
• Задайте более простой вопрос без /review

Технические детали: ${error.message}`;
            }

            this.addMessage(
                `Ошибка при выполнении code review:\n\n${errorMessage}`,
                'error'
            );
        } finally {
            // Re-enable input
            this.setInputState(true);
            this.messageInput.focus();
        }
    }

    async handleMultiFileReview(files) {
        try {
            this.hideLoadingIndicator();
            this.addMessage(
                `🔍 Обнаружено ${files.length} измененных файлов. Выполняю пофайловый анализ для избежания rate limit...`,
                'assistant'
            );
            this.showLoadingIndicator();

            const fileReviews = [];

            for (let i = 0; i < files.length; i++) {
                const file = files[i];

                this.hideLoadingIndicator();
                this.addMessage(`📄 Анализирую файл ${i + 1}/${files.length}: ${file}`, 'assistant');
                this.showLoadingIndicator();

                try {
                    const reviewMessage = `Проанализируй изменения в файле ${file}:

1. Используй get_git_diff(file="${file}") чтобы получить diff
2. Проверь на: баги, security, performance
3. Дай краткий отчет (3-5 пунктов)`;

                    const systemPrompt = `Ты code reviewer. Используй get_git_diff для анализа конкретного файла.
Формат:
✅ Хорошо: [1-2 пункта]
⚠️ Проблемы: [конкретные проблемы]
💡 Рекомендации: [советы]
Будь кратким!`;

                    const response = await this.sendMessage(reviewMessage, this.modelSelect.value, systemPrompt);

                    fileReviews.push({
                        file: file,
                        review: response.response
                    });
                } catch (error) {
                    console.error(`Error reviewing ${file}:`, error);
                    fileReviews.push({
                        file: file,
                        review: `❌ Ошибка: ${error.message}`
                    });
                }
            }

            // Aggregate results
            this.hideLoadingIndicator();

            const aggregatedReview = this.aggregateFileReviews(fileReviews);
            this.addMessage(aggregatedReview, 'assistant', this.modelSelect.value);

            // Reload conversations
            await this.loadConversations();

            const conv = this.conversations.find(c => c.sessionId === this.sessionId);
            if (conv && conv.title) {
                this.chatTitle.textContent = conv.title;
            }
        } catch (error) {
            this.hideLoadingIndicator();
            console.error('Error in multi-file review:', error);
            this.addMessage(
                `Ошибка при пофайловом анализе: ${error.message}`,
                'error'
            );
        } finally {
            this.setInputState(true);
            this.messageInput.focus();
        }
    }

    aggregateFileReviews(fileReviews) {
        let result = '## 📊 Code Review (пофайловый анализ)\n\n';
        result += `### 📁 Проанализировано файлов: ${fileReviews.length}\n\n`;

        fileReviews.forEach((fr, idx) => {
            result += `#### ${idx + 1}. ${fr.file}\n\n`;
            result += `${fr.review}\n\n`;
            result += '---\n\n';
        });

        result += '### 📋 Общие выводы\n\n';
        result += '✅ Все измененные файлы проанализированы отдельно для более качественного review.\n';
        result += '💡 Обратите внимание на проблемы и рекомендации для каждого файла выше.';

        return result;
    }

    showHelpMessage() {
        const helpText = `Доступные команды:

/dev [описание] - Создать веб-приложение или игру
  Claude AI сгенерирует HTML/CSS/JS код на основе описания

  Подкоманды:
  • /dev [описание] - создать новое приложение
  • /dev edit [имя] [изменения] - изменить существующее приложение
  • /dev list - показать список приложений
  • /dev delete [имя] - удалить приложение

  Примеры:
  • /dev Сделай игру 2048 на JS
  • /dev Создай калькулятор с красивым дизайном
  • /dev edit igra_2048 Добавь анимации при движении плиток
  • /dev list

/review [ветка/коммит] - Выполнить code review
  Без параметров: review текущих изменений (git diff)
  С параметром: review указанной ветки или коммита

  Умный режим: при >3 файлах автоматически делает пофайловый анализ

  Примеры:
  • /review - проверить текущие изменения
  • /review main - проверить изменения в ветке main
  • /review 5364a66 - проверить конкретный коммит

/analytics [вопрос] или /a [вопрос] - Аналитика тикетов
  Задавайте вопросы по 500 тикетам техподдержки

  Подкоманды:
  • /analytics stats - статистика по тикетам
  • /analytics status - статус системы

  Примеры:
  • /a какая ошибка чаще всего?
  • /a топ-3 проблемы пользователей
  • /a где пользователи теряются в воронке?
  • /analytics stats

/help - Показать это сообщение`;

        this.addMessage(helpText, 'assistant');
        this.setInputState(true);
        this.messageInput.focus();
    }

    async sendMessage(message, model, systemPrompt = null) {
        const requestBody = { message };
        if (model) {
            requestBody.model = model;
        }
        // Send provider
        if (this.currentProvider) {
            requestBody.provider = this.currentProvider;
        }
        // Send sessionId if we have one
        if (this.sessionId) {
            requestBody.sessionId = this.sessionId;
        }
        // Send custom system prompt if provided
        if (systemPrompt) {
            requestBody.systemPrompt = systemPrompt;
        }
        // Send preset (determines system prompt and default options)
        requestBody.preset = this.currentPreset;
        // Send generation options
        requestBody.options = {
            temperature: this.generationSettings.temperature,
            topP: this.generationSettings.topP,
            topK: this.generationSettings.topK,
            repeatPenalty: this.generationSettings.repeatPenalty,
            maxTokens: this.generationSettings.maxTokens,
            numCtx: this.generationSettings.numCtx
        };

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

    /**
     * Попытка извлечь текст из JSON ответа (для Ollama)
     */
    parseJsonResponse(text) {
        // Проверяем, похоже ли на JSON
        const trimmed = text.trim();
        if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) {
            return text;
        }

        try {
            const parsed = JSON.parse(trimmed);
            // Если есть поле answer - возвращаем его
            if (parsed.answer) {
                return parsed.answer;
            }
            // Если есть поле response - возвращаем его
            if (parsed.response) {
                return parsed.response;
            }
            // Если есть поле text - возвращаем его
            if (parsed.text) {
                return parsed.text;
            }
            // Если есть поле content - возвращаем его
            if (parsed.content) {
                return parsed.content;
            }
            // Иначе возвращаем оригинал
            return text;
        } catch (e) {
            // Не JSON - возвращаем как есть
            return text;
        }
    }

    addMessage(text, type, modelOrHtml = null, stats = null, scrollToBottom = true) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        const label = type === 'user' ? 'Вы' : type === 'error' ? 'Ошибка' : 'Assistant';
        let modelInfo = '';
        let formattedText;

        // Parse JSON response if it's from assistant
        let displayText = text;
        if (type === 'assistant' && modelOrHtml !== true) {
            displayText = this.parseJsonResponse(text);
        }

        // Check if modelOrHtml is boolean (true = HTML content) or string (model name)
        if (modelOrHtml === true) {
            // Text is already HTML formatted
            formattedText = displayText;
        } else {
            // Regular text - escape and format
            if (modelOrHtml && type === 'assistant' && typeof modelOrHtml === 'string') {
                const modelName = this.getModelDisplayName(modelOrHtml);
                modelInfo = ` <span class="model-badge">${modelName}</span>`;
            }
            // Preserve line breaks by converting \n to <br>
            formattedText = this.escapeHtml(displayText).replace(/\n/g, '<br>');
        }

        contentDiv.innerHTML = `<strong>${label}:${modelInfo}</strong> ${formattedText}`;

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
        // All models are Ollama models now
        if (modelId) {
            return `🦙 ${modelId}`;
        }
        return modelId;
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

    /**
     * Форматирование текста с поддержкой базового markdown
     */
    formatMessage(text) {
        let html = this.escapeHtml(text);

        // Заголовки (## и ###)
        html = html.replace(/^### (.+)$/gm, '<h4>$1</h4>');
        html = html.replace(/^## (.+)$/gm, '<h3>$1</h3>');

        // Жирный текст (**text**)
        html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

        // Курсив (*text*)
        html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');

        // Код (`code`)
        html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

        // Списки (- item)
        html = html.replace(/^- (.+)$/gm, '• $1');

        // Переносы строк
        html = html.replace(/\n/g, '<br>');

        return html;
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

// Task Manager Class
class TaskManager {
    constructor() {
        this.createTaskForm = document.getElementById('createTaskForm');
        this.tasksList = document.getElementById('tasksList');
        this.scheduleTypeSelect = document.getElementById('scheduleType');
        this.refreshTasksButton = document.getElementById('refreshTasksButton');
        this.resetTaskFormButton = document.getElementById('resetTaskForm');

        this.tasks = [];

        this.init();
    }

    init() {
        // Event listeners
        this.createTaskForm.addEventListener('submit', (e) => this.handleCreateTask(e));
        this.scheduleTypeSelect.addEventListener('change', () => this.updateScheduleOptions());
        this.refreshTasksButton.addEventListener('click', () => this.loadTasks());
        this.resetTaskFormButton.addEventListener('click', () => this.resetForm());

        // Load tasks initially
        this.loadTasks();
    }

    updateScheduleOptions() {
        const type = this.scheduleTypeSelect.value;

        document.getElementById('onceSchedule').style.display = type === 'ONCE' ? 'block' : 'none';
        document.getElementById('dailySchedule').style.display = type === 'DAILY' ? 'block' : 'none';
        document.getElementById('weeklySchedule').style.display = type === 'WEEKLY' ? 'block' : 'none';
    }

    async handleCreateTask(e) {
        e.preventDefault();

        const name = document.getElementById('taskName').value.trim();
        const description = document.getElementById('taskDescription').value.trim();
        const question = document.getElementById('taskQuestion').value.trim();
        const sessionId = document.getElementById('taskSessionId').value.trim() || null;
        const scheduleType = this.scheduleTypeSelect.value;

        if (!name || !question) {
            alert('Пожалуйста, заполните обязательные поля');
            return;
        }

        // Build schedule object
        const schedule = { type: scheduleType };

        if (scheduleType === 'ONCE') {
            const dateTime = document.getElementById('onceDateTime').value;
            if (!dateTime) {
                alert('Пожалуйста, укажите дату и время');
                return;
            }
            schedule.startTime = dateTime + ':00';
        } else if (scheduleType === 'DAILY') {
            schedule.hour = parseInt(document.getElementById('dailyHour').value);
            schedule.minute = parseInt(document.getElementById('dailyMinute').value);
        } else if (scheduleType === 'WEEKLY') {
            schedule.dayOfWeek = parseInt(document.getElementById('weeklyDay').value);
            schedule.hour = parseInt(document.getElementById('weeklyHour').value);
            schedule.minute = parseInt(document.getElementById('weeklyMinute').value);
        }

        const taskData = {
            name,
            description,
            question,
            sessionId,
            schedule
        };

        try {
            const response = await fetch('/api/tasks', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(taskData)
            });

            if (response.ok) {
                alert('Задача успешно создана!');
                this.resetForm();
                this.loadTasks();
            } else {
                const error = await response.json();
                alert(`Ошибка: ${error.error || 'Не удалось создать задачу'}`);
            }
        } catch (error) {
            console.error('Error creating task:', error);
            alert('Ошибка при создании задачи');
        }
    }

    async loadTasks() {
        try {
            const response = await fetch('/api/tasks');
            const data = await response.json();

            this.tasks = data.tasks || [];
            this.renderTasks();
        } catch (error) {
            console.error('Error loading tasks:', error);
            this.tasksList.innerHTML = '<p class="error">Ошибка загрузки задач</p>';
        }
    }

    renderTasks() {
        if (this.tasks.length === 0) {
            this.tasksList.innerHTML = '<p class="mcp-placeholder">Нет созданных задач</p>';
            return;
        }

        this.tasksList.innerHTML = this.tasks.map(task => this.renderTask(task)).join('');

        // Add event listeners for task actions
        this.tasks.forEach(task => {
            const deleteBtn = document.getElementById(`delete-${task.id}`);
            const toggleBtn = document.getElementById(`toggle-${task.id}`);
            const viewBtn = document.getElementById(`view-${task.id}`);

            if (deleteBtn) deleteBtn.addEventListener('click', () => this.deleteTask(task.id));
            if (toggleBtn) toggleBtn.addEventListener('click', () => this.toggleTask(task.id, !task.enabled));
            if (viewBtn) viewBtn.addEventListener('click', () => this.viewExecutions(task.id));
        });
    }

    renderTask(task) {
        const scheduleText = this.getScheduleText(task.schedule);
        const nextExecution = task.nextExecutionAt ? new Date(task.nextExecutionAt).toLocaleString('ru-RU') : 'N/A';
        const lastExecution = task.lastExecutedAt ? new Date(task.lastExecutedAt).toLocaleString('ru-RU') : 'Никогда';

        return `
            <div class="task-card ${task.enabled ? '' : 'disabled'}">
                <div class="task-header">
                    <h3>${this.escapeHtml(task.name)}</h3>
                    <span class="task-status ${task.enabled ? 'enabled' : 'disabled'}">
                        ${task.enabled ? '🟢 Активна' : '⚫ Отключена'}
                    </span>
                </div>

                ${task.description ? `<p class="task-description">${this.escapeHtml(task.description)}</p>` : ''}

                <div class="task-details">
                    <p><strong>Вопрос:</strong> ${this.escapeHtml(task.question)}</p>
                    <p><strong>Расписание:</strong> ${scheduleText}</p>
                    <p><strong>Следующее выполнение:</strong> ${nextExecution}</p>
                    <p><strong>Последнее выполнение:</strong> ${lastExecution}</p>
                    ${task.sessionId ? `<p><strong>ID диалога:</strong> <code>${task.sessionId}</code></p>` : ''}
                </div>

                <div class="task-actions">
                    <button id="toggle-${task.id}" class="btn-secondary">
                        ${task.enabled ? '⏸️ Остановить' : '▶️ Запустить'}
                    </button>
                    <button id="view-${task.id}" class="btn-secondary">📊 История</button>
                    <button id="delete-${task.id}" class="btn-danger">🗑️ Удалить</button>
                </div>
            </div>
        `;
    }

    getScheduleText(schedule) {
        switch (schedule.type) {
            case 'ONCE':
                return `Одноразово: ${new Date(schedule.startTime).toLocaleString('ru-RU')}`;
            case 'DAILY':
                return `Ежедневно в ${String(schedule.hour).padStart(2, '0')}:${String(schedule.minute).padStart(2, '0')}`;
            case 'WEEKLY':
                const days = ['', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье'];
                return `Еженедельно, ${days[schedule.dayOfWeek]} в ${String(schedule.hour).padStart(2, '0')}:${String(schedule.minute).padStart(2, '0')}`;
            default:
                return 'Неизвестно';
        }
    }

    async toggleTask(taskId, enabled) {
        try {
            const response = await fetch(`/api/tasks/${taskId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled })
            });

            if (response.ok) {
                this.loadTasks();
            } else {
                alert('Ошибка при изменении статуса задачи');
            }
        } catch (error) {
            console.error('Error toggling task:', error);
            alert('Ошибка при изменении статуса задачи');
        }
    }

    async deleteTask(taskId) {
        if (!confirm('Вы уверены, что хотите удалить эту задачу?')) {
            return;
        }

        try {
            const response = await fetch(`/api/tasks/${taskId}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                this.loadTasks();
            } else {
                alert('Ошибка при удалении задачи');
            }
        } catch (error) {
            console.error('Error deleting task:', error);
            alert('Ошибка при удалении задачи');
        }
    }

    async viewExecutions(taskId) {
        try {
            const response = await fetch(`/api/tasks/${taskId}/executions`);
            const data = await response.json();

            if (data.executions && data.executions.length > 0) {
                const executionsHtml = data.executions.map(ex => `
                    <div class="execution-item ${ex.success ? 'success' : 'error'}">
                        <p><strong>Выполнено:</strong> ${new Date(ex.executedAt).toLocaleString('ru-RU')}</p>
                        <p><strong>Статус:</strong> ${ex.success ? '✅ Успешно' : '❌ Ошибка'}</p>
                        <p><strong>Время выполнения:</strong> ${ex.executionTimeMs}мс</p>
                        ${ex.response ? `<p><strong>Ответ:</strong> ${this.escapeHtml(ex.response.substring(0, 200))}...</p>` : ''}
                        ${ex.errorMessage ? `<p class="error"><strong>Ошибка:</strong> ${this.escapeHtml(ex.errorMessage)}</p>` : ''}
                    </div>
                `).join('');

                alert('История выполнений:\n\n' + executionsHtml.replace(/<[^>]*>/g, '\n'));
            } else {
                alert('Нет истории выполнений для этой задачи');
            }
        } catch (error) {
            console.error('Error loading executions:', error);
            alert('Ошибка при загрузке истории выполнений');
        }
    }

    resetForm() {
        this.createTaskForm.reset();
        this.updateScheduleOptions();
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Apps Manager Class
class AppsManager {
    constructor() {
        this.appsGrid = document.getElementById('appsGrid');
        this.refreshButton = document.getElementById('refreshAppsButton');
        this.devCommandForm = document.getElementById('devCommandForm');
        this.devCommandInput = document.getElementById('devCommandInput');
        this.devCommandButton = document.getElementById('devCommandButton');
        this.devCommandStatus = document.getElementById('devCommandStatus');
        this.apps = [];

        this.init();
    }

    init() {
        if (this.refreshButton) {
            this.refreshButton.addEventListener('click', () => this.loadApps());
        }

        // Handle dev command form submission
        if (this.devCommandForm) {
            this.devCommandForm.addEventListener('submit', (e) => this.handleDevCommand(e));
        }

        // Load apps when tab is shown
        document.querySelectorAll('.tab-button').forEach(button => {
            button.addEventListener('click', () => {
                if (button.dataset.tab === 'apps') {
                    this.loadApps();
                    if (this.devCommandInput) {
                        this.devCommandInput.focus();
                    }
                }
            });
        });

        // Initial load
        this.loadApps();
    }

    async handleDevCommand(e) {
        e.preventDefault();

        const description = this.devCommandInput.value.trim();
        if (!description) return;

        // Disable form
        this.devCommandInput.disabled = true;
        this.devCommandButton.disabled = true;
        this.devCommandButton.textContent = 'Создание...';

        // Show status
        this.showStatus('Создаю приложение... Это может занять до минуты.', '');

        try {
            // Get model from chat app if available
            const model = window.chatApp?.modelSelect?.value || 'qwen2.5:0.5b';
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    message: `/dev ${description}`,
                    model: model
                })
            });

            const data = await response.json();

            if (!response.ok) {
                // Extract error message from response
                const errorMsg = data.error || data.response || 'Не удалось создать приложение';
                throw new Error(errorMsg);
            }

            // Clear input
            this.devCommandInput.value = '';

            // Show success
            this.showStatus('Приложение создано!', 'success');

            // Reload apps list
            await this.loadApps();

            // Parse app name from response and open it in modal
            const urlMatch = data.response?.match(/URL:\s*\/apps\/([a-zA-Z0-9_-]+)\/?/);
            if (urlMatch) {
                const appName = urlMatch[1];
                setTimeout(() => this.showPreview(appName), 500);
            }

        } catch (error) {
            console.error('Error creating app:', error);
            // Make error message more readable
            let errorText = error.message;
            // Check for common API errors and provide friendly messages
            if (errorText.includes('credit balance is too low')) {
                errorText = 'Недостаточно средств на балансе Anthropic API. Пополните баланс в Plans & Billing.';
            } else if (errorText.includes('rate_limit') || errorText.includes('429')) {
                errorText = 'Превышен лимит запросов. Подождите минуту и попробуйте снова.';
            } else if (errorText.length > 200) {
                errorText = errorText.substring(0, 200) + '...';
            }
            this.showStatus(`Ошибка: ${errorText}`, 'error');
        } finally {
            // Re-enable form
            this.devCommandInput.disabled = false;
            this.devCommandButton.disabled = false;
            this.devCommandButton.textContent = '🚀 Создать';
            this.devCommandInput.focus();
        }
    }

    showStatus(message, type) {
        if (this.devCommandStatus) {
            this.devCommandStatus.textContent = message;
            this.devCommandStatus.className = 'dev-command-status visible';
            if (type) {
                this.devCommandStatus.classList.add(type);
            }

            // Auto-hide success messages after 5 seconds
            if (type === 'success') {
                setTimeout(() => {
                    this.devCommandStatus.classList.remove('visible');
                }, 5000);
            }
        }
    }

    async loadApps() {
        try {
            this.appsGrid.innerHTML = '<div class="loading">Загрузка приложений...</div>';

            const response = await fetch('/api/apps');
            if (!response.ok) {
                throw new Error('Failed to load apps');
            }

            const data = await response.json();
            this.apps = data.apps || [];
            this.renderApps();
        } catch (error) {
            console.error('Error loading apps:', error);
            this.appsGrid.innerHTML = `
                <div class="apps-placeholder">
                    <p>Ошибка загрузки приложений</p>
                    <small>${error.message}</small>
                </div>
            `;
        }
    }

    renderApps() {
        if (this.apps.length === 0) {
            this.appsGrid.innerHTML = `
                <div class="apps-placeholder">
                    <p>Пока нет созданных приложений</p>
                    <small>Используйте команду <code>/dev Сделай игру 2048</code> в чате</small>
                </div>
            `;
            return;
        }

        this.appsGrid.innerHTML = this.apps.map(app => this.renderAppCard(app)).join('');

        // Add event listeners
        this.apps.forEach(app => {
            const card = document.querySelector(`[data-app-name="${app.name}"]`);
            if (card) {
                const openBtn = card.querySelector('.btn-open');
                const newTabBtn = card.querySelector('.btn-newtab');
                const deleteBtn = card.querySelector('.btn-delete');

                if (openBtn) {
                    openBtn.addEventListener('click', () => this.showPreview(app.name));
                }
                if (newTabBtn) {
                    newTabBtn.addEventListener('click', () => window.open(`/apps/${app.name}/`, '_blank'));
                }
                if (deleteBtn) {
                    deleteBtn.addEventListener('click', () => this.deleteApp(app.name));
                }
            }
        });
    }

    renderAppCard(app) {
        const createdDate = app.createdAt
            ? new Date(app.createdAt).toLocaleDateString('ru-RU', {
                day: 'numeric',
                month: 'short',
                year: 'numeric'
            })
            : 'Неизвестно';

        return `
            <div class="app-card" data-app-name="${this.escapeHtml(app.name)}">
                <div class="app-card-preview">
                    <iframe src="/apps/${this.escapeHtml(app.name)}/" loading="lazy"></iframe>
                    <div class="app-card-overlay"></div>
                </div>
                <div class="app-card-info">
                    <div class="app-card-name">${this.escapeHtml(app.name)}</div>
                    <div class="app-card-meta">
                        ${app.files?.length || 0} файлов • ${createdDate}
                    </div>
                    <div class="app-card-actions">
                        <button class="btn-open">▶️ Открыть</button>
                        <button class="btn-newtab">↗️ В новой вкладке</button>
                        <button class="btn-delete">🗑️</button>
                    </div>
                </div>
            </div>
        `;
    }

    showPreview(appName) {
        // Use ChatApp's showAppPreview if available
        if (window.chatApp && window.chatApp.showAppPreview) {
            window.chatApp.showAppPreview(appName);
        } else {
            // Fallback: open in new tab
            window.open(`/apps/${appName}/`, '_blank');
        }
    }

    async deleteApp(appName) {
        if (!confirm(`Вы уверены, что хотите удалить приложение "${appName}"?`)) {
            return;
        }

        try {
            const response = await fetch(`/api/apps/${encodeURIComponent(appName)}`, {
                method: 'DELETE'
            });

            if (!response.ok) {
                throw new Error('Failed to delete app');
            }

            // Reload apps list
            await this.loadApps();
        } catch (error) {
            console.error('Error deleting app:', error);
            alert(`Ошибка удаления приложения: ${error.message}`);
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
    window.chatApp = new ChatApp();
    new MCPManager();
    new TaskManager();
    new AppsManager();

    // Initialize voice recorder
    if (window.VoiceRecorder) {
        window.voiceRecorder = new VoiceRecorder(window.chatApp);
    }
});
