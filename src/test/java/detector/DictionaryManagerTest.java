package detector;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors; // Добавлен для удобства
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для проверки корректности загрузки словарей в DictionaryManager.
 * Проверяет, что темы загружены, и их содержимое (слова/фразы) доступно и правильно отформатировано.
 */
public class DictionaryManagerTest {

    @Test
    void testDictionaryLoadingAndContent() throws IOException {
        // Инициализация менеджера (запускает загрузку ресурсов)
        DictionaryManager dm = new DictionaryManager();

        // 1. Проверка, что все ожидаемые темы загружены
        Set<String> topics = dm.topics();

        // Список тем, которые должны быть в ресурсах (из DictionaryManager.java)
        List<String> expectedTopics = List.of(
                "medical",
                "history",
                "programming",
                "networks",
                "cryptography",
                "finance"
        );

        // Проверяем, что количество загруженных тем соответствует ожидаемому
        assertEquals(expectedTopics.size(), topics.size(),
                "Должно быть загружено точное количество словарей (тем).");

        // Проверяем, что все ожидаемые имена тем присутствуют
        assertTrue(topics.containsAll(expectedTopics),
                "Все ожидаемые имена тем должны быть в списке.");

        // 2. Проверка содержимого для конкретной темы ("networks")
        String testTopic = "networks";

        // ИСПРАВЛЕНИЕ: Используем новый метод getActiveDictionaryMap,
        // который возвращает Map<слово, тема> для заданных тем.
        Map<String, String> wordToTopicMap = dm.getActiveDictionaryMap(List.of(testTopic));

        // Получаем набор слов (ключей) для проверки содержимого
        Set<String> words = wordToTopicMap.keySet();

        assertNotNull(words, "Набор слов для 'networks' не должен быть null.");
        // Предполагаем, что 50+ слов — это признак значительного количества
        assertTrue(words.size() > 50, "Должно быть загружено значительное количество слов.");

        // 3. Проверка форматирования и ключевых слов

        // Проверяем, что слова корректно преобразованы в нижний регистр
        assertTrue(words.stream().allMatch(s -> s.equals(s.toLowerCase())),
                "Все слова должны быть в нижнем регистре.");

        // Проверяем наличие ключевых, однословных и многословных фраз
        assertTrue(words.contains("сеть"), "Должно содержать базовое слово 'сеть'.");
        assertTrue(words.contains("маршрутизатор"), "Должно содержать слово 'маршрутизатор'.");
        assertTrue(words.contains("сетевой переход"), "Должно содержать многословную фразу 'сетевой переход'.");
    }

    @Test
    void testWordsForNonExistingTopic() throws IOException {
        DictionaryManager dm = new DictionaryManager();

        // Запрашиваем слова для несуществующей темы
        // ИСПРАВЛЕНИЕ: Используем новый метод getActiveDictionaryMap.
        Map<String, String> wordToTopicMap = dm.getActiveDictionaryMap(List.of("non_existent_topic"));

        // Ожидается, что будет возвращен пустой Map
        assertNotNull(wordToTopicMap, "Должен возвращаться пустой Map, а не null.");
        assertTrue(wordToTopicMap.isEmpty(), "Для несуществующей темы должен возвращаться пустой Map.");
    }
}