package kmu.mods.nexerelin;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import exerelin.campaign.alliances.Alliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Pins the one translation out of Nexerelin's own type: everything downstream is built on the
 * record rather than on the alliance, so the bloc ID every member shares, the label the bloc
 * carries and the member whose palette it paints in are all read out of one row here. A field taken
 * from the wrong place would show as a bloc named after its neighbour or painted in a rival's
 * colour.
 *
 * <p>The live read beside it is not held here and cannot be: naming Nexerelin's alliance manager
 * initialises it, and it reads its own configuration off {@code Global.getSettings()} in a static
 * initialiser - so the class cannot load at all outside a running game, whatever is on the
 * classpath. That is why the flattening is separable from the read in the first place. The manager
 * guard the read carries is therefore exercised in play rather than here.
 *
 * <p>Each alliance is a real {@link Alliance} rather than a mock, since its ID is minted in its own
 * constructor and a mock would carry none. Only the member ranking is stood in for: the real one
 * sums each member's market sizes off the running economy.
 */
class NexAllianceSourceTest {

    private static final String ALLIANCE_NAME = "Hegemonic Pact";
    private static final String OTHER_ALLIANCE_NAME = "Persean League";

    // The two members of each staged alliance, named for their rank: the flattening carries the
    // order through untouched, and element 0 is the member a bloc takes its colour from.
    private static final String DOMINANT_MEMBER = "hegemony";
    private static final String LESSER_MEMBER = "tritachyon";

    @Nested
    class FlattenAlliances {

        @Test
        void flattenAlliancesReadsAnAlliancesIdNameAndRankedMembers() {

            var alliance = buildAllianceNamed(ALLIANCE_NAME);

            var records = NexAllianceSource.flattenAlliances(List.of(alliance));

            assertThat(records)
                .hasSize(1);

            // The ID is minted in the alliance's own constructor, so the staged alliance is the
            // only place a test can read what the record should carry; the other two are literals.
            assertThat(records.get(0).allianceId())
                .isEqualTo(alliance.uuId);
            assertThat(records.get(0).name())
                .isEqualTo(ALLIANCE_NAME);
            assertThat(records.get(0).membersSortedDescending())
                .containsExactly(DOMINANT_MEMBER, LESSER_MEMBER);
        }

        @Test
        void flattenAlliancesYieldsOneRecordPerAllianceInTheOrderGiven() {
            // The bloc list the sidebar offers is built off these rows in order, so a flattening
            // that reordered them would reorder the picker for a reason nothing states.
            var records = NexAllianceSource.flattenAlliances(List.of(
                buildAllianceNamed(ALLIANCE_NAME),
                buildAllianceNamed(OTHER_ALLIANCE_NAME)));

            assertThat(records)
                .extracting("name")
                .containsExactly(ALLIANCE_NAME, OTHER_ALLIANCE_NAME);
        }

        @Test
        void flattenAlliancesYieldsNothingForNoAlliances() {
            // The answer a sector where nobody has allied yet produces, which the live read also
            // returns for a session too early to have a manager.
            assertThat(NexAllianceSource.flattenAlliances(List.of()))
                .isEmpty();
        }
    }

    // One alliance of the two members above, its ID and name its own constructor's, with the
    // member ranking stated rather than summed off an economy no test JVM runs.
    private static Alliance buildAllianceNamed(String name) {

        var alliance = spy(new Alliance(
            name,
            Alliance.Alignment.CORPORATE,
            DOMINANT_MEMBER,
            LESSER_MEMBER));

        doReturn(List.of(DOMINANT_MEMBER, LESSER_MEMBER))
            .when(alliance)
            .getMembersSorted();

        return alliance;
    }
}
