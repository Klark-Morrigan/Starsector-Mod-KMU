package kmu.desktop.ui;

import java.util.function.Consumer;

/**
 * One value a window remembers between runs, and the owner it tells.
 *
 * <p>Every remembered control is the same three acts: open on what was remembered and tell its
 * owner before anything draws, tell the owner and write the value down on every change, and put
 * the fallback back on demand. Written per control, those acts are free to come out in a
 * different order in each - and a control that writes before it tells, or opens without telling,
 * is one whose first use appears to do the wrong thing.
 *
 * <p>Read from the installed {@link SavedValues}, so a control and anything reading the same
 * value without one go through one store and one {@link SavedValueFormat}.
 *
 * @param key      what it is remembered under
 * @param fallback what it is when nothing is remembered, and what a reset puts back
 * @param format   how it is written down
 * @param apply    tells the owner a new value
 * @param <T>      the kind of value
 */
public record RememberedValue<T>(
    String key,
    T fallback,
    SavedValueFormat<T> format,
    Consumer<T> apply) {

    /**
     * Tells the owner what is remembered, or the fallback where nothing is.
     *
     * @return the value the owner was told
     */
    public T applyRemembered() {

        var remembered = format.readValue(SavedValues.findSavedValues(), key, fallback);

        apply.accept(remembered);

        return remembered;
    }

    /**
     * Tells the owner a new value, then writes it down.
     *
     * <p>In that order, so a failed write never leaves the owner holding the old value while the
     * control shows the new one.
     *
     * @param value the new value
     */
    public void recordValue(T value) {

        apply.accept(value);
        format.writeValue(SavedValues.findSavedValues(), key, value);
    }
}
