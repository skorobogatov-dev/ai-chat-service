// Voice Recorder Class
class VoiceRecorder {
    constructor(chatApp) {
        this.chatApp = chatApp;
        this.mediaRecorder = null;
        this.audioChunks = [];
        this.stream = null;
        this.isRecording = false;
        this.startTime = null;
        this.timerInterval = null;

        this.voiceButton = document.getElementById('voiceButton');
        this.recordingIndicator = document.getElementById('recordingIndicator');
        this.recordingTimer = document.getElementById('recordingTimer');
        this.messageInput = document.getElementById('messageInput');

        if (!this.voiceButton || !this.recordingIndicator || !this.recordingTimer || !this.messageInput) {
            console.error('Voice recording elements not found in DOM');
            return;
        }

        this.init();
    }

    init() {
        // Check browser support
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            console.error('MediaDevices API not supported');
            this.voiceButton.disabled = true;
            this.voiceButton.title = 'Ваш браузер не поддерживает запись аудио';
            return;
        }

        // Mouse events
        this.voiceButton.addEventListener('mousedown', (e) => {
            e.preventDefault();
            this.startRecording();
        });

        this.voiceButton.addEventListener('mouseup', (e) => {
            e.preventDefault();
            this.stopRecording();
        });

        // Глобальный обработчик mouseup - останавливает запись даже если отпустили вне кнопки
        document.addEventListener('mouseup', () => {
            if (this.isRecording) {
                this.stopRecording();
            }
        });

        // Touch events for mobile
        this.voiceButton.addEventListener('touchstart', (e) => {
            e.preventDefault();
            this.startRecording();
        });

        this.voiceButton.addEventListener('touchend', (e) => {
            e.preventDefault();
            this.stopRecording();
        });

        console.log('Voice recorder initialized');
    }

    async startRecording() {
        if (this.isRecording) {
            console.log('Already recording, ignoring startRecording call');
            return;
        }

        try {
            console.log('=== Starting voice recording ===');
            console.log('Requesting microphone access...');

            // Request microphone access
            this.stream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    echoCancellation: true,
                    noiseSuppression: true,
                    autoGainControl: true
                }
            });

            console.log('Microphone access granted');
            console.log('Stream tracks:', this.stream.getTracks().map(t => ({
                kind: t.kind,
                enabled: t.enabled,
                readyState: t.readyState
            })));

            // Determine supported MIME type
            let mimeType = 'audio/webm';
            if (!MediaRecorder.isTypeSupported(mimeType)) {
                mimeType = 'audio/ogg';
                if (!MediaRecorder.isTypeSupported(mimeType)) {
                    mimeType = 'audio/wav';
                }
            }

            console.log(`Using MIME type: ${mimeType}`);

            // Create MediaRecorder
            this.mediaRecorder = new MediaRecorder(this.stream, {
                mimeType: mimeType
            });

            this.audioChunks = [];

            this.mediaRecorder.ondataavailable = (event) => {
                console.log('Data available:', event.data.size, 'bytes');
                if (event.data.size > 0) {
                    this.audioChunks.push(event.data);
                    console.log('Total chunks:', this.audioChunks.length);
                }
            };

            this.mediaRecorder.onstop = () => {
                // Небольшая задержка, чтобы все данные успели записаться
                setTimeout(() => {
                    this.sendAudio();
                }, 100);
            };

            this.mediaRecorder.onerror = (event) => {
                console.error('MediaRecorder error:', event.error);
                this.cleanup();
                this.chatApp.addMessage('Ошибка записи аудио: ' + event.error.message, 'error');
            };

            // Запись с timeslice для периодического сохранения данных
            this.mediaRecorder.start(100); // Сохранять данные каждые 100мс
            this.isRecording = true;
            this.startTime = Date.now();

            // Visual indication - заменяем поле ввода на индикатор
            this.voiceButton.classList.add('recording');
            this.messageInput.style.display = 'none';
            this.recordingIndicator.style.display = 'flex';

            // Start timer
            this.startTimer();

            console.log('Recording started at', new Date().toISOString());

        } catch (error) {
            console.error('Microphone access error:', error);
            let errorMessage = 'Не удалось получить доступ к микрофону.';

            if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
                errorMessage += ' Разрешите доступ к микрофону в настройках браузера.';
            } else if (error.name === 'NotFoundError') {
                errorMessage += ' Микрофон не найден.';
            }

            alert(errorMessage);
            this.cleanup();
        }
    }

    stopRecording() {
        if (!this.isRecording) return;

        const duration = Date.now() - this.startTime;
        console.log('Stopping recording at', new Date().toISOString());
        console.log('Recording duration:', duration, 'ms');

        // Проверка минимальной длительности
        if (duration < 300) {
            console.warn('Recording too short:', duration, 'ms');
            this.cleanup();
            this.chatApp.addMessage('Запись слишком короткая. Удерживайте кнопку минимум 1 секунду и говорите четко.', 'error');
            return;
        }

        this.isRecording = false;

        if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
            this.mediaRecorder.stop();
        }

        // Stop stream
        if (this.stream) {
            this.stream.getTracks().forEach(track => track.stop());
        }

        // Visual indication - возвращаем поле ввода
        this.voiceButton.classList.remove('recording');
        this.recordingIndicator.style.display = 'none';
        this.messageInput.style.display = 'block';

        // Stop timer
        this.stopTimer();
    }

    cleanup() {
        this.isRecording = false;

        if (this.stream) {
            this.stream.getTracks().forEach(track => track.stop());
            this.stream = null;
        }

        this.voiceButton.classList.remove('recording');
        this.recordingIndicator.style.display = 'none';
        this.messageInput.style.display = 'block';
        this.stopTimer();
    }

    startTimer() {
        this.timerInterval = setInterval(() => {
            const elapsed = Math.floor((Date.now() - this.startTime) / 1000);
            const minutes = Math.floor(elapsed / 60);
            const seconds = elapsed % 60;
            this.recordingTimer.textContent = `${minutes}:${seconds.toString().padStart(2, '0')}`;
        }, 100);
    }

    stopTimer() {
        if (this.timerInterval) {
            clearInterval(this.timerInterval);
            this.timerInterval = null;
        }
        this.recordingTimer.textContent = '0:00';
    }

    async sendAudio() {
        if (this.audioChunks.length === 0) {
            console.warn('No audio data recorded');
            this.chatApp.addMessage('Не удалось записать аудио. Попробуйте еще раз.', 'error');
            return;
        }

        try {
            console.log(`Creating audio blob from ${this.audioChunks.length} chunks`);

            // Create Blob from recorded chunks
            const audioBlob = new Blob(this.audioChunks, { type: this.mediaRecorder.mimeType });

            console.log(`Audio blob created: ${audioBlob.size} bytes, type: ${audioBlob.type}`);

            // Create FormData for sending
            const formData = new FormData();
            const extension = this.mediaRecorder.mimeType.includes('webm') ? 'webm' : 'wav';
            formData.append('audio', audioBlob, `voice.${extension}`);
            formData.append('provider', this.chatApp.currentProvider || 'ollama');

            if (this.chatApp.modelSelect) {
                formData.append('model', this.chatApp.modelSelect.value);
            }

            if (this.chatApp.sessionId) {
                formData.append('sessionId', this.chatApp.sessionId);
            }

            console.log('Sending audio to server...');

            // Show loading indicator
            this.chatApp.showLoadingIndicator();

            const response = await fetch('/api/voice', {
                method: 'POST',
                body: formData
            });

            this.chatApp.hideLoadingIndicator();

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.error || `HTTP error ${response.status}`);
            }

            const data = await response.json();

            console.log('Voice response received:', data);

            // Add transcribed text as user message
            this.chatApp.addMessage(
                `🎤 ${data.transcribedText}`,
                'user'
            );

            // Add LLM response
            this.chatApp.addMessage(
                data.response,
                'assistant',
                data.model,
                {
                    inputTokens: data.inputTokens,
                    outputTokens: data.outputTokens,
                    totalTokens: data.totalTokens,
                    responseTimeMs: data.llmResponseTimeMs
                }
            );

            // Update sessionId
            if (data.sessionId) {
                this.chatApp.sessionId = data.sessionId;
                this.chatApp.updateHistoryButton();
            }

            // Reload conversations list
            await this.chatApp.loadConversations();

        } catch (error) {
            console.error('Voice send error:', error);
            this.chatApp.hideLoadingIndicator();
            this.chatApp.addMessage(
                `Ошибка распознавания речи: ${error.message}`,
                'error'
            );
        } finally {
            this.cleanup();
        }
    }
}

// Export for use in app.js
window.VoiceRecorder = VoiceRecorder;
