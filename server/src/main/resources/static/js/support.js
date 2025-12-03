// Support Tab functionality for AI Chat Service

let currentSupportSessionId = null;

// Initialize support tab when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    initializeSupportTab();
});

function initializeSupportTab() {
    // User Search Form
    const userSearchForm = document.getElementById('userSearchForm');
    if (userSearchForm) {
        userSearchForm.addEventListener('submit', handleUserSearch);
    }

    // Create Ticket Form
    const createTicketForm = document.getElementById('createTicketForm');
    if (createTicketForm) {
        createTicketForm.addEventListener('submit', handleCreateTicket);
    }

    // Reset Ticket Form Button
    const resetTicketBtn = document.getElementById('resetTicketForm');
    if (resetTicketBtn) {
        resetTicketBtn.addEventListener('click', resetTicketForm);
    }

    // Support Chat Form
    const supportChatForm = document.getElementById('supportChatForm');
    if (supportChatForm) {
        supportChatForm.addEventListener('submit', handleSupportChat);
    }
}

// Handle user search
async function handleUserSearch(e) {
    e.preventDefault();

    const userId = document.getElementById('userId').value.trim();
    if (!userId) return;

    try {
        // Fetch user info
        const userResponse = await fetch(`/api/support/user/${userId}`);
        if (!userResponse.ok) {
            throw new Error(`HTTP ${userResponse.status}: ${userResponse.statusText}`);
        }
        const userData = await userResponse.json();

        // Display user info
        displayUserInfo(userData);

        // Fetch user tickets
        const ticketsResponse = await fetch(`/api/support/tickets/${userId}`);
        if (!ticketsResponse.ok) {
            throw new Error(`HTTP ${ticketsResponse.status}: ${ticketsResponse.statusText}`);
        }
        const ticketsData = await ticketsResponse.json();

        // Display tickets
        displayUserTickets(ticketsData.tickets, userId);

        // Auto-fill ticket creation form
        document.getElementById('ticketUserId').value = userId;
        document.getElementById('supportUserId').value = userId;

    } catch (error) {
        console.error('Error fetching user data:', error);
        alert(`Ошибка при загрузке данных пользователя: ${error.message}`);
    }
}

// Display user info
function displayUserInfo(user) {
    const userInfoDiv = document.getElementById('userInfo');
    userInfoDiv.style.display = 'block';

    const roleColors = {
        'basic': '#3498db',
        'premium': '#9b59b6',
        'enterprise': '#e74c3c'
    };

    userInfoDiv.innerHTML = `
        <div class="user-card">
            <h3>${user.name}</h3>
            <p><strong>Email:</strong> ${user.email}</p>
            <p><strong>User ID:</strong> <code>${user.userId}</code></p>
            <p><strong>Роль:</strong> <span class="user-role" style="background-color: ${roleColors[user.role] || '#95a5a6'}">${user.role.toUpperCase()}</span></p>
            <p><strong>Зарегистрирован:</strong> ${formatDate(user.registeredAt)}</p>
        </div>
    `;
}

// Display user tickets
function displayUserTickets(tickets, userId) {
    const ticketsListDiv = document.getElementById('ticketsList');

    if (tickets.length === 0) {
        ticketsListDiv.innerHTML = '<p class="support-placeholder">У пользователя нет тикетов</p>';
        return;
    }

    const statusColors = {
        'OPEN': '#3498db',
        'IN_PROGRESS': '#f39c12',
        'WAITING_USER': '#9b59b6',
        'RESOLVED': '#2ecc71',
        'CLOSED': '#95a5a6'
    };

    const priorityColors = {
        'LOW': '#95a5a6',
        'MEDIUM': '#3498db',
        'HIGH': '#f39c12',
        'URGENT': '#e74c3c'
    };

    ticketsListDiv.innerHTML = `
        <div class="tickets-grid">
            ${tickets.map(ticket => `
                <div class="ticket-card" data-ticket-id="${ticket.id}">
                    <div class="ticket-header">
                        <span class="ticket-id">#${ticket.id}</span>
                        <span class="ticket-status" style="background-color: ${statusColors[ticket.status]}">${ticket.status}</span>
                    </div>
                    <h4 class="ticket-subject">${escapeHtml(ticket.subject)}</h4>
                    <p class="ticket-description">${escapeHtml(ticket.description).substring(0, 100)}...</p>
                    <div class="ticket-meta">
                        <span class="ticket-priority" style="color: ${priorityColors[ticket.priority]}">⚠️ ${ticket.priority}</span>
                        <span class="ticket-date">📅 ${formatDate(ticket.createdAt)}</span>
                    </div>
                    <div class="ticket-actions">
                        <button class="btn-small" onclick="viewTicketDetails('${ticket.id}')">Подробнее</button>
                        <button class="btn-small btn-secondary" onclick="loadTicketInChat('${ticket.id}', '${userId}')">Открыть в чате</button>
                    </div>
                </div>
            `).join('')}
        </div>
    `;
}

// View ticket details
async function viewTicketDetails(ticketId) {
    try {
        const response = await fetch(`/api/support/ticket/${ticketId}`);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        const ticket = await response.json();

        // Create modal for ticket details
        showTicketModal(ticket);

    } catch (error) {
        console.error('Error fetching ticket details:', error);
        alert(`Ошибка при загрузке деталей тикета: ${error.message}`);
    }
}

// Show ticket modal
function showTicketModal(ticket) {
    const statusColors = {
        'OPEN': '#3498db',
        'IN_PROGRESS': '#f39c12',
        'WAITING_USER': '#9b59b6',
        'RESOLVED': '#2ecc71',
        'CLOSED': '#95a5a6'
    };

    const modalHtml = `
        <div class="modal-overlay" id="ticketModalOverlay">
            <div class="modal-dialog">
                <div class="modal-header">
                    <h2>Тикет #${ticket.id}</h2>
                    <button class="close-button" onclick="closeTicketModal()">&times;</button>
                </div>
                <div class="modal-body">
                    <div class="ticket-detail">
                        <div class="ticket-detail-header">
                            <h3>${escapeHtml(ticket.subject)}</h3>
                            <span class="ticket-status" style="background-color: ${statusColors[ticket.status]}">${ticket.status}</span>
                        </div>
                        <p class="ticket-detail-description">${escapeHtml(ticket.description)}</p>
                        <div class="ticket-detail-meta">
                            <p><strong>Приоритет:</strong> ${ticket.priority}</p>
                            <p><strong>Создан:</strong> ${formatDate(ticket.createdAt)}</p>
                            <p><strong>Обновлен:</strong> ${formatDate(ticket.updatedAt)}</p>
                            ${ticket.assignedTo ? `<p><strong>Назначен:</strong> ${ticket.assignedTo}</p>` : ''}
                        </div>
                        ${ticket.messages && ticket.messages.length > 0 ? `
                            <div class="ticket-messages">
                                <h4>История сообщений:</h4>
                                ${ticket.messages.map(msg => `
                                    <div class="ticket-message ${msg.isFromSupport ? 'support-msg' : 'user-msg'}">
                                        <div class="msg-author">${msg.isFromSupport ? '🎧 Support' : '👤 User'}: ${escapeHtml(msg.author)}</div>
                                        <div class="msg-content">${escapeHtml(msg.content)}</div>
                                        <div class="msg-timestamp">${formatDate(msg.timestamp)}</div>
                                    </div>
                                `).join('')}
                            </div>
                        ` : ''}
                    </div>
                </div>
            </div>
        </div>
    `;

    document.body.insertAdjacentHTML('beforeend', modalHtml);
}

// Close ticket modal
function closeTicketModal() {
    const modal = document.getElementById('ticketModalOverlay');
    if (modal) {
        modal.remove();
    }
}

// Load ticket in chat
function loadTicketInChat(ticketId, userId) {
    document.getElementById('supportTicketId').value = ticketId;
    document.getElementById('supportUserId').value = userId;

    // Scroll to chat section
    const chatSection = document.querySelector('.support-section:last-child');
    if (chatSection) {
        chatSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    // Focus on message input
    setTimeout(() => {
        document.getElementById('supportMessage').focus();
    }, 500);
}

// Handle create ticket
async function handleCreateTicket(e) {
    e.preventDefault();

    const userId = document.getElementById('ticketUserId').value.trim();
    const subject = document.getElementById('ticketSubject').value.trim();
    const description = document.getElementById('ticketDescription').value.trim();
    const priority = document.getElementById('ticketPriority').value;

    if (!userId || !subject || !description) {
        alert('Пожалуйста, заполните все обязательные поля');
        return;
    }

    try {
        const response = await fetch('/api/support/tickets', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                userId,
                subject,
                description,
                priority
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const ticket = await response.json();

        alert(`Тикет успешно создан!\nID: ${ticket.id}\nСтатус: ${ticket.status}`);

        // Reset form
        resetTicketForm();

        // Reload tickets if user is loaded
        if (document.getElementById('userId').value === userId) {
            handleUserSearch({ preventDefault: () => {} });
        }

    } catch (error) {
        console.error('Error creating ticket:', error);
        alert(`Ошибка при создании тикета: ${error.message}`);
    }
}

// Reset ticket form
function resetTicketForm() {
    document.getElementById('ticketSubject').value = '';
    document.getElementById('ticketDescription').value = '';
    document.getElementById('ticketPriority').value = 'MEDIUM';
}

// Handle support chat
async function handleSupportChat(e) {
    e.preventDefault();

    const message = document.getElementById('supportMessage').value.trim();
    const userId = document.getElementById('supportUserId').value.trim() || null;
    const ticketId = document.getElementById('supportTicketId').value.trim() || null;

    if (!message) return;

    // Display user message
    addSupportMessage('user', message);

    // Clear input
    document.getElementById('supportMessage').value = '';

    // Show loading indicator
    const loadingDiv = addSupportMessage('assistant', '⏳ Поиск информации в документации и истории тикетов...');

    try {
        const response = await fetch('/api/support/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                message,
                userId,
                ticketId,
                sessionId: currentSupportSessionId,
                useRAG: true,
                ragTopK: 5,
                ragMinSimilarity: 0.7
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();

        // Save session ID
        currentSupportSessionId = data.sessionId;

        // Remove loading message
        loadingDiv.remove();

        // Display assistant response
        addSupportMessage('assistant', data.response);

        // Display context info (RAG and MCP)
        displaySupportContextInfo(data);

    } catch (error) {
        console.error('Error sending support message:', error);
        loadingDiv.remove();
        addSupportMessage('error', `Ошибка: ${error.message}`);
    }
}

// Add support message to chat
function addSupportMessage(role, content) {
    const messagesDiv = document.getElementById('supportMessages');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}`;

    const roleLabel = {
        'user': '👤 Вы',
        'assistant': '🎧 Support Assistant',
        'error': '❌ Ошибка'
    }[role] || 'Unknown';

    messageDiv.innerHTML = `
        <div class="message-content">
            <strong>${roleLabel}:</strong> ${formatMessageText(content)}
        </div>
    `;

    messagesDiv.appendChild(messageDiv);

    // Scroll to bottom
    messagesDiv.scrollTop = messagesDiv.scrollHeight;

    return messageDiv;
}

// Display support context info (RAG and MCP)
function displaySupportContextInfo(data) {
    const infoDiv = document.getElementById('supportChatInfo');

    if (!data.ragUsed && !data.userContext && !data.ticketContext) {
        infoDiv.style.display = 'none';
        return;
    }

    infoDiv.style.display = 'block';

    let infoHtml = '<div class="context-info">';

    // RAG info
    if (data.ragUsed) {
        infoHtml += `
            <div class="context-section">
                <h4>📚 Использована документация:</h4>
                <p>Найдено релевантных разделов: <strong>${data.ragChunksFound}</strong></p>
                <p>Источники: ${data.ragSources.map(s => `<code>${s}</code>`).join(', ')}</p>
            </div>
        `;
    }

    // User context
    if (data.userContext) {
        infoHtml += `
            <div class="context-section">
                <h4>👤 Контекст пользователя:</h4>
                <p><strong>${data.userContext.name}</strong> (${data.userContext.email})</p>
                <p>Роль: <strong>${data.userContext.role.toUpperCase()}</strong></p>
            </div>
        `;
    }

    // Ticket context
    if (data.ticketContext) {
        infoHtml += `
            <div class="context-section">
                <h4>🎫 Контекст тикета:</h4>
                <p><strong>#${data.ticketContext.ticketId}</strong>: ${escapeHtml(data.ticketContext.subject)}</p>
                <p>Статус: <strong>${data.ticketContext.status}</strong></p>
            </div>
        `;
    }

    infoHtml += '</div>';
    infoDiv.innerHTML = infoHtml;
}

// Utility functions
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatMessageText(text) {
    // Сначала экранируем HTML для безопасности
    let formatted = escapeHtml(text);

    // Заменяем переносы строк на <br>
    formatted = formatted.replace(/\n/g, '<br>');

    // Базовое форматирование markdown-подобного текста
    // Жирный текст: **текст** или __текст__
    formatted = formatted.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    formatted = formatted.replace(/__(.+?)__/g, '<strong>$1</strong>');

    // Курсив: *текст* или _текст_
    formatted = formatted.replace(/\*(.+?)\*/g, '<em>$1</em>');
    formatted = formatted.replace(/_(.+?)_/g, '<em>$1</em>');

    // Списки с дефисами (- пункт)
    formatted = formatted.replace(/^- (.+)$/gm, '• $1');

    return formatted;
}

function formatDate(dateString) {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleString('ru-RU', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

// Make functions available globally
window.viewTicketDetails = viewTicketDetails;
window.closeTicketModal = closeTicketModal;
window.loadTicketInChat = loadTicketInChat;
