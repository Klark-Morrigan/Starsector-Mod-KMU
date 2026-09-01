package kmu.maplayers.base.tooltip.content;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what an entry holds: a thing that breaks down no further is the ordinary case rather than a
 * degenerate one, an entry carrying others holds them in reading order and to whatever depth they
 * themselves go, they are copied so an entry cannot change under a block that has already been handed
 * it, and which of the two relations they stand in is stated where the entry is resolved rather than
 * inferred from how deep they landed.
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
            assertThat(entry.isSubordinatingChildren())
                .isFalse();
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

        @Test
        void nestingStatesThatWhatItCarriesIsTheLinesAccount() {
            // The half of the distinction that demotes: what a market breaks down into is the box
            // explaining itself, so it reads a step quieter than the finding it explains.
            var entry = CellTooltipEntry
                .createEntry(createLine("Chicomoztoc"))
                .nesting(List.of(createEntry("Size")));

            assertThat(entry.isSubordinatingChildren())
                .isTrue();
        }

        @Test
        void nestingCarriesNoRelationWhereItCarriesNothing() {
            // A relation describing no children says nothing, and an entry answering an unasked
            // question compares unequal to the same entry built the other way - which would make a
            // resolver's choice of call visible in a listing that shows no difference.
            var entry = CellTooltipEntry
                .createEntry(createLine("Independent"))
                .nesting(List.of());

            assertThat(entry.isSubordinatingChildren())
                .isFalse();
            assertThat(entry)
                .isEqualTo(CellTooltipEntry.createEntry(createLine("Independent")));
        }
    }

    @Nested
    class Grouping {

        @Test
        void groupingKeepsWhatTheEntryGathersInTheOrderItIsRead() {

            var entry = CellTooltipEntry
                .createEntry(createLine("Rebel Pact"))
                .grouping(List.of(createEntry("The Hegemony"), createEntry("Tri-Tachyon")));

            assertThat(entry.children())
                .containsExactly(createEntry("The Hegemony"), createEntry("Tri-Tachyon"));
        }

        @Test
        void groupingStatesThatWhatItCarriesAreTheLinesPeers() {
            // The half of the distinction that does not demote: naming an alliance and naming the factions
            // in it are one answer at two granularities, so nothing has been broken down yet.
            var entry = CellTooltipEntry
                .createEntry(createLine("Rebel Pact"))
                .grouping(List.of(createEntry("The Hegemony")));

            assertThat(entry.isSubordinatingChildren())
                .isFalse();
        }

        @Test
        void groupingCopiesWhatItIsBuiltFrom() {
            // The same guarantee nesting gives, stated of the other relation: a resolver hands over the
            // very list it gathered the members in, and a block laid out against a list still changing
            // draws something other than what it was given.
            var children = new ArrayList<CellTooltipEntry>();
            children.add(createEntry("The Hegemony"));

            var entry = CellTooltipEntry
                .createEntry(createLine("Rebel Pact"))
                .grouping(children);

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
        return CellTooltipEntryLine.createLine(null, labelText, CellTooltipEntryLine.NO_SCORE);
    }
}
