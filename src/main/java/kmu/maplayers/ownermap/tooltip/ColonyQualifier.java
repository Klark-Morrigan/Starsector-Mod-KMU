package kmu.maplayers.ownermap.tooltip;

import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.util.KmuStringKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a box calls out on a colony's line: what its layer finds first, what sort of place it is, and
 * how it is out of plain view.
 *
 * <p>One read for every box, because boxes name the same colonies of the same system: a word resolved
 * separately at each could have one box call a world collapsed while another lists it as governed. It
 * applies the wording as well as resolving it, so the sharing reaches all the way to the line rather
 * than leaving each box to restate which end of the line a finding speaks at and in which shade.
 *
 * <p>A qualifier rather than a note, so it reads in the finding's shade: what the box has found out
 * about the place, where the remark beside it - how current the news is - is the box talking about its
 * own account. Lower case throughout, the qualifier being a phrase run on after a name rather than a
 * heading; the status row's own {@code Decivilised} keeps its capital, one fact at two altitudes.
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
     * <p>Where the colony's name says one of the words itself, that word is gilded in place instead of
     * being stated after the name. Two findings in one shade on one line is the right reading and not
     * a clash - a gilded <em>Abandoned Station</em> beside an {@code undiscovered} states two things
     * about two different subjects, and a rule about how much gold a line may carry would be a
     * typographic budget standing in for a statement about the world.
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
        var words = resolveWords(facts);

        if (words.isEmpty()) {
            return line;
        }
        var wordInName = findFirstWordInName(words, line);
        var statedWords = new ArrayList<>(words);
        var qualifiedLine = line;

        // The word the name already carries is gilded where it stands and leaves the list, so the
        // finding is made exactly once. Every other word is read at the end of the line as usual.
        // Which word moves is settled by the name and not by the resolution, so what a colony is
        // cannot come out differently for a colony that happens to be named after itself.
        if (wordInName != null) {
            var word = statedWords.remove(wordInName.wordIndex());
            qualifiedLine = line.callsOutInLabel(
                wordInName.nameStartIndex(),
                wordInName.nameStartIndex() + word.length());
        }
        if (statedWords.isEmpty()) {
            return qualifiedLine;
        }
        return qualifiedLine.qualifiedWith(String.join(
            KmuStringKeys.get(KmuStringKeys.OWNER_MAP_TOOLTIP_QUALIFIER_SEPARATOR),
            statedWords));
    }

    // Every word the facts call for, in the order they are read. A straight ordered pass over
    // independent predicates, so each word is one clause here rather than a re-reckoning of which
    // of the others it outranks - a hidden colony the economy does not list has two things to say
    // about itself, and a chain of pairwise choices would have to be reopened for every word added.
    //
    // The order runs from what a place is to how it is concealed: the layer's own leading finding,
    // then its kind, then the ways it may be out of sight.
    private static List<String> resolveWords(ColonyQualifierFacts facts) {

        var words = new ArrayList<String>();

        var concealment = facts.concealment();

        // Already worded, being the layer's finding rather than one of this vocabulary's.
        if (facts.leadingFinding() != null) {
            words.add(facts.leadingFinding());
        }
        resolveKindWord(facts.kind()).ifPresent(words::add);

        // Displacement rather than a pair: an undiscovered colony is concealed from the player by
        // that alone, and the market's own flag adds nothing a reader could act on.
        //
        // A concealment the sector openly points at says nothing either. The word is what parts a
        // base keeping itself out of sight from an ordinary colony, and a landmark that merely
        // keeps no comm directory wears the identical flag - so calling it out beside a pirate base
        // would say the two are the same sort of place.
        if (!concealment.isDiscoveredByPlayer()) {
            words.add(KmuStringKeys.get(KmuStringKeys.OWNER_MAP_TOOLTIP_QUALIFIER_UNDISCOVERED));
        } else if (concealment.isHiddenMarket() && !concealment.isOpenlyKnownMarket()) {
            words.add(KmuStringKeys.get(KmuStringKeys.OWNER_MAP_TOOLTIP_QUALIFIER_HIDDEN));
        }
        // The fallback, speaking only where nothing above it held, or it would repeat itself on every
        // derelict and every decivilised world, both off-economy by construction. The emptiness read
        // here is the conditions above rather than what is left once the name has taken its word -
        // which is what lets a word gilded into the name still have qualified.
        if (words.isEmpty() && !facts.isListedByEconomy()) {
            words.add(KmuStringKeys.get(KmuStringKeys.OWNER_MAP_TOOLTIP_QUALIFIER_UNLISTED));
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
                Optional.of(KmuStringKeys.get(KmuStringKeys.OWNER_MAP_TOOLTIP_QUALIFIER_ABANDONED));
            case UNGOVERNED_COLONY ->
                Optional.of(KmuStringKeys.get(KmuStringKeys.OWNER_MAP_TOOLTIP_QUALIFIER_DECIVILISED));
            case COLONY, OUTPOST -> Optional.empty();
        };
    }

    // The first word of the vocabulary the colony's own name carries, and where it starts in that
    // name - or nothing where the name says none of them. A station named "Abandoned Station" would
    // otherwise read as abandoned twice over, and the rule is written over the whole vocabulary
    // rather than attached to one word, since a name may carry any of them.
    //
    // First in the name's reading order rather than in the resolved order, because the reader meets
    // the name before the line's end and one gilded stretch is all a line carries. Two of the
    // vocabulary in one name is a shape nothing has ever needed, and a second gilded stretch would
    // cost the line model a list of parts where one part does.
    //
    // Read off the line's own label rather than off a name handed in beside it: what the reader can
    // see is exactly what the line says, and a second copy of the name would be free to disagree
    // with it. What counts as the name saying a word at all is KmlibStrings.findWholeWordIndex's.
    //
    // A line withholding its name says none of the vocabulary: the blocks drawn in its place stand for
    // words without spelling them, so there is nothing to match and nothing to gild. Every word due is
    // read at the end of the line instead, which is where a line whose name happens to carry none of
    // them states them anyway.
    private static WordInName findFirstWordInName(List<String> words, CellTooltipEntryLine line) {

        if (line.hasRedactedName()) {
            return null;
        }
        var colonyName = line.labelText();

        WordInName firstWordInName = null;

        for (var wordIndex = 0; wordIndex < words.size(); wordIndex++) {
            var nameStartIndex = KmlibStrings.findWholeWordIndex(colonyName, words.get(wordIndex));

            if (nameStartIndex == KmlibStrings.NO_WORD_MATCH) {
                continue;
            }
            if (firstWordInName == null || nameStartIndex < firstWordInName.nameStartIndex()) {
                firstWordInName = new WordInName(wordIndex, nameStartIndex);
            }
        }
        return firstWordInName;
    }

    /**
     * One of the vocabulary's words found in a colony's own name, and where the name says it.
     *
     * <p>The word is carried as its place in the resolved list rather than as the word itself, so the
     * caller drops the one entry it found rather than the first entry equal to it - the two differ
     * wherever the resolved list holds one word twice.
     *
     * @param wordIndex      where the word sits in the resolved list
     * @param nameStartIndex where the word starts in the name, counted in characters from its start
     */
    private record WordInName(
        int wordIndex,
        int nameStartIndex) {
    }
}
