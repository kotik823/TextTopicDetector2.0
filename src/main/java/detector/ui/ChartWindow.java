package detector.ui;

import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Утилитарный класс, предназначенный для создания и отображения
 * отдельного окна с гистограммой (BarChart) распределения тематики.
 * <p>
 * Отображает графическое представление общего количества совпадений,
 * сгруппированных по темам.
 * </p>
 * @version 1.1 (Добавлено русскоязычное логирование)
 */
public class ChartWindow {

    /** Логгер для класса ChartWindow. */
    private static final Logger logger = LogManager.getLogger(ChartWindow.class);

    /**
     * Создает и отображает новое окно с гистограммой.
     * <p>
     * График строится на основе предоставленной статистики, где оси X — темы,
     * а ось Y — количество совпадений.
     * </p>
     *
     * @param stats Карта, содержащая результаты анализа ({Тема : Количество совпадений}).
     */
    public static void showChart(Map<String, Integer> stats) {
        logger.info("Начато создание и отображение окна графика.");

        if (stats.isEmpty()) {
            logger.warn("Передана пустая статистика. Окно графика не будет отображено.");
            return;
        }

        Stage stage = new Stage();
        stage.setTitle("График распределения тематики");
        // Устанавливаем Modality.NONE, чтобы окно не блокировало другие окна приложения
        stage.initModality(Modality.NONE);

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Темы");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Количество");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Распределение тем в тексте");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Частоты");

        logger.debug("Статистика для графика содержит {} тем(ы).", stats.size());

        // Заполнение серии данных из карты статистики
        for (var e : stats.entrySet()) {
            series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
        }

        chart.getData().add(series);
        chart.setLegendVisible(false); // Скрываем легенду, так как серия только одна

        Scene scene = new Scene(chart, 800, 600);
        stage.setScene(scene);
        stage.show();

        logger.info("Окно графика успешно отображено.");
    }
}