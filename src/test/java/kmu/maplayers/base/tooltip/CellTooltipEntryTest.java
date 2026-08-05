package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what an entry holds: a thing that breaks down no further is the ordinary case rather than a
 * degenerate one, an entry made up of others carries them in reading order and to whatever depth they
 * themselves go, and they are copied so an entry cannot change under a block that has already been
 * handed it.
 */
final class CellTooltipEntryTest {

    @Nested
    class CreateEntry {

        @Test
        void createEntryHoldsTheLineItIsBuiltFromMadeUpOfNothing() {
            // What a block of plain lines is made of, which is most of them: a caller listing flat
            // content never states an emptiness it has nothing to say about.
            var line = createLine("The Hegemony");
            var entry = CellTooltipEntry.createEntry(line);

            assertThat(entry.line())
                .isEqualTo(line);
            assertThat(entry.children())
                .isEmpty();
        }

        @Test
        void createEntryRefusesAnEntryWithNoLineOfItsOwn() {
            assertThatThrownBy(() -> CellTooltipEntry.createEntry(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Nesting {

        @Test
        void nestingKeepsWhatTheEntryIsMadeUpOfInTheOrderItIsRead() {

            var entry = CellTooltipEntry
                .createEntry(createLine("Rebel Pact"))
                .nesting(List.of(createEntry("The Hegemony"), createEntry("Tri-Tachyon")));

            assertThat(entry.children())
                .containsExactly(createEntry("The Hegemony"), createEntry("Tri-Tachyon"));
        }

        @Test
        void nestingKeepsWhatItsOwnChildrenAreMadeUpOf() {
            // The reason an entry is made up of entries rather than of lines: a breakdown that goes
            // three levels is stated one level per call, and no level is flattened away on the way up.
            var patrols = CellTooltipEntry
                .createEntry(createLine("Patrols"))
                .nesting(List.of(createEntry("Light patrol")));

            var entry = CellTooltipEntry
                .createEntry(createLine("Chicomoztoc"))
                .nesting(List.of(patrols));

            assertThat(entry.children().get(0).children())
                .containsExactly(createEntry("Light patrol"));
        }

        @Test
        void nestingCopiesWhatItIsBuiltFrom() {
            // A resolver ordinarily hands over the very list it built the children in, so an entry that
            // held it would go on changing after the block was handed it - and the box would be laid
            // out against something other than what it was given.
            var children = new ArrayList<CellTooltipEntry>();
            children.add(createEntry("The Hegemony"));

            var entry = CellTooltipEntry
                .createEntry(createLine("Rebel Pact"))
                .nesting(children);

            children.add(createEntry("Tri-Tachyon"));

            assertThat(entry.children())
                .containsExactly(createEntry("The Hegemony"));
        }
    }

    // One thing that breaks down no further, told apart from its siblings by its name alone.
    private static CellTooltipEntry createEntry(String labelText) {
        return CellTooltipEntry.createEntry(createLine(labelText));
    }

    // One listed thing, told apart from its siblings by its name alone - what it carries beyond that is
    // the line's own suite.
    private static CellTooltipEntryLine createLine(String labelText) {
        return CellTooltipEntryLine.createLine(null, labelText, CellTooltipRows.NO_SCORE);
    }
}
