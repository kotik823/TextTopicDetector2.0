package detector.ui;

import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.util.Map;

/**
 * Утилитарный класс, предназначенный для создания и отображения
 * отдельного окна с подробной статистикой анализа текста.
 * <p>
 * Отображает слова-совпадения и их частоту, сгруппированные по темам.
 * Это простое модальное окно на базе JavaFX.
 * </p>
 */
public class DetailedStatsWindow {

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
        Stage stage = new Stage();
        stage.setTitle("Подробная статистика");

        TextArea area = new TextArea();
        area.setEditable(false);

        StringBuilder sb = new StringBuilder();

        data.forEach((topic, words) -> {
            sb.append("=== ").append(topic).append(" ===\n");
            words.forEach((w, c) ->
                    sb.append("   ").append(w)
                            .append(" — ").append(c)
                            .append("\n"));
            sb.append("\n");
        });

        area.setText(sb.toString());

        stage.setScene(new Scene(area, 600, 500));
        stage.show();
    }
}