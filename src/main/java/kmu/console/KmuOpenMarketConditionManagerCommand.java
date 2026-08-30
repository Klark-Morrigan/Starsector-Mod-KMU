package kmu.console;

import kmlib.console.KmlibBaseConsoleCommand;
import kmlib.console.output.CommandOutput;
import kmlib.console.output.ConsoleCommandOutput;
import kmlib.console.parsing.ParameterSpec;

import kmu.KmuErrorReporter;
import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.StarsectorConditionOfferPolicy;
import kmu.conditions.domain.StarsectorConditionRepository;
import kmu.conditions.ui.editor.KmuConditionEditorEntryPoint;
import kmu.conditions.ui.editor.KmuConditionEditorOpenResult;
import kmu.conditions.ui.editor.KmuConditionEditorOpenStatus;
import kmu.conditions.ui.picker.KmuConditionPickerEditor;
import kmu.conditions.ui.picker.dialog.StarsectorInteractionDialogPickerOpener;
import kmu.settings.KmuFeatureSettings;
import kmu.settings.KmuMarketConditionSettings;
import kmu.ui.context.StarsectorMarketUiContextResolver;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static kmu.util.KmuValues.normalizeText;

public final class KmuOpenMarketConditionManagerCommand extends KmlibBaseConsoleCommand {

    // No parameters; declaring the spec still makes the parser reject a stray
    // argument as bad syntax rather than silently ignoring it.
    private static final ParameterSpec SPEC =
        ParameterSpec.takingNoArguments("Usage: kmu_mcm_open.");

    // What a player typing the command while the feature is off is told. It names where the switch
    // is, because the command is registered by data file whatever the switch says: Console Commands
    // reads commands.csv at load, so the command exists to be typed even when nothing will open,
    // and a bare refusal would read as the command being broken.
    private static final String FEATURE_SWITCHED_OFF_MESSAGE =
        "The Market Condition Manager is unfinished and switched off."
            + " Switch it on under LunaLib's mod settings, on KMU's Features tab, to try it anyway.";

    private final BooleanSupplier isFeatureSwitchedOn;
    private final Supplier<KmuConditionEditorOpenResult> openEditor;

    public KmuOpenMarketConditionManagerCommand() {
        this(
            KmuFeatureSettings::isMarketConditionManagerEnabled,
            () -> createDefaultEntryPoint().openForCurrentMarketDetailed(),
            ConsoleCommandOutput.INSTANCE);
    }

    KmuOpenMarketConditionManagerCommand(
            BooleanSupplier isFeatureSwitchedOn,
            Supplier<KmuConditionEditorOpenResult> openEditor,
            CommandOutput output) {

        super(output);
        this.isFeatureSwitchedOn = Objects.requireNonNull(isFeatureSwitchedOn, "isFeatureSwitchedOn");
        this.openEditor = Objects.requireNonNull(openEditor, "openEditor");
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {

        Objects.requireNonNull(context, "context");

        // Ahead of the context and syntax guards, because it is the more fundamental answer: a
        // player told they are in the wrong place would go and type the command somewhere else,
        // where a feature that is switched off still opens nothing.
        if (!isFeatureSwitchedOn.getAsBoolean()) {

            output.showMessage(FEATURE_SWITCHED_OFF_MESSAGE);
            return CommandResult.ERROR;
        }

        // Defer the campaign-context guard to KMLib's shared validator so this
        // command's precondition check and feedback match every other KM
        // console command rather than maintaining a parallel inline check.
        var parsed = readInput(context, args)
            .requireCampaign()
            .parseArguments(SPEC);

        if (!parsed.isValid()) {
            return parsed.getResult();
        }

        KmuConditionEditorOpenResult result;

        try {
            result = openEditor.get();

        } catch (RuntimeException exception) {

            output.showMessage("Failed to open Market Condition Manager: " + exception.getMessage());
            return CommandResult.ERROR;
        }

        output.showMessage(messageFor(result));

        if (result.getStatus() == KmuConditionEditorOpenStatus.OPENED) {

            return CommandResult.SUCCESS;
        }
        if (result.getStatus() == KmuConditionEditorOpenStatus.NO_MARKET_CONTEXT
                || result.getStatus() == KmuConditionEditorOpenStatus.UNSUPPORTED_TARGET) {

            return CommandResult.WRONG_CONTEXT;
        }
        return CommandResult.ERROR;
    }

    private String messageFor(KmuConditionEditorOpenResult result) {

        var causeMessage = result.getCause()
            .map(RuntimeException::getMessage)
            .map(message -> normalizeText(message))
            .orElse(null);

        if (result.getStatus() == KmuConditionEditorOpenStatus.FAILED
                && causeMessage != null) {

            var message = result.getMessage();

            if (message.endsWith(".")) {
                message = message.substring(0, message.length() - 1);
            }
            return message + ": " + causeMessage;
        }
        return result.getMessage();
    }

    private static KmuConditionEditorEntryPoint createDefaultEntryPoint() {

        var conditionService = new KmuConditionService(
            new StarsectorConditionRepository(),
            KmuErrorReporter.noop(),
            new StarsectorConditionOfferPolicy(KmuMarketConditionSettings::shouldOfferAllConditions));

        return new KmuConditionEditorEntryPoint(
            new StarsectorMarketUiContextResolver(),
            new KmuConditionPickerEditor(
                conditionService,
                new StarsectorInteractionDialogPickerOpener()));
    }
}
