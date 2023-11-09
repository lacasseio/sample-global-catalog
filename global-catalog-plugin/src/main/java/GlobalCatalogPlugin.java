import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.specs.Spec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.Callable;

public abstract class GlobalCatalogPlugin implements Plugin<Settings> {
    private static final String CATALOG_NAME = "libs";
    private static final String SERVICE_NAME = "globalCatalog";
    private final ProviderFactory providers;
    private final ObjectFactory objects;

    @Inject
    public GlobalCatalogPlugin(ProviderFactory providers, ObjectFactory objects) {
        this.providers = providers;
        this.objects = objects;
    }

    @Override
    public void apply(Settings settings) {
        final Provider<File> localCatalogProvider = providers.provider(() -> versionCatalogFile(settings))
                .map(onlyIf(File::exists))
                ;

        final Provider<File> globalCatalogProvider = Optional.ofNullable(settings.getGradle().getParent())
                .flatMap(this::findService)
                .map(this::versionCatalog)
                .orElseGet(() -> providers.provider(notDefined()))
                .map(onlyIf(File::exists))
                ;

        // Use the build service to communicate between Gradle projects
        final Provider<GlobalCatalogService> service = settings.getGradle().getSharedServices().registerIfAbsent(SERVICE_NAME, GlobalCatalogService.class, spec -> {
            spec.parameters(parameters -> {
                parameters.getGlobalCatalogFile().fileProvider(globalCatalogProvider.orElse(localCatalogProvider));
            });
        });

        // Load the local catalog now to allow user defined override
        ifPresent(localCatalogProvider.map(onlyIf(File::exists)), catalogFile -> {
            settings.getDependencyResolutionManagement().getVersionCatalogs().maybeCreate(CATALOG_NAME)
                    .from(toFileCollection(catalogFile));
        });

        // Load the global catalog last to override any versions already specified
        settings.getGradle().settingsEvaluated(loadGlobalCatalogIfAvailable(service.flatMap(GlobalCatalogService::getGlobalCatalogFile)));
    }

    private static <T> Callable<T> notDefined() {
        return () -> null;
    }

    private Provider<File> versionCatalog(BuildServiceRegistration<?, ?> registration) {
        return registration.getService().flatMap(GlobalCatalogService::globalCatalogFile);
    }

    private Optional<BuildServiceRegistration<?, ?>> findService(Gradle gradle) {
        return Optional.ofNullable(gradle.getSharedServices().getRegistrations().findByName(SERVICE_NAME));
    }

    private static <T> Transformer<T, T> onlyIf(Spec<? super T> spec) {
        return it -> {
            if (spec.isSatisfiedBy(it)) {
                return it;
            } else {
                return null;
            }
        };
    }

    private static File versionCatalogFile(Settings settings) {
        return new File(settings.getSettingsDir(), "gradle/versions.toml");
    }

    private Action<Settings> loadGlobalCatalogIfAvailable(Provider<File> catalogFileProvider) {
        return settings -> {
            // Only if the global catalog wasn't already loaded, e.g. is not the local catalog
            ifPresent(catalogFileProvider.map(onlyIf(it -> !it.equals(versionCatalogFile(settings)))), catalogFile -> {
                settings.getDependencyResolutionManagement().getVersionCatalogs().maybeCreate(CATALOG_NAME)
                        .from(toFileCollection(catalogFile));
            });
        };
    }

    private FileCollection toFileCollection(Object path) {
        return objects.fileCollection().from(path);
    }

    private static <T> void ifPresent(Provider<T> provider, Action<? super T> action) {
        @Nullable final T value = provider.getOrNull();
        if (value != null) {
            action.execute(value);
        }
    }

    public static abstract class GlobalCatalogService implements BuildService<GlobalCatalogService.Parameters> {
        public interface Parameters extends BuildServiceParameters {
            RegularFileProperty getGlobalCatalogFile();
        }

        public Provider<File> getGlobalCatalogFile() {
            return getParameters().getGlobalCatalogFile().getAsFile();
        }

        // Since the build service can have different classloader, we need to use reflection
        @SuppressWarnings("unchecked")
        public static Provider<File> globalCatalogFile(Object target) {
            try {
                return (Provider<File>) target.getClass().getMethod("getGlobalCatalogFile").invoke(target);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
