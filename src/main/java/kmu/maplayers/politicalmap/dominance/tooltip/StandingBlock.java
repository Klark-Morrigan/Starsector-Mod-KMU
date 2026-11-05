package kmu.maplayers.politicalmap.dominance.tooltip;

import kmu.maplayers.politicalmap.tooltip.ContestBlockHeading;
import kmu.maplayers.politicalmap.tooltip.ContestWording;
import kmu.util.KmuPoliticalMapStringKeys;

import java.util.function.Function;

/**
 * The blocks a hovered system's standings are listed under, in the order a box lays them down: who
 * holds the system, who stands with them by alliance, who stands with them in disposition, who stands
 * against them, who is there where nobody holds it, and who was never in the running at all.
 *
 * <p>A closed set for the reason {@link ContestBlockHeading} states: a routing that stopped placing a
 * block fails to compile where a misspelled heading key would simply have drawn a block that never
 * filled.
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
     * The bloc the map fills the system in the colour of, as the dominance pass settled it - which
     * is what keeps the box an explanation of the cell beneath it. Empty where that bloc is barred
     * from the contest, or where the fill was painted for nobody.
     */
    HOLDER(ContestBlockHeading.createFixedKeySource(
        KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_DOMINATED)),

    /** The blocs the alliance set folds into the holder's own, which are not contesting it. */
    ALLIED(ContestBlockHeading.createFixedKeySource(
        KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_SYSTEM_HOLDER)),

    /**
     * The blocs on good terms with the holder without standing in its alliance, taken from what
     * alliance left standing against it - see
     * {@link kmu.maplayers.ownermap.owners.holders.alliances.ContestSides}.
     */
    FRIENDLY(ContestBlockHeading.createFixedKeySource(
        KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_FRIENDLY_WITH_SYSTEM_HOLDER)),

    /**
     * The blocs standing against the holder - or merely standing beside it, on an install where no
     * system ever changes hands. The one block whose heading the install decides, which is
     * {@link ContestWording}'s answer rather than this declaration's.
     */
    CONTESTED(ContestWording::resolveHeadingKey),

    /**
     * Every bloc in the running, in the order they ranked, where none of them holds the system - so
     * there is no holder for anybody to stand with or against. Filled only then.
     *
     * <p>A fixed heading rather than {@link ContestWording}'s: the wording decides whether presence
     * beside a holder amounts to a contest, and with no holder there is nothing to contest whatever
     * the install.
     *
     * <p>Every bloc listed here holds nothing but colonies the economy does not list, since a
     * weighed bloc in the running would have been the one painted.
     */
    PRESENT(ContestBlockHeading.createFixedKeySource(
        KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_PRESENT)),

    /**
     * The blocs that take no part in the contest for the system. Listed with whatever they scored,
     * and never named as holding it. Which blocs those are is
     * {@link kmu.maplayers.politicalmap.dominance.BlocCandidacy}'s answer.
     */
    NON_POLITICAL(ContestBlockHeading.createFixedKeySource(
        KmuPoliticalMapStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_NON_POLITICAL));

    private final Function<ContestWording, String> headingKeySource;

    StandingBlock(Function<ContestWording, String> headingKeySource) {
        this.headingKeySource = headingKeySource;
    }

    @Override
    public String resolveHeadingKey(ContestWording contestWording) {
        return headingKeySource.apply(contestWording);
    }
}
