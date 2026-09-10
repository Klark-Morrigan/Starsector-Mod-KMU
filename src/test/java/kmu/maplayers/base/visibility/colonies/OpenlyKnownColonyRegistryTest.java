package kmu.maplayers.base.visibility.colonies;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.ACADEMY_ENTITY_ID;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.buildEntity;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.clearRegistrations;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.registerTheAcademy;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static final String BASE_ENTITY_ID = "station_daybreak";

    @AfterEach
    void clearRegisteredEntityIds() {
        clearRegistrations();
    }

    @Nested
    class IsOpenlyKnownEntity {

        @Test
        void readsARegisteredEntityAsOpenlyKnown() {

            registerTheAcademy();

            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(buildEntity(ACADEMY_ENTITY_ID)))
                .isTrue();
        }

        @Test
        void readsAnEntityNothingVouchesForAsASecret() {
            // The base beside the landmark, differing in nothing a market read can see.
            registerTheAcademy();

            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(buildEntity(BASE_ENTITY_ID)))
                .isFalse();
        }

        @Test
        void readsATaggedEntityTheRegisteredSetDoesNotNameAsOpenlyKnown() {
            // How another mod's quest hub opts in without this mod carrying a list of other mods'
            // content, which is the whole reason the ids are not a constant.
            registerTheAcademy();

            var entityMock = buildEntity("station_some_other_mods_hub");

            when(entityMock.hasTag(OpenlyKnownColonyRegistry.OPENLY_KNOWN_TAG))
                .thenReturn(true);

            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(entityMock))
                .isTrue();
        }

        @Test
        void readsAnEntityTheGameNeverNamedAsASecret() {
            // An unnamed entity cannot be the one that was registered, and asking a set about
            // nothing is not a question either.
            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(buildEntity(null)))
                .isFalse();
        }

        @Test
        void readsAColonyStandingOnNoEntityAsASecret() {

            assertThat(OpenlyKnownColonyRegistry.isOpenlyKnownEntity(null))
                .isFalse();
        }

        @Test
        void readsEveryEntityAsASecretBeforeAnythingIsRegistered() {
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
        void keepsTheIdsItWasRegisteredWithWhenTheSourceCollectionChangesLater() {
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
}
