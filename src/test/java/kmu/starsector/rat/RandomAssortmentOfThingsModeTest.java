package kmu.starsector.rat;

import kmlib.testfixtures.starsector.ui.map.presence.CampaignMinimapFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that neither half of the mode carries it alone: a player who switched it on but runs no
 * minimap must see the layers behave exactly as they did before the switch existed, and a player
 * who switched it off must see that whatever is installed.
 *
 * <p>The switch-off case additionally pins that no minimap read runs, which is what the ordering
 * inside the mode is for.
 */
final class RandomAssortmentOfThingsModeTest {

    @Nested
    class IsEngaged {

        @Test
        void engagesWhileTheSwitchIsOnAndAMinimapReplacesTheRadar() {

            var minimapFake = new CampaignMinimapFake();
            minimapFake.replaceRadarWithMinimap();

            var mode = new RandomAssortmentOfThingsMode(() -> true, minimapFake);

            assertThat(mode.isEngaged())
                .isTrue();
        }

        @Test
        void standsDownWhileNoMinimapReplacesTheRadar() {

            var minimapFake = new CampaignMinimapFake();

            var mode = new RandomAssortmentOfThingsMode(() -> true, minimapFake);

            assertThat(mode.isEngaged())
                .isFalse();
        }

        @Test
        void standsDownWithoutReadingTheMinimapWhileTheSwitchIsOff() {

            var minimapReadCount = new AtomicInteger();
            var mode = new RandomAssortmentOfThingsMode(
                () -> false,
                () -> {
                    minimapReadCount.incrementAndGet();
                    return true;
                });

            assertThat(mode.isEngaged())
                .isFalse();
            assertThat(minimapReadCount.get())
                .isZero();
        }
    }
}
