package detector;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

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
 */
public class TextParser {

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
        String file = path.toString().toLowerCase();

        if (file.endsWith(".txt")) {
            return readTxtWithEncodingFallback(path);
        }

        if (file.endsWith(".doc")) {
            return parseDoc(path);
        }

        if (file.endsWith(".docx")) {
            return parseDocx(path);
        }

        throw new IOException("Неподдерживаемый формат файла: " + file);
    }

    /**
     * Читает текстовый файл, используя резервную кодировку. Сначала пытается
     * декодировать как **UTF-8**, затем, если обнаружены "кракозябры",
     * пробует **windows-1251 (CP1251)**.
     *
     * @param path Объект {@code Path} к файлу TXT.
     * @return Содержимое файла в виде строки с корректной кодировкой.
     * @throws IOException Если произошла ошибка ввода/вывода.
     */
    private static String readTxtWithEncodingFallback(Path path) throws IOException {
        // Сначала пробуем UTF-8
        String s = Files.readString(path, StandardCharsets.UTF_8);
        s = removeBom(s);
        if (!looksLikeGarbledRussian(s)) return s;

        // Если похоже на кракозябры, пробуем CP1251
        try {
            s = Files.readString(path, Charset.forName("windows-1251"));
            s = removeBom(s);
            if (!looksLikeGarbledRussian(s)) return s;
        } catch (Exception ignored) {}

        // как последний вариант — возвращаем то, что есть (UTF-8), даже если кракозябры
        return removeBom(Files.readString(path, StandardCharsets.UTF_8));
    }

    /**
     * Удаляет маркер последовательности байтов (BOM - Byte Order Mark, \uFEFF),
     * который иногда присутствует в начале UTF-8 файлов.
     *
     * @param s Входная строка.
     * @return Строка без BOM, если он присутствовал.
     */
    private static String removeBom(String s) {
        if (s != null && s.length() > 0 && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    /**
     * Простая эвристика для определения, является ли строка, вероятно,
     * некорректно декодированным русским текстом ("кракозябрами").
     * Проверяет наличие символов замены ('\uFFFD') и отношение количества
     * букв к общей длине текста.
     *
     * @param s Строка для проверки.
     * @return {@code true}, если строка, вероятно, содержит ошибки кодировки.
     */
    private static boolean looksLikeGarbledRussian(String s) {
        if (s == null || s.isEmpty()) return true;
        int total = s.length();
        int repl = 0;
        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\uFFFD') repl++;
            if (Character.isLetter(c)) letters++;
        }
        // если есть маркеры замены или мало букв относительно длины
        return repl > 0 || ((double)letters / Math.max(1, total) < 0.3);
    }

    /**
     * Парсит содержимое файла формата **Microsoft Word (.doc)**, используя Apache POI.
     *
     * @param path Объект {@code Path} к файлу DOC.
     * @return Текстовое содержимое документа.
     * @throws Exception Если произошла ошибка ввода/вывода или парсинга документа.
     */
    private static String parseDoc(Path path) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(new FileInputStream(path.toFile()));
             WordExtractor extractor = new WordExtractor(doc)) {
            StringBuilder sb = new StringBuilder();
            for (String p : extractor.getParagraphText()) {
                sb.append(p).append("\n");
            }
            return removeBom(sb.toString());
        }
    }

    /**
     * Парсит содержимое файла формата **Microsoft Word Open XML (.docx)**,
     * используя Apache POI.
     *
     * @param path Объект {@code Path} к файлу DOCX.
     * @return Текстовое содержимое документа.
     * @throws Exception Если произошла ошибка ввода/вывода или парсинга документа.
     */
    private static String parseDocx(Path path) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(path.toFile()))) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            return removeBom(sb.toString());
        }
    }
}