package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.ColonyKind;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a box calls out on a colony's line: what sort of place it is, whether it took the system,
 * and how it is out of plain view.
 *
 * <p>One read for both hover families, because the two name the same colonies of the same system: a
 * word resolved separately at each could have the domination box call a world collapsed while the
 * claims box lists it as governed, which is the one disagreement a shared account cannot survive.
 *
 * <p>It applies the wording as well as resolving it, so the sharing reaches as far as it claims to.
 * A box left to layer the answer onto its own line would be a second statement of which end of the
 * line a finding speaks at and in which shade - and two of those are what a shared read was supposed
 * to have ruled out.
 *
 * <p>Several words at once, joined into the one slot the line carries. A hidden colony the economy
 * does not list has two things to say about itself and a derelict admitted by its neighbours has
 * two, so a chain of pairwise choices - which of these outranks which - would have to be reopened
 * for every word added. An ordered pass over independent facts is reopened for none.
 *
 * <p>The order runs from what a place <em>is</em> to how it is concealed: the claim it took, then
 * its kind, then the three ways it may be out of sight. Two of those never join what stands above
 * them. {@code undiscovered} displaces {@code hidden}, a colony the player has not found being
 * concealed from them by that fact alone; {@code unlisted} is a fallback and speaks only where
 * nothing above it held, or it would repeat itself on every derelict and every dead world, both
 * being off-economy by construction.
 *
 * <p>{@code hidden} is withheld from a colony whose concealment is public knowledge, that being a
 * fact the market itself does not carry. Such a colony falls through to whatever stands below,
 * which is the separation the word was wanted for: a landmark reads as absent from the economy's
 * listing and a base keeping out of sight reads as concealed.
 *
 * <p>Suppression is by the condition holding rather than by anything being printed, which is what
 * keeps the fallback honest: a derelict named for what it is drops its own word below and still
 * reads bare, rather than falling through to the weaker statement the suppression was meant to
 * improve on.
 *
 * <p>A qualifier rather than a note, so it reads in the finding's shade: what the box has found out
 * about the place, where the remark beside it - how current the news is - is the box talking about
 * its own account. A reader scanning for findings should meet the first and pass over the second.
 *
 * <p>Lower case throughout, the qualifier being a phrase run on after a name rather than a heading.
 * The status row's own {@code Decivilised} keeps its capital and its key: one fact at two altitudes,
 * and the altitudes disagree about case.
 */
public final class ColonyQualifier {

    private ColonyQualifier() {
    }

    /**
     * Runs a colony's line on into everything the box has found out about it, where it has found
     * out anything.
     *
     * <p>Layered independently of any remark the line already carries, and the two cannot displace
     * each other: they are drawn in different shades and in slots the line keeps apart - the
     * qualifier a finding, run on after the name; the remark quiet, closing the line.
     *
     * @param line  the colony's line as the box has built it so far
     * @param facts what the box knows about the colony beyond its own number; null states nothing
     * @return the line, called out where anything is due and untouched otherwise
     */
    public static CellTooltipEntryLine qualifyColony(
            CellTooltipEntryLine line,
            ColonyQualifierFacts facts) {

        if (facts == null) {
            return line;
        }
        var statedWords = dropWordsAlreadyInName(resolveWords(facts), line.labelText());

        if (statedWords.isEmpty()) {
            return line;
        }
        return line.qualifiedWith(String.join(
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_SEPARATOR),
            statedWords));
    }

    // Every word the facts call for, in the order they are read. A straight ordered pass over
    // independent predicates, so a word added later is one clause here rather than a re-reckoning
    // of which of the existing ones it outranks.
    private static List<String> resolveWords(ColonyQualifierFacts facts) {

        var words = new ArrayList<String>();

        if (facts.isHoldingTheClaim()) {
            words.add(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_HOLDER));
        }
        resolveKindWord(facts.kind()).ifPresent(words::add);

        // Displacement rather than a pair: a colony the player has not found is concealed from them
        // by that alone, and the market's own flag adds nothing a reader could act on.
        //
        // A concealment the sector openly points at says nothing either. The word is what parts a
        // base keeping itself out of sight from an ordinary colony, and a landmark that merely
        // keeps no comm directory wears the identical flag - so calling it out beside a pirate base
        // would say the two are the same sort of place.
        if (!facts.isDiscoveredByPlayer()) {
            words.add(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_UNDISCOVERED));
        } else if (facts.isHiddenMarket() && !facts.isOpenlyKnownMarket()) {
            words.add(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_HIDDEN));
        }
        // The fallback, and the emptiness read here is the conditions above rather than what will
        // survive the name check below - which is what lets a word dropped for being in the name
        // still have qualified.
        if (words.isEmpty() && !facts.isListedByEconomy()) {
            words.add(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_UNLISTED));
        }
        return words;
    }

    // What the kind calls out, or nothing. Stated over every kind rather than over the two that
    // speak, so a fifth kind cannot be admitted without deciding what it says for itself.
    private static Optional<String> resolveKindWord(ColonyKind kind) {

        if (kind == null) {
            return Optional.empty();
        }
        return switch (kind) {
            case SPACE_DERELICT ->
                Optional.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_ABANDONED));
            case UNGOVERNED_COLONY ->
                Optional.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_DECIVILISED));
            case COLONY, OUTPOST -> Optional.empty();
        };
    }

    // Drops any word the colony is already called, case-insensitively. A station named "Abandoned
    // Station" would otherwise read as abandoned twice over, and the rule is written over the whole
    // vocabulary rather than attached to the one word it fires for today.
    //
    // Read off the line's own label rather than off a name handed in beside it: what the reader can
    // see is exactly what the line says, and a second copy of the name would be free to disagree
    // with it.
    private static List<String> dropWordsAlreadyInName(List<String> words, String colonyName) {

        if (colonyName == null) {
            return words;
        }
        var loweredName = colonyName.toLowerCase(Locale.ROOT);
        var statedWords = new ArrayList<String>(words.size());

        for (var word : words) {

            if (!loweredName.contains(word.toLowerCase(Locale.ROOT))) {
                statedWords.add(word);
            }
        }
        return statedWords;
    }
}
