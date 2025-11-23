package detector.ui;

import javafx.beans.property.*;

/**
 * Класс-модель для представления одной строки в таблице статистики
 * (например, в пользовательском интерфейсе JavaFX).
 * <p>
 * Использует JavaFX {@code Property} для поддержки автоматического
 * обновления пользовательского интерфейса при изменении данных.
 * </p>
 */
public class StatRow {

    /** Свойство для хранения слова или названия элемента. */
    private final StringProperty word;

    /** Свойство для хранения частоты или количества совпадений. */
    private final IntegerProperty count;

    /**
     * Создает новый экземпляр строки статистики.
     *
     * @param word Слово или элемент, который нужно отобразить.
     * @param count Частота или количество совпадений для этого слова.
     */
    public StatRow(String word, int count) {
        this.word = new SimpleStringProperty(word);
        this.count = new SimpleIntegerProperty(count);
    }

    /**
     * Возвращает значение слова/элемента.
     *
     * @return Слово в виде строки.
     */
    public String getWord() { return word.get(); }

    /**
     * Возвращает свойство слова/элемента (для привязки к UI-компонентам).
     *
     * @return {@code StringProperty} слова.
     */
    public StringProperty wordProperty() { return word; }

    /**
     * Возвращает значение частоты/количества.
     *
     * @return Количество в виде целого числа.
     */
    public int getCount() { return count.get(); }

    /**
     * Возвращает свойство частоты/количества (для привязки к UI-компонентам).
     *
     * @return {@code IntegerProperty} количества.
     */
    public IntegerProperty countProperty() { return count; }
}