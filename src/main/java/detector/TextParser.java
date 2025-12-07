package detector;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Утилитарный класс для парсинга и извлечения текстового содержимого
 * из различных форматов файлов, включая TXT, DOC и DOCX.
 * <p>
 * Предоставляет логику для обработки различных кодировок (UTF-8, CP1251)
 * при чтении текстовых файлов.
 * </p>
 * @version 1.1 (Добавлено русскоязычное логирование)
 */
public class TextParser {

    /** Логгер для класса TextParser. */
    private static final Logger logger = LogManager.getLogger(TextParser.class);

    /**
     * Основной метод парсинга. Определяет формат файла по его расширению
     * и вызывает соответствующий метод для извлечения текста.
     *
     * @param path Объект {@code Path}, указывающий на файл для парсинга.
     * @return Текстовое содержимое файла в виде одной строки.
     * @throws Exception Если формат файла не поддерживается или произошла
     * ошибка ввода/вывода при чтении/парсинге файла.
     */
    public static String parse(Path path) throws Exception {
        String file = path.toString();
        logger.info("Начат парсинг файла: {}", file);

        if (file.endsWith(".txt")) {
            return parseTxt(path);
        } else if (file.endsWith(".doc")) {
            return parseDoc(path);
        } else if (file.endsWith(".docx")) {
            return parseDocx(path);
        } else {
            logger.error("Неподдерживаемый формат файла: {}", file);
            throw new IllegalArgumentException("Неподдерживаемый формат файла: " + file);
        }
    }

    /**
     * Парсит содержимое файла формата **TXT**.
     */
    private static String parseTxt(Path path) throws IOException {
        logger.debug("Используется парсер TXT для файла: {}", path.getFileName());

        // 1. Считывание байтов
        byte[] bytes = Files.readAllBytes(path);

        // 2. Декодирование, сначала UTF-8
        String text = new String(bytes, StandardCharsets.UTF_8);

        // 3. Проверка на "кракозябры" и откат к CP1251
        if (looksLikeGarbledRussian(text)) {
            logger.warn("Обнаружена некорректная кодировка (возможно, CP1251) в TXT-файле. Попытка отката.");
            text = new String(bytes, Charset.forName("windows-1251"));
        }

        // 4. Удаление BOM
        String finalContent = removeBom(text);
        logger.info("TXT-файл успешно прочитан. Длина текста: {} символов.", finalContent.length());
        return finalContent;
    }

    /**
     * Удаляет маркер последовательности байтов (BOM), если он присутствует.
     */
    private static String removeBom(String s) {
        if (s.startsWith("\uFEFF")) {
            logger.debug("Удален маркер BOM (Byte Order Mark).");
            return s.substring(1);
        }
        return s;
    }

    /**
     * Эвристическая проверка, указывает ли строка на некорректно декодированный русский текст.
     */
    private static boolean looksLikeGarbledRussian(String s) {
        if (s == null || s.isEmpty()) return false;
        int total = s.length();
        int letters = 0;
        int repl = 0;

        for (char c : s.toCharArray()) {
            // Символ замены Юникода () часто появляется при неправильной кодировке
            if (c == '\uFFFD') repl++;
            if (Character.isLetter(c)) letters++;
        }

        // Если есть много символов замены ИЛИ слишком мало букв относительно общего размера
        return repl > 0 || ((double)letters / Math.max(1, total) < 0.3);
    }

    /**
     * Парсит содержимое файла формата **Microsoft Word (.doc)**, используя Apache POI.
     */
    private static String parseDoc(Path path) throws Exception {
        logger.debug("Используется парсер DOC (Apache POI HWPF) для файла: {}", path.getFileName());
        try (HWPFDocument doc = new HWPFDocument(new FileInputStream(path.toFile()));
             WordExtractor extractor = new WordExtractor(doc)) {
            StringBuilder sb = new StringBuilder();
            for (String p : extractor.getParagraphText()) {
                sb.append(p).append("\n");
            }
            String result = removeBom(sb.toString());
            logger.info("DOC-файл успешно прочитан. Длина текста: {} символов.", result.length());
            return result;
        } catch (Exception e) {
            logger.error("Ошибка при парсинге DOC-файла '{}': {}", path.getFileName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Парсит содержимое файла формата **Microsoft Word Open XML (.docx)**,
     * используя Apache POI.
     */
    private static String parseDocx(Path path) throws Exception {
        logger.debug("Используется парсер DOCX (Apache POI XWPF) для файла: {}", path.getFileName());
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(path.toFile()))) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));

            String result = removeBom(sb.toString());
            logger.info("DOCX-файл успешно прочитан. Длина текста: {} символов.", result.length());
            return result;
        } catch (Exception e) {
            logger.error("Ошибка при парсинге DOCX-файла '{}': {}", path.getFileName(), e.getMessage(), e);
            throw e;
        }
    }
}