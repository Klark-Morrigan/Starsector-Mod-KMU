package kmu.desktop.ui;

import java.awt.Color;
import java.util.List;

/**
 * How one kind of value is written down in a {@link SavedValues} file, and read back.
 *
 * <p>Named once per kind, so a control and anything reading its value without the control read
 * it by one rule. A colour stored as packed ARGB by one and read as RGB by the other is a
 * remembered value that silently loses its alpha on the way back.
 *
 * @param <T> the kind of value
 */
public final class SavedValueFormat<T> {

    /** A flag, as {@code true} or {@code false}. */
    public static final SavedValueFormat<Boolean> FLAG =
        new SavedValueFormat<>(SavedValues::getBoolean, SavedValues::putBoolean);

    /** A measurement, as a decimal number. */
    public static final SavedValueFormat<Double> MEASUREMENT =
        new SavedValueFormat<>(SavedValues::getDouble, SavedValues::putDouble);

    /** A colour, as its packed ARGB value, alpha included. */
    public static final SavedValueFormat<Color> COLOUR = new SavedValueFormat<>(
        (values, key, fallback) -> new Color(values.getInt(key, fallback.getRGB()), true),
        (values, key, colour) -> values.putInt(key, colour.getRGB()));

    private final ValueReader<T> reader;
    private final ValueWriter<T> writer;

    private SavedValueFormat(ValueReader<T> reader, ValueWriter<T> writer) {

        this.reader = reader;
        this.writer = writer;
    }

    /**
     * One of a fixed set of values, written as its own name.
     *
     * <p>The value's name rather than any label shown beside it: a label is copy and gets
     * reworded, and a file keyed by copy forgets what was picked the first time the wording
     * improves. A remembered name that is no longer offered reads back as the fallback, since a
     * control set to a value outside its own options shows nothing and cannot be put back.
     *
     * @param offered every value that can be picked
     * @param <T>     what is picked between
     * @return the format
     */
    public static <T> SavedValueFormat<T> createNamedFormat(List<T> offered) {

        return new SavedValueFormat<>(
            (values, key, fallback) -> findValueNamed(
                offered,
                values.get(key, String.valueOf(fallback)),
                fallback),
            (values, key, value) -> values.put(key, String.valueOf(value)));
    }

    /**
     * Reads a value back.
     *
     * @param values   the file
     * @param key      what it is remembered under
     * @param fallback what it is when nothing readable is
     * @return the value
     */
    public T readValue(SavedValues values, String key, T fallback) {
        return reader.readValue(values, key, fallback);
    }

    /**
     * Writes a value down.
     *
     * @param values the file
     * @param key    what to remember it under
     * @param value  the value
     */
    public void writeValue(SavedValues values, String key, T value) {
        writer.writeValue(values, key, value);
    }

    // Which offered value a remembered name stands for, or the fallback where it stands for none.
    private static <T> T findValueNamed(List<T> offered, String name, T fallback) {

        for (var value : offered) {

            if (String.valueOf(value).equals(name)) {
                return value;
            }
        }

        return fallback;
    }

    @FunctionalInterface
    private interface ValueReader<T> {

        T readValue(SavedValues values, String key, T fallback);
    }

    @FunctionalInterface
    private interface ValueWriter<T> {

        void writeValue(SavedValues values, String key, T value);
    }
}
