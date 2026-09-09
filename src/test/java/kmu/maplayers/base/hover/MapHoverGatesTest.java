package kmu.maplayers.base.hover;

import java.util.function.BooleanSupplier;

import kmu.settings.KmuMapHoverSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the two tiers of hover switching that answer for every map layer: that the master takes both
 * kinds of feedback down with it, and that the pair under it takes down one kind each without
 * touching the other. The second is the case a single switch could not express - a player who wants
 * the box without the map lighting up, or the reverse - and it is what a layer's own pair hangs off.
 *
 * <p>And beside them the question of a different kind: whether the pass now running is one the
 * cursor can be located against at all. What that pins is which frames each permission adds, and -
 * as importantly - that withholding both leaves the vanilla hosts answering on their own, neither
 * permission being what admits them.
 */
final class MapHoverGatesTest {

    private static final BooleanSupplier NO_MAP_SHOWING = () -> false;
    private static final BooleanSupplier NOT_IN_GAME_SPACE = () -> false;
    private static final BooleanSupplier IN_GAME_SPACE = () -> true;
    private static final BooleanSupplier A_MAP_IS_SHOWING = () -> true;

    @Nested
    class IsHoverEffectsEnabled {

        @Test
        void isHoverEffectsEnabledIsTrueWithBothTiersOn() {

            try (var settingsMock = mockStatic(KmuMapHoverSettings.class)) {

                settingsMock
                    .when(KmuMapHoverSettings::isMapHoveringEnabled)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapHoverSettings::areMapHoverEffectsEnabled)
                    .thenReturn(true);

                assertThat(MapHoverGates.isHoverEffectsEnabled()).isTrue();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheHoveringMasterOff() {
            // The master reaches past its own kind: with it off there is no cursor read to draw off.
            try (var settingsMock = mockStatic(KmuMapHoverSettings.class)) {

                settingsMock
                    .when(KmuMapHoverSettings::isMapHoveringEnabled)
                    .thenReturn(false);
                settingsMock
                    .when(KmuMapHoverSettings::areMapHoverEffectsEnabled)
                    .thenReturn(true);

                assertThat(MapHoverGates.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheGlobalEffectsSwitchOff() {

            try (var settingsMock = mockStatic(KmuMapHoverSettings.class)) {

                settingsMock
                    .when(KmuMapHoverSettings::isMapHoveringEnabled)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapHoverSettings::areMapHoverEffectsEnabled)
                    .thenReturn(false);

                assertThat(MapHoverGates.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsUntouchedByTheGlobalTooltipSwitch() {
            // The pair is a pair, not a chain: switching the box off leaves the halo and wash alone.
            try (var settingsMock = mockStatic(KmuMapHoverSettings.class)) {

                settingsMock
                    .when(KmuMapHoverSettings::isMapHoveringEnabled)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapHoverSettings::areMapHoverEffectsEnabled)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapHoverSettings::isMapHoverTooltipEnabled)
                    .thenReturn(false);

                assertThat(MapHoverGates.isHoverEffectsEnabled())
                    .isTrue();
            }
        }
    }

    @Nested
    class IsHoverTooltipEnabled {

        @Test
        void isHoverTooltipEnabledIsTrueWithBothTiersOn() {

            try (var settingsMock = mockStatic(KmuMapHoverSettings.class)) {

                settingsMock
                    .when(KmuMapHoverSettings::isMapHoveringEnabled)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapHoverSettings::isMapHoverTooltipEnabled)
                    .thenReturn(true);

                assertThat(MapHoverGates.isHoverTooltipEnabled())
                    .isTrue();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheHoveringMasterOff() {

            try (var settingsMock = mockStatic(KmuMapHoverSettings.class)) {

                settingsMock
                    .when(KmuMapHoverSettings::isMapHoveringEnabled)
                    .thenReturn(false);
                settingsMock
                    .when(KmuMapHoverSettings::isMapHoverTooltipEnabled)
                    .thenReturn(true);

                assertThat(MapHoverGates.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheGlobalTooltipSwitchOff() {

            try (var settingsMock = mockStatic(KmuMapHoverSettings.class)) {

                settingsMock
                    .when(KmuMapHoverSettings::isMapHoveringEnabled)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapHoverSettings::isMapHoverTooltipEnabled)
                    .thenReturn(false);

                assertThat(MapHoverGates.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsUntouchedByTheGlobalEffectsSwitch() {
            // The other half of the pair: a player who wants the standings box without the map
            // lighting up under the cursor keeps the box.
            try (var settingsMock = mockStatic(KmuMapHoverSettings.class)) {

                settingsMock
                    .when(KmuMapHoverSettings::isMapHoveringEnabled)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapHoverSettings::isMapHoverTooltipEnabled)
                    .thenReturn(true);
                settingsMock
                    .when(KmuMapHoverSettings::areMapHoverEffectsEnabled)
                    .thenReturn(false);

                assertThat(MapHoverGates.isHoverTooltipEnabled())
                    .isTrue();
            }
        }
    }

    @Nested
    class IsCursorLocatableOn {

        @Test
        void isCursorLocatableOnIsTrueOnAVanillaHostWithNeitherPermissionGiven() {
            // Neither permission is the vanilla hosts, so with both withheld the hosts have to
            // answer on their own or the map would stop hovering on the screen it belongs to.
            runWithMouseoverPermissions(false, false, () ->
                assertThat(MapHoverGates.isCursorLocatableOn(A_MAP_IS_SHOWING, NOT_IN_GAME_SPACE))
                    .isTrue());
        }

        @Test
        void isCursorLocatableOnIsFalseOffTheVanillaHostsWithNeitherPermissionGiven() {
            // The other half of that: with both withheld, a foreign surface drawing with no map
            // open resolves a confident wrong answer, so the pass is not read at all.
            runWithMouseoverPermissions(false, false, () ->
                assertThat(MapHoverGates.isCursorLocatableOn(NO_MAP_SHOWING, IN_GAME_SPACE))
                    .isFalse());
        }

        @Test
        void isCursorLocatableOnIsTrueAnywhereWithTheGlobalPermissionGiven() {
            // Every pass there is, which is what the switch says: no map on screen and no game
            // space either - some other screen, with a mod's surface drawing over it.
            runWithMouseoverPermissions(true, false, () ->
                assertThat(MapHoverGates.isCursorLocatableOn(NO_MAP_SHOWING, NOT_IN_GAME_SPACE))
                    .isTrue());
        }

        @Test
        void isCursorLocatableOnIsTrueInGameSpaceWithTheGameSpacePermissionGiven() {
            // The frames a docked minimap is the only map on: no screen open, nothing drawn over
            // the campaign.
            runWithMouseoverPermissions(false, true, () ->
                assertThat(MapHoverGates.isCursorLocatableOn(NO_MAP_SHOWING, IN_GAME_SPACE))
                    .isTrue());
        }

        @Test
        void isCursorLocatableOnIsFalseOutsideGameSpaceWithOnlyTheGameSpacePermissionGiven() {
            // What makes the narrower permission narrower, and what closes the docked minimap
            // without naming a mod: a panel is parked on exactly the conditions that end game
            // space, so the frames it is parked on are frames this permission never reaches.
            runWithMouseoverPermissions(false, true, () ->
                assertThat(MapHoverGates.isCursorLocatableOn(NO_MAP_SHOWING, NOT_IN_GAME_SPACE))
                    .isFalse());
        }

        // Settles both permissions for the length of one body. The gate reads them statically, so
        // they can only be stood in for within a scope - and a case naming one of them alone would
        // be answered by whatever the other happened to be mocked to.
        private void runWithMouseoverPermissions(
                boolean isGlobal,
                boolean isInGameSpacePermitted,
                Runnable body) {

            try (var settingsMock = mockStatic(KmuMapHoverSettings.class)) {

                settingsMock
                    .when(KmuMapHoverSettings::isMapLayerMouseoverGlobal)
                    .thenReturn(isGlobal);
                settingsMock
                    .when(KmuMapHoverSettings::isMapLayerMouseoverEnabledInGameSpace)
                    .thenReturn(isInGameSpacePermitted);

                body.run();
            }
        }
    }
}
