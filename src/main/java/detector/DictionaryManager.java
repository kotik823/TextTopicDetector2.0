package detector;

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
 */
public class DictionaryManager {

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
     * Имя файла без расширения (.txt) используется как название темы.
     *
     * @throws IOException Если ресурс не найден или возникла ошибка ввода/вывода при чтении.
     */
    private void loadDictionariesFromResources() throws IOException {
        // Предполагаемый список имен словарей.
        String[] dictNames = {
                "medical.txt", "history.txt", "programming.txt",
                "networks.txt", "cryptography.txt", "finance.txt"
        };

        for (String name : dictNames) {
            String path = "dictionaries/" + name;

            // Получаем InputStream для чтения из ресурсов
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            if (is == null) {
                // Если файл не найден, логируем ошибку или пропускаем
                System.err.println("Не найден предопределенный файл словаря: " + path);
                continue;
            }

            try (is) {
                byte[] bytes = is.readAllBytes();
                List<String> words = readLinesFromBytesWithEncodingFallback(bytes);

                dictionaries.put(name.replace(".txt", ""), words); // ключ = имя темы
            }
        }
    }

    /**
     * Загружает словарь из указанного пользователем файла на диске и добавляет его
     * в список доступных словарей. Имя темы определяется как имя файла без расширения.
     *
     * @param file Файл словаря (ожидается формат .txt).
     * @return Имя загруженной темы (topicName) в нижнем регистре.
     * @throws IOException Если произошла ошибка при чтении файла.
     */
    public String loadDictionaryFromFile(File file) throws IOException {
        // 1. Извлечение имени темы
        String fileName = file.getName();
        String topicName;
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0) {
            topicName = fileName.substring(0, dotIndex).toLowerCase();
        } else {
            topicName = fileName.toLowerCase();
        }

        // 2. Считывание байтов и обработка кодировки
        byte[] bytes = Files.readAllBytes(file.toPath());
        List<String> words = readLinesFromBytesWithEncodingFallback(bytes);

        // 3. Сохранение в хранилище
        if (!words.isEmpty()) {
            dictionaries.put(topicName, words);
        }

        return topicName; // Возвращаем имя для обновления GUI
    }

    /**
     * Считывает содержимое массива байтов, используя UTF-8, а при обнаружении
     * "кракозябр" (испорченной кириллицы) переключается на кодировку windows-1251.
     *
     * @param bytes Массив байтов для чтения.
     * @return Список уникальных строк, содержащих слова из словаря в нижнем регистре.
     */
    private List<String> readLinesFromBytesWithEncodingFallback(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);

        // Проверяем, если UTF-8 некорректно декодировал кириллицу
        if (!looksLikeGarbledRussian(text)) return toUniqueList(text);

        // Если UTF-8 не сработал, пробуем CP1251
        text = new String(bytes, Charset.forName("windows-1251"));
        return toUniqueList(text);
    }

    // --- Вспомогательные методы ---

    /**
     * Эвристическая проверка, указывает ли строка на некорректно декодированный русский текст.
     */
    private boolean looksLikeGarbledRussian(String s) {
        if (s == null || s.isEmpty()) return false; // Пустая строка - не ошибка
        int total = s.length();
        int letters = 0;
        int repl = 0;

        for (char c : s.toCharArray()) {
            if (c == '\uFFFD') repl++;
            if (Character.isLetter(c)) letters++;
        }

        // Если есть символы замены () или меньше 30% символов являются буквами,
        // считаем, что кодировка, скорее всего, некорректна.
        return repl > 0 || ((double) letters / total < 0.3);
    }

    /**
     * Преобразует строку текста в список уникальных строк, удаляя пробелы и пустые строки,
     * и приводя все слова к нижнему регистру.
     * <p>
     * **ИСПРАВЛЕНО:** Используется {@code HashSet} для обеспечения уникальности слов.
     * </p>
     */
    private List<String> toUniqueList(String text) {
        // Используем HashSet для автоматического удаления дубликатов
        Set<String> uniqueWords = new HashSet<>();
        try (Scanner sc = new Scanner(text)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (!line.isBlank()) {
                    uniqueWords.add(line.toLowerCase());
                }
            }
        }
        // Возвращаем уникальные слова в виде List (порядок не гарантируется, но это не критично)
        return new ArrayList<>(uniqueWords);
    }

    // --- ГЛАВНЫЕ методы доступа к данным ---

    /**
     * Возвращает набор названий всех доступных тем (словарей).
     *
     * @return {@code Set<String>} с названиями тем (ключей словарей).
     */
    public Set<String> topics() {
        return dictionaries.keySet();
    }

    /**
     * Возвращает список словарных слов (в нижнем регистре) для указанной темы.
     * Слова в списке гарантированно уникальны.
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