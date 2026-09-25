package kmu.maplayers.ownermap;

import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;

/**
 * The readings of the sidebar preferences a test poses a bake under, each varying the one pick its
 * suite is about and leaving the other four inert.
 *
 * <p>One home for them because the inert four are the same four everywhere, and spelt out per suite
 * they are four chances for two suites to disagree about what "no other pick" means - which shows
 * up as one suite's build receding, spelling or outlining something the other's does not, for a
 * reason neither states.
 *
 * <p>Inert here means: nothing spotlighted, neither backdrop receding, no uninhabited outline, and
 * <em>no names drawn</em>. The names are the one default that is not the shipped one, and
 * deliberately: a build that draws them reaches the label font, which no test JVM loads, so a suite
 * whose subject is not the names should not be paying for them. The suite whose subject is the
 * names says so through {@link #createInputsSpellingNames}.
 *
 * <p>{@link ContentInputsTest} builds its own readings rather than taking these: it is what pins
 * what the record means, so a fixture standing between it and the constructor would be pinning the
 * fixture.
 */
public final class ContentInputsFixtures {

    // Fixtures only; never instantiated.
    private ContentInputsFixtures() {
    }

    /**
     * A reading with nothing picked at all - the one a suite takes when the preferences reach its
     * subject only by having to be there.
     *
     * @return the inert reading
     */
    public static ContentInputs createInertInputs() {
        return createInputsSpotlighting(null);
    }

    /**
     * A reading spotlighting one bloc, with nothing receding behind it.
     *
     * @param selectedBlocId the spotlighted bloc, or null for a build off filter
     * @return that pick over the inert reading
     */
    public static ContentInputs createInputsSpotlighting(String selectedBlocId) {
        return createInputsRecedingBehind(selectedBlocId, ElementStyleAdjustment.NONE);
    }

    /**
     * A reading spotlighting one bloc with a stated recede behind it, for the suites about what the
     * rest of the sector does while a spotlight is up.
     *
     * @param selectedBlocId  the spotlighted bloc, or null for a build off filter
     * @param filterRecede    how every non-spotlit bloc draws
     * @return those two picks over the inert reading
     */
    public static ContentInputs createInputsRecedingBehind(
            String selectedBlocId,
            ElementStyleAdjustment filterRecede) {

        return new ContentInputs(
            selectedBlocId,
            filterRecede,
            ElementStyleAdjustment.NONE, // No backdrop recede.
            FactionNameFormatChoice.NONE, // No names drawn.
            false); // No uninhabited outline.
    }

    /**
     * A reading carrying a view's own backdrop recede, for the suites about which blocs a view
     * hands that recede to.
     *
     * @param backdropRecede how a bloc outside a group draws
     * @return that pick over the inert reading
     */
    public static ContentInputs createInputsRecedingNonAllied(
            ElementStyleAdjustment backdropRecede) {

        return new ContentInputs(
            null, // Nothing spotlighted.
            ElementStyleAdjustment.NONE, // No filter recede.
            backdropRecede,
            FactionNameFormatChoice.NONE, // No names drawn.
            false); // No uninhabited outline.
    }

    /**
     * A reading stating whether never-settled space strokes its outline, for the suites about the
     * one pick that reaches the theme rather than the fills.
     *
     * @param isUninhabitedOutlineDrawn whether uninhabited cells outline
     * @return that pick over the inert reading
     */
    public static ContentInputs createInputsOutlining(boolean isUninhabitedOutlineDrawn) {
        return new ContentInputs(
            null, // Nothing spotlighted.
            ElementStyleAdjustment.NONE, // No filter recede.
            ElementStyleAdjustment.NONE, // No backdrop recede.
            FactionNameFormatChoice.NONE, // No names drawn.
            isUninhabitedOutlineDrawn);
    }

    /**
     * A reading spelling the cluster names a stated way, for the suites about what a drawn name
     * costs the map around it.
     *
     * @param nameFormat how this build's cluster labels spell their holders' names
     * @return that pick over the inert reading
     */
    public static ContentInputs createInputsSpellingNames(FactionNameFormatChoice nameFormat) {
        return new ContentInputs(
            null, // Nothing spotlighted.
            ElementStyleAdjustment.NONE, // No filter recede.
            ElementStyleAdjustment.NONE, // No backdrop recede.
            nameFormat,
            false); // No uninhabited outline.
    }
}
