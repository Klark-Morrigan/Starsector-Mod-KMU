package kmu.maplayers.politicalmap.dominance.tooltip;

import kmu.maplayers.politicalmap.tooltip.ContestBlockHeading;
import kmu.maplayers.politicalmap.tooltip.ContestWording;
import kmu.util.KmuStringKeys;

import java.util.function.Function;

/**
 * The blocks a hovered system's standings are listed under, in the order a box lays them down: who
 * holds the system, who stands with them by alliance, who stands with them in disposition, who stands
 * against them, and who was never in the running at all.
 *
 * <p>A closed set rather than a heading string handed around, so which block a group falls in is an
 * answer the compiler checks: a block stated here is a block the box lays down, and a routing that
 * stopped placing one fails to compile where a misspelled heading key would simply have drawn a
 * block that never filled.
 *
 * <p>Each block carries the heading it draws under, because the two are one decision - a block is
 * the groups it takes under the wording naming them - and the order they read in is this
 * declaration order rather than a sequence of calls somewhere else that could drift from it.
 *
 * <p>What it carries is how that heading is worded rather than one fixed key, since one block's
 * wording turns on the install ({@link ContestWording}). Held as the choice itself, a block whose
 * heading varies is declared here like any other, and nothing downstream has to know which of them
 * that is.
 */
public enum StandingBlock implements ContestBlockHeading {

    /**
     * The bloc the map fills the system in the colour of. The strongest of those in the running,
     * which is what keeps the box an explanation of the cell beneath it.
     */
    HOLDER(wording -> KmuStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_DOMINATED),

    /** The blocs the alliance set folds into the holder's own, which are not contesting it. */
    ALLIED(wording -> KmuStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_SYSTEM_HOLDER),

    /**
     * The blocs on good terms with the holder without standing in its alliance. Inside the allied
     * block rather than beside it: an ally who is merely favourable is still an ally, so disposition
     * sorts only what alliance left standing against the holder.
     */
    FRIENDLY(wording -> KmuStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_FRIENDLY_WITH_SYSTEM_HOLDER),

    /**
     * The blocs standing against the holder - or merely standing beside it, on an install where no
     * system ever changes hands. The one block whose heading the install decides, which is
     * {@link ContestWording}'s answer rather than this declaration's.
     */
    CONTESTED(ContestWording::resolveHeadingKey),

    /**
     * The blocs that take no part in the contest for the system. Listed with whatever they scored,
     * and never named as holding it. Which blocs those are is
     * {@link kmu.maplayers.politicalmap.dominance.BlocCandidacy}'s answer.
     */
    NON_POLITICAL(wording -> KmuStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_NON_POLITICAL);

    private final Function<ContestWording, String> headingKeySource;

    StandingBlock(Function<ContestWording, String> headingKeySource) {
        this.headingKeySource = headingKeySource;
    }

    @Override
    public String resolveHeadingKey(ContestWording contestWording) {
        return headingKeySource.apply(contestWording);
    }
}
