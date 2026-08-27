package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.ColonyKind;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the vocabulary one colony's line calls out, the order the words are read in, and which of
 * them never stand together.
 *
 * <p>The pairs are what the suite is mostly about, because the resolver is a straight ordered pass
 * over independent predicates and has to be right for every combination rather than for the ones
 * the sector currently produces. Two kinds of impossibility are pinned apart: the words a rule here
 * suppresses, and the pairs the game itself cannot produce - a claim holder is listed, open and
 * territorially owned, so the facts behind one are posed as vanilla builds them and the single word
 * asserted.
 *
 * <p>Pure over a small value with no Starsector types, the boxes' own carriers being the thing the
 * shared read exists not to be built around.
 */
final class ColonyQualifierTest {

    // Whether the colony took the system, which only the claims box ever states.
    private static final boolean HOLDS_THE_CLAIM = true;
    private static final boolean HOLDS_NO_CLAIM = false;

    // Whether the player has found the colony's entity. Only a reveal puts an unfound colony on a
    // list at all, which is where the word it earns has anywhere to appear.
    private static final boolean IS_FOUND = true;
    private static final boolean IS_UNFOUND = false;

    // Whether the colony conceals itself rather than being held in the open.
    private static final boolean IS_CONCEALED = true;
    private static final boolean IS_OPEN = false;

    // Whether the economy's own set holds the colony, as the walk that selected it decided.
    private static final boolean IS_LISTED = true;
    private static final boolean IS_UNLISTED = false;

    @BeforeEach
    void installStrings() {
        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class QualifyColony {

        @Test
        void qualifyColonyCallsNothingOutBesideAnOrdinaryColonyInPlainSight() {
            // The common case, and the one the whole vocabulary has to leave alone: a listed,
            // open, found colony nobody has any finding about reads on its name.
            assertThat(qualify("Jangala", new ColonyQualifierFacts(
                    ColonyKind.COLONY,
                    HOLDS_NO_CLAIM,
                    IS_FOUND,
                    IS_OPEN,
                    IS_LISTED)))
                .isNull();
        }

        @Test
        void qualifyColonyCallsADerelictAbandoned() {
            assertThat(qualify("Sentinel Gantries", buildUnlistedFacts(ColonyKind.SPACE_DERELICT)))
                .isEqualTo("abandoned");
        }

        @Test
        void qualifyColonyCallsACollapsedColonyDecivilised() {
            assertThat(qualify("Tibicena", buildUnlistedFacts(ColonyKind.UNGOVERNED_COLONY)))
                .isEqualTo("decivilised");
        }

        @Test
        void qualifyColonyCallsAKeptStationNothingAtAll() {
            // An outpost is a place somebody runs, whatever shape of market it wears, so the
            // vocabulary has nothing to say about it.
            assertThat(qualify("Tigra City", new ColonyQualifierFacts(
                    ColonyKind.OUTPOST,
                    HOLDS_NO_CLAIM,
                    IS_FOUND,
                    IS_OPEN,
                    IS_LISTED)))
                .isNull();
        }

        @Test
        void qualifyColonyCallsAnUnfoundColonyUndiscovered() {
            assertThat(qualify("Kanta's Den", new ColonyQualifierFacts(
                    ColonyKind.COLONY,
                    HOLDS_NO_CLAIM,
                    IS_UNFOUND,
                    IS_OPEN,
                    IS_LISTED)))
                .isEqualTo("undiscovered");
        }

        @Test
        void qualifyColonyCallsAConcealedColonyHidden() {
            assertThat(qualify("Kanta's Den", new ColonyQualifierFacts(
                    ColonyKind.COLONY,
                    HOLDS_NO_CLAIM,
                    IS_FOUND,
                    IS_CONCEALED,
                    IS_LISTED)))
                .isEqualTo("hidden");
        }

        @Test
        void qualifyColonyCallsAColonyTheEconomyDoesNotHoldUnlisted() {
            assertThat(qualify("Galatia Academy", new ColonyQualifierFacts(
                    ColonyKind.COLONY,
                    HOLDS_NO_CLAIM,
                    IS_FOUND,
                    IS_OPEN,
                    IS_UNLISTED)))
                .isEqualTo("unlisted");
        }

        @Test
        void qualifyColonyCallsTheClaimHolderOutAheadOfEverythingElse() {
            // Under the reveal, which is the one state a claim holder can be joined in: it is
            // listed, open and territorially owned by construction, so nothing else can join it.
            assertThat(qualify("Chicomoztoc", new ColonyQualifierFacts(
                    ColonyKind.COLONY,
                    HOLDS_THE_CLAIM,
                    IS_UNFOUND,
                    IS_OPEN,
                    IS_LISTED)))
                .isEqualTo("claim holder, undiscovered");
        }

        @Test
        void qualifyColonyCallsTheClaimHolderOutAloneAsVanillaBuildsOne() {
            // The pairs the table records as unreachable, posed from the facts rather than from the
            // resolver: the mechanic draws its candidates from the economy's own listing, skips
            // concealed markets, and takes a territorial owner - so a claim holder is listed, open
            // and a place somebody runs, and the word stands by itself.
            assertThat(qualify("Chicomoztoc", new ColonyQualifierFacts(
                    ColonyKind.COLONY,
                    HOLDS_THE_CLAIM,
                    IS_FOUND,
                    IS_OPEN,
                    IS_LISTED)))
                .isEqualTo("claim holder");
        }

        @Test
        void qualifyColonyRunsAKindOnIntoHowTheColonyIsOutOfSight() {
            // What a place is outranks how it is concealed, so the kind leads. Reachable under the
            // reveal, a derelict being exactly what a bare fog would leak.
            assertThat(qualify("Sentinel Gantries", new ColonyQualifierFacts(
                    ColonyKind.SPACE_DERELICT,
                    HOLDS_NO_CLAIM,
                    IS_UNFOUND,
                    IS_OPEN,
                    IS_UNLISTED)))
                .isEqualTo("abandoned, undiscovered");
        }

        @Test
        void qualifyColonyRunsAKindOnIntoAConcealmentAModHungOnIt() {
            // Vanilla conceals neither kind; a mod may, and the resolver has to be right for it.
            assertThat(qualify("Tibicena", new ColonyQualifierFacts(
                    ColonyKind.UNGOVERNED_COLONY,
                    HOLDS_NO_CLAIM,
                    IS_FOUND,
                    IS_CONCEALED,
                    IS_UNLISTED)))
                .isEqualTo("decivilised, hidden");
        }

        @Test
        void qualifyColonyDisplacesHiddenWithUndiscoveredWhereBothHold() {
            // Not a pair: a colony the player has not found is concealed from them by that alone,
            // and the market's own flag adds nothing a reader could act on.
            assertThat(qualify("Kanta's Den", new ColonyQualifierFacts(
                    ColonyKind.COLONY,
                    HOLDS_NO_CLAIM,
                    IS_UNFOUND,
                    IS_CONCEALED,
                    IS_LISTED)))
                .isEqualTo("undiscovered");
        }

        @Test
        void qualifyColonySuppressesUnlistedBehindAKind() {
            // Both kinds that speak are off-economy by construction, so the fallback would repeat
            // itself on every derelict and every dead world.
            assertThat(qualify("Sentinel Gantries", buildUnlistedFacts(ColonyKind.SPACE_DERELICT)))
                .isEqualTo("abandoned");
        }

        @Test
        void qualifyColonySuppressesUnlistedBehindUndiscovered() {
            assertThat(qualify("Daybreak", new ColonyQualifierFacts(
                    ColonyKind.COLONY,
                    HOLDS_NO_CLAIM,
                    IS_UNFOUND,
                    IS_OPEN,
                    IS_UNLISTED)))
                .isEqualTo("undiscovered");
        }

        @Test
        void qualifyColonySuppressesUnlistedBehindHidden() {
            // Daybreak's own shape: concealed, off-economy and in plain sight of anyone who has
            // found it, so the fallback has a stronger word standing above it.
            assertThat(qualify("Daybreak", new ColonyQualifierFacts(
                    ColonyKind.COLONY,
                    HOLDS_NO_CLAIM,
                    IS_FOUND,
                    IS_CONCEALED,
                    IS_UNLISTED)))
                .isEqualTo("hidden");
        }

        @Test
        void qualifyColonyDropsAWordTheColonyIsAlreadyCalled() {
            // A station called Abandoned Station is the shape the rule exists for: the word is
            // already on the line, and repeating it reads as a fault in the box.
            assertThat(qualify("Abandoned Station", buildUnlistedFacts(ColonyKind.SPACE_DERELICT)))
                .isNull();
        }

        @Test
        void qualifyColonyKeepsUnlistedSuppressedBehindAWordItDropped() {
            // A dropped word has still qualified. The fallback is suppressed by the condition
            // holding above it, not by anything being printed - so a derelict named for what it is
            // reads bare rather than falling through to the weaker statement.
            assertThat(qualify("Abandoned Station", new ColonyQualifierFacts(
                    ColonyKind.SPACE_DERELICT,
                    HOLDS_NO_CLAIM,
                    IS_UNFOUND,
                    IS_OPEN,
                    IS_UNLISTED)))
                .isEqualTo("undiscovered");
        }

        @Test
        void qualifyColonyLeavesALineAloneWhereNothingIsFound() {
            // The same line back rather than a copy carrying an empty status, which the block
            // beneath would have to lay out a separator for.
            var line = buildLine("Jangala");

            assertThat(ColonyQualifier.qualifyColony(line, new ColonyQualifierFacts(
                    ColonyKind.COLONY,
                    HOLDS_NO_CLAIM,
                    IS_FOUND,
                    IS_OPEN,
                    IS_LISTED)))
                .isSameAs(line);
        }

        @Test
        void qualifyColonyLeavesALineAloneWhereNothingWasReadAboutTheColony() {
            // A box holding a row it has no reading behind states nothing about it rather than
            // failing, the row being one it is already committed to drawing.
            var line = buildLine("Jangala");

            assertThat(ColonyQualifier.qualifyColony(line, null))
                .isSameAs(line);
        }

        @Test
        void qualifyColonyReadsAnUnstatedKindAsNoGroundsForAFinding() {
            assertThat(qualify("Jangala", new ColonyQualifierFacts(
                    null,
                    HOLDS_NO_CLAIM,
                    IS_FOUND,
                    IS_OPEN,
                    IS_LISTED)))
                .isNull();
        }
    }

    // What the resolver called out on the colony's line, which is the whole of what every case
    // above reads.
    //
    // The name is handed in beside the facts rather than held among them, because that is where the
    // resolver reads it from: one rule drops a word the colony is already called, and it reads the
    // label the line carries.
    private static String qualify(String colonyName, ColonyQualifierFacts facts) {
        return ColonyQualifier
            .qualifyColony(buildLine(colonyName), facts)
            .qualifierText();
    }

    // A colony's line before anything has been called out on it.
    private static CellTooltipEntryLine buildLine(String colonyName) {
        return CellTooltipEntryLine.createLine(null, colonyName, "0");
    }

    // The off-economy colony in plain sight both speaking kinds arrive as, which is what the cases
    // about a kind's own word are posed over.
    private static ColonyQualifierFacts buildUnlistedFacts(ColonyKind kind) {
        return new ColonyQualifierFacts(kind, HOLDS_NO_CLAIM, IS_FOUND, IS_OPEN, IS_UNLISTED);
    }
}
