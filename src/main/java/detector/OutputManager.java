package detector;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Map;

/**
 * Утилитарный класс для управления выводом результатов анализа.
 * <p>
 * Предоставляет статические методы для сохранения тематической статистики
 * в текстовый файл и для генерации гистограммы (диаграммы) в формате PNG.
 * Использует библиотеку JFreeChart для создания графика.
 * </p>
 */
public class OutputManager {

    /**
     * Сохраняет статистику анализа в текстовый файл.
     * Каждая строка содержит пару "Тема : Количество совпадений".
     *
     * @param stats Карта, содержащая результаты анализа ({Тема : Количество совпадений}).
     * @param file Имя файла, в который будет записана статистика (например, "statistics.txt").
     * @throws Exception Если произошла ошибка ввода/вывода при записи в файл.
     */
    public static void saveStatistics(Map<String, Integer> stats, String file) throws Exception {
        try (FileWriter fw = new FileWriter(file)) {
            for (var e : stats.entrySet()) {
                fw.write(e.getKey() + " : " + e.getValue() + "\n");
            }
        }
    }

    /**
     * Генерирует и сохраняет гистограмму распределения тематики в виде PNG-файла.
     * Использует JFreeChart для построения графика.
     *
     * @param stats Карта, содержащая результаты анализа ({Тема : Количество совпадений}).
     * @param file Имя файла, в который будет сохранен график (например, "chart.png").
     * @throws Exception Если произошла ошибка при генерации или сохранении графика.
     */
    public static void saveChart(Map<String, Integer> stats, String file) throws Exception {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (var e : stats.entrySet()) {
            ds.addValue(e.getValue(), e.getKey(), "");
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Распределение тематики",
                "Темы",
                "Количество",
                ds
        );

        // Сохранение графика в файл с размерами 800x600 пикселей
        ChartUtils.saveChartAsPNG(Path.of(file).toFile(), chart, 800, 600);
    }
}