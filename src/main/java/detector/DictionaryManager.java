package detector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Менеджер словарей, отвечающий за загрузку тематических словарей из ресурсов
 * и предоставление к ним доступа.
 * <p>
 * Словари хранятся в виде Map, где ключ — это название темы (например, "medical"),
 * а значение — список слов, относящихся к этой теме.
 * Класс включает логику для обработки различных кодировок файлов (UTF-8 и CP1251).
 * </p>
 */
public class DictionaryManager {

    /** Карта, хранящая словари. Ключ: Название темы, Значение: Список слов. */
    private final Map<String, List<String>> dictionaries = new HashMap<>();

    /**
     * Конструктор, который запускает загрузку всех словарей.
     *
     * @throws IOException Если не удалось найти или прочитать один из файлов словарей
     * в ресурсах приложения.
     */
    public DictionaryManager() throws IOException {
        loadDictionaries();
    }

    // --- Методы загрузки словарей ---

    /**
     * Загружает все предопределенные словари из папки resources/dictionaries/.
     * Имя файла без расширения (.txt) используется как название темы.
     *
     * @throws IOException Если ресурс не найден или возникла ошибка ввода/вывода при чтении.
     */
    private void loadDictionaries() throws IOException {
        String[] dictNames = {
                "medical.txt",
                "history.txt",
                "programming.txt",
                "networks.txt",
                "cryptography.txt",
                "finance.txt"
        };

        for (String name : dictNames) {
            String path = "dictionaries/" + name;

            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            if (is == null) {
                throw new IOException("Не найден файл словаря: " + path);
            }

            List<String> words = readLinesWithEncodingFallback(is);
            dictionaries.put(name.replace(".txt", ""), words); // ключ = имя темы
        }
    }

    /**
     * Считывает содержимое InputStream, используя UTF-8, а при обнаружении
     * "кракозябр" (испорченной кириллицы) переключается на кодировку windows-1251.
     *
     * @param resourceStream Входной поток для чтения файла словаря.
     * @return Список строк, содержащих слова из словаря в нижнем регистре.
     * @throws IOException Если возникла ошибка чтения потока.
     */
    private List<String> readLinesWithEncodingFallback(InputStream resourceStream) throws IOException {
        byte[] bytes = resourceStream.readAllBytes();

        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!looksLikeGarbledRussian(text)) return toList(text);

        // Если UTF-8 не сработал, пробуем CP1251
        text = new String(bytes, Charset.forName("windows-1251"));
        return toList(text);
    }

    /**
     * Эвристическая проверка, указывает ли строка на некорректно декодированный русский текст.
     * Проверяет наличие символов замены ('\uFFFD') и отношение количества букв к общей длине.
     *
     * @param s Строка, которую нужно проверить.
     * @return {@code true} если строка, вероятно, содержит "кракозябры" (неправильную кодировку),
     * {@code false} в противном случае.
     */
    private boolean looksLikeGarbledRussian(String s) {
        if (s == null || s.isEmpty()) return true;
        int total = s.length();
        int letters = 0;
        int repl = 0;

        for (char c : s.toCharArray()) {
            if (c == '\uFFFD') repl++;
            if (Character.isLetter(c)) letters++;
        }

        // Если есть символы замены (repl > 0) или слишком мало букв (менее 30% от длины)
        return repl > 0 || ((double) letters / total < 0.3);
    }

    /**
     * Преобразует строку текста в список строк, удаляя пробелы и пустые строки,
     * и приводя все слова к нижнему регистру.
     *
     * @param text Исходный текст словаря.
     * @return Список слов в нижнем регистре.
     */
    private List<String> toList(String text) {
        List<String> out = new ArrayList<>();
        try (Scanner sc = new Scanner(text)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (!line.isBlank()) out.add(line.toLowerCase());
            }
        }
        return out;
    }

    // --- ГЛАВНЫЕ методы доступа к данным ---

    /**
     * Возвращает набор названий всех доступных тем (словарей).
     *
     * @return {@code Set<String>} с названиями тем.
     */
    public Set<String> topics() {
        return dictionaries.keySet();
    }

    /**
     * Возвращает список слов для указанной темы.
     *
     * @param topic Название темы (ключа в словаре).
     * @return {@code List<String>} слов для темы. Возвращает пустой список, если тема не найдена.
     */
    public List<String> wordsForTopic(String topic) {
        return dictionaries.getOrDefault(topic, List.of());
    }

    /**
     * Возвращает всю карту словарей.
     *
     * @return Неизменяемая карта, где ключ — тема, значение — список слов.
     */
    public Map<String, List<String>> getDictionaries() {
        return dictionaries;
    }
}