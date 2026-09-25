package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.HolderResolution;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.holding.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the default holding provider's one job: delegating to the same two resolvers the
 * render pass used to call inline, branching on whether a bloc is spotlighted. Off filter it
 * reproduces {@link SectorPolitics#resolveDominantHolderBySystemKey} with nothing contested;
 * under a filter it hands on {@link FilteredPolitics#resolveFilteredHolder}'s resolution whole.
 * The resolvers themselves read the live economy, so they are stubbed here and covered end to end
 * in their own integration suites.
 */
final class DefaultHolderProviderTest {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    private static final SystemKey OWNED_SYSTEM = buildCellKey("owned-system");

    @Nested
    class ResolveHolder {

        @Test
        void resolveHolderReturnsTheDominantHoldersAndNothingContestedOffFilter() {

            var sectorMock = mock(SectorAPI.class);
            var pass = HolderPass.over(sectorMock, UNDER_THE_FOG, HolderGrouping.identity());
            var holders = Map.of(
                OWNED_SYSTEM,
                new SystemOwner("hegemony", PRIMARY, SECONDARY));

            try (var sectorPoliticsMock = mockStatic(SectorPolitics.class)) {

                sectorPoliticsMock
                    .when(() -> SectorPolitics.resolveDominantHolderBySystemKey(pass))
                    .thenReturn(holders);

                var resolution = DefaultHolderProvider.INSTANCE.resolveHolder(pass, null);

                assertThat(resolution.ownerBySystemKey())
                    .isEqualTo(holders);
                assertThat(resolution.contestedSystemKeys())
                    .isEmpty();
                assertThat(resolution.unfilledSystemKeys())
                    .isEmpty();
            }
        }

        @Test
        void resolveHolderPassesThroughThePresenceAwareResolverWhenABlocIsSpotlighted() {

            var sectorMock = mock(SectorAPI.class);
            var pass = HolderPass.over(sectorMock, UNDER_THE_FOG, HolderGrouping.identity());
            var holders = Map.of(
                OWNED_SYSTEM,
                new SystemOwner("$spotlit", PRIMARY, SECONDARY));

            var contested = Set.of(OWNED_SYSTEM);
            var filtered = new HolderResolution(holders, contested, Set.of());

            try (var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                filteredPoliticsMock
                    .when(() -> FilteredPolitics.resolveFilteredHolder(pass, "hegemony"))
                    .thenReturn(filtered);

                // Handed on whole: the presence-aware resolver already answers the full resolution,
                // so nothing about its holders or its contested and unfilled sets is restated here.
                assertThat(DefaultHolderProvider.INSTANCE.resolveHolder(pass, "hegemony"))
                    .isSameAs(filtered);
            }
        }
    }
}
