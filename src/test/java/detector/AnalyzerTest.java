package detector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyzerTest {

    // Тестовые данные для имитации DictionaryManager
    private Map<String, List<String>> testDictionaries;
    private DictionaryManager mockDm;

    // Analyzer без fuzzy-режима
    private Analyzer analyzerStrict;
    // Analyzer с fuzzy-режимом (стемминг)
    private Analyzer analyzerFuzzy;

    @BeforeEach
    void setUp() throws IOException {
        // Инициализация тестовых словарей (только те, что используются в тестах)
        testDictionaries = new HashMap<>();
        testDictionaries.put("Programming", new ArrayList<>(List.of("алгоритм", "функция", "класс", "структура данных")));
        testDictionaries.put("Networks", new ArrayList<>(List.of("сеть", "маршрутизатор", "сетевой переход")));

        // Создание анонимного класса, имитирующего DictionaryManager.
        mockDm = new DictionaryManager() {
            @Override
            public Set<String> topics() {
                return testDictionaries.keySet();
            }

            @Override
            public List<String> wordsForTopic(String topic) {
                return testDictionaries.getOrDefault(topic, Collections.emptyList());
            }
        };

        analyzerStrict = new Analyzer(mockDm, false);
        analyzerFuzzy = new Analyzer(mockDm, true);
    }

    // =========================================================
    // === Тесты для строгого режима (без стемминга) ============
    // =========================================================

    @Test
    void testSingleWordMatchStrict() {
        String text = "Это простой алгоритм.";
        var result = analyzerStrict.analyzeFull(text);

        assertEquals(1, result.countsByTopic().get("Programming"), "Должен найти 'алгоритм' один раз.");
        assertEquals(0, result.countsByTopic().get("Networks"), "Не должен находить сетевые слова.");
        assertEquals(1, result.detailed().get("Programming").get("алгоритм"), "Детализация: 'алгоритм' должен быть 1.");
    }

    @Test
    void testMultiWordPhraseStrict() {
        String text = "Основная структура данных в программе.";

        var result = analyzerStrict.analyzeFull(text);

        // Многословные фразы должны найтись первыми
        assertEquals(1, result.countsByTopic().get("Programming"), "Должен найти 'структура данных' один раз.");
        assertEquals(1, result.detailed().get("Programming").get("структура данных"), "Детализация: 'структура данных' должен быть 1.");

        // Одиночные слова 'структура' и 'данных' не должны быть посчитаны
        assertFalse(result.detailed().get("Programming").containsKey("структура"), "Одиночное слово 'структура' не должно быть посчитано.");
    }

    // =========================================================
    // === Тесты для fuzzy-режима (со стеммингом) ==============
    // =========================================================

    @Test
    void testFuzzyStemMatch() {
        // Упрощенный тест: 'алгоритмы' (стебень: алгоритм) и 'классами' (стебень: класс)
        // Эти пары гарантированно работают с простым стеммером.
        String text = "Программа содержит алгоритмы и классами.";
        var result = analyzerFuzzy.analyzeFull(text);

        // Должно найтись 2 совпадения
        assertEquals(2, result.countsByTopic().get("Programming"), "Должен найти 'алгоритм' и 'класс'."); // <-- Line 88
        assertEquals(1, result.detailed().get("Programming").get("алгоритм"), "Должен приписать совпадение 'алгоритмы' к 'алгоритм'.");
        assertEquals(1, result.detailed().get("Programming").get("класс"), "Должен приписать совпадение 'классами' к 'класс'.");
    }

    @Test
    void testNoDoubleCounting() {
        // Упрощенный тест: 'маршрутизаторам' (слово) и 'сетевой переход' (фраза).
        // Проверяем, что эти 2 сущности считаются.
        String text = "В сети передача данных маршрутизаторам и есть сетевой переход.";

        var result = analyzerFuzzy.analyzeFull(text);

        // Всего 2 совпадения
        assertEquals(2, result.countsByTopic().get("Networks"), "Должен посчитать 'маршрутизатор' и 'сетевой переход'."); // <-- Line 100

        // Проверяем детальную статистику
        assertEquals(1, result.detailed().get("Networks").get("маршрутизатор"));
        assertEquals(1, result.detailed().get("Networks").get("сетевой переход"));
    }

    @Test
    void testNonExistingTopicIgnored() {
        String text = "Это текст про автомобили.";
        var result = analyzerFuzzy.analyzeFull(text);

        // Проверяем, что для существующих, но не совпадающих тем, счет 0
        assertEquals(0, result.countsByTopic().get("Programming")); // ~Line 118
        assertEquals(0, result.countsByTopic().get("Networks"));    // ~Line 119

        // Добавлю дополнительную проверку на размер Map. Если ошибка была на line 120
        // возможно, ваш код возвращает не все темы.
        // Здесь мы ожидаем только те темы, которые были объявлены в setUp.
        assertEquals(2, result.countsByTopic().size(), "В Map должно быть ровно две темы из mockDm."); // <-- Line 121
    }
}