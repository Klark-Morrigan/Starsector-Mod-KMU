package kmu.maplayers.base.hover;

import kmu.settings.KmuMapHoverSettings;

import static org.mockito.Mockito.mockStatic;

/**
 * Answers every hover switch the tiers above a gate read, for the length of one body:
 * {@link MapHoverGates} reads them statically and so they can only be stood in for within a scope.
 *
 * <p>Shared rather than written out per suite because every hover test opens by settling the same
 * switches, and one that spelled them out itself would be free to open the master while meaning to
 * test the tooltip switch - naming the tier once keeps a case about what it says it is about.
 *
 * <p>Each scope settles <em>all</em> of them rather than only the ones its name mentions, including
 * the global permission, which every scope withholds. An unstubbed switch reads false under the mock
 * and so would behave the same today; stating it is what stops a case passing for a reason it never
 * chose, and the global permission in particular short-circuits every screen read below it, which
 * would make a case about those reads observe nothing at all.
 */
public final class HoverSwitchScopes {

    // The states as named values, since the two flags are the same type and mean nothing at a call
    // site that spells them out in order.
    private static final HoverSwitchState TOOLTIP_OFF = new HoverSwitchState(false, false);
    private static final HoverSwitchState TOOLTIP_ON = new HoverSwitchState(true, false);
    private static final HoverSwitchState TOOLTIP_ON_IN_GAME_SPACE = new HoverSwitchState(true, true);

    private HoverSwitchScopes() {
    }

    /**
     * Runs body with the tooltip switch off under an open master, so what the body observes is that
     * switch alone rather than hovering being off altogether.
     *
     * @param body the case to run inside the scope
     */
    public static void runWithHoverTooltipSwitchOff(Runnable body) {
        runWithHoverSwitches(TOOLTIP_OFF, body);
    }

    /**
     * Runs body with the master and the tooltip switch on and both permissions withheld - the tier
     * above every gate a hover test is usually about, over the vanilla hosts answering alone.
     *
     * @param body the case to run inside the scope
     */
    public static void runWithHoverTooltipSwitchOn(Runnable body) {
        runWithHoverSwitches(TOOLTIP_ON, body);
    }

    /**
     * Runs body with the same switches on and the game-space permission granted, for a case about the
     * frames where the player is looking at the campaign world rather than at a map screen.
     *
     * <p>Named apart from the pair above rather than defaulted into them, because granting it is
     * what a case about game space is testing: a scope that granted it to everyone would leave every
     * other case unable to say that withholding it closes those frames.
     *
     * @param body the case to run inside the scope
     */
    public static void runWithHoverTooltipSwitchOnInGameSpace(Runnable body) {
        runWithHoverSwitches(TOOLTIP_ON_IN_GAME_SPACE, body);
    }

    private static void runWithHoverSwitches(HoverSwitchState state, Runnable body) {

        try (var settingsMock = mockStatic(KmuMapHoverSettings.class)) {

            settingsMock
                .when(KmuMapHoverSettings::isMapHoveringEnabled)
                .thenReturn(true);
            settingsMock
                .when(KmuMapHoverSettings::isMapHoverTooltipEnabled)
                .thenReturn(state.isTooltipEnabled());
            settingsMock
                .when(KmuMapHoverSettings::isMapLayerMouseoverGlobal)
                .thenReturn(false);
            settingsMock
                .when(KmuMapHoverSettings::isMapLayerMouseoverEnabledInGameSpace)
                .thenReturn(state.isGameSpacePermitted());

            body.run();
        }
    }

    /**
     * The switches a scope settles, as one value so the flags are named where the scope is chosen
     * rather than ordered where it is opened.
     *
     * @param isTooltipEnabled      whether hover boxes may draw on any layer, under an open master
     * @param isGameSpacePermitted  whether the layers may answer the cursor on frames showing the
     *                              campaign world itself
     */
    private record HoverSwitchState(
        boolean isTooltipEnabled,
        boolean isGameSpacePermitted) {
    }
}
