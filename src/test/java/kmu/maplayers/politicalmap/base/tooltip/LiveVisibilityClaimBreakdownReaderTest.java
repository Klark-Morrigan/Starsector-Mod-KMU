package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;

import kmu.maplayers.base.visibility.MapVisibilityRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.List;

import static kmlib.starsector.colonies.ColonyVisibility.BASE_FOG;

import static kmu.maplayers.base.visibility.ColonyVisibilityFixtures.UNDER_THE_REVEAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the one thing this binding decides: when the player's visibility rule is sampled.
 *
 * <p>The boxes it serves are singletons held for the session, so a rule sampled once at
 * construction would look right on the first hover and drift silently afterwards - a claim
 * breakdown reports the player's knowledge of a market as a flag on it rather than by leaving the
 * market out, so a stale rule shows up as a name the box should have withheld and nothing else.
 * Two reads under two rules is the only case that can tell the two designs apart.
 *
 * <p>Beside it, the decree: it reaches no colony, so no rule applies to it, and the port
 * undertakes that it stays a single memory read. Asserting that the settings go untouched is what
 * keeps a later edit from routing it through the live read for symmetry's sake.
 */
class LiveVisibilityClaimBreakdownReaderTest {

    private static final String DECREED_FACTION_ID = "hegemony";

    // A system under no decree at all, named so the case reads as the absence it poses rather
    // than as an argument somebody forgot to fill in.
    private static final String NO_DECREE = null;

    @Nested
    class ReadBreakdown {

        @Test
        void readBreakdownOpensItsReaderUnderTheRuleInForceAtThatMoment() {

            var openedRules = new ArrayList<ColonyVisibility>();

            try (var visibilityRulesMock = mockStatic(MapVisibilityRules.class);
                    var ignoredReaders = captureOpenedRules(openedRules)) {

                visibilityRulesMock
                    .when(MapVisibilityRules::readFromLunaSettings)
                    .thenReturn(new MapVisibilityRules(UNDER_THE_REVEAL, false));

                new LiveVisibilityClaimBreakdownReader().readBreakdown(mock(StarSystemAPI.class));

                assertThat(openedRules)
                    .containsExactly(UNDER_THE_REVEAL);
            }
        }

        @Test
        void readBreakdownSamplesTheRuleAgainForASecondReadRatherThanReusingTheFirst() {
            // The whole reason this binding exists. One instance serves the session, so the second
            // read has to see the toggle the player moved between the two hovers.
            var openedRules = new ArrayList<ColonyVisibility>();

            try (var visibilityRulesMock = mockStatic(MapVisibilityRules.class);
                    var ignoredReaders = captureOpenedRules(openedRules)) {

                var reader = new LiveVisibilityClaimBreakdownReader();
                var systemMock = mock(StarSystemAPI.class);

                visibilityRulesMock
                    .when(MapVisibilityRules::readFromLunaSettings)
                    .thenReturn(new MapVisibilityRules(BASE_FOG, false));
                reader.readBreakdown(systemMock);

                visibilityRulesMock
                    .when(MapVisibilityRules::readFromLunaSettings)
                    .thenReturn(new MapVisibilityRules(UNDER_THE_REVEAL, false));
                reader.readBreakdown(systemMock);

                assertThat(openedRules)
                    .containsExactly(BASE_FOG, UNDER_THE_REVEAL);
            }
        }
    }

    @Nested
    class ReadCoreFactionId {

        @Test
        void readCoreFactionIdNamesTheFactionTheSystemsDecreeImposes() {

            var reader = new LiveVisibilityClaimBreakdownReader();

            assertThat(reader.readCoreFactionId(buildSystemDecreedTo(DECREED_FACTION_ID)))
                .isEqualTo(DECREED_FACTION_ID);
        }

        @Test
        void readCoreFactionIdReadsNoVisibilitySettingAtAll() {
            // The port promises a decree costs one memory read, and a box heads itself with one on
            // every draw. A settings lookup in front of it would be invisible in the answer.
            try (var visibilityRulesMock = mockStatic(MapVisibilityRules.class)) {

                new LiveVisibilityClaimBreakdownReader()
                    .readCoreFactionId(buildSystemDecreedTo(DECREED_FACTION_ID));

                visibilityRulesMock.verifyNoInteractions();
            }
        }

        @Test
        void readCoreFactionIdNamesNobodyForASystemUnderNoDecree() {

            var reader = new LiveVisibilityClaimBreakdownReader();

            assertThat(reader.readCoreFactionId(buildSystemDecreedTo(NO_DECREE)))
                .isNull();
        }
    }

    // Stands in every reader the binding opens, recording the rule it was handed. The rule is
    // read nowhere else - it reaches the breakdown as a filter over markets - so intercepting
    // construction is what makes "which rule was this answered under" assertable at all.
    private static MockedConstruction<VanillaClaimBreakdownReader> captureOpenedRules(
            List<ColonyVisibility> openedRules) {

        return mockConstruction(
            VanillaClaimBreakdownReader.class,
            (readerMock, context) ->
                openedRules.add((ColonyVisibility) context.arguments().get(0)));
    }

    // A system whose memory carries the vanilla claiming-faction flag, which is the whole of what
    // a decree is - no economy, no colonies, nothing else the read could reach.
    private static StarSystemAPI buildSystemDecreedTo(String factionId) {

        var memoryMock = mock(MemoryAPI.class);
        var systemMock = mock(StarSystemAPI.class);

        when(memoryMock.getString(MemFlags.CLAIMING_FACTION))
            .thenReturn(factionId);
        when(systemMock.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);

        return systemMock;
    }
}
