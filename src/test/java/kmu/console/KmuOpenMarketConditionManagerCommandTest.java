package kmu.console;

import kmu.conditions.ui.editor.KmuConditionEditorOpenResult;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.BaseCommand.CommandResult;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class KmuOpenMarketConditionManagerCommandTest {

    @Nested
    class RunCommand {

        @Test
        void rejectsNonCampaignContextWithoutOpeningEditor() {
            var opened = new AtomicBoolean(false);
            var output = new ArrayList<String>();
            var command = new KmuOpenMarketConditionManagerCommand(
                    () -> {
                        opened.set(true);
                        return KmuConditionEditorOpenResult.opened();
                    },
                    output::add);

            var result = command.runCommand("", CommandContext.COMBAT_MISSION);

            assertThat(result).isEqualTo(CommandResult.WRONG_CONTEXT);
            assertThat(opened).isFalse();
            assertThat(output).containsExactly("This command can only run in a campaign.");
        }

        @Test
        void rejectsAStrayArgumentAsBadSyntaxWithoutOpeningEditor() {
            var opened = new AtomicBoolean(false);
            var output = new ArrayList<String>();
            var command = new KmuOpenMarketConditionManagerCommand(
                    () -> {
                        opened.set(true);
                        return KmuConditionEditorOpenResult.opened();
                    },
                    output::add);

            var result = command.runCommand("bogus", CommandContext.CAMPAIGN_MARKET);

            assertThat(result).isEqualTo(CommandResult.BAD_SYNTAX);
            // A malformed invocation must not reach the editor entry point.
            assertThat(opened).isFalse();
            assertThat(output).anyMatch(message -> message.contains("Too many arguments"));
        }

        @Test
        void opensEditorInCampaignContext() {
            var opened = new AtomicBoolean(false);
            var output = new ArrayList<String>();
            var command = new KmuOpenMarketConditionManagerCommand(
                    () -> {
                        opened.set(true);
                        return KmuConditionEditorOpenResult.opened();
                    },
                    output::add);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

            assertThat(result).isEqualTo(CommandResult.SUCCESS);
            assertThat(opened).isTrue();
            assertThat(output).containsExactly("Opened Market Condition Manager.");
        }

        @Test
        void surfacesNoMarketContextAsWrongContext() {
            var output = new ArrayList<String>();
            var command = new KmuOpenMarketConditionManagerCommand(
                    KmuConditionEditorOpenResult::noMarketContext,
                    output::add);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MAP);

            assertThat(result).isEqualTo(CommandResult.WRONG_CONTEXT);
            assertThat(output).containsExactly("No active context supports market condition editing.");
        }

        @Test
        void surfacesUnsupportedTargetAsWrongContext() {
            var output = new ArrayList<String>();
            var command = new KmuOpenMarketConditionManagerCommand(
                    () -> KmuConditionEditorOpenResult.unsupportedTarget("Unsupported target."),
                    output::add);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

            assertThat(result).isEqualTo(CommandResult.WRONG_CONTEXT);
            assertThat(output).containsExactly("Unsupported target.");
        }

        @Test
        void surfacesEntryPointFailureAsError() {
            var output = new ArrayList<String>();
            var command = new KmuOpenMarketConditionManagerCommand(
                    () -> KmuConditionEditorOpenResult.failed(
                            "Failed to open Market Condition Manager.",
                            new IllegalStateException("dialog unavailable")),
                    output::add);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

            assertThat(result).isEqualTo(CommandResult.ERROR);
            assertThat(output).containsExactly("Failed to open Market Condition Manager: dialog unavailable");
        }

        @Test
        void catchesUnexpectedEntryPointException() {
            var output = new ArrayList<String>();
            var command = new KmuOpenMarketConditionManagerCommand(
                    () -> {
                        throw new IllegalStateException("unexpected failure");
                    },
                    output::add);

            var result = command.runCommand("", CommandContext.CAMPAIGN_MARKET);

            assertThat(result).isEqualTo(CommandResult.ERROR);
            assertThat(output).containsExactly("Failed to open Market Condition Manager: unexpected failure");
        }
    }
}
