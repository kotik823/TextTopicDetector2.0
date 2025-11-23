package detector;

import java.nio.file.Path;

/**
 * Главный класс приложения для консольного запуска.
 * <p>
 * Принимает путь к текстовому файлу в качестве аргумента,
 * анализирует его на предмет тематической принадлежности с
 * использованием словарей и сохраняет результаты анализа
 * в виде текстового отчета и диаграммы.
 * </p>
 */
public class App {

    /**
     * Основная точка входа в консольное приложение.
     * Осуществляет чтение, анализ и вывод результатов.
     *
     * @param args Аргументы командной строки. Ожидается один аргумент:
     * путь к анализируемому файлу (TXT, DOC или DOCX).
     * @throws Exception если произошла ошибка ввода/вывода, парсинга файла
     * или ошибка при сохранении выходных файлов.
     */
    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("Использование: java -jar app.jar <путь_к_файлу>");
            return;
        }

        String filePath = args[0];

        // Загружаем словари из ресурсов
        DictionaryManager dm = new DictionaryManager();

        // Анализатор с fuzzy-режимом
        Analyzer analyzer = new Analyzer(dm, true);

        // Парсим текст
        String text = TextParser.parse(Path.of(filePath));

        // Анализируем
        var stats = analyzer.analyze(text);

        // Вывод
        System.out.println("=== Результаты анализа ===");
        stats.forEach((topic, count) ->
                System.out.println(topic + ": " + count));

        // График + статистика
        OutputManager.saveStatistics(stats, "statistics.txt");
        OutputManager.saveChart(stats, "chart.png");

        System.out.println("\nГотово! Файлы: statistics.txt и chart.png");
    }
}