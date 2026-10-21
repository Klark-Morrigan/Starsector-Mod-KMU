package kmu.conditions.ui.editor;

import kmu.util.KmuStringsSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionEditorOpenResultTests {

    @BeforeEach
    void installStrings() {
        KmuStringsSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        KmuStringsSettingsFake.clearSettings();
    }

    @Nested
    class Opened {

        @Test
        void resultIsOpenedWithNoCause() {

            var result = KmuConditionEditorOpenResult.opened();

            assertThat(result.getStatus())
                .isEqualTo(KmuConditionEditorOpenStatus.OPENED);
            assertThat(result.isOpened())
                .isTrue();
            assertThat(result.getMessage())
                .isEqualTo("Opened Market Condition Manager.");
            assertThat(result.getCause())
                .isEmpty();
        }
    }

    @Nested
    class NoMarketContext {

        @Test
        void resultIsNotOpened() {

            var result = KmuConditionEditorOpenResult.noMarketContext();

            assertThat(result.getStatus())
                .isEqualTo(KmuConditionEditorOpenStatus.NO_MARKET_CONTEXT);
            assertThat(result.isOpened())
                .isFalse();
            assertThat(result.getMessage())
                .isEqualTo("No active context supports market condition editing.");
        }
    }

    @Nested
    class UnsupportedTarget {

        @Test
        void usesProvidedReason() {

            var result = KmuConditionEditorOpenResult.unsupportedTarget(
                "Station markets are not supported.");

            assertThat(result.getStatus())
                .isEqualTo(KmuConditionEditorOpenStatus.UNSUPPORTED_TARGET);
            assertThat(result.isOpened())
                .isFalse();
            assertThat(result.getMessage())
                .isEqualTo("Station markets are not supported.");
        }

        @Test
        void fallsBackToTheShippedReasonWhenGivenABlankOne() {

            var result = KmuConditionEditorOpenResult.unsupportedTarget(" ");

            assertThat(result.getMessage())
                .isEqualTo("Current market does not support market condition editing.");
        }
    }

    @Nested
    class Failed {

        @Test
        void resultExposesCauseAndIsNotOpened() {

            var cause = new RuntimeException("something went wrong");
            var result = KmuConditionEditorOpenResult.failed(
                "Editor failed to open.",
                cause);

            assertThat(result.getStatus())
                .isEqualTo(KmuConditionEditorOpenStatus.FAILED);
            assertThat(result.isOpened())
                .isFalse();
            assertThat(result.getMessage())
                .isEqualTo("Editor failed to open.");
            assertThat(result.getCause())
                .contains(cause);
        }
    }
}
