package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the default holding provider's one job: delegating to the same two resolvers the
 * render pass used to call inline, branching on whether a bloc is spotlighted. Off filter it
 * reproduces {@link SectorPolitics#resolveDominantHolderBySystemKey} with nothing contested;
 * under a filter it reproduces {@link FilteredPolitics#resolveFilteredHolder}, passing its
 * holders and contested set straight through. The resolvers themselves read the live economy,
 * so they are stubbed here and covered end to end in their own integration suites.
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
            var pass = HolderPass.over(sectorMock, BASE_FOG, HolderGrouping.identity());
            var holders = Map.of(
                OWNED_SYSTEM,
                new DominantHolder("hegemony", PRIMARY, SECONDARY));

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
            var pass = HolderPass.over(sectorMock, BASE_FOG, HolderGrouping.identity());
            var holders = Map.of(
                OWNED_SYSTEM,
                new DominantHolder("$spotlit", PRIMARY, SECONDARY));

            var contested = Set.of(OWNED_SYSTEM);
            var filtered = new FilteredPolitics.FilteredHolder(holders, contested);

            try (var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                filteredPoliticsMock
                    .when(() -> FilteredPolitics.resolveFilteredHolder(pass, "hegemony"))
                    .thenReturn(filtered);

                var resolution = DefaultHolderProvider.INSTANCE.resolveHolder(pass, "hegemony");

                assertThat(resolution.ownerBySystemKey())
                    .isEqualTo(holders);
                assertThat(resolution.contestedSystemKeys())
                    .isEqualTo(contested);

                // The presence-aware resolver reports no unfilled systems; the filter path leaves
                // that fill state empty.
                assertThat(resolution.unfilledSystemKeys())
                    .isEmpty();
            }
        }
    }
}
