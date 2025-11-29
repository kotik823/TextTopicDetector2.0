package detector;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Класс, отвечающий за **анализ текста** на предмет соответствия темам,
 * загруженным из словарей ({@link DictionaryManager}).
 * <p>
 * **Версия 2.1: Исправлена ошибка множественного счета (омонимии/общего корня) в Fuzzy Mode.**
 * Устраняет проблему, когда одно слово в тексте, совпадающее по корню
 * с несколькими словарными словами, засчитывалось многократно.
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
 * @version 2.1 (Исправлен множественный счет в Fuzzy Mode)
 */
public class Analyzer {

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

    /**
     * Конструктор анализатора.
     *
     * @param dm Менеджер словарей, содержащий все тематические слова.
     * @param fuzzy Флаг режима анализа: {@code true} для нечеткого (стемминг), {@code false} для строгого.
     */
    public Analyzer(DictionaryManager dm, boolean fuzzy) {
        this.dm = dm;
        this.fuzzy = fuzzy;
    }

    /**
     * Вспомогательный класс для хранения найденного совпадения многословной фразы,
     * включая ее позицию в массиве токенов.
     */
    private static class Match {
        /** Список названий тем, к которым принадлежит совпадение (для устранения омонимии). */
        final List<String> topics;
        /** Само словарное слово или фраза (как в словаре). */
        final String entry;
        /** Индекс начала фразы в списке токенов текста. */
        final int startIndex;
        /** Индекс конца фразы (исключительно) в списке токенов текста. */
        final int endIndex;

        /**
         * Конструктор для создания объекта Match.
         *
         * @param topics Список тем.
         * @param entry Словарная фраза.
         * @param startIndex Начальный индекс токена.
         * @param endIndex Конечный индекс токена (исключительно).
         */
        Match(List<String> topics, String entry, int startIndex, int endIndex) {
            this.topics = topics;
            this.entry = entry;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    /**
     * Вспомогательный класс для хранения информации о словарном слове,
     * сгруппированной по корню (stem). Используется для Fuzzy Mode.
     */
    private static class StemInfo {
        /** Каноническое словарное слово для отчета (например, самое короткое или первое). */
        final String representativeWord;
        /** Список всех тем, к которым принадлежит хотя бы одно слово с этим корнем. */
        final Set<String> allTopics = new HashSet<>();

        /**
         * Конструктор для StemInfo.
         * @param representativeWord Слово, которое будет отображаться в детальном отчете.
         * @param topics Список тем.
         */
        StemInfo(String representativeWord, List<String> topics) {
            this.representativeWord = representativeWord;
            this.allTopics.addAll(topics);
        }

        /**
         * Объединяет информацию о другом слове с тем же корнем.
         * @param word Новое слово с тем же корнем.
         * @param topics Список тем нового слова.
         */
        void merge(String word, List<String> topics) {
            // RepresentativeWord остается прежним (первым найденным), чтобы избежать хаотичного отображения в отчете
            allTopics.addAll(topics);
        }
    }

    /**
     * Выполняет **полный анализ** текста, используя только те словари,
     * названия которых указаны в {@code activeTopics}.
     * <p>
     * **Логика мульти-тематического анализа:** Создает карту {@code Map<Слово, List<Тема>>}
     * для корректного учета омонимов.
     *
     * @param text Исходный текст для анализа.
     * @param activeTopics Список имен тем, которые должны участвовать в анализе (например, "networks", "finance").
     * @return Объект {@link AnalysisResult}, содержащий общие счетчики по темам
     * и детальную статистику по конкретным найденным словарным словам.
     */
    public AnalysisResult analyzeFull(String text, List<String> activeTopics) {

        // 1. Инициализация результатов только для АКТИВНЫХ тем
        Map<String, Integer> countsByTopic = new HashMap<>();
        Map<String, Map<String, Integer>> detailed = new HashMap<>();

        for (String topic : activeTopics) {
            countsByTopic.put(topic, 0);
            detailed.put(topic, new LinkedHashMap<>());
        }

        if (activeTopics.isEmpty()) {
            return new AnalysisResult(countsByTopic, detailed);
        }

        // 2. Создание объединенного словаря с поддержкой множественных тем
        // Карта: Слово/Фраза -> List<Тема>
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

        // 3.1. Предварительная обработка для Fuzzy Mode (Stem Map) - НОВОЕ!
        // Карта: Корень -> StemInfo (группирует слова по корням для корректного подсчета)
        Map<String, StemInfo> stemToInfoMap = new HashMap<>();
        if (fuzzy) {
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
        }


        // 4. Токенизация и Стемминг текста
        String lower = text.toLowerCase();
        List<String> tokens = Arrays.stream(SPLIT.split(lower))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        List<String> stems = tokens.stream()
                .map(this::stem)
                .collect(Collectors.toList());


        // 5. Ищем ВСЕ многословные совпадения
        List<Match> allMultiwordMatches = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : multiwordEntries.entrySet()) {
            allMultiwordMatches.addAll(
                    findMultiWordMatches(entry.getValue(), entry.getKey(), tokens, stems)
            );
        }

        // 6. Обрабатываем многословные совпадения (предотвращая перекрытие)
        // Массив для предотвращения перекрытия токенов
        boolean[] usedTokens = new boolean[tokens.size()];

        for (Match m : allMultiwordMatches) {
            boolean isOverlapping = false;
            // Проверка, не использован ли токен ранее другой фразой
            for (int i = m.startIndex; i < m.endIndex; i++) {
                if (usedTokens[i]) {
                    isOverlapping = true;
                    break;
                }
            }

            if (!isOverlapping) {
                // Регистрируем совпадение для ВСЕХ связанных тем
                for (String topic : m.topics) {
                    if (detailed.containsKey(topic)) {
                        detailed.get(topic).merge(m.entry, 1, Integer::sum);
                        countsByTopic.merge(topic, 1, Integer::sum);
                    }
                }

                // Помечаем токены как использованные
                for (int i = m.startIndex; i < m.endIndex; i++) {
                    usedTokens[i] = true;
                }
            }
        }


        // 7. Ищем все одиночные слова (только в неиспользованных токенах)
        for (int i = 0; i < tokens.size(); i++) {
            // Пропускаем токен, если он был использован многословной фразой
            if (usedTokens[i]) {
                continue;
            }

            String token = tokens.get(i);
            String stemToken = stems.get(i);

            // Флаг для предотвращения двойного счета одного токена
            boolean tokenAlreadyCounted = false;

            // 7.1. Строгое совпадение
            if (singlewordEntries.containsKey(token)) {
                List<String> topics = singlewordEntries.get(token);
                for (String topic : topics) {
                    detailed.get(topic).merge(token, 1, Integer::sum);
                    countsByTopic.merge(topic, 1, Integer::sum);
                }
                usedTokens[i] = true;
                tokenAlreadyCounted = true;
            }

            if (fuzzy && !tokenAlreadyCounted) {
                // 7.2. Нечеткое (стем) совпадение (Исправлено: считает токен только один раз!)
                StemInfo info = stemToInfoMap.get(stemToken);

                if (info != null) {
                    // Найдено нечеткое совпадение: регистрируем для ВСЕХ связанных тем ОДИН РАЗ
                    for (String topic : info.allTopics) {
                        if (detailed.containsKey(topic)) {
                            // Для детального отчета берем каноническое слово (representativeWord)
                            // Это предотвращает появление нескольких однокоренных слов в отчете за один токен.
                            detailed.get(topic).merge(info.representativeWord, 1, Integer::sum);
                            countsByTopic.merge(topic, 1, Integer::sum);
                        }
                    }
                    usedTokens[i] = true;
                }
            }
        }

        return new AnalysisResult(countsByTopic, detailed);
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ---

    /**
     * Находит все многословные фразы (совпадения) для данной словарной записи в токенизированном тексте.
     *
     * @param topics Список тем, к которым принадлежит фраза.
     * @param phrase Словарная фраза, которую ищем.
     * @param tokens Список токенов текста (в нижнем регистре).
     * @param stems Список стемов токенов текста.
     * @return Список объектов {@link Match}, представляющих найденные совпадения.
     */
    private List<Match> findMultiWordMatches(List<String> topics, String phrase, List<String> tokens, List<String> stems) {
        String[] parts = phrase.toLowerCase().split(" ");
        String[] pStems = Arrays.stream(parts).map(this::stem).toArray(String[]::new);
        List<Match> matches = new ArrayList<>();

        for (int i = 0; i <= tokens.size() - parts.length; i++) {
            boolean ok = true;

            for (int j = 0; j < parts.length; j++) {
                // Проверка: (точное совпадение) ИЛИ (режим fuzzy И совпадение стемов)
                if (!tokens.get(i + j).equals(parts[j]) &&
                        !(fuzzy && stems.get(i + j).equals(pStems[j]))) {
                    ok = false;
                    break;
                }
            }

            if (ok) {
                matches.add(new Match(topics, phrase, i, i + parts.length));
            }
        }

        return matches;
    }

    /**
     * Реализует **простой русский стеммер**.
     *
     * @param w Слово для стемминга.
     * @return Корень слова.
     */
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
            if (w.endsWith(s)) return w.substring(0, w.length() - s.length());
        }

        return w;
    }

    /**
     * **Устаревший метод.**
     * Выполняет анализ текста и возвращает только **общие счетчики** по темам.
     *
     * @param text Исходный текст.
     * @return Map, где ключ - название темы, значение - общее количество совпадений.
     * @deprecated Используйте {@link #analyzeFull(String, List)} для получения полной информации и управления активными темами.
     */
    @Deprecated
    public Map<String, Integer> analyze(String text) {
        List<String> allTopics = new ArrayList<>(dm.topics());
        return analyzeFull(text, allTopics).countsByTopic();
    }


    /**
     * Контейнер для возврата результатов анализа.
     * Инкапсулирует общие счетчики и детальную статистику.
     */
    public static class AnalysisResult {
        /** Общие счетчики совпадений по каждой теме. */
        private final Map<String, Integer> countsByTopic;
        /** Детальная статистика: какие словарные слова были найдены и сколько раз. */
        private final Map<String, Map<String, Integer>> detailed;

        /**
         * Конструктор для создания объекта с результатами анализа.
         *
         * @param countsByTopic Map, где ключ - название темы, значение - общее количество совпадений.
         * @param detailed Карта с подробной статистикой. Ключ: название темы (String), Значение: {@code Map<словарное слово, счетчик>}.
         */
        public AnalysisResult(Map<String, Integer> countsByTopic,
                              Map<String, Map<String, Integer>> detailed) {
            this.countsByTopic = countsByTopic;
            this.detailed = detailed;
        }

        /**
         * Возвращает общие счетчики совпадений по каждой теме.
         *
         * @return Map, где ключ - название темы, значение - общее количество совпадений.
         */
        public Map<String, Integer> countsByTopic() {
            return countsByTopic;
        }

        /**
         * Возвращает детальную статистику, показывающую, какие конкретно словарные
         * слова были найдены и сколько раз.
         *
         * @return Карта с подробной статистикой. Ключ: название темы (String), Значение: {@code Map<словарное слово, счетчик>}.
         */
        public Map<String, Map<String, Integer>> detailed() {
            return detailed;
        }
    }
}