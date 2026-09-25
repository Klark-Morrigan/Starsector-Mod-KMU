package kmu.maplayers.ownermap;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the rule the owner-painted tier is built to: it keeps no static mutable state. Everything a
 * layer's map is made of - its views and the pick among them, its cache, its renderer, its picker
 * memo - is held by the layer or by the sector's machinery under the layer's ID, so a second
 * owner-painted layer standing beside the first shares none of it.
 *
 * <p>Read off the compiled tier rather than stated class by class, because the failure this guards
 * against is a new holder nobody thought to list: a static field added anywhere under the tier is a
 * value every layer on every sector sees at once. A static final field passes - a constant, a frozen
 * key, a profiling section - since what it holds is fixed for the process.
 *
 * <p>Integration rather than unit, since the subject is the whole compiled package tree rather than
 * any one class's behaviour.
 */
final class OwnerMapStaticStateIntegrationTest {

    // The tier's root, as a path under the compiled classes and as the package it names.
    private static final String TIER_PATH = "kmu/maplayers/ownermap";
    private static final String CLASS_SUFFIX = ".class";

    @Nested
    class EveryTierClass {

        @Test
        void everyTierClassHoldsNoStaticFieldThatCanChange() throws IOException, URISyntaxException {

            var tierClasses = listTierClassNames();

            // A walk that found nothing would pass for the wrong reason, so the tier is required to
            // have been read before its absence of state is believed.
            assertThat(tierClasses)
                .contains(OwnerPaintedView.class.getName());

            assertThat(findMutableStaticFields(tierClasses))
                .isEmpty();
        }
    }

    // Every class compiled under the tier, nested ones included, read off the directory the tier's own
    // classes were loaded from - which is the production output, never a test double of it.
    private static List<String> listTierClassNames() throws IOException, URISyntaxException {

        var classesRoot = Path.of(
            OwnerPaintedView.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        try (Stream<Path> files = Files.walk(classesRoot.resolve(TIER_PATH))) {
            return files
                .filter(file -> file.toString().endsWith(CLASS_SUFFIX))
                .map(file -> classesRoot.relativize(file).toString()
                    .replace('\\', '.')
                    .replace('/', '.'))
                .map(name -> name.substring(0, name.length() - CLASS_SUFFIX.length()))
                .sorted()
                .toList();
        }
    }

    // Each static field that is not final, named with its class so a failure says where to look.
    // Loaded without initialising, so a class whose initialiser reaches the running game is read
    // for its shape alone; synthetic fields are the compiler's and the coverage agent's, not the tier's.
    private static List<String> findMutableStaticFields(List<String> classNames) {

        var mutableStaticFields = new ArrayList<String>();

        for (var className : classNames) {
            for (Field field : loadWithoutInitialising(className).getDeclaredFields()) {

                var modifiers = field.getModifiers();

                if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && !field.isSynthetic()) {
                    mutableStaticFields.add(className + "." + field.getName());
                }
            }
        }
        return mutableStaticFields;
    }

    private static Class<?> loadWithoutInitialising(String className) {
        try {
            return Class.forName(
                className,
                false,
                OwnerMapStaticStateIntegrationTest.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("Compiled tier class could not be loaded: " + className, missing);
        }
    }
}
