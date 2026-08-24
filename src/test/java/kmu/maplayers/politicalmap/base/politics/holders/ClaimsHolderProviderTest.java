package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.colonies.KnownColonyReader;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.SystemColoniesIndex;
import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.base.visibility.ColonyKnowledge;
import kmu.maplayers.base.visibility.ColonyVisibility;
import kmu.maplayers.base.visibility.RevelationGate;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorClaims;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the claims-only provider's one job: handing the claim resolve through as the whole holding,
 * every claimed system solid, spotlit where a bloc is picked. The claim resolve and the spotlight
 * holder are both stubbed - each has its own suite - so these tests isolate that this provider adds
 * no fill exceptions of its own either on or off filter.
 */
final class ClaimsHolderProviderTest {

    // A rule that is plainly not the fog alone, so a reader opened under a default of its own
    // fails the pass-through case rather than passing it by coincidence.
    private static final ColonyVisibility GATED_VISIBILITY = new ColonyVisibility(
        false,
        DecivilisedMarkets.DEFAULT_SURVEY_LEVEL,
        Set.of(RevelationGate.SPACE_DERELICTS, RevelationGate.HIDDEN_COLONIES));

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    @Nested
    class ResolveHolder {

        @Test
        void resolveHolderPaintsEveryClaimSolidWithNoFillExceptions() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.over(sectorMock, BASE_FOG, grouping);
            var claimHolder = new DominantHolder("hegemony", PRIMARY, SECONDARY);
            var provider = new ClaimsHolderProvider((visibility, colonies) -> claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        pass,
                        claimReaderMock))
                    .thenReturn(Map.of("claimed", claimHolder));

                var resolution = provider.resolveHolder(pass, null);

                // The claim resolve is the whole holder map, and nothing draws hatched or unfilled -
                // every claim paints solid, so the fill split takes its whole-cluster-solid fast path.
                assertThat(resolution.ownerBySystemId())
                    .containsExactly(Map.entry("claimed", claimHolder));
                assertThat(resolution.contestedSystemIds())
                    .isEmpty();
                assertThat(resolution.unfilledSystemIds())
                    .isEmpty();
            }
        }

        @Test
        void resolveHolderRekeysTheSpotlitBlocsOwnClaimsAndRecedesTheRest() {

            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.over(sectorMock, BASE_FOG, grouping);
            var spotlightHolder = new DominantHolder("$spotlit", PRIMARY, SECONDARY);
            var ownClaimHolder = new DominantHolder("hegemony", PRIMARY, SECONDARY);
            var rivalClaimHolder = new DominantHolder("tritachyon", PRIMARY, SECONDARY);
            var claims = new LinkedHashMap<String, DominantHolder>();

            claims.put("own-claimed", ownClaimHolder);
            claims.put("rival-claimed", rivalClaimHolder);

            var provider = new ClaimsHolderProvider((visibility, colonies) -> claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        pass,
                        claimReaderMock))
                    .thenReturn(claims);

                filteredPoliticsMock
                    .when(() -> FilteredPolitics
                        .resolveSpotlitHolder(sectorMock, grouping, "hegemony"))
                    .thenReturn(spotlightHolder);

                var resolution = provider.resolveHolder(pass, "hegemony");

                // The pick actually recedes the sector: the spotlit faction's claims carry the key
                // that keeps them at full strength, while a rival's claim keeps its plain bloc key
                // for the style layer to mute and desaturate.
                assertThat(resolution.ownerBySystemId().get("own-claimed"))
                    .isSameAs(spotlightHolder);
                assertThat(resolution.ownerBySystemId().get("rival-claimed"))
                    .isSameAs(rivalClaimHolder);

                // The spotlight changes only which key a claim carries: every claim still paints
                // solid, so the fill split keeps its whole-cluster-solid fast path under a filter.
                assertThat(resolution.contestedSystemIds())
                    .isEmpty();
                assertThat(resolution.unfilledSystemIds())
                    .isEmpty();
            }
        }

        @Test
        void resolveHolderRecedesTheWholeSectorForAPickThatClaimsNothing() {
            // The picker offers every bloc that claims or holds something, so a colony holder that
            // claims nowhere is pickable. Receding the whole sector is then the answer rather than a
            // degenerate case - it is what "this faction claims nothing" looks like - and nothing here
            // detects it: the rekey simply finds no system belonging to the pick, and every claim
            // keeps its own key for the style layer to mute.
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var grouping = HolderGrouping.identity();
            var pass = HolderPass.over(sectorMock, BASE_FOG, grouping);
            var spotlightHolder = new DominantHolder("$spotlit", PRIMARY, SECONDARY);
            var claimHolder = new DominantHolder("hegemony", PRIMARY, SECONDARY);
            var provider = new ClaimsHolderProvider((visibility, colonies) -> claimReaderMock);

            try (var sectorClaimsMock = mockStatic(SectorClaims.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(
                        pass,
                        claimReaderMock))
                    .thenReturn(Map.of("claimed", claimHolder));

                // The spotlight holder resolves as it would for any pick; what makes the difference
                // is that no claimed system carries this bloc, not that the holder is missing.
                filteredPoliticsMock
                    .when(() -> FilteredPolitics
                        .resolveSpotlitHolder(sectorMock, grouping, "tritachyon"))
                    .thenReturn(spotlightHolder);

                var resolution = provider.resolveHolder(pass, "tritachyon");

                assertThat(resolution.ownerBySystemId())
                    .containsExactly(Map.entry("claimed", claimHolder));
                assertThat(resolution.ownerBySystemId())
                    .doesNotContainValue(spotlightHolder);
            }
        }

        @Test
        void resolveHolderOpensItsClaimReaderOverThePassesOwnWalk() {
            // A reader is only as current as the colonies behind it, so this provider holds the
            // means of opening one rather than a reader: the one it uses is opened over the pass
            // being resolved and discarded with it, which is also what shares that pass's walk of
            // each system rather than adding one.
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var pass = HolderPass.over(sectorMock, BASE_FOG, HolderGrouping.identity());
            var openedOver = new ArrayList<SystemColoniesIndex>();

            var provider = new ClaimsHolderProvider((visibility, colonies) -> {
                openedOver.add(colonies);
                return claimReaderMock;
            });

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(pass, claimReaderMock))
                    .thenReturn(Map.of());

                provider.resolveHolder(pass, null);

                assertThat(openedOver)
                    .containsExactly(pass.colonies());
            }
        }

        @Test
        void resolveHolderOpensItsClaimReaderUnderThePassesOwnVisibilityRule() {
            // A reader states no rule of its own, so what its breakdowns report the player may be
            // told is whatever rule the pass was resolved under. One invented where the reader is
            // opened would let a claim account name colonies the map around it is holding back.
            var sectorMock = mock(SectorAPI.class);
            var claimReaderMock = mock(ClaimReader.class);
            var pass = HolderPass.over(sectorMock, GATED_VISIBILITY, HolderGrouping.identity());
            var openedUnder = new ArrayList<KnownColonyReader>();

            var provider = new ClaimsHolderProvider((knownColonyReader, colonies) -> {
                openedUnder.add(knownColonyReader);
                return claimReaderMock;
            });

            try (var sectorClaimsMock = mockStatic(SectorClaims.class)) {

                sectorClaimsMock
                    .when(() -> SectorClaims.resolveClaimingHolderBySystemId(pass, claimReaderMock))
                    .thenReturn(Map.of());

                provider.resolveHolder(pass, null);

                // The port itself is the pass's own knowledge, so what it was opened under is
                // read back off the rule that knowledge carries - stated against the literal the
                // pass was built with rather than against the pass a second time.
                assertThat(openedUnder)
                    .singleElement(as(type(ColonyKnowledge.class)))
                    .extracting(ColonyKnowledge::rule)
                    .isEqualTo(GATED_VISIBILITY);
            }
        }
    }
}
