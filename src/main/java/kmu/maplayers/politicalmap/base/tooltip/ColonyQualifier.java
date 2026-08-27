package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.visibility.ColonyKind;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
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
 * <p>Suppression is by the condition holding rather than by anything being stated at the end of the
 * line, which is what keeps the fallback honest: a derelict named for what it is says its word inside
 * its name and still suppresses {@code unlisted} below, rather than falling through to the weaker
 * statement the suppression was meant to improve on.
 *
 * <p>A word the colony's own name already carries moves into the name rather than being repeated
 * after it: the stretch that says it is drawn in the qualifier's gold where it stands. The finding is
 * made exactly once, and it is made where the reader is already looking - where dropping the word
 * outright left the one shape that most needs telling apart from an ordinary colony with nothing on
 * its line at all. At most one word moves; the rest are read at the end of the line as usual.
 *
 * <p>Which word moves is settled by the name and not by the resolution. The list, the order and the
 * suppressions are identical either way: a gilded word has qualified in every sense, resolved by the
 * same predicate and drawn somewhere else, so a reading of what a colony is cannot come out
 * differently for a colony that happens to be named after itself.
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

    // What the name search answers where the colony is not called the word at all. Named rather than
    // read as a bare negative index, since the search is over positions and one of them being an
    // absence is the only thing a caller has to know about it.
    private static final int NOT_IN_NAME = -1;

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
        var wordInName = findFirstWordInName(words, line.labelText());
        var statedWords = new ArrayList<>(words);
        var qualifiedLine = line;

        // The word the name already carries is gilded where it stands and leaves the list, so the
        // finding is made exactly once. Every other word is read at the end of the line as usual.
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
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_SEPARATOR),
            statedWords));
    }

    // Every word the facts call for, in the order they are read. A straight ordered pass over
    // independent predicates, so a word added later is one clause here rather than a re-reckoning
    // of which of the existing ones it outranks.
    private static List<String> resolveWords(ColonyQualifierFacts facts) {

        var words = new ArrayList<String>();

        var concealment = facts.concealment();

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
        if (!concealment.isDiscoveredByPlayer()) {
            words.add(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_UNDISCOVERED));
        } else if (concealment.isHiddenMarket() && !concealment.isOpenlyKnownMarket()) {
            words.add(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_HIDDEN));
        }
        // The fallback, and the emptiness read here is the conditions above rather than what is left
        // once the name has taken its word - which is what lets a word gilded into the name still
        // have qualified.
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

    // The first word of the vocabulary the colony's own name carries, and where it starts in that
    // name - or nothing where the name says none of them. A station named "Abandoned Station" would
    // otherwise read as abandoned twice over, and the rule is written over the whole vocabulary
    // rather than attached to the one word it fires for today.
    //
    // First in the name's reading order rather than in the resolved order, because the reader meets
    // the name before the line's end and one gilded stretch is all a line carries. Two of the
    // vocabulary in one name is a shape nothing has ever needed, and a second gilded stretch would
    // cost the line model a list of parts where one part does.
    //
    // Read off the line's own label rather than off a name handed in beside it: what the reader can
    // see is exactly what the line says, and a second copy of the name would be free to disagree
    // with it.
    private static WordInName findFirstWordInName(List<String> words, String colonyName) {

        WordInName firstWordInName = null;

        for (var wordIndex = 0; wordIndex < words.size(); wordIndex++) {
            var nameStartIndex = findWholeWordIndex(colonyName, words.get(wordIndex));

            if (nameStartIndex == NOT_IN_NAME) {
                continue;
            }
            if (firstWordInName == null || nameStartIndex < firstWordInName.nameStartIndex()) {
                firstWordInName = new WordInName(wordIndex, nameStartIndex);
            }
        }
        return firstWordInName;
    }

    // Where the name says the word as a word of its own, case-insensitively, or that it does not.
    //
    // A whole word rather than a bare containment, because the consequence is now gold letters in
    // the middle of a name rather than a word quietly not being said: "Abandonedium" would be gilded
    // across its first nine characters.
    //
    // Matched against the name as it is spelled rather than against a lowered copy of it, so the
    // position returned indexes the name the line actually carries - a copy folded to one case is
    // free to come out a different length and would slide the gilding along the name.
    private static int findWholeWordIndex(String colonyName, String word) {

        for (var index = 0; index + word.length() <= colonyName.length(); index++) {

            if (colonyName.regionMatches(true, index, word, 0, word.length())
                    && isWholeWordAt(colonyName, index, word.length())) {
                return index;
            }
        }
        return NOT_IN_NAME;
    }

    // Whether the stretch found at that position stands alone in the name rather than opening or
    // closing a longer word.
    private static boolean isWholeWordAt(String colonyName, int matchIndex, int wordLength) {
        return isWordBoundaryAt(colonyName, matchIndex - 1)
            && isWordBoundaryAt(colonyName, matchIndex + wordLength);
    }

    // Whether that position parts one word from another - anything that is not a letter or a digit,
    // and the ends of the name itself. A hyphen among them, since "Abandoned-Station" says the word
    // as plainly as the spaced form does and a reader would not forgive the box for missing it.
    private static boolean isWordBoundaryAt(String colonyName, int index) {
        return index < 0
            || index >= colonyName.length()
            || !Character.isLetterOrDigit(colonyName.charAt(index));
    }

    /**
     * One of the vocabulary's words found in a colony's own name, and where the name says it.
     *
     * <p>The word is carried as its place in the resolved list rather than as the word itself, so the
     * caller drops the one entry it found rather than the first entry equal to it - which is the same
     * thing today and stops being so the moment the vocabulary can resolve one word twice.
     *
     * @param wordIndex      where the word sits in the resolved list
     * @param nameStartIndex where the word starts in the name, counted in characters from its start
     */
    private record WordInName(
        int wordIndex,
        int nameStartIndex) {
    }
}
