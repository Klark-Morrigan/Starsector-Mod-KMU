package kmu.starsector.rat;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.suppression.OffScreenWidgetSuppressor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins the script's lifetime: registered transient and fresh per load, and taken back by the
 * instance this installed rather than by its class. When any of this runs at all is the composed
 * switch's, pinned where the switch is.
 */
class RandomAssortmentOfThingsCompatibilityInstallerTest {

    @Nested
    class InstallParkedMinimapSuppressor {

        @Test
        void installsTheSuppressorAsATransientScript() {
            // addTransientScript, never addScript: this script writes into a widget another mod
            // owns, so one restored from a save alongside the one added on load would have two of
            // them racing to hold and hand back the same opacity - and would bake a library class's
            // name into the file.
            var sectorMock = mock(SectorAPI.class);

            RandomAssortmentOfThingsCompatibilityInstaller
                .installParkedMinimapSuppressor(sectorMock);

            verify(sectorMock)
                .addTransientScript(any(OffScreenWidgetSuppressor.class));
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void installsAFreshSuppressorPerLoadSoNoWidgetIsCarriedAcross() {
            // The script holds the widget it wrote to and the opacity that widget was found at, so
            // one carried across loads would answer the newly loaded sector holding a widget from
            // the sector just left - and would hand that dead widget an opacity on its first frame.
            var firstLoadSectorMock = mock(SectorAPI.class);
            var secondLoadSectorMock = mock(SectorAPI.class);

            RandomAssortmentOfThingsCompatibilityInstaller
                .installParkedMinimapSuppressor(firstLoadSectorMock);
            RandomAssortmentOfThingsCompatibilityInstaller
                .installParkedMinimapSuppressor(secondLoadSectorMock);

            var firstSuppressor = ArgumentCaptor.forClass(OffScreenWidgetSuppressor.class);
            var secondSuppressor = ArgumentCaptor.forClass(OffScreenWidgetSuppressor.class);

            verify(firstLoadSectorMock)
                .addTransientScript(firstSuppressor.capture());
            verify(secondLoadSectorMock)
                .addTransientScript(secondSuppressor.capture());

            assertThat(secondSuppressor.getValue())
                .isNotSameAs(firstSuppressor.getValue());
        }

        @Test
        void toleratesANullSector() {

            var suppressorInstallOnNullSector = (Runnable) () ->
                RandomAssortmentOfThingsCompatibilityInstaller
                    .installParkedMinimapSuppressor(null);

            assertThatCode(suppressorInstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class RemoveParkedMinimapSuppressor {

        @Test
        void takesOutTheScriptThisInstalledRatherThanEveryScriptOfItsClass() {
            // OffScreenWidgetSuppressor is KMLib's, so another mod may be running its own over the
            // same sector. Removing by class would take that one too - and it hands the widget it
            // silenced its opacity back as it goes, so a widget this mod never touched would be
            // written into on the way out.
            var sectorMock = mock(SectorAPI.class);

            RandomAssortmentOfThingsCompatibilityInstaller
                .installParkedMinimapSuppressor(sectorMock);

            var installedSuppressor = ArgumentCaptor.forClass(OffScreenWidgetSuppressor.class);

            verify(sectorMock)
                .addTransientScript(installedSuppressor.capture());

            RandomAssortmentOfThingsCompatibilityInstaller
                .removeParkedMinimapSuppressor(sectorMock);

            verify(sectorMock)
                .removeTransientScript(installedSuppressor.getValue());
            verify(sectorMock, never())
                .removeTransientScriptsOfClass(any());
        }

        @Test
        void takesOutNothingWhereNothingIsHeld() {
            // A removal asked twice, which must not reach for a script belonging to whatever sector
            // was wired before this one.
            var sectorMock = mock(SectorAPI.class);

            RandomAssortmentOfThingsCompatibilityInstaller
                .installParkedMinimapSuppressor(sectorMock);
            RandomAssortmentOfThingsCompatibilityInstaller
                .removeParkedMinimapSuppressor(sectorMock);
            RandomAssortmentOfThingsCompatibilityInstaller
                .removeParkedMinimapSuppressor(sectorMock);

            verify(sectorMock)
                .removeTransientScript(any());
        }

        @Test
        void toleratesANullSector() {

            var suppressorRemovalOnNullSector = (Runnable) () ->
                RandomAssortmentOfThingsCompatibilityInstaller
                    .removeParkedMinimapSuppressor(null);

            assertThatCode(suppressorRemovalOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }
}
