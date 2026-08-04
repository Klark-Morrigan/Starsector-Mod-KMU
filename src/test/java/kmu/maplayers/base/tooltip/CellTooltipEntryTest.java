package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what an entry holds: a thing that breaks down no further is the ordinary case rather than a
 * degenerate one, an entry made up of members carries them in reading order, and the members are copied
 * so an entry cannot change under a block that has already been handed it.
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
            assertThat(entry.memberLines())
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
        void nestingKeepsTheMembersInTheOrderTheyAreRead() {

            var entry = CellTooltipEntry
                .createEntry(createLine("Rebel Pact"))
                .nesting(List.of(createLine("The Hegemony"), createLine("Tri-Tachyon")));

            assertThat(entry.memberLines())
                .containsExactly(createLine("The Hegemony"), createLine("Tri-Tachyon"));
        }

        @Test
        void nestingCopiesTheMembersItIsBuiltFrom() {
            // A resolver ordinarily hands over the very list it built the members in, so an entry that
            // held it would go on changing after the block was handed it - and the box would be laid
            // out against something other than what it was given.
            var memberLines = new ArrayList<CellTooltipEntryLine>();
            memberLines.add(createLine("The Hegemony"));

            var entry = CellTooltipEntry
                .createEntry(createLine("Rebel Pact"))
                .nesting(memberLines);

            memberLines.add(createLine("Tri-Tachyon"));

            assertThat(entry.memberLines())
                .containsExactly(createLine("The Hegemony"));
        }
    }

    // One listed thing, told apart from its siblings by its name alone - what it carries beyond that is
    // the line's own suite.
    private static CellTooltipEntryLine createLine(String labelText) {
        return CellTooltipEntryLine.createLine(null, labelText, CellTooltipRows.NO_SCORE);
    }
}
