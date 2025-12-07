package detector;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;

/**
 * Утилитарный класс для управления выводом результатов анализа.
 * <p>
 * Предоставляет статические методы для сохранения тематической статистики
 * в текстовый файл и для генерации гистограммы (диаграммы) в формате PNG.
 * Использует библиотеку JFreeChart для создания графика.
 * </p>
 *
 * @version 1.1 (Добавлено русскоязычное логирование)
 */
public class OutputManager {

    /** Логгер для класса OutputManager. */
    private static final Logger logger = LogManager.getLogger(OutputManager.class);

    /**
     * Сохраняет статистику анализа в текстовый файл.
     * Каждая строка содержит пару "Тема : Количество совпадений".
     *
     * @param stats Карта, содержащая результаты анализа ({Тема : Количество совпадений}).
     * @param file Имя файла, в который будет записана статистика (например, "statistics.txt").
     * @throws Exception Если произошла ошибка ввода/вывода при записи в файл.
     */
    public static void saveStatistics(Map<String, Integer> stats, String file) throws Exception {
        logger.info("Начало сохранения текстовой статистики в файл: {}", file);
        try (FileWriter fw = new FileWriter(file)) {
            for (var e : stats.entrySet()) {
                fw.write(e.getKey() + " : " + e.getValue() + "\n");
            }
            logger.info("Текстовая статистика успешно сохранена в файл: {}", file);
        } catch (Exception e) {
            logger.error("Ошибка при сохранении текстовой статистики в файл '{}': {}", file, e.getMessage(), e);
            throw e;
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
        logger.info("Начало генерации и сохранения графика в файл: {}", file);
        try {
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

            // Сохранение графика в файл с размерами 800x600
            File chartFile = new File(file);
            ChartUtils.saveChartAsPNG(chartFile, chart, 800, 600);

            logger.info("График успешно сохранен в формате PNG: {}", file);
        } catch (Exception e) {
            logger.error("Ошибка при генерации или сохранении графика в файл '{}': {}", file, e.getMessage(), e);
            throw e;
        }
    }
}