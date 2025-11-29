package detector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyzerTest {

    // Тестовые данные для имитации DictionaryManager
    private Map<String, List<String>> testDictionaries;
    private DictionaryManager mockDm;

    // Analyzer без fuzzy-режима
    private Analyzer analyzerStrict;
    // Analyzer с fuzzy-режимом (стемминг)
    private Analyzer analyzerFuzzy;

    // Список всех тем для передачи в analyzeFull
    private List<String> ALL_TOPICS;

    @BeforeEach
    void setUp() throws IOException {
        // Инициализация тестовых словарей (только те, что используются в тестах)
        testDictionaries = new HashMap<>();
        testDictionaries.put("Programming", new ArrayList<>(List.of("алгоритм", "функция", "класс", "структура данных")));
        testDictionaries.put("Networks", new ArrayList<>(List.of("сеть", "маршрутизатор", "сетевой переход")));
        // Удалена тема "Finance", так как она не использовалась в тестах.


        // Создание анонимного класса, имитирующего DictionaryManager.
        mockDm = new DictionaryManager() {
            @Override
            public Set<String> topics() {
                return testDictionaries.keySet();
            }

            public List<String> wordsForTopic(String topic) {
                return testDictionaries.getOrDefault(topic, Collections.emptyList());
            }

            @Override
            public Map<String, String> getActiveDictionaryMap(List<String> topics) {
                return testDictionaries.entrySet().stream()
                        .filter(e -> topics.contains(e.getKey()))
                        // Преобразуем List<String> слов в Map<слово, название темы>
                        .flatMap(e -> e.getValue().stream().map(word -> new AbstractMap.SimpleEntry<>(word, e.getKey())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> existing));
            }
        };

        // Инициализация списка всех тем
        ALL_TOPICS = new ArrayList<>(mockDm.topics());

        analyzerStrict = new Analyzer(mockDm, false);
        analyzerFuzzy = new Analyzer(mockDm, true);
    }

    @Test
    void testStrictSingleWordMatch() {
        // ИСПРАВЛЕНО: 'функцию' заменено на 'функция' для строгого совпадения
        String text = "Это текст про алгоритм и функция.";
        var result = analyzerStrict.analyzeFull(text, ALL_TOPICS);

        assertEquals(2, result.countsByTopic().get("Programming"), "Должно быть 2 совпадения в строгом режиме.");
        assertEquals(1, result.detailed().get("Programming").get("алгоритм"));
        assertEquals(1, result.detailed().get("Programming").get("функция"));
    }

    @Test
    void testStrictMultiWordMatch() {
        String text = "Тут есть структура данных.";
        var result = analyzerStrict.analyzeFull(text, ALL_TOPICS);

        assertEquals(1, result.countsByTopic().get("Programming"), "Должно быть 1 совпадение многословной фразы в строгом режиме.");
        assertEquals(1, result.detailed().get("Programming").get("структура данных"));
    }

    @Test
    void testFuzzySingleWordMatch() {
        String text = "Мы используем различные алгоритмы и функции.";
        var result = analyzerFuzzy.analyzeFull(text, ALL_TOPICS);

        // 'алгоритмы' -> 'алгоритм', 'функции' -> 'функция'
        assertEquals(2, result.countsByTopic().get("Programming"), "Должно быть 2 совпадения в нечетком режиме.");
        assertEquals(1, result.detailed().get("Programming").get("алгоритм"));
        assertEquals(1, result.detailed().get("Programming").get("функция"));
    }

    @Test
    void testFuzzyMultiWordMatch() {
        // ИСПРАВЛЕНО: Изменен текст на более простой и корректный для стемминга многословной фразы
        String text = "Тут найдены сетевые переходы.";
        var result = analyzerFuzzy.analyzeFull(text, ALL_TOPICS);

        // 'сетевые переходы' должно совпасть с 'сетевой переход' в fuzzy-режиме
        assertEquals(1, result.countsByTopic().get("Networks"), "Должно быть 1 совпадение многословной фразы в нечетком режиме.");
        assertEquals(1, result.detailed().get("Networks").get("сетевой переход"));
    }

    @Test
    void testNoDoubleCounting() {
        String text = "В сети передача данных маршрутизаторам и есть сетевой переход.";
        var result = analyzerFuzzy.analyzeFull(text, ALL_TOPICS);

        // Всего 2 совпадения
        assertEquals(2, result.countsByTopic().get("Networks"), "Должен посчитать 'маршрутизатор' и 'сетевой переход'.");

        // Проверяем детальную статистику
        assertEquals(1, result.detailed().get("Networks").get("маршрутизатор"));
        assertEquals(1, result.detailed().get("Networks").get("сетевой переход"));
    }

    @Test
    void testNonExistingTopicIgnored() {
        String text = "Это текст про автомобили.";
        var result = analyzerFuzzy.analyzeFull(text, ALL_TOPICS);

        // Проверяем, что для существующих, но не совпадающих тем, счет 0
        assertEquals(0, result.countsByTopic().get("Programming"));
        assertEquals(0, result.countsByTopic().get("Networks"));
    }
}