/**
 * Класс, отвечающий за **анализ текста** на предмет соответствия темам,
 * загруженным из словарей ({@link DictionaryManager}).
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
 * @version 1.0
 */
package detector;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
/**
 * Класс, отвечающий за полный анализ текста.
 * Выполняет токенизацию, нормализацию, поиск совпадений со словами из словарей
 * и подсчет статистики по темам.
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
        /** Название темы, к которой принадлежит совпадение. */
        final String topic;
        /** Само словарное слово или фраза (как в словаре). */
        final String entry;
        /** Индекс начала фразы в списке токенов текста. */
        final int startIndex;
        /** Индекс конца фразы (исключительно) в списке токенов текста. */
        final int endIndex;

        /**
         * Конструктор для создания объекта Match.
         *
         * @param topic Название темы.
         * @param entry Словарная фраза.
         * @param startIndex Начальный индекс токена.
         * @param endIndex Конечный индекс токена (исключительно).
         */
        Match(String topic, String entry, int startIndex, int endIndex) {
            this.topic = topic;
            this.entry = entry;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    /**
     * Выполняет **полный анализ** текста.
     * <p>
     * Сначала токенизирует текст, затем ищет многословные фразы, предотвращая перекрытие.
     * После этого ищет одиночные слова в тех токенах, которые не были использованы.
     *
     * @param text Исходный текст для анализа.
     * @return Объект {@link AnalysisResult}, содержащий общие счетчики по темам
     * и детальную статистику по конкретным найденным словарным словам.
     */
    public AnalysisResult analyzeFull(String text) {

        Map<String, Integer> countsByTopic = new HashMap<>();
        Map<String, Map<String, Integer>> detailed = new HashMap<>();

        for (String topic : dm.topics()) {
            countsByTopic.put(topic, 0);
            detailed.put(topic, new LinkedHashMap<>());
        }

        // 1. Подготовка текста
        String lower = text.toLowerCase();
        List<String> tokens = Arrays.stream(SPLIT.split(lower))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        List<String> stems = tokens.stream()
                .map(this::stem)
                .collect(Collectors.toList());

        // 2. Сначала ищем ВСЕ многословные совпадения
        List<Match> allMultiwordMatches = new ArrayList<>();
        // Массив для предотвращения перекрытия МЕЖДУ многословными фразами
        boolean[] usedForMultiword = new boolean[tokens.size()];

        for (String topic : dm.topics()) {
            for (String entry : dm.wordsForTopic(topic)) {
                if (entry.contains(" ")) {
                    allMultiwordMatches.addAll(findMultiWordMatches(topic, entry, tokens, stems));
                }
            }
        }

        // 3. Обрабатываем многословные совпадения (без двойного счета между фразами)
        for (Match m : allMultiwordMatches) {
            boolean isOverlapping = false;
            for (int i = m.startIndex; i < m.endIndex; i++) {
                if (usedForMultiword[i]) {
                    isOverlapping = true;
                    break;
                }
            }

            if (!isOverlapping) {
                // Регистрируем совпадение
                detailed.get(m.topic).merge(m.entry, 1, Integer::sum);
                countsByTopic.merge(m.topic, 1, Integer::sum);

                // Помечаем токены как использованные для многословной фразы
                for (int i = m.startIndex; i < m.endIndex; i++) {
                    usedForMultiword[i] = true;
                }
            }
        }


        // 4. Ищем все одиночные слова (с проверкой на перекрытие ВНУТРИ темы и внутри одиночных слов)
        for (String topic : dm.topics()) {

            // Получаем уникальные одиночные слова для этой темы. (Решает дубликаты в словаре: "система", "система")
            Set<String> uniqueSingleWords = dm.wordsForTopic(topic).stream()
                    .filter(w -> !w.contains(" "))
                    .collect(Collectors.toSet());

            // Массив для отслеживания, какой токен текста уже был посчитан как одиночное слово
            // в рамках ТЕКУЩЕЙ темы. (Решает дубликаты стемминга: "система", "системные")
            boolean[] tokensCountedAsSingle = new boolean[tokens.size()];

            Map<String, Integer> topicDetailedCounts = detailed.get(topic);

            for (String entry : uniqueSingleWords) {

                String w = entry.toLowerCase();
                String wStem = stem(w);
                int currentEntryCount = 0;

                // Перебираем токены текста
                for (int i = 0; i < tokens.size(); i++) {

                    // 1. Пропускаем токен, если он был использован многословной фразой в ЭТОЙ ЖЕ ТЕМЕ.
                    if (isTokenUsedByTopicMultiwordMatch(i, topic, allMultiwordMatches)) {
                        continue;
                    }

                    // 2. Пропускаем токен, если он уже был посчитан как одиночное слово в ЭТОЙ ЖЕ ТЕМЕ.
                    if (tokensCountedAsSingle[i]) {
                        continue;
                    }

                    boolean isMatch = tokens.get(i).equals(w) || (fuzzy && stems.get(i).equals(wStem));

                    if (isMatch) {
                        currentEntryCount++;

                        // Помечаем токен как использованный для одиночных слов в этой теме.
                        tokensCountedAsSingle[i] = true;
                    }
                }

                if (currentEntryCount > 0) {
                    // Добавляем счетчик для этого слова в детальную статистику
                    topicDetailedCounts.put(entry, currentEntryCount);

                    // Добавляем к общему счету темы
                    countsByTopic.merge(topic, currentEntryCount, Integer::sum);
                }
            }
        }

        return new AnalysisResult(countsByTopic, detailed);
    }

    /**
     * Проверяет, был ли токен по индексу 'tokenIndex' использован
     * в многословной фразе, принадлежащей теме 'currentTopic'.
     * <p>
     * Этот метод помогает избежать двойного счета однословных совпадений внутри
     * уже найденной многословной фразы, если фраза и слово принадлежат одной теме.
     *
     * @param tokenIndex Индекс токена в списке токенов текста.
     * @param currentTopic Название текущей темы.
     * @param allMatches Список всех найденных многословных совпадений.
     * @return {@code true}, если токен был использован в многословной фразе текущей темы, иначе {@code false}.
     */
    private boolean isTokenUsedByTopicMultiwordMatch(int tokenIndex, String currentTopic, List<Match> allMatches) {
        for (Match m : allMatches) {
            // Ищем только в текущей теме (currentTopic)
            if (m.topic.equals(currentTopic)) {
                // Проверяем, находится ли индекс токена в диапазоне совпадения фразы
                if (tokenIndex >= m.startIndex && tokenIndex < m.endIndex) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * **Устаревший метод.**
     * Выполняет анализ текста и возвращает только **общие счетчики** по темам.
     * <p>
     * Использует {@link #analyzeFull(String)} и возвращает только {@link AnalysisResult#countsByTopic()}.
     *
     * @param text Исходный текст.
     * @return Map, где ключ - название темы, значение - общее количество совпадений.
     * @deprecated Используйте {@link #analyzeFull(String)} для получения полной информации.
     */
    @Deprecated
    public Map<String, Integer> analyze(String text) {
        return analyzeFull(text).countsByTopic();
    }

    /**
     * Находит все многословные фразы (совпадения) для данной словарной записи в токенизированном тексте.
     * Поддерживает как строгий, так и нечеткий (fuzzy) режим.
     *
     * @param topic Название темы, к которой относится фраза.
     * @param phrase Многословная фраза из словаря (например, "передача данных").
     * @param tokens Список токенов (слов) из исходного текста.
     * @param stems Список стемов, соответствующих токенам (используется в fuzzy режиме).
     * @return Список объектов {@link Match}, представляющих найденные совпадения.
     */
    private List<Match> findMultiWordMatches(String topic, String phrase, List<String> tokens, List<String> stems) {
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
                matches.add(new Match(topic, phrase, i, i + parts.length));
            }
        }

        return matches;
    }

    /**
     * Реализует **простой русский стеммер**.
     * Удаляет наиболее распространенные окончания.
     * <p>
     * **Важно:** Этот стеммер является эвристическим и не гарантирует точности,
     * но достаточен для простого fuzzy-анализа.
     *
     * @param w Слово в нижнем регистре.
     * @return Стем (основа) слова.
     */
    private String stem(String w) {
        if (w.length() <= 3) return w;

        String[] suf = {
                "иями","ями","ами","иях","ием","ете","ить",
                "ение","ением","ений",
                "ов","ев","ёв","ей","ия","ие",
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