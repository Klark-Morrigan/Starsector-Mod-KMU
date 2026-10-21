package kmu.maplayers.base.tooltip.content;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * What a listed line is called: its name said outright, or the shape of a name the box withholds.
 *
 * <p>Sealed over the two cases rather than held as a name and a redaction side by side. A line carries
 * exactly one account of what it is called, and with two nullable parts the other two pairings - both,
 * and neither - could still be spelt. Here they have no spelling.
 *
 * <p>Only a spoken name can say one of the box's findings inside itself. A withheld name has no
 * letters to pick a stretch out of, so it holds no place for one.
 */
public sealed interface CellTooltipLineName {

    /**
     * Builds a name said outright that says none of the box's findings inside itself, which is every
     * name until a resolver finds one of its words already on it.
     *
     * @param labelText what the line is called
     * @return the spoken name
     */
    static Spoken createSpokenName(String labelText) {
        return new Spoken(labelText, Spoken.NO_LABEL_FINDING);
    }

    /**
     * Hands this name to whichever of the two cases the caller wrote for it, and answers what that one
     * produced.
     *
     * <p>A caller states both cases or does not compile. Java 17, the runtime the game supplies, has no
     * exhaustive switch over a sealed type, so this fold is how a reader of the name is held to both.
     *
     * @param <R>          what the caller produces for a name
     * @param spokenCase   what a name said outright produces
     * @param withheldCase what a withheld name produces
     * @return the answer of the case this name is
     */
    <R> R selectByCase(
            Function<Spoken, R> spokenCase,
            Function<Withheld, R> withheldCase);

    /**
     * A name said outright.
     *
     * @param labelText    what the line is called
     * @param labelFinding the stretch of the name that is itself one of the box's findings, or null
     *                     where the name says none - a name calls at most one out inside itself
     */
    record Spoken(
            String labelText,
            CellTooltipLabelFinding labelFinding) implements CellTooltipLineName {

        // What a name saying none of the box's findings carries in their place.
        private static final CellTooltipLabelFinding NO_LABEL_FINDING = null;

        /**
         * Rejects a missing name and a finding running past the end of it, where the caller that
         * composed them is still on the stack rather than inside the draw that splits the label.
         */
        public Spoken {

            Objects.requireNonNull(labelText, "labelText");

            if (labelFinding != null && labelFinding.endIndex() > labelText.length()) {
                throw new IllegalArgumentException("endIndex must not run past the label");
            }
        }

        @Override
        public <R> R selectByCase(
                Function<Spoken, R> spokenCase,
                Function<Withheld, R> withheldCase) {

            return spokenCase.apply(this);
        }
    }

    /**
     * The shape of a name the box withholds. The name itself never reaches it: the lengths stand for
     * the words without being them, so there is nothing here for a later change to draw.
     *
     * @param redactedWordLengths how many characters each word of the withheld name ran to, in reading
     *                            order
     */
    record Withheld(List<Integer> redactedWordLengths)
        implements CellTooltipLineName {

        /**
         * Copies the lengths, so a caller that derived them from a list it goes on using cannot
         * reshape a name the box has already stated.
         */
        public Withheld {
            redactedWordLengths = List.copyOf(redactedWordLengths);
        }

        @Override
        public <R> R selectByCase(
                Function<Spoken, R> spokenCase,
                Function<Withheld, R> withheldCase) {

            return withheldCase.apply(this);
        }
    }
}
