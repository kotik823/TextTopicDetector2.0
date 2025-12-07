package detector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Менеджер словарей, отвечающий за загрузку тематических словарей и предоставление к ним доступа.
 *
 * <p>Класс загружает предопределенные словари из ресурсов при инициализации и поддерживает
 * динамическую загрузку пользовательских словарей с диска. Обеспечивает, что каждое
 * слово в рамках одного словаря уникально, даже если в исходном файле есть дубликаты.</p>
 *
 * @version 2.2 (Добавлено русскоязычное логирование)
 */
public class DictionaryManager {

    /** Логгер для класса DictionaryManager. */
    private static final Logger logger = LogManager.getLogger(DictionaryManager.class);

    /**
     * Карта, хранящая все загруженные словари. Ключ: Название темы (String),
     * Значение: Список уникальных слов (List<String>) в нижнем регистре.
     */
    private final Map<String, List<String>> dictionaries = new HashMap<>();

    /**
     * Конструктор, который запускает загрузку всех предопределенных словарей из ресурсов.
     *
     * @throws IOException Если не удалось найти или прочитать один из файлов словарей
     * в ресурсах приложения.
     */
    public DictionaryManager() throws IOException {
        loadDictionariesFromResources();
    }

    // --- Методы загрузки словарей ---

    /**
     * Загружает все предопределенные словари из папки resources/dictionaries/.
     */
    private void loadDictionariesFromResources() throws IOException {
        logger.info("Начало загрузки предопределенных словарей из ресурсов."); // LOG

        String[] dictNames = {
                "medical.txt", "history.txt", "programming.txt",
                "networks.txt", "cryptography.txt", "finance.txt"
        };

        for (String name : dictNames) {
            String path = "dictionaries/" + name;

            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            if (is == null) {
                logger.error("Предопределенный файл словаря не найден: {}", path); // LOG
                continue;
            }

            try (is) {
                byte[] bytes = is.readAllBytes();
                List<String> words = readLinesFromBytesWithEncodingFallback(bytes);

                dictionaries.put(name.replace(".txt", ""), words);
                logger.info("Успешно загружен предопределенный словарь '{}'. Уникальных слов: {}.", // LOG
                        name.replace(".txt", ""), words.size());
            } catch (IOException e) {
                logger.error("Ошибка ввода/вывода при чтении файла ресурсов '{}': {}", path, e.getMessage()); // LOG
                throw e;
            }
        }
        logger.info("Завершена загрузка всех предопределенных словарей."); // LOG
    }

    /**
     * Загружает словарь из указанного пользователем файла на диске и добавляет его
     * в список доступных словарей.
     *
     * @param file Файл словаря, предоставленный пользователем.
     * @return Имя темы, извлеченное из имени файла.
     * @throws IOException Если произошла ошибка при чтении файла.
     */
    public String loadDictionaryFromFile(File file) throws IOException {
        logger.info("Начало загрузки пользовательского словаря из файла: {}", file.getAbsolutePath()); // LOG

        // 1. Извлечение имени темы
        String fileName = file.getName();
        String topicName;
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0) {
            topicName = fileName.substring(0, dotIndex).toLowerCase();
        } else {
            topicName = fileName.toLowerCase();
        }

        // Проверка на дублирование темы
        if (dictionaries.containsKey(topicName)) {
            logger.warn("Перезапись существующей темы: '{}'. Старый словарь будет заменен.", topicName); // LOG
        }

        // 2. Считывание байтов и обработка кодировки
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            List<String> words = readLinesFromBytesWithEncodingFallback(bytes);

            // 3. Сохранение в хранилище
            if (!words.isEmpty()) {
                dictionaries.put(topicName, words);
                logger.info("Успешно загружен пользовательский словарь '{}'. Уникальных слов: {}.", // LOG
                        topicName, words.size());
            } else {
                logger.warn("Пользовательский словарь '{}' ({}) пуст после обработки.", file.getName(), topicName); // LOG
            }

            return topicName; // Возвращаем имя для обновления GUI
        } catch (IOException e) {
            logger.error("Ошибка при загрузке пользовательского файла словаря '{}': {}", file.getName(), e.getMessage(), e); // LOG
            throw e;
        }
    }

    /**
     * Считывает содержимое массива байтов, используя UTF-8, а при обнаружении
     * "кракозябр" (испорченной кириллицы) переключается на кодировку windows-1251.
     */
    private List<String> readLinesFromBytesWithEncodingFallback(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);

        // Проверяем, если UTF-8 некорректно декодировал кириллицу
        if (looksLikeGarbledRussian(text)) {
            logger.warn("Декодирование UTF-8, возможно, не удалось (обнаружены 'кракозябры'). Выполняется откат к windows-1251."); // LOG
            // Если UTF-8 не сработал, пробуем CP1251
            text = new String(bytes, Charset.forName("windows-1251"));
        }

        return toUniqueList(text);
    }

    // --- Вспомогательные методы ---

    /**
     * Эвристическая проверка, указывает ли строка на некорректно декодированный русский текст.
     */
    private boolean looksLikeGarbledRussian(String s) {
        // ... (Логика без изменений)
        if (s == null || s.isEmpty()) return false;
        int total = s.length();
        int letters = 0;
        int repl = 0;

        for (char c : s.toCharArray()) {
            if (c == '\uFFFD') repl++;
            if (Character.isLetter(c)) letters++;
        }

        return repl > 0 || ((double) letters / total < 0.3);
    }

    /**
     * Преобразует строку текста в список уникальных строк.
     */
    private List<String> toUniqueList(String text) {
        // ... (Логика без изменений)
        Set<String> uniqueWords = new HashSet<>();
        try (Scanner sc = new Scanner(text)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (!line.isBlank()) {
                    uniqueWords.add(line.toLowerCase());
                }
            }
        }
        logger.debug("Извлечено уникальных слов: {}", uniqueWords.size()); // LOG
        return new ArrayList<>(uniqueWords);
    }

    // --- ГЛАВНЫЕ методы доступа к данным ---

    /**
     * Возвращает набор названий всех доступных тем (словарей).
     *
     * @return Набор названий тем.
     */
    public Set<String> topics() {
        return dictionaries.keySet();
    }

    /**
     * Возвращает список словарных слов (в нижнем регистре) для указанной темы.
     *
     * @param topic Имя темы (например, "programming").
     * @return Список слов для темы. Если тема не найдена, возвращает пустой список (Collections.emptyList()).
     */
    public List<String> wordsForTopic(String topic) {
        return dictionaries.getOrDefault(topic, Collections.emptyList());
    }
    /**
     * Формирует и возвращает объединенный словарь, включающий ключевые слова
     * только из указанных активных тем.
     *
     * @param activeTopics Список имен тем (String), выбранных пользователем для анализа.
     * @return Map
     * Значение = Имя темы, к которой это слово относится.
     * @deprecated Этот метод устарел. Используйте {@code wordsForTopic(String topic)} для
     * построения мульти-тематической карты в классе Analyzer.
     */
    @Deprecated
    public Map<String, String> getActiveDictionaryMap(List<String> activeTopics) {
        if (activeTopics == null || activeTopics.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> activeDictionary = new HashMap<>();

        for (String topic : activeTopics) {
            List<String> keywords = dictionaries.get(topic);

            if (keywords != null) {
                // ПРЕДУПРЕЖДЕНИЕ: Здесь происходит перезапись, если слово есть в нескольких темах (конфликт омонимии)
                keywords.forEach(keyword -> activeDictionary.put(keyword, topic));
            }
        }
        return activeDictionary;
    }
}