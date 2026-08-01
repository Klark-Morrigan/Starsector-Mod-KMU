package kmu.conditions.ui.picker.tooltip;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmuTooltipSectionTest {
    private static final Color BLUE = new Color(170, 222, 255, 255);
    private static final Color DARK_BLUE = new Color(31, 94, 112, 175);
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color TEXT = new Color(220, 220, 220, 255);

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void mockStarsectorThemeColours() {
        StarsectorSettingsFake.installSettings();
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock.when(Misc::getBasePlayerColor).thenReturn(BLUE);
        miscMock.when(Misc::getDarkPlayerColor).thenReturn(DARK_BLUE);
        miscMock.when(Misc::getGrayColor).thenReturn(GRAY);
        miscMock.when(Misc::getTextColor).thenReturn(TEXT);
    }

    @AfterEach
    void closeStarsectorThemeColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class Add {

        @Test
        void addsMutedSectionWithStandardBlueBannerAndGrayBodyText() {
            var tooltip = RecordingTooltip.create();

            KmuTooltipSection.add(
                    tooltip.getApi(),
                    KmuTooltipSectionStyle.MUTED,
                    "Metadata",
                    Arrays.asList("id: hot", "", null, "source: Starsector"));

            assertThat(tooltip.getHeadings())
                    .containsExactly(new HeadingCall(
                            "Metadata",
                            BLUE,
                            DARK_BLUE,
                            Alignment.MID,
                            10f));
            assertThat(tooltip.getParagraphs())
                    .containsExactly(
                            new ParagraphCall("id: hot", 4f, GRAY),
                            new ParagraphCall("source: Starsector", 4f, GRAY));
        }

        @Test
        void addsWarningSectionWithWarningBannerAndNormalBodyText() {
            var tooltip = RecordingTooltip.create();

            KmuTooltipSection.add(
                    tooltip.getApi(),
                    KmuTooltipSectionStyle.WARNING,
                    "Suppressed",
                    "Reason text");

            assertThat(tooltip.getHeadings())
                    .containsExactly(new HeadingCall(
                            "Suppressed",
                            new Color(255, 100, 0, 255),
                            new Color(70, 20, 20),
                            Alignment.MID,
                            10f));
            assertThat(tooltip.getParagraphs())
                    .containsExactly(new ParagraphCall("Reason text", 4f, TEXT));
        }
    }

    private static final class RecordingTooltip implements InvocationHandler {
        private final List<HeadingCall> headings = new java.util.ArrayList<>();
        private final List<ParagraphCall> paragraphs = new java.util.ArrayList<>();
        private final TooltipMakerAPI api;

        private RecordingTooltip() {
            this.api = (TooltipMakerAPI) Proxy.newProxyInstance(
                    TooltipMakerAPI.class.getClassLoader(),
                    new Class<?>[]{TooltipMakerAPI.class},
                    this);
        }

        static RecordingTooltip create() {
            return new RecordingTooltip();
        }

        TooltipMakerAPI getApi() {
            return api;
        }

        List<HeadingCall> getHeadings() {
            return headings;
        }

        List<ParagraphCall> getParagraphs() {
            return paragraphs;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("addSectionHeading".equals(method.getName())
                    && args != null
                    && args.length == 5
                    && args[1] instanceof Color) {
                headings.add(new HeadingCall(
                        (String) args[0],
                        (Color) args[1],
                        (Color) args[2],
                        (Alignment) args[3],
                        (Float) args[4]));
                return null;
            }
            if ("addPara".equals(method.getName())
                    && args != null
                    && args.length == 3
                    && args[1] instanceof Color
                    && args[2] instanceof Float) {
                paragraphs.add(new ParagraphCall((String) args[0], (Float) args[2], (Color) args[1]));
                return null;
            }
            if ("toString".equals(method.getName())) {
                return "RecordingTooltip";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            return defaultValue(method.getReturnType());
        }

        private Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (boolean.class.equals(returnType)) {
                return false;
            }
            if (char.class.equals(returnType)) {
                return '\0';
            }
            if (byte.class.equals(returnType)) {
                return (byte) 0;
            }
            if (short.class.equals(returnType)) {
                return (short) 0;
            }
            if (int.class.equals(returnType)) {
                return 0;
            }
            if (long.class.equals(returnType)) {
                return 0L;
            }
            if (float.class.equals(returnType)) {
                return 0f;
            }
            if (double.class.equals(returnType)) {
                return 0d;
            }
            return null;
        }
    }

    private static final class HeadingCall {
        private final String title;
        private final Color titleColour;
        private final Color backgroundColour;
        private final Alignment alignment;
        private final float pad;

        private HeadingCall(
                String title,
                Color titleColour,
                Color backgroundColour,
                Alignment alignment,
                float pad) {
            this.title = title;
            this.titleColour = titleColour;
            this.backgroundColour = backgroundColour;
            this.alignment = alignment;
            this.pad = pad;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HeadingCall)) {
                return false;
            }
            var that = (HeadingCall) other;
            return Float.compare(that.pad, pad) == 0
                    && java.util.Objects.equals(title, that.title)
                    && java.util.Objects.equals(titleColour, that.titleColour)
                    && java.util.Objects.equals(backgroundColour, that.backgroundColour)
                    && alignment == that.alignment;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(title, titleColour, backgroundColour, alignment, pad);
        }

        @Override
        public String toString() {
            return "HeadingCall{"
                    + "title='" + title + '\''
                    + ", titleColour=" + titleColour
                    + ", backgroundColour=" + backgroundColour
                    + ", alignment=" + alignment
                    + ", pad=" + pad
                    + '}';
        }
    }

    private static final class ParagraphCall {
        private final String text;
        private final float pad;
        private final Color colour;

        private ParagraphCall(String text, float pad, Color colour) {
            this.text = text;
            this.pad = pad;
            this.colour = colour;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ParagraphCall)) {
                return false;
            }
            var that = (ParagraphCall) other;
            return Float.compare(that.pad, pad) == 0
                    && java.util.Objects.equals(text, that.text)
                    && java.util.Objects.equals(colour, that.colour);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(text, pad, colour);
        }

        @Override
        public String toString() {
            return "ParagraphCall{"
                    + "text='" + text + '\''
                    + ", pad=" + pad
                    + ", colour=" + colour
                    + '}';
        }
    }
}
