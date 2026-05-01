package kmu.starsector;

import com.fs.starfarer.api.util.Misc;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class StarsectorUiColorProviderTest {
    @Test
    void getReturnsStarsectorColorWhenRawColorUsesMisc() {
        Color expected = new Color(1, 2, 3);

        StarsectorTestSupport.installSettings();
        try (MockedStatic<Misc> misc = Mockito.mockStatic(Misc.class)) {
            misc.when(Misc::getHighlightColor).thenReturn(expected);

                assertThat(StarsectorUiColorProvider.get(StarsectorUiColor.GOLD))
                        .isEqualTo(expected);
        } finally {
            StarsectorTestSupport.clearSettings();
        }
    }

    @Test
    void getReturnsCustomColorWhenRawColorHasNoStarsectorSource() {
        assertThat(StarsectorUiColorProvider.get(StarsectorUiColor.ORANGE))
                .isEqualTo(new Color(255, 100, 0, 255));
    }

    @Test
    void getRejectsMissingColor() {
        assertThatNullPointerException()
                .isThrownBy(() -> StarsectorUiColorProvider.get(null));
    }

    @Test
    void distinguishesCustomAndStarsectorColors() {
        assertThat(StarsectorUiColor.ORANGE.isCustom()).isTrue();
        assertThat(StarsectorUiColor.GRAY.isCustom()).isFalse();
    }
}
