package detector.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Главный класс приложения JavaFX, отвечающий за инициализацию пользовательского
 * интерфейса и запуск основного окна.
 * <p>
 * Класс наследуется от {@code javafx.application.Application} и содержит
 * логику для загрузки FXML-файла, настройки сцены и управления жизненным циклом приложения.
 * Использует Log4j2 для ведения журнала событий.
 * </p>
 */
public class MainFX extends Application {

    /** Логгер для данного класса, используемый для записи событий приложения. */
    private static final Logger logger = LogManager.getLogger(MainFX.class);

    /**
     * Основной метод, вызываемый фреймворком JavaFX после инициализации.
     * Отвечает за настройку и отображение главного окна приложения.
     *
     * @param stage Основной контейнер (окно) для пользовательского интерфейса.
     */
    @Override
    public void start(Stage stage) {
        logger.info("Запуск приложения Topic Detector.");

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/ui/main.fxml")
            );

            Scene scene = new Scene(loader.load());

            stage.setTitle("Topic Detector — Анализатор тем текста");
            stage.setScene(scene);

            // Иконка окна (если есть icon.png)
            try {
                stage.getIcons().add(
                        new Image(getClass().getResourceAsStream("/icon.png"))
                );
                logger.debug("Иконка приложения загружена.");
            } catch (Exception ignored) {
                logger.debug("Иконка приложения не найдена или не загружена.");
            }

            stage.setMinWidth(800);
            stage.setMinHeight(600);

            stage.show();
            logger.info("Главное окно успешно отображено.");

        } catch (Exception e) {
            // ИСПОЛЬЗУЕМ ЛОГГЕР ВМЕСТО System.err
            logger.fatal("Критическая ошибка запуска интерфейса!", e);
        }
    }

    /**
     * Вызывается фреймворком JavaFX, когда приложение вот-вот закроется.
     * Используется для записи события закрытия в журнал.
     *
     * @throws Exception Если возникает ошибка при выполнении метода {@code super.stop()}.
     */
    @Override
    public void stop() throws Exception {
        logger.info("Приложение Topic Detector завершает работу.");
        super.stop();
    }

    /**
     * Стандартная точка входа для запуска Java-приложения.
     * Передает управление фреймворку JavaFX для запуска UI.
     *
     * @param args Аргументы командной строки.
     */
    public static void main(String[] args) {
        launch(args);
    }
}