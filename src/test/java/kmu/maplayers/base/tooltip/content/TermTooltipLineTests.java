package kmu.maplayers.base.tooltip.content;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one shape a term of a number takes: no mark, its name and what it contributed, and
 * nothing else called out - whether it comes as an entry that breaks down no further or as a line
 * an account goes on to refine.
 */
final class TermTooltipLineTests {

    private static final String LABEL_TEXT = "Same-faction market bonus";
    private static final String VALUE_TEXT = "+2";

    // What a term is, as the line a reader sees: uncrested, named outright, and carrying no place,
    // note, qualifier, count or working until an account states one on it.
    private static final CellTooltipEntryLine TERM_LINE = new CellTooltipEntryLine(
        null,
        new CellTooltipLineName.Spoken(LABEL_TEXT, null),
        null,
        null,
        null,
        new CellTooltipLineValue(VALUE_TEXT, null, null, false),
        false);

    @Nested
    class BuildTermEntry {

        @Test
        void holdsTheTermLineMadeUpOfNothing() {

            // Most terms break down no further, so the entry carries no children for a block to
            // indent beneath it.
            var entry = TermTooltipLine.buildTermEntry(LABEL_TEXT, VALUE_TEXT);

            assertThat(entry.line())
                .isEqualTo(TERM_LINE);
            assertThat(entry.children())
                .isEmpty();
        }
    }

    @Nested
    class BuildTermLine {

        @Test
        void carriesTheNameAndValueCallingNothingOut() {

            assertThat(TermTooltipLine.buildTermLine(LABEL_TEXT, VALUE_TEXT))
                .isEqualTo(TERM_LINE);
        }

        @Test
        void leadsWithNoMark() {

            // A term is arithmetic rather than a thing in the sector, so a layout reserves no mark
            // column for it.
            assertThat(TermTooltipLine.buildTermLine(LABEL_TEXT, VALUE_TEXT).hasMark())
                .isFalse();
        }
    }
}
