package kmu.console;

import kmlib.testfixtures.console.output.CommandOutputFake;

import kmu.conditions.ui.editor.KmuConditionEditorOpenResult;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class KmuOpenMarketConditionManagerCommandTest {

    // The feature switch each case runs under. Every case but one holds it on, the switch being a
    // guard ahead of the behaviour they are about: read off, the command answers nothing else.
    private static final BooleanSupplier FEATURE_SWITCHED_ON = () -> true;
    private static final BooleanSupplier FEATURE_SWITCHED_OFF = () -> false;

    @Nested
    class RunCommand {

        @Test
        void refusesToOpenTheEditorWhileTheFeatureIsSwitchedOff() {

            var opened = new AtomicBoolean(false);
            var outputFake = new CommandOutputFake();
            var command = new KmuOpenMarketConditionManagerCommand(
                FEATURE_SWITCHED_OFF,
                buildRecordingOpener(opened),
                outputFake);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

            assertThat(result)
                .isEqualTo(CommandResult.ERROR);

            // In a context that would otherwise have opened it, so the refusal is the switch's and
            // not the context's.
            assertThat(opened)
                .isFalse();

            assertThat(outputFake.getMessages())
                .containsExactly(
                    "The Market Condition Manager is unfinished and switched off."
                        + " Switch it on under LunaLib's mod settings, on KMU's Features tab,"
                        + " to try it anyway.");
        }

        @Test
        void rejectsNonCampaignContextWithoutOpeningEditor() {

            var opened = new AtomicBoolean(false);
            var outputFake = new CommandOutputFake();
            var command = new KmuOpenMarketConditionManagerCommand(
                FEATURE_SWITCHED_ON,
                buildRecordingOpener(opened),
                outputFake);

            var result = command.runCommand("", CommandContext.COMBAT_MISSION);

            assertThat(result)
                .isEqualTo(CommandResult.WRONG_CONTEXT);
            assertThat(opened)
                .isFalse();
            assertThat(outputFake.getMessages())
                .containsExactly("This command can only run in a campaign.");
        }

        @Test
        void rejectsAStrayArgumentAsBadSyntaxWithoutOpeningEditor() {

            var opened = new AtomicBoolean(false);
            var outputFake = new CommandOutputFake();
            var command = new KmuOpenMarketConditionManagerCommand(
                FEATURE_SWITCHED_ON,
                buildRecordingOpener(opened),
                outputFake);

            var result = command.runCommand("bogus", CommandContext.CAMPAIGN_MARKET);

            assertThat(result)
                .isEqualTo(CommandResult.BAD_SYNTAX);

            // A malformed invocation must not reach the editor entry point.
            assertThat(opened)
                .isFalse();
            assertThat(outputFake.getMessages())
                .anyMatch(message -> message.contains("Too many arguments"));
        }

        @Test
        void opensEditorInCampaignContext() {

            var opened = new AtomicBoolean(false);
            var outputFake = new CommandOutputFake();
            var command = new KmuOpenMarketConditionManagerCommand(
                FEATURE_SWITCHED_ON,
                buildRecordingOpener(opened),
                outputFake);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

            assertThat(result)
                .isEqualTo(CommandResult.SUCCESS);
            assertThat(opened)
                .isTrue();
            assertThat(outputFake.getMessages())
                .containsExactly("Opened Market Condition Manager.");
        }

        @Test
        void surfacesNoMarketContextAsWrongContext() {

            var outputFake = new CommandOutputFake();
            var command = new KmuOpenMarketConditionManagerCommand(
                FEATURE_SWITCHED_ON,
                KmuConditionEditorOpenResult::noMarketContext,
                outputFake);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MAP);

            assertThat(result)
                .isEqualTo(CommandResult.WRONG_CONTEXT);
            assertThat(outputFake.getMessages())
                .containsExactly("No active context supports market condition editing.");
        }

        @Test
        void surfacesUnsupportedTargetAsWrongContext() {

            var outputFake = new CommandOutputFake();
            var command = new KmuOpenMarketConditionManagerCommand(
                FEATURE_SWITCHED_ON,
                () -> KmuConditionEditorOpenResult.unsupportedTarget("Unsupported target."),
                outputFake);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

            assertThat(result)
                .isEqualTo(CommandResult.WRONG_CONTEXT);
            assertThat(outputFake.getMessages())
                .containsExactly("Unsupported target.");
        }

        @Test
        void surfacesEntryPointFailureAsError() {

            var outputFake = new CommandOutputFake();
            var command = new KmuOpenMarketConditionManagerCommand(
                FEATURE_SWITCHED_ON,
                () -> KmuConditionEditorOpenResult.failed(
                    "Failed to open Market Condition Manager.",
                    new IllegalStateException("dialog unavailable")),
                outputFake);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

            assertThat(result)
                .isEqualTo(CommandResult.ERROR);
            assertThat(outputFake.getMessages())
                .containsExactly("Failed to open Market Condition Manager: dialog unavailable");
        }

        @Test
        void catchesUnexpectedEntryPointException() {

            var outputFake = new CommandOutputFake();
            var command = new KmuOpenMarketConditionManagerCommand(
                FEATURE_SWITCHED_ON,
                () -> {
                    throw new IllegalStateException("unexpected failure");
                },
                outputFake);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

            assertThat(result)
                .isEqualTo(CommandResult.ERROR);
            assertThat(outputFake.getMessages())
                .containsExactly("Failed to open Market Condition Manager: unexpected failure");
        }
    }

    // An opener that would succeed, and says whether it was reached. What every case about a guard
    // asserts on: the guard's own answer says the command declined, and this says the editor was
    // never opened behind it.
    private static Supplier<KmuConditionEditorOpenResult> buildRecordingOpener(AtomicBoolean opened) {
        return () -> {
            opened.set(true);
            return KmuConditionEditorOpenResult.opened();
        };
    }
}
