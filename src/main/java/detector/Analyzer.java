package detector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Класс, отвечающий за **анализ текста** на предмет соответствия темам,
 * загруженным из словарей ({@link DictionaryManager}).
 * <p>
 * **Версия 2.3: Добавлено логирование (Log4j 2) на русском языке.**
 * </p>
 * <p>
 * Поддерживает два режима поиска совпадений:
 * <ul>
 * <li>**Строгий (Strict)**: только точное совпадение слова.</li>
 * <li>**Нечеткий (Fuzzy)**: использует внутренний простой стеммер для поиска словоформ.</li>
 * </ul>
 * Логика анализа включает специальную обработку многословных фраз,
 * предотвращая двойной счет (однословное совпадение внутри многословной фразы).
 *
 * @author (Ваше имя)
 * @version 2.3 (Логирование на русском)
 */
public class Analyzer {

    /** Логгер для класса Analyzer. */
    private static final Logger logger = LogManager.getLogger(Analyzer.class);

    /**
     * Менеджер словарей, предоставляющий темы и словарные слова.
     */
    private final DictionaryManager dm;
    /**
     * Флаг, указывающий на использование нечеткого (стеммингового) анализа ({@code true})
     * или строгого ({@code false}).
     */
    private final boolean fuzzy;

    /**
     * Регулярное выражение для разделения текста на токены (слова).
     * Разделяет по любым символам, не являющимся буквами ({@code \p{L}}).
     */
    private static final Pattern SPLIT =
            Pattern.compile("[^\\p{L}]+", Pattern.UNICODE_CHARACTER_CLASS);

    // --- Вспомогательные классы Match и StemInfo (без изменений) ---

    private static class Match {
        final List<String> topics;
        final String entry;
        final int startIndex;
        final int endIndex;

        Match(List<String> topics, String entry, int startIndex, int endIndex) {
            this.topics = topics;
            this.entry = entry;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    private static class StemInfo {
        final String representativeWord;
        final Set<String> allTopics = new HashSet<>();

        StemInfo(String representativeWord, List<String> topics) {
            this.representativeWord = representativeWord;
            this.allTopics.addAll(topics);
        }

        void merge(String word, List<String> topics) {
            allTopics.addAll(topics);
        }
    }

    // --- Конструктор ---

    public Analyzer(DictionaryManager dm, boolean fuzzy) {
        this.dm = dm;
        this.fuzzy = fuzzy;
        logger.debug("Анализатор инициализирован в режиме: {}.", fuzzy ? "Нечеткий (Fuzzy)" : "Строгий (Strict)");
    }

    /**
     * Выполняет **полный анализ** текста.
     */
    public AnalysisResult analyzeFull(String text, List<String> activeTopics) {
        logger.info("Начат анализ в режиме: {}. Активные темы: {}",
                fuzzy ? "Нечеткий (Fuzzy)" : "Строгий (Strict)", activeTopics);

        // 1. Инициализация результатов
        Map<String, Integer> countsByTopic = new HashMap<>();
        Map<String, Map<String, Integer>> detailed = new HashMap<>();

        for (String topic : activeTopics) {
            countsByTopic.put(topic, 0);
            detailed.put(topic, new LinkedHashMap<>());
        }

        if (activeTopics.isEmpty()) {
            logger.warn("Анализ пропущен: не выбраны активные темы.");
            return new AnalysisResult(countsByTopic, detailed);
        }

        // 2. Создание объединенного словаря
        Map<String, List<String>> activeWordToTopicsMap = new HashMap<>();
        for (String topic : activeTopics) {
            List<String> words = dm.wordsForTopic(topic);
            for (String word : words) {
                activeWordToTopicsMap.computeIfAbsent(word, k -> new ArrayList<>()).add(topic);
            }
        }

        // 3. Разделение словаря
        Map<String, List<String>> multiwordEntries = activeWordToTopicsMap.entrySet().stream()
                .filter(entry -> entry.getKey().contains(" "))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<String, List<String>> singlewordEntries = activeWordToTopicsMap.entrySet().stream()
                .filter(entry -> !entry.getKey().contains(" "))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 3.1. Предварительная обработка для Fuzzy Mode (Stem Map)
        Map<String, StemInfo> stemToInfoMap = new HashMap<>();
        if (fuzzy) {
            logger.debug("Построение StemMap для нечеткого анализа...");
            for (Map.Entry<String, List<String>> entry : singlewordEntries.entrySet()) {
                String dictWord = entry.getKey();
                List<String> dictTopics = entry.getValue();
                String dictStem = stem(dictWord);

                stemToInfoMap.computeIfPresent(dictStem, (k, v) -> {
                    v.merge(dictWord, dictTopics);
                    return v;
                });
                stemToInfoMap.computeIfAbsent(dictStem, k -> new StemInfo(dictWord, dictTopics));
            }
            logger.debug("StemMap успешно построена. Уникальных корней: {}.", stemToInfoMap.size());
        }


        // 4. Токенизация и Стемминг текста
        String lower = text.toLowerCase();
        List<String> tokens = Arrays.stream(SPLIT.split(lower))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        if (tokens.isEmpty()) {
            logger.warn("Текст пуст или не содержит допустимых токенов (слов).");
            return new AnalysisResult(countsByTopic, detailed);
        }

        List<String> stems = tokens.stream()
                .map(this::stem)
                .collect(Collectors.toList());

        logger.debug("Текст токенизирован. Всего токенов: {}. Первый токен: '{}'", tokens.size(), tokens.get(0));


        // 5. Ищем ВСЕ многословные совпадения
        logger.debug("Поиск {} многословных фраз.", multiwordEntries.size());
        List<Match> allMultiwordMatches = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : multiwordEntries.entrySet()) {
            allMultiwordMatches.addAll(
                    findMultiWordMatches(entry.getValue(), entry.getKey(), tokens, stems)
            );
        }

        // 6. Обрабатываем многословные совпадения (предотвращая перекрытие)
        boolean[] usedTokens = new boolean[tokens.size()];

        for (Match m : allMultiwordMatches) {
            boolean isOverlapping = false;
            for (int i = m.startIndex; i < m.endIndex; i++) {
                if (usedTokens[i]) {
                    isOverlapping = true;
                    break;
                }
            }

            if (!isOverlapping) {
                // Регистрируем совпадение
                for (String topic : m.topics) {
                    if (detailed.containsKey(topic)) {
                        detailed.get(topic).merge(m.entry, 1, Integer::sum);
                        countsByTopic.merge(topic, 1, Integer::sum);
                    }
                }

                logger.info("Найдено многословное совпадение: '{}' (Токены: {} по {}) в темах: {}",
                        m.entry, m.startIndex, m.endIndex - 1, m.topics);

                // Помечаем токены как использованные
                for (int i = m.startIndex; i < m.endIndex; i++) {
                    usedTokens[i] = true;
                }
            } else {
                logger.debug("Пропуск перекрывающейся многословной фразы: '{}'", m.entry);
            }
        }


        // 7. Ищем все одиночные слова
        logger.debug("Поиск совпадений для одиночных слов в оставшихся токенах...");
        for (int i = 0; i < tokens.size(); i++) {
            if (usedTokens[i]) {
                continue;
            }

            String token = tokens.get(i);
            String stemToken = stems.get(i);

            boolean tokenAlreadyCounted = false;

            // 7.1. Строгое совпадение
            if (singlewordEntries.containsKey(token)) {
                List<String> topics = singlewordEntries.get(token);
                logger.debug("Найдено строгое совпадение для токена '{}' в темах: {}", token, topics);
                for (String topic : topics) {
                    detailed.get(topic).merge(token, 1, Integer::sum);
                    countsByTopic.merge(topic, 1, Integer::sum);
                }
                usedTokens[i] = true;
                tokenAlreadyCounted = true;
            }

            if (fuzzy && !tokenAlreadyCounted) {
                // 7.2. Нечеткое (стем) совпадение
                StemInfo info = stemToInfoMap.get(stemToken);

                if (info != null) {
                    // Найдено нечеткое совпадение: регистрируем для ВСЕХ связанных тем ОДИН РАЗ
                    logger.debug("Найдено нечеткое совпадение для токена '{}' (корень: '{}'). Представительное слово: '{}'. Темы: {}",
                            token, stemToken, info.representativeWord, info.allTopics);

                    for (String topic : info.allTopics) {
                        if (detailed.containsKey(topic)) {
                            detailed.get(topic).merge(info.representativeWord, 1, Integer::sum);
                            countsByTopic.merge(topic, 1, Integer::sum);
                        }
                    }
                    usedTokens[i] = true;
                }
            }
        }

        logger.info("Анализ завершен. Общие счетчики: {}", countsByTopic);
        return new AnalysisResult(countsByTopic, detailed);
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ---

    private List<Match> findMultiWordMatches(List<String> topics, String phrase, List<String> tokens, List<String> stems) {
        String[] parts = phrase.toLowerCase().split(" ");
        String[] pStems = Arrays.stream(parts).map(this::stem).toArray(String[]::new);
        List<Match> matches = new ArrayList<>();

        for (int i = 0; i <= tokens.size() - parts.length; i++) {
            boolean ok = true;

            for (int j = 0; j < parts.length; j++) {
                if (!tokens.get(i + j).equals(parts[j]) &&
                        !(fuzzy && stems.get(i + j).equals(pStems[j]))) {
                    ok = false;
                    break;
                }
            }

            if (ok) {
                logger.trace("Найдено совпадение для фразы '{}' по индексу {}", phrase, i);
                matches.add(new Match(topics, phrase, i, i + parts.length));
            }
        }

        return matches;
    }

    private String stem(String w) {
        if (w.length() <= 3) return w;

        String[] suf = {
                "иями","ями","ами","иях","ием","ете","ить",
                "ение","ением","ений",
                "ов","ев","ёв","ей","ия","ие","ий","ии",
                "ость","остей","ости",
                "ой","ый","ая","ые","ое","ых","их",
                "ам","ям","ом","ем","ах","ях",
                "у","ю","а","я","е","о","и","ы"
        };

        for (String s : suf) {
            if (w.endsWith(s)) {
                String stem = w.substring(0, w.length() - s.length());
                return stem;
            }
        }

        return w;
    }

    // --- Устаревший метод analyze (без изменений) ---

    @Deprecated
    public Map<String, Integer> analyze(String text) {
        List<String> allTopics = new ArrayList<>(dm.topics());
        return analyzeFull(text, allTopics).countsByTopic();
    }


    // --- Вспомогательный класс AnalysisResult (без изменений) ---

    public static class AnalysisResult {
        private final Map<String, Integer> countsByTopic;
        private final Map<String, Map<String, Integer>> detailed;

        public AnalysisResult(Map<String, Integer> countsByTopic,
                              Map<String, Map<String, Integer>> detailed) {
            this.countsByTopic = countsByTopic;
            this.detailed = detailed;
        }

        public Map<String, Integer> countsByTopic() {
            return countsByTopic;
        }

        public Map<String, Map<String, Integer>> detailed() {
            return detailed;
        }
    }
}