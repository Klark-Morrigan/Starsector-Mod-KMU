package kmu.settings;

import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins each option of the survey-level Radio to the game's own level it stands for.
 *
 * <p>Nothing else can. The settings suite holds the option labels against the CSV, so a reworded
 * label fails there; what no walk over the file can see is which level a label is wired to. A
 * transposed pair - "Preliminary" mapped to {@link SurveyLevel#FULL}, say - would ship green, read
 * correctly on the settings screen, and quietly withhold every world between the two: the player
 * picks one thing and the map does another, with nothing on screen to say so.
 *
 * <p>The mapping is asserted per constant rather than by walking the enum against a list, since a
 * walk written from the same enum would agree with whatever it says.
 */
final class SurveyLevelChoiceTest {

    @Nested
    class ResolveSurveyLevel {

        @Test
        void resolveSurveyLevelMapsNotSurveyedToNone() {

            assertThat(SurveyLevelChoice.NOT_SURVEYED.resolveSurveyLevel())
                .isEqualTo(SurveyLevel.NONE);
        }

        @Test
        void resolveSurveyLevelMapsSeenToSeen() {

            assertThat(SurveyLevelChoice.SEEN.resolveSurveyLevel())
                .isEqualTo(SurveyLevel.SEEN);
        }

        @Test
        void resolveSurveyLevelMapsPreliminaryToPreliminary() {

            assertThat(SurveyLevelChoice.PRELIMINARY.resolveSurveyLevel())
                .isEqualTo(SurveyLevel.PRELIMINARY);
        }

        @Test
        void resolveSurveyLevelMapsFullToFull() {

            assertThat(SurveyLevelChoice.FULL.resolveSurveyLevel())
                .isEqualTo(SurveyLevel.FULL);
        }
    }

    @Nested
    class GetLabel {

        @Test
        void getLabelReadsTheWordingTheSettingsScreenStores() {
            // The stored key wearing a caption's costume. Held here as well as against the CSV
            // because a label edited on one side alone is a player's pick that silently stops
            // resolving.
            assertThat(SurveyLevelChoice.NOT_SURVEYED.getLabel())
                .isEqualTo("Not surveyed");
            assertThat(SurveyLevelChoice.SEEN.getLabel())
                .isEqualTo("Seen");
            assertThat(SurveyLevelChoice.PRELIMINARY.getLabel())
                .isEqualTo("Preliminary");
            assertThat(SurveyLevelChoice.FULL.getLabel())
                .isEqualTo("Full");
        }
    }
}
