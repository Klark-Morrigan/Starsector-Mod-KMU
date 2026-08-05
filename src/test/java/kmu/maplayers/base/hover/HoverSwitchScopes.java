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
        runWithHoverSwitches(true, body);
    }

    private static void runWithHoverSwitches(boolean isTooltipEnabled, Runnable body) {
        try (var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

            settingsMock
                .when(KmuMapLayerSettings::getMapHoveringEnabled)
                .thenReturn(true);
            settingsMock
                .when(KmuMapLayerSettings::getMapHoverTooltipEnabled)
                .thenReturn(isTooltipEnabled);

            body.run();
        }
    }
}
