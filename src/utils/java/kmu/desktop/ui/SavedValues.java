package kmu.desktop.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a window remembers between runs, kept in one file under keys of its own.
 *
 * <p><b>A named file rather than the JDK's preference nodes.</b> Those are keyed by
 * the PACKAGE of whichever class asks for one, so everything a person had set is silently
 * abandoned the day that class moves - and moving a class is a refactor nobody expects to
 * cost anything. A file named once, in a place chosen once, is remembered across every rename
 * and every repackaging there will ever be.
 *
 * <p><b>The keys are identities, never labels.</b> A label is copy: it gets reworded, and every
 * rewording would abandon whatever had been set under it. Worse, two rows worded alike come to
 * share a key - a switch and a colour writing over each other - which is a fault nobody could
 * diagnose from the window, and which a consistent rewording is the likeliest way to cause.
 *
 * <p>Installed once, by whatever assembles the window, rather than discovered. Where an
 * application remembers things is the application's decision, and a shared library reaching
 * for a location of its own is how two of them come to write to one file.
 */
public final class SavedValues {

    // What is installed, or null before an application has said where to remember things.
    private static SavedValues installed;

    private final Path file;

    // Sorted, so the file reads as a list a person can scan and two runs that set the same
    // things produce the same bytes.
    private final Map<String, String> values = new TreeMap<>();

    private SavedValues(Path file) {
        this.file = file;
    }

    /**
     * Says where to remember things, and reads back whatever is there.
     *
     * @param file where to keep them
     * @return the store, which is also what {@link #findSavedValues} will hand back
     */
    public static SavedValues rememberIn(Path file) {

        var store = new SavedValues(file);

        store.readFile();
        installed = store;

        return store;
    }

    /**
     * The store an application installed.
     *
     * @return it
     * @throws IllegalStateException where nothing has been installed, which is a window built
     *                               without anyone saying where it should remember things -
     *                               a wiring mistake, not a state to carry on in silently
     */
    public static SavedValues findSavedValues() {

        if (installed == null) {
            throw new IllegalStateException(
                "no saved-value file installed; call rememberIn(...) while building the window");
        }
        return installed;
    }

    /**
     * Reads a remembered flag.
     *
     * @param key      what it is remembered under
     * @param fallback what it is when nothing is
     * @return the flag
     */
    public boolean getBoolean(String key, boolean fallback) {

        var saved = values.get(key);

        return saved == null ? fallback : Boolean.parseBoolean(saved);
    }

    /**
     * Remembers a flag.
     *
     * @param key   what to remember it under
     * @param value the flag
     */
    public void putBoolean(String key, boolean value) {
        put(key, Boolean.toString(value));
    }

    /**
     * Reads a remembered number.
     *
     * @param key      what it is remembered under
     * @param fallback what it is when nothing is, or when what is there is not a number
     * @return the number
     */
    public int getInt(String key, int fallback) {

        var saved = values.get(key);

        if (saved == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(saved);
        } catch (NumberFormatException notANumber) {

            // A hand-edited file is a file someone can mistype. One unreadable value falls
            // back to its default rather than taking the window down with it.
            return fallback;
        }
    }

    /**
     * Remembers a number.
     *
     * @param key   what to remember it under
     * @param value the number
     */
    public void putInt(String key, int value) {
        put(key, Integer.toString(value));
    }

    /**
     * Reads a remembered measurement.
     *
     * @param key      what it is remembered under
     * @param fallback what it is when nothing is, or when what is there is not a number
     * @return the measurement
     */
    public double getDouble(String key, double fallback) {

        var saved = values.get(key);

        if (saved == null) {
            return fallback;
        }

        try {
            return Double.parseDouble(saved);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /**
     * Remembers a measurement.
     *
     * @param key   what to remember it under
     * @param value the measurement
     */
    public void putDouble(String key, double value) {
        put(key, Double.toString(value));
    }

    /**
     * Reads a remembered string.
     *
     * @param key      what it is remembered under
     * @param fallback what it is when nothing is
     * @return the string
     */
    public String get(String key, String fallback) {
        return values.getOrDefault(key, fallback);
    }

    /**
     * Remembers a string, and writes the file.
     *
     * <p>Written on every change rather than at exit. What is being remembered is a handful of
     * toggles and colours a person changes a few times a session, so the cost is nothing - and
     * a window that is closed by killing it, which is how a tool like this usually ends, would
     * otherwise remember nothing at all.
     *
     * @param key   what to remember it under
     * @param value the string
     */
    public void put(String key, String value) {

        values.put(key, value);
        writeFile();
    }

    private void readFile() {

        if (!Files.exists(file)) {
            return;
        }

        try {
            values.putAll(parseFlatJson(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private void writeFile() {

        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, writeFlatJson(values), StandardCharsets.UTF_8);

        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    // One object of string to string, one pair per line. Written by hand because that is all
    // the shape there is: pulling in a JSON library to spell out a flat map would be a
    // dependency for something this file can state completely in a dozen lines.
    private static String writeFlatJson(Map<String, String> values) {

        var json = new StringBuilder("{\n");
        var remaining = values.size();

        for (var entry : values.entrySet()) {

            json.append("  ")
                .append(quote(entry.getKey()))
                .append(": ")
                .append(quote(entry.getValue()))
                .append(--remaining > 0 ? "," : "")
                .append('\n');
        }
        return json.append("}\n").toString();
    }

    private static String quote(String text) {

        var quoted = new StringBuilder("\"");

        for (var index = 0; index < text.length(); index++) {

            var letter = text.charAt(index);

            switch (letter) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> quoted.append(letter);
            }
        }
        return quoted.append('"').toString();
    }

    // The mirror of the writer, and no more general than it: a flat object of strings. Anything
    // else in the file is not something this wrote, and is skipped rather than guessed at.
    private static Map<String, String> parseFlatJson(String json) {

        var values = new LinkedHashMap<String, String>();
        var strings = new ArrayList<String>();
        var index = 0;

        while (index < json.length()) {

            if (json.charAt(index) != '"') {
                index++;
                continue;
            }

            var text = new StringBuilder();

            index++;

            while (index < json.length() && json.charAt(index) != '"') {

                var letter = json.charAt(index);

                if (letter == '\\' && index + 1 < json.length()) {

                    index++;

                    var escaped = json.charAt(index);

                    text.append(switch (escaped) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> escaped;
                    });
                } else {
                    text.append(letter);
                }
                index++;
            }
            strings.add(text.toString());
            index++;
        }

        // Keys and values alternate, so an odd count is a truncated file - the last key is
        // dropped rather than paired with nothing.
        for (var pair = 0; pair + 1 < strings.size(); pair += 2) {
            values.put(strings.get(pair), strings.get(pair + 1));
        }
        return values;
    }
}
