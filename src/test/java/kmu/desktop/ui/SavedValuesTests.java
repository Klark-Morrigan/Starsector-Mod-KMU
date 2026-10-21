package kmu.desktop.ui;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the file {@link SavedValues} keeps: what one run writes, the next reads back unchanged,
 * text that needs escaping included, and a file cut short loses only its unfinished pair.
 */
final class SavedValuesTests {

    @TempDir
    private Path store;

    @Nested
    class RememberIn {

        @Test
        void readsBackEveryKindOfValueAnEarlierRunWrote() {

            var file = store.resolve("values.json");
            var written = SavedValues.rememberIn(file);

            written.putBoolean("flag", true);
            written.putInt("count", 42);
            written.putDouble("reach", 1234.5);
            written.put("name", "sector-366");

            var read = SavedValues.rememberIn(file);

            assertThat(read.getBoolean("flag", false))
                .isTrue();
            assertThat(read.getInt("count", 0))
                .isEqualTo(42);
            assertThat(read.getDouble("reach", 0))
                .isEqualTo(1234.5);
            assertThat(read.get("name", "none"))
                .isEqualTo("sector-366");
        }

        @Test
        void readsBackTextThatHadToBeEscaped() {

            var file = store.resolve("values.json");
            var awkward = "a \"quoted\" back\\slash\nnew line\ttab\rreturn";

            SavedValues.rememberIn(file).put("awkward", awkward);

            assertThat(SavedValues.rememberIn(file).get("awkward", "none"))
                .isEqualTo(awkward);
        }

        @Test
        void dropsTheUnfinishedPairOfATruncatedFile() throws IOException {

            var file = store.resolve("values.json");

            Files.writeString(
                file,
                "{\n  \"kept\": \"yes\",\n  \"cut\"",
                StandardCharsets.UTF_8);

            var read = SavedValues.rememberIn(file);

            assertThat(read.get("kept", "none"))
                .isEqualTo("yes");
            assertThat(read.get("cut", "none"))
                .isEqualTo("none");
        }

        @Test
        void answersTheFallbackWhereNoFileExists() {

            var read = SavedValues.rememberIn(store.resolve("missing.json"));

            assertThat(read.getInt("count", 7))
                .isEqualTo(7);
        }
    }

    @Nested
    class Put {

        @Test
        void writesTheFileSortedByKeyOnePairToALine() throws IOException {

            var file = store.resolve("values.json");
            var values = SavedValues.rememberIn(file);

            values.put("zebra", "z");
            values.put("apple", "a");

            assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .isEqualTo("{\n  \"apple\": \"a\",\n  \"zebra\": \"z\"\n}\n");
        }
    }

    @Nested
    class GetInt {

        @Test
        void answersTheFallbackForAValueThatIsNotANumber() {

            var values = SavedValues.rememberIn(store.resolve("values.json"));

            values.put("count", "many");

            assertThat(values.getInt("count", 3))
                .isEqualTo(3);
        }
    }
}
