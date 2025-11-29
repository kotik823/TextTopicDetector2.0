package detector.ui;

import detector.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// ДОБАВЛЕНО: Импорты Log4j
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Контроллер JavaFX для главного окна приложения Topic Detector.
 * <p>
 * Отвечает за обработку пользовательских действий (загрузка файла, анализ, выбор словарей),
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
    private Button loadButton; // Кнопка загрузки ТЕКСТА (сохранено из оригинального FXML)

    // --- НОВЫЕ ПОЛЯ FXML для управления словарями и режимом анализа ---
    @FXML
    private Button loadDictButton; // Кнопка загрузки СЛОВАРЯ

    @FXML
    private ListView<String> topicListView; // Список для выбора активных тем

    @FXML
    private CheckBox fuzzyCheckBox; // Чекбокс для режима Fuzzy
    // ------------------------------------------------------------------

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

            // Инициализация анализатора на основе начального состояния чекбокса
            boolean initialFuzzy = fuzzyCheckBox != null && fuzzyCheckBox.isSelected();
            analyzer = new Analyzer(dm, initialFuzzy);
            detector = new TopicDetector(dm);

            inputArea.setWrapText(true);
            detailsButton.setDisable(true);

            // Настройка списка тем (topicListView)
            if (topicListView != null) {
                // Позволяет выбирать несколько тем с помощью Ctrl/Shift
                topicListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                updateTopicList(); // Загрузка и отображение доступных тем
            }

            // Добавление слушателя для обновления анализатора при изменении fuzzy
            if (fuzzyCheckBox != null) {
                fuzzyCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    this.analyzer = new Analyzer(dm, newVal);
                    logger.info("Режим Fuzzy изменен: {}", newVal ? "Включен" : "Выключен");
                });
            }

            // ЛОГИРОВАНИЕ: успешная инициализация
            logger.info("Контроллер MainController инициализирован. Словари загружены.");

        } catch (Exception e) {
            // ЛОГИРОВАНИЕ: критическая ошибка инициализации
            logger.fatal("Ошибка инициализации словарей!", e);
            showError("Ошибка инициализации словарей: " + e.getMessage());
        }
    }

    /**
     * Обновляет ListView, отображая все доступные темы из DictionaryManager.
     * Все элементы по умолчанию выбираются для удобства пользователя.
     */
    private void updateTopicList() {
        if (topicListView == null) return;

        // Получаем названия всех тем, сортируем их и преобразуем в ObservableList
        List<String> topics = dm.topics().stream()
                .sorted()
                .collect(Collectors.toList());

        topicListView.setItems(FXCollections.observableArrayList(topics));

        // Автоматически выбираем все темы
        topicListView.getSelectionModel().selectAll();
        logger.info("Список тем обновлен. Доступно тем: {}", topics.size());
    }

    /**
     * Обработчик события нажатия кнопки "Загрузить словарь" (привязана к loadDictButton).
     * Открывает диалог выбора файла и загружает новый словарь (.txt) в менеджер.
     */
    @FXML
    private void handleLoadDictionary() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите файл словаря (.txt)");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Текстовые файлы словарей (*.txt)", "*.txt")
        );

        // Если loadDictButton не загружен, используем другой элемент
        Stage stage = loadDictButton != null ? (Stage) loadDictButton.getScene().getWindow() : null;

        File file = fc.showOpenDialog(stage);

        if (file == null) {
            logger.info("Пользователь отменил выбор файла словаря.");
            return;
        }

        logger.info("Выбран файл для загрузки словаря: {}", file.getAbsolutePath());

        try {
            // Загрузка словаря и получение его имени
            String topicName = dm.loadDictionaryFromFile(file);
            updateTopicList(); // Обновляем список тем в UI
            logger.info("Словарь '{}' успешно загружен.", topicName);
            showInfo("Успех", "Словарь '" + topicName + "' успешно загружен и добавлен в список активных тем!");
        } catch (IOException e) {
            logger.error("Ошибка чтения файла словаря: {}", file.getAbsolutePath(), e);
            showError("Ошибка чтения файла словаря: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Неизвестная ошибка при загрузке словаря: {}", file.getAbsolutePath(), e);
            showError("Неизвестная ошибка при загрузке словаря: " + e.getMessage());
        }
    }

    /**
     * Обработчик события нажатия кнопки "Загрузить файл" (текст, привязана к loadButton).
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

        // Используем старую кнопку для привязки к окну
        Stage stage = loadButton != null ? (Stage) loadButton.getScene().getWindow() : null;
        File file = fc.showOpenDialog(stage);

        if (file == null) {
            logger.info("Пользователь отменил выбор файла.");
            return;
        }

        logger.info("Выбран файл для загрузки текста: {}", file.getAbsolutePath());

        try {
            String text = TextParser.parse(file.toPath());
            inputArea.setText(text);
            logger.info("Файл успешно прочитан и загружен в текстовое поле.");
        } catch (Exception e) {
            logger.error("Ошибка чтения файла: {}", file.getAbsolutePath(), e);
            showError("Ошибка чтения файла: " + e.getMessage());
        }
    }

    /**
     * Обработчик события нажатия кнопки "Анализировать" (привязана к analyzeButton).
     * <p>
     * Выполняет полный анализ текста с учетом выбранных тем и режима Fuzzy,
     * обновляет график и метку определенной темы,
     * сохраняет статистику в файлы ("statistics.txt", "chart.png").
     * </p>
     */
    @FXML
    private void handleAnalyze() {

        String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            logger.warn("Попытка анализа пустого текста.");
            showError("Введите текст или загрузите файл.");
            return;
        }

        // 1. Получение списка активных тем
        List<String> activeTopics = topicListView.getSelectionModel().getSelectedItems();
        if (activeTopics.isEmpty()) {
            logger.warn("Не выбраны активные темы для анализа.");
            showError("Выберите хотя бы одну тему для анализа.");
            return;
        }

        // Обновление анализатора с текущим режимом fuzzy
        boolean isFuzzy = fuzzyCheckBox != null && fuzzyCheckBox.isSelected();
        // Переинициализация анализатора гарантирует, что он использует актуальное состояние fuzzy
        this.analyzer = new Analyzer(dm, isFuzzy);


        logger.info("Начат анализ текста (длина: {} символов). Активных тем: {}. Fuzzy: {}",
                text.length(), activeTopics.size(), isFuzzy);

        try {
            // 2. Вызов обновленной версии analyzeFull с передачей активных тем
            // Требует, чтобы Analyzer.java был обновлен!
            lastResult = analyzer.analyzeFull(text, activeTopics);

            Map<String, Integer> stats = lastResult.countsByTopic();
            updateChart(stats);
            detailsButton.setDisable(false);

            String top = detector.detectTopic(stats);
            detectedTopicLabel.setText("Определена тема: " + top);

            // Сохранение файлов
            OutputManager.saveStatistics(stats, "statistics.txt");
            OutputManager.saveChart(stats, "chart.png");

            logger.info("Анализ завершен. Определенная тема: {}", top);

        } catch (Exception e) {
            logger.error("Произошла ошибка во время анализа текста!", e);
            showError("Ошибка анализа: " + e.getMessage());
        }
    }

    /**
     * Обработчик события нажатия кнопки "Подробности".
     * Открывает модальное окно с детальной статистикой совпадений по каждому слову
     * для всех тем, которые участвовали в анализе.
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
        // Используем моноширинный шрифт для лучшего выравнивания
        area.setFont(javafx.scene.text.Font.font("Monospaced", 12));

        StringBuilder sb = new StringBuilder();

        lastResult.detailed().forEach((topic, words) -> {
            sb.append("=== ").append(topic.toUpperCase()).append(" ===\n");
            if (words.isEmpty()) {
                sb.append("  (нет совпадений)\n\n");
            } else {
                for (var e : words.entrySet()) {
                    // Форматирование для выравнивания
                    sb.append(String.format("  %-30s : %d\n", e.getKey(), e.getValue()));
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

        // Корректировка оси Y, если все значения нулевые, чтобы график был виден
        if (stats.values().stream().allMatch(v -> v == 0)) {
            yAxis.setAutoRanging(false);
            yAxis.setUpperBound(1);
            yAxis.setTickUnit(0.1);
        } else {
            yAxis.setAutoRanging(true);
        }


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

    /**
     * Отображает модальное окно с информационным сообщением в UI-потоке JavaFX.
     *
     * @param title Заголовок окна.
     * @param msg Сообщение.
     */
    private void showInfo(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }
}