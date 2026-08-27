package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorEntityToken;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the two ways an entity is admitted as a landmark - the ids a composition root registers and
 * the tag another mod hangs on content of its own - and the direction the registry errs in when
 * neither says anything.
 *
 * <p>The erring is the half worth pinning. Every answer here excuses a concealment the player can
 * see stated on a hover box, so a registry that guessed would quietly stop calling a base hidden;
 * an entity nothing vouches for reads as the secret it claims to be.
 */
final class OpenlyKnownColonyRegistryTest {

    private static final String ACADEMY_ENTITY_ID = "station_galatia_academy";
    private static final String BASE_ENTITY_ID = "station_daybreak";

    @AfterEach
    void clearRegisteredEntityIds() {
        // Registered once at start-up and read for the rest of the launch, so a case leaving its
        // own ids behind would answer for every suite that ran after it.
        OpenlyKnownColonyRegistry.registerEntityIds(List.of());
    }

    @Nested
    class IsOpenlyKnownEntity {

        @Test
        void reads_a_registered_entity_as_openly_known() {

            OpenlyKnownColonyRegistry.registerEntityIds(List.of(ACADEMY_ENTITY_ID));

            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(buildEntity(ACADEMY_ENTITY_ID)))
                .isTrue();
        }

        @Test
        void reads_an_entity_nothing_vouches_for_as_a_secret() {
            // The base beside the landmark, differing in nothing a market read can see.
            OpenlyKnownColonyRegistry.registerEntityIds(List.of(ACADEMY_ENTITY_ID));

            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(buildEntity(BASE_ENTITY_ID)))
                .isFalse();
        }

        @Test
        void reads_a_tagged_entity_the_registered_set_does_not_name_as_openly_known() {
            // How another mod's quest hub opts in without this mod carrying a list of other mods'
            // content, which is the whole reason the ids are not a constant.
            OpenlyKnownColonyRegistry.registerEntityIds(List.of(ACADEMY_ENTITY_ID));

            var entityMock = buildEntity("station_some_other_mods_hub");

            when(entityMock.hasTag(OpenlyKnownColonyRegistry.OPENLY_KNOWN_TAG))
                .thenReturn(true);

            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(entityMock))
                .isTrue();
        }

        @Test
        void reads_an_entity_the_game_never_named_as_a_secret() {
            // An unnamed entity cannot be the one that was registered, and asking a set about
            // nothing is not a question either.
            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(buildEntity(null)))
                .isFalse();
        }

        @Test
        void reads_a_colony_standing_on_no_entity_as_a_secret() {

            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(null))
                .isFalse();
        }

        @Test
        void reads_every_entity_as_a_secret_before_anything_is_registered() {
            // What a registry with nothing wired should say: a concealed colony is a secret until
            // something states otherwise.
            OpenlyKnownColonyRegistry.registerEntityIds(null);

            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(buildEntity(ACADEMY_ENTITY_ID)))
                .isFalse();
        }
    }

    @Nested
    class RegisterEntityIds {

        @Test
        void keeps_the_ids_it_was_registered_with_when_the_source_collection_changes_later() {
            // Registered once and read by every hover box for the rest of the launch, so a caller
            // still holding the collection must not be able to empty the registry underneath them.
            var entityIds = new ArrayList<String>();
            entityIds.add(ACADEMY_ENTITY_ID);

            OpenlyKnownColonyRegistry.registerEntityIds(entityIds);
            entityIds.clear();

            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(buildEntity(ACADEMY_ENTITY_ID)))
                .isTrue();
        }
    }

    // The entity a concealed colony stands on, named as the game names one. The tag answers false
    // unstubbed, which is what an entity nobody marked carries.
    private static SectorEntityToken buildEntity(String entityId) {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.getId())
            .thenReturn(entityId);

        return entityMock;
    }
}
