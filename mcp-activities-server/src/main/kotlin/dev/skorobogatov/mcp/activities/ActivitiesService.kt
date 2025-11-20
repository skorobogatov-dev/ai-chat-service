package dev.skorobogatov.mcp.activities

import org.slf4j.LoggerFactory

/**
 * Сервис для рекомендаций активностей на основе погоды
 */
class ActivitiesService {
    private val logger = LoggerFactory.getLogger(ActivitiesService::class.java)

    /**
     * Получить рекомендации активностей на основе погодных условий
     */
    fun getRecommendations(
        temperature: Double,
        weatherCode: Int,
        precipitation: Double = 0.0,
        windSpeed: Double = 0.0,
        humidity: Int = 50
    ): List<ActivityRecommendation> {
        val condition = getWeatherConditionFromCode(weatherCode)
        val context = WeatherContext(temperature, condition, precipitation, windSpeed, humidity)

        logger.info("Getting activity recommendations for: temp=${temperature}°C, condition=$condition, precipitation=${precipitation}mm")

        val recommendations = mutableListOf<ActivityRecommendation>()

        // Добавляем рекомендации в зависимости от погоды
        recommendations.addAll(getOutdoorActivities(context))
        recommendations.addAll(getIndoorActivities(context))
        recommendations.addAll(getSportActivities(context))
        recommendations.addAll(getCulturalActivities(context))
        recommendations.addAll(getRelaxationActivities(context))

        // Сортируем по подходящести (suitability) в порядке убывания
        return recommendations.sortedByDescending { it.suitability }.take(10)
    }

    /**
     * Активности на улице
     */
    private fun getOutdoorActivities(context: WeatherContext): List<ActivityRecommendation> {
        val activities = mutableListOf<ActivityRecommendation>()

        when {
            context.temperature > 20 && context.condition == WeatherCondition.CLEAR -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Прогулка в парке",
                        type = ActivityType.OUTDOOR.name,
                        description = "Насладитесь солнечной погодой и свежим воздухом",
                        reason = "Идеальная погода для прогулок",
                        suitability = 10
                    )
                )
                activities.add(
                    ActivityRecommendation(
                        name = "Пикник",
                        type = ActivityType.OUTDOOR.name,
                        description = "Отличное время для пикника с друзьями или семьей",
                        reason = "Тепло и солнечно",
                        suitability = 9
                    )
                )
            }
            context.temperature in 10.0..20.0 && context.precipitation < 1.0 -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Велопрогулка",
                        type = ActivityType.OUTDOOR.name,
                        description = "Комфортная температура для велосипедной прогулки",
                        reason = "Умеренная температура, без дождя",
                        suitability = 8
                    )
                )
            }
            context.temperature < 0 && context.condition == WeatherCondition.SNOW -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Катание на лыжах",
                        type = ActivityType.OUTDOOR.name,
                        description = "Прекрасные условия для зимних видов спорта",
                        reason = "Снег и подходящая температура",
                        suitability = 9
                    )
                )
                activities.add(
                    ActivityRecommendation(
                        name = "Лепка снеговика",
                        type = ActivityType.OUTDOOR.name,
                        description = "Отличное семейное развлечение",
                        reason = "Свежий снег",
                        suitability = 7
                    )
                )
            }
            context.temperature in -5.0..5.0 -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Прогулка по городу",
                        type = ActivityType.OUTDOOR.name,
                        description = "Можно одеться теплее и прогуляться",
                        reason = "Прохладно, но приемлемо для прогулки",
                        suitability = 6
                    )
                )
            }
        }

        // Если дождь - снижаем приоритет уличных активностей
        if (context.precipitation > 5.0) {
            activities.forEach { it.suitability - 5 }
        }

        return activities
    }

    /**
     * Активности в помещении
     */
    private fun getIndoorActivities(context: WeatherContext): List<ActivityRecommendation> {
        val activities = mutableListOf<ActivityRecommendation>()

        when {
            context.condition == WeatherCondition.RAIN || context.precipitation > 5.0 -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Поход в кино",
                        type = ActivityType.INDOOR.name,
                        description = "Идеальное время для просмотра фильма",
                        reason = "Дождь на улице",
                        suitability = 9
                    )
                )
                activities.add(
                    ActivityRecommendation(
                        name = "Посещение торгового центра",
                        type = ActivityType.INDOOR.name,
                        description = "Шопинг и развлечения под крышей",
                        reason = "Плохая погода на улице",
                        suitability = 8
                    )
                )
            }
            context.temperature < -10 || context.temperature > 35 -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Посещение музея",
                        type = ActivityType.INDOOR.name,
                        description = "Познавательный досуг в комфортных условиях",
                        reason = "Экстремальная температура снаружи",
                        suitability = 8
                    )
                )
            }
            else -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Настольные игры дома",
                        type = ActivityType.INDOOR.name,
                        description = "Уютный вечер с семьей или друзьями",
                        reason = "Всегда хороший вариант",
                        suitability = 7
                    )
                )
            }
        }

        return activities
    }

    /**
     * Спортивные активности
     */
    private fun getSportActivities(context: WeatherContext): List<ActivityRecommendation> {
        val activities = mutableListOf<ActivityRecommendation>()

        when {
            context.temperature in 15.0..25.0 && context.precipitation < 1.0 -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Бег в парке",
                        type = ActivityType.SPORT.name,
                        description = "Идеальные условия для пробежки",
                        reason = "Комфортная температура, нет осадков",
                        suitability = 9
                    )
                )
                activities.add(
                    ActivityRecommendation(
                        name = "Игра в теннис",
                        type = ActivityType.SPORT.name,
                        description = "Отличная погода для игр на открытом воздухе",
                        reason = "Сухо и комфортно",
                        suitability = 8
                    )
                )
            }
            context.temperature < 0 -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Катание на коньках",
                        type = ActivityType.SPORT.name,
                        description = "Зимний спорт на свежем воздухе",
                        reason = "Холодная погода",
                        suitability = 8
                    )
                )
            }
            else -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Тренажерный зал",
                        type = ActivityType.SPORT.name,
                        description = "Спорт в комфортных условиях",
                        reason = "Любая погода",
                        suitability = 7
                    )
                )
                activities.add(
                    ActivityRecommendation(
                        name = "Плавание в бассейне",
                        type = ActivityType.SPORT.name,
                        description = "Отличная тренировка всего тела",
                        reason = "Не зависит от погоды",
                        suitability = 7
                    )
                )
            }
        }

        return activities
    }

    /**
     * Культурные активности
     */
    private fun getCulturalActivities(context: WeatherContext): List<ActivityRecommendation> {
        val activities = mutableListOf<ActivityRecommendation>()

        // Культурные мероприятия хороши при любой погоде
        activities.add(
            ActivityRecommendation(
                name = "Посещение театра",
                type = ActivityType.CULTURAL.name,
                description = "Спектакль или концерт",
                reason = "Культурное обогащение",
                suitability = 8
            )
        )

        activities.add(
            ActivityRecommendation(
                name = "Выставка искусства",
                type = ActivityType.CULTURAL.name,
                description = "Посещение галереи или выставки",
                reason = "Познавательно и интересно",
                suitability = 7
            )
        )

        // В плохую погоду культурные активности более приоритетны
        if (context.precipitation > 5.0 || context.temperature < -10 || context.temperature > 35) {
            activities.forEach { it.suitability + 2 }
        }

        return activities
    }

    /**
     * Активности для отдыха и релаксации
     */
    private fun getRelaxationActivities(context: WeatherContext): List<ActivityRecommendation> {
        val activities = mutableListOf<ActivityRecommendation>()

        when {
            context.condition == WeatherCondition.RAIN || context.condition == WeatherCondition.SNOW -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Чтение книги дома",
                        type = ActivityType.RELAXATION.name,
                        description = "Уютный вечер с хорошей книгой",
                        reason = "Погода располагает к домашнему уюту",
                        suitability = 9
                    )
                )
                activities.add(
                    ActivityRecommendation(
                        name = "Просмотр фильмов/сериалов",
                        type = ActivityType.RELAXATION.name,
                        description = "Марафон любимых фильмов",
                        reason = "Не хочется выходить на улицу",
                        suitability = 8
                    )
                )
            }
            context.temperature > 25 -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Посещение SPA",
                        type = ActivityType.RELAXATION.name,
                        description = "Релаксация и охлаждение",
                        reason = "Жаркая погода",
                        suitability = 8
                    )
                )
            }
            else -> {
                activities.add(
                    ActivityRecommendation(
                        name = "Йога или медитация",
                        type = ActivityType.RELAXATION.name,
                        description = "Практика осознанности",
                        reason = "Полезно при любой погоде",
                        suitability = 7
                    )
                )
                activities.add(
                    ActivityRecommendation(
                        name = "Кафе с друзьями",
                        type = ActivityType.RELAXATION.name,
                        description = "Приятное общение за чашкой кофе",
                        reason = "Социальная активность",
                        suitability = 8
                    )
                )
            }
        }

        return activities
    }
}
