# Обновление веб-интерфейса: поддержка Reranking

## Внесенные изменения

### 1. HTML (index.html)
Добавлен новый чекбокс для включения reranking:

```html
<div class="rag-toggle">
    <label for="rerankingCheckbox" class="rag-label">
        <input type="checkbox" id="rerankingCheckbox">
        <span class="rag-text">🎯 Включить Reranking</span>
        <span class="rag-hint">(Улучшает качество RAG)</span>
    </label>
</div>
```

**Расположение:** Под чекбоксом "Использовать RAG"

### 2. JavaScript (app.js)

#### 2.1. Инициализация элемента
```javascript
this.rerankingCheckbox = document.getElementById('rerankingCheckbox');
```

#### 2.2. Зависимость от RAG
Добавлена логика, чтобы reranking был доступен только при включенном RAG:

```javascript
// Enable/disable reranking checkbox based on RAG checkbox
this.ragCheckbox.addEventListener('change', () => {
    this.rerankingCheckbox.disabled = !this.ragCheckbox.checked;
    if (!this.ragCheckbox.checked) {
        this.rerankingCheckbox.checked = false;
    }
});

// Initially disable reranking checkbox
this.rerankingCheckbox.disabled = true;
```

**Поведение:**
- По умолчанию чекбокс reranking **отключен** (disabled)
- Становится активным только когда включен RAG
- Автоматически снимается галочка, если выключается RAG

#### 2.3. Отправка параметра в API
```javascript
// Send RAG setting
if (this.ragCheckbox && this.ragCheckbox.checked) {
    requestBody.useRAG = true;
    requestBody.ragTopK = 3;
    requestBody.ragMinSimilarity = 0.5;

    // Send Reranking setting (only if RAG is enabled)
    if (this.rerankingCheckbox && this.rerankingCheckbox.checked) {
        requestBody.useReranking = true;
    }
}
```

#### 2.4. Отображение статистики
Добавлены новые индикаторы в статистику ответа:

```javascript
// Add RAG indicator if RAG was used
if (stats.ragUsed) {
    statsHtml += `
        <div class="stats-item rag">
            <span class="stats-label">🔍 RAG:</span>
            <span class="stats-value">${stats.ragChunksFound} чанков</span>
        </div>
    `;
}

// Add Reranking indicator if reranking was used
if (stats.rerankingUsed) {
    statsHtml += `
        <div class="stats-item reranking">
            <span class="stats-label">🎯 Reranking:</span>
            <span class="stats-value">${(stats.rerankingTimeMs / 1000).toFixed(2)}с</span>
        </div>
    `;
}
```

## Как использовать

### Шаг 1: Откройте веб-интерфейс
```
http://localhost:8080/static/index.html
```

### Шаг 2: Настройте параметры
1. ✅ **Включите RAG** - поставьте галочку на "🔍 Использовать RAG"
2. ✅ **Включите Reranking** - поставьте галочку на "🎯 Включить Reranking" (станет доступным после включения RAG)
3. Выберите модель (рекомендуется Sonnet 4 для лучшего качества)

### Шаг 3: Отправьте сообщение
Введите вопрос и отправьте. Например:
```
Расскажи про современную эпоху
```

### Шаг 4: Просмотрите статистику
В ответе Assistant вы увидите:
- **Токены** (вход/выход/всего)
- **Время** ответа
- **🔍 RAG:** количество использованных чанков
- **🎯 Reranking:** время, потраченное на reranking
- **🗜️ История сжата** (если была сжата)

## Пример использования

### Без Reranking
```
Параметры:
- ✅ RAG включен
- ❌ Reranking выключен

Результат:
- Токены: 3535
- Время: 10.44с
- 🔍 RAG: 3 чанков
```

### С Reranking
```
Параметры:
- ✅ RAG включен
- ✅ Reranking включен

Результат:
- Токены: 3645
- Время: 10.18с
- 🔍 RAG: 3 чанков
- 🎯 Reranking: 2.58с
```

## Визуальные индикаторы

### Чекбоксы
```
□ 🔍 Использовать RAG (Поиск в документах)
□ 🎯 Включить Reranking (Улучшает качество RAG) [disabled]
```

**После включения RAG:**
```
☑ 🔍 Использовать RAG (Поиск в документах)
□ 🎯 Включить Reranking (Улучшает качество RAG) [enabled]
```

**С обоими включенными:**
```
☑ 🔍 Использовать RAG (Поиск в документах)
☑ 🎯 Включить Reranking (Улучшает качество RAG)
```

### Статистика в ответе
```
┌─────────────────────────────────────┐
│ Вход: 2890  Выход: 755  Всего: 3645 │
│ Время: 10.18с                       │
│ 🔍 RAG: 3 чанков                    │
│ 🎯 Reranking: 2.58с                 │
└─────────────────────────────────────┘
```

## Технические детали

### API Request
```json
{
  "message": "Расскажи про современную эпоху",
  "useRAG": true,
  "ragTopK": 3,
  "ragMinSimilarity": 0.5,
  "useReranking": true
}
```

### API Response
```json
{
  "response": "...",
  "sessionId": "uuid",
  "model": "claude-sonnet-4-20250514",
  "inputTokens": 2890,
  "outputTokens": 755,
  "totalTokens": 3645,
  "responseTimeMs": 10179,
  "ragUsed": true,
  "ragChunksFound": 3,
  "ragSources": ["input_text_embeddings_1764009635305.json"],
  "rerankingUsed": true,
  "rerankingTimeMs": 2580
}
```

## Преимущества UI изменений

1. **Интуитивность** - Reranking доступен только когда имеет смысл (при включенном RAG)
2. **Прозрачность** - Показывается время reranking и количество найденных документов
3. **Удобство** - Визуальные индикаторы 🔍 и 🎯 помогают быстро понять, что активно
4. **Информативность** - Пользователь видит реальное влияние reranking на производительность

## Файлы изменены

1. `/server/src/main/resources/static/index.html` - добавлен чекбокс
2. `/server/src/main/resources/static/js/app.js` - логика и отображение

## Совместимость

- ✅ Работает со всеми существующими функциями
- ✅ Обратная совместимость (reranking по умолчанию выключен)
- ✅ Не влияет на работу без RAG
- ✅ Корректно работает с историей диалогов
