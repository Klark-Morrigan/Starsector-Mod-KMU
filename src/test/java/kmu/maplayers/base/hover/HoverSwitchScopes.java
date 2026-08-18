package kmu.maplayers.base.hover;

import kmu.settings.KmuMapLayerSettings;

import static org.mockito.Mockito.mockStatic;

/**
 * Answers the settings tier above every hover gate for the length of one body: the hovering master
 * and the global tooltip switch, which {@link MapHoverGates} reads statically and so can only be
 * stood in for within a scope.
 *
 * <p>Shared rather than written out per suite because every hover test opens by settling the same
 * two switches, and one that spelled them out itself would be free to open the master while meaning
 * to test the tooltip switch - naming the tier once keeps a case about what it says it is about.
 */
public final class HoverSwitchScopes {

    private HoverSwitchScopes() {
    }

    /**
     * Runs body with the tooltip switch off under an open master, so what the body observes is that
     * switch alone rather than hovering being off altogether.
     *
     * @param body the case to run inside the scope
     */
    public static void runWithHoverTooltipSwitchOff(Runnable body) {
        runWithHoverSwitches(false, body);
    }

    /**
     * Runs body with both switches on - the tier above every gate a hover test is usually about.
     *
     * @param body the case to run inside the scope
     */
    public static void runWithHoverTooltipSwitchOn(Runnable body) {
        runWithHoverSwitches(true, false, body);
    }

    /**
     * Runs body with both switches on and the game-space permission granted, for a case about the
     * frames where the player is looking at the campaign world rather than at a map screen.
     *
     * <p>Named apart from the pair above rather than defaulted into them, because granting it is
     * what a case about game space is testing: a scope that granted it to everyone would leave every
     * other case unable to say that withholding it closes those frames.
     *
     * @param body the case to run inside the scope
     */
    public static void runWithHoverTooltipSwitchOnInGameSpace(Runnable body) {
        runWithHoverSwitches(true, true, body);
    }

    private static void runWithHoverSwitches(
            boolean isTooltipEnabled,
            boolean isGameSpacePermitted,
            Runnable body) {

        try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

            settingsMock
                .when(KmuMapLayerSettings::getMapHoveringEnabled)
                .thenReturn(true);
            settingsMock
                .when(KmuMapLayerSettings::getMapHoverTooltipEnabled)
                .thenReturn(isTooltipEnabled);
            settingsMock
                .when(KmuMapLayerSettings::getMapLayerMouseoverIsEnabledInGameSpace)
                .thenReturn(isGameSpacePermitted);

            body.run();
        }
    }
}
