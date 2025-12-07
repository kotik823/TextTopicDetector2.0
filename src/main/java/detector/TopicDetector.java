package detector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;


/**
 * Класс, отвечающий за определение наиболее вероятной тематики текста
 * на основе подсчитанных частот совпадений слов по каждой теме.
 * <p>
 * Считает темой текста ту, которая набрала наибольшее количество совпадений
 * со словами из соответствующего словаря.
 * </p>
 *
 * @version 1.1 (Добавлено русскоязычное логирование)
 */
public class TopicDetector {

    /** Логгер для класса TopicDetector. */
    private static final Logger logger = LogManager.getLogger(TopicDetector.class);

    /** Менеджер словарей, предоставляющий доступ к темам (хотя напрямую не используется в методах). */
    private final DictionaryManager dm;

    /**
     * Создает новый экземпляр TopicDetector.
     *
     * @param dm Менеджер словарей, который содержит все доступные темы.
     */
    public TopicDetector(DictionaryManager dm) {
        this.dm = dm;
        logger.debug("TopicDetector инициализирован.");
    }


    /**
     * Определяет ведущую тему на основе карты частот.
     *
     * @param counts Карта, где ключ — название темы, а значение — общее количество
     * совпадений слов из текста для этой темы.
     * @return Название темы с максимальным количеством совпадений. Если ни в одной
     * теме нет совпадений (счетчик = 0), возвращается строка "Неопределено".
     */
    public String detectTopic(Map<String, Integer> counts) {
        logger.info("Начало определения ведущей темы. Входящие счетчики: {}", counts);

        String resultTopic = counts.entrySet().stream()
                // Находим запись с максимальным значением (количеством совпадений)
                .max(Map.Entry.comparingByValue())
                // Фильтруем: учитываем только те темы, где количество совпадений > 0
                .filter(e -> e.getValue() > 0)
                // Получаем ключ (название темы)
                .map(Map.Entry::getKey)
                // Если совпадений нет (или карта пуста), возвращаем "Неопределено"
                .orElse("Неопределено");

        // Определяем максимальное значение для логирования
        int maxCount = counts.values().stream().mapToInt(v -> v).max().orElse(0);

        if (resultTopic.equals("Неопределено")) {
            logger.warn("Определение темы завершено: Ведущая тема не определена (все счетчики равны 0).");
        } else {
            logger.info("Определение темы завершено: Найдена ведущая тема '{}' с максимальным количеством совпадений: {}.",
                    resultTopic, maxCount);
        }

        return resultTopic;
    }
}