package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the default holding provider's one job: delegating to the same two resolvers the
 * render pass used to call inline, branching on whether a bloc is spotlighted. Off filter it
 * reproduces {@link SectorPolitics#resolveDominantHolderBySystemId} with nothing contested;
 * under a filter it reproduces {@link FilteredPolitics#resolveFilteredHolder}, passing its
 * holders and contested set straight through. The resolvers themselves read the live economy,
 * so they are stubbed here and covered end to end in their own integration suites.
 */
final class DefaultHolderProviderTest {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    @Nested
    class ResolveHolder {

        @Test
        void resolveHolderReturnsTheDominantHoldersAndNothingContestedOffFilter() {

            var sectorMock = mock(SectorAPI.class);
            var pass = HolderPass.over(sectorMock, false, HolderGrouping.identity());
            var holders = Map.of(
                "owned-system",
                new DominantHolder("hegemony", PRIMARY, SECONDARY));

            try (var sectorPoliticsMock = mockStatic(SectorPolitics.class)) {

                sectorPoliticsMock
                    .when(() -> SectorPolitics.resolveDominantHolderBySystemId(pass))
                    .thenReturn(holders);

                var resolution = DefaultHolderProvider.INSTANCE.resolveHolder(pass, null);

                assertThat(resolution.ownerBySystemId())
                    .isEqualTo(holders);
                assertThat(resolution.contestedSystemIds())
                    .isEmpty();
                assertThat(resolution.unfilledSystemIds())
                    .isEmpty();
            }
        }

        @Test
        void resolveHolderPassesThroughThePresenceAwareResolverWhenABlocIsSpotlighted() {

            var sectorMock = mock(SectorAPI.class);
            var pass = HolderPass.over(sectorMock, false, HolderGrouping.identity());
            var holders = Map.of(
                "owned-system",
                new DominantHolder("$spotlit", PRIMARY, SECONDARY));

            var contested = Set.of("owned-system");
            var filtered = new FilteredPolitics.FilteredHolder(holders, contested);

            try (var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                filteredPoliticsMock
                    .when(() -> FilteredPolitics.resolveFilteredHolder(pass, "hegemony"))
                    .thenReturn(filtered);

                var resolution = DefaultHolderProvider.INSTANCE.resolveHolder(pass, "hegemony");

                assertThat(resolution.ownerBySystemId())
                    .isEqualTo(holders);
                assertThat(resolution.contestedSystemIds())
                    .isEqualTo(contested);

                // The presence-aware resolver reports no unfilled systems; the filter path leaves
                // that fill state empty.
                assertThat(resolution.unfilledSystemIds())
                    .isEmpty();
            }
        }
    }
}
