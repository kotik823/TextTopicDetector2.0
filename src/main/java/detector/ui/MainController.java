package detector.ui;

import detector.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.Map;
// ДОБАВЛЕНО: Импорты Log4j
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Контроллер JavaFX для главного окна приложения Topic Detector.
 * <p>
 * Отвечает за обработку пользовательских действий (загрузка файла, анализ),
 * взаимодействие с логикой анализатора ({@link Analyzer}, {@link TopicDetector})
 * и обновление элементов пользовательского интерфейса (текстовое поле, график, метка темы).
 * </p>
 */
public class MainController {

    /** Статический экземпляр логгера для данного класса. */
    private static final Logger logger = LogManager.getLogger(MainController.class);

    @FXML
    private TextArea inputArea;

    @FXML
    private Button loadButton;

    @FXML
    private Button analyzeButton;

    @FXML
    private Button detailsButton;

    @FXML
    private Label detectedTopicLabel;

    @FXML
    private BarChart<String, Number> barChart;

    @FXML
    private CategoryAxis xAxis;

    @FXML
    private NumberAxis yAxis;

    private DictionaryManager dm;
    private Analyzer analyzer;
    private TopicDetector detector;

    /** Хранит результат последнего полного анализа для отображения деталей. */
    private Analyzer.AnalysisResult lastResult;

    /**
     * Вызывается автоматически после загрузки FXML-файла.
     * Инициализирует менеджеры, анализатор и детектор, а также
     * устанавливает начальные настройки UI.
     */
    @FXML
    public void initialize() {
        try {
            dm = new DictionaryManager();
            analyzer = new Analyzer(dm, true);
            detector = new TopicDetector(dm);

            inputArea.setWrapText(true);
            detailsButton.setDisable(true);

            // ЛОГИРОВАНИЕ: успешная инициализация
            logger.info("Контроллер MainController инициализирован. Словари загружены.");

        } catch (Exception e) {
            // ЛОГИРОВАНИЕ: критическая ошибка инициализации
            logger.fatal("Ошибка инициализации словарей!", e);
            showError("Ошибка инициализации словарей: " + e.getMessage());
        }
    }

    /**
     * Обработчик события нажатия кнопки "Загрузить файл".
     * Открывает диалог выбора файла и загружает его текстовое содержимое
     * в {@code inputArea}. Поддерживает форматы TXT, DOC, DOCX.
     */
    @FXML
    private void onLoadFile() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Текстовые файлы (*.txt, *.doc, *.docx)", "*.txt", "*.doc", "*.docx"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );

        File file = fc.showOpenDialog(loadButton.getScene().getWindow());

        if (file == null) {
            // ЛОГИРОВАНИЕ: отмена выбора файла
            logger.info("Пользователь отменил выбор файла.");
            return;
        }

        // ЛОГИРОВАНИЕ: выбран файл
        logger.info("Выбран файл для загрузки: {}", file.getAbsolutePath());

        try {
            String text = TextParser.parse(file.toPath());
            inputArea.setText(text);
            // ЛОГИРОВАНИЕ: файл успешно прочитан
            logger.info("Файл успешно прочитан и загружен в текстовое поле.");
        } catch (Exception e) {
            // ЛОГИРОВАНИЕ: ошибка чтения файла
            logger.error("Ошибка чтения файла: {}", file.getAbsolutePath(), e);
            showError("Ошибка чтения файла: " + e.getMessage());
        }
    }

    /**
     * Обработчик события нажатия кнопки "Анализировать".
     * <p>
     * Выполняет полный анализ текста, обновляет график и метку определенной темы,
     * сохраняет статистику в файлы ("statistics.txt", "chart.png").
     * </p>
     */
    @FXML
    private void onAnalyze() {

        String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            // ЛОГИРОВАНИЕ: попытка анализа пустого текста
            logger.warn("Попытка анализа пустого текста.");
            showError("Введите текст или загрузите файл.");
            return;
        }

        // ЛОГИРОВАНИЕ: начало анализа
        logger.info("Начат анализ текста (длина: {} символов).", text.length());

        try {
            lastResult = analyzer.analyzeFull(text);

            Map<String, Integer> stats = lastResult.countsByTopic();
            updateChart(stats);
            detailsButton.setDisable(false);

            String top = detector.detectTopic(stats);
            detectedTopicLabel.setText("Определена тема: " + top);

            // Сохранение файлов
            OutputManager.saveStatistics(stats, "statistics.txt");
            OutputManager.saveChart(stats, "chart.png");

            // ЛОГИРОВАНИЕ: анализ завершен
            logger.info("Анализ завершен. Определенная тема: {}", top);

        } catch (Exception e) {
            // ЛОГИРОВАНИЕ: ошибка во время анализа
            logger.error("Произошла ошибка во время анализа текста!", e);
            showError("Ошибка анализа: " + e.getMessage());
        }
    }

    /**
     * Обработчик события нажатия кнопки "Подробности".
     * Открывает модальное окно с детальной статистикой совпадений по каждому слову
     * для всех тем.
     */
    @FXML
    private void onShowDetails() {
        if (lastResult == null) return;

        logger.info("Открытие окна подробной статистики."); // ЛОГИРОВАНИЕ

        Stage dialog = new Stage();
        dialog.setTitle("Подробная статистика");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(detailsButton.getScene().getWindow());

        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);

        StringBuilder sb = new StringBuilder();

        lastResult.detailed().forEach((topic, words) -> {
            sb.append("=== ").append(topic).append(" ===\n");
            if (words.isEmpty()) {
                sb.append("  (нет совпадений)\n\n");
            } else {
                for (var e : words.entrySet()) {
                    sb.append("  ").append(e.getKey())
                            .append(" : ").append(e.getValue()).append("\n");
                }
                sb.append("\n");
            }
        });

        area.setText(sb.toString());

        dialog.setScene(new javafx.scene.Scene(area, 600, 500));
        dialog.showAndWait();
    }

    /**
     * Обновляет гистограмму на основе предоставленной статистики.
     *
     * @param stats Карта с общим количеством совпадений по темам.
     */
    private void updateChart(Map<String, Integer> stats) {
        barChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Темы");

        for (var e : stats.entrySet()) {
            series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
        }

        barChart.getData().add(series);
    }

    /**
     * Отображает модальное окно с сообщением об ошибке в UI-потоке JavaFX.
     *
     * @param msg Сообщение об ошибке.
     */
    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }
}