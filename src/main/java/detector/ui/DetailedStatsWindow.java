package detector.ui;

import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Утилитарный класс, предназначенный для создания и отображения
 * отдельного окна с подробной статистикой анализа текста.
 * <p>
 * Отображает слова-совпадения и их частоту, сгруппированные по темам.
 * Это простое модальное окно на базе JavaFX.
 * </p>
 *
 * @version 1.1 (Добавлено русскоязычное логирование)
 */
public class DetailedStatsWindow {

    /** Логгер для класса DetailedStatsWindow. */
    private static final Logger logger = LogManager.getLogger(DetailedStatsWindow.class);

    /**
     * Отображает окно с детальной статистикой.
     * <p>
     * Форматирует и выводит данные из вложенной карты, где внешний ключ — тема,
     * а внутренняя карта содержит пары "слово : частота".
     * </p>
     *
     * @param data Карта с подробными результатами анализа.
     * Ключ: Название темы (String).
     * Значение: Карта {Слово (String) : Частота совпадений (Integer)}.
     */
    public static void show(Map<String, Map<String, Integer>> data) {
        logger.info("Начато создание и отображение окна подробной статистики.");

        if (data.isEmpty() || data.values().stream().allMatch(Map::isEmpty)) {
            logger.warn("Передана пустая детальная статистика. Окно не будет отображено.");
            return;
        }

        Stage stage = new Stage();
        stage.setTitle("Подробная статистика");

        TextArea area = new TextArea();
        area.setEditable(false);

        StringBuilder sb = new StringBuilder();

        // Форматирование данных для отображения
        data.forEach((topic, words) -> {
            // Исключаем темы, в которых не было найдено совпадений
            if (!words.isEmpty()) {
                sb.append("=== ").append(topic.toUpperCase()).append(" (Всего: ").append(
                        words.values().stream().mapToInt(Integer::intValue).sum()
                ).append(") ===\n");

                // Сортируем слова по частоте в убывающем порядке для лучшей читаемости
                words.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(entry ->
                                sb.append("   - ").append(entry.getKey()).append(" : ").append(entry.getValue()).append("\n")
                        );

                sb.append("\n"); // Разделитель между темами
                logger.debug("Сформирован раздел статистики для темы: {}", topic);
            }
        });

        area.setText(sb.toString().trim());

        Scene scene = new Scene(area, 600, 450);
        stage.setScene(scene);
        stage.show();

        logger.info("Окно подробной статистики успешно отображено.");
    }
}