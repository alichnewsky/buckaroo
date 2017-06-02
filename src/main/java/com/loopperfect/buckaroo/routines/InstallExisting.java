package com.loopperfect.buckaroo.routines;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.common.collect.Maps;
import com.loopperfect.buckaroo.*;
import com.loopperfect.buckaroo.io.IO;

import java.io.IOException;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.loopperfect.buckaroo.routines.Routines.*;

@Deprecated
public final class InstallExisting {

    private InstallExisting() {

    }

    private static String buckarooDeps(final Stream<RecipeIdentifier> versions) {
        Preconditions.checkNotNull(versions);
        final String list = versions
                .map(x -> "  '" + x.organization + "-" + x.recipe + "//:" + x.recipe + "', \n")
                .collect(Collectors.joining(""));
        return "# Generated by Buckaroo, do not edit!\n" +
                "BUCKAROO_DEPS = [\n" +
                list +
                "]\n";
    }

    private static IO<Optional<IOException>> generateBuckarooDeps(
            final String path, final ImmutableMap<RecipeIdentifier, SemanticVersion> versions) {
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(versions);
        return IO.writeFile(path, buckarooDeps(versions.keySet().stream()), true);
    }

    private static String buckConfig(final String rootPath, final ImmutableMap<RecipeIdentifier, SemanticVersion> versions) {
        Preconditions.checkNotNull(versions);
        final String list = versions.entrySet()
                .stream()
                .map(x -> "  " + x.getKey().organization + "-" + x.getKey().recipe + " = " +
                        rootPath + "/" + x.getKey().organization + "/" + x.getKey().recipe + "/" + x.getValue() + "/")
                .collect(Collectors.joining("\n"));
        return "# Generated by Buckaroo, do not edit!\n" +
                "[repositories]\n" + list + "\n";
    }

    private static IO<Optional<IOException>> generateBuckConfig(
            final String path, String rootPath, final ImmutableMap<RecipeIdentifier, SemanticVersion> versions) {
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(rootPath);
        Preconditions.checkNotNull(versions);
        return IO.writeFile(path, buckConfig(rootPath, versions), true);
    }

    private static IO<String> recipePath(final String dependenciesDirectory, final RecipeVersionIdentifier recipe) {
        Preconditions.checkNotNull(dependenciesDirectory);
        Preconditions.checkNotNull(recipe);
        return IO.of(x -> x.fs().getPath(dependenciesDirectory, "/",
                recipe.project.toString(), "/", recipe.version.toString(), "/").toString());
    }

    private static IO<Optional<IOException>> fetchDependency(
            final String dependenciesDirectory,
            final RecipeVersionIdentifier identifier,
            final RecipeVersion recipeVersion,
            final ImmutableMap<RecipeIdentifier, SemanticVersion> resolvedDependencies) {
        Preconditions.checkNotNull(dependenciesDirectory);
        Preconditions.checkNotNull(identifier);
        Preconditions.checkNotNull(recipeVersion);
        Preconditions.checkNotNull(resolvedDependencies);
        return recipePath(dependenciesDirectory, identifier)
                .flatMap(path -> Routines.fetchSource(path, recipeVersion.source))
                .map(x -> x.map(IOException::new));
    }

    private static ImmutableMap<RecipeIdentifier, SemanticVersion> refineDependencies(
        final Optional<RecipeIdentifier> identifier,
        final ImmutableMap<RecipeIdentifier, SemanticVersion> resolvedDependencies,
        final DependencyGroup dependencies) {
        return resolvedDependencies.entrySet().stream()
                .filter(entry -> Optionals.join(identifier, x -> !entry.getKey().equals(x), () -> true))
                .filter(entry -> dependencies.requires(entry.getKey()))
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static IO<Optional<IOException>> installDependency(
            final String dependenciesDirectory,
            final RecipeVersionIdentifier identifier,
            final RecipeVersion recipeVersion,
            final ImmutableMap<RecipeIdentifier, SemanticVersion> resolvedDependencies) {
        Preconditions.checkNotNull(dependenciesDirectory);
        Preconditions.checkNotNull(identifier);
        Preconditions.checkNotNull(recipeVersion);
        Preconditions.checkNotNull(resolvedDependencies);
        final ImmutableMap<RecipeIdentifier, SemanticVersion> refinedDependencies = refineDependencies(
                Optional.of(identifier.project),
                resolvedDependencies,
                recipeVersion.dependencies.orElse(DependencyGroup.of()));
        return continueUntilPresent(ImmutableList.of(
                IO.println("Installing " + identifier.encode() + "... ")
                        .next(IO.value(Optional.empty())),
                fetchDependency(dependenciesDirectory, identifier, recipeVersion, refinedDependencies),
                recipePath(dependenciesDirectory, identifier)
                        .flatMap(path -> recipeVersion.buckResource.map(
                                resource -> Routines.fetchRemoteFile(path + "/BUCK", resource))
                                        .orElseGet(() -> IO.value(Optional.empty()))),
                recipePath(dependenciesDirectory, identifier)
                        .flatMap(path -> generateBuckConfig(path + "/.buckconfig.local", "../../..", refinedDependencies)),
                recipePath(dependenciesDirectory, identifier)
                        .flatMap(path -> generateBuckarooDeps(path + "/BUCKAROO_DEPS", refinedDependencies))));
    }

    private static Optional<RecipeVersion> fetchRecipeVersion(
        final ImmutableList<Cookbook> cookBooks, final RecipeVersionIdentifier identifier) {
        Preconditions.checkNotNull(cookBooks);
        Preconditions.checkNotNull(identifier);
        return cookBooks.stream()
            .flatMap(x -> x.organizations.entrySet()
                .stream()
                .flatMap(y -> y.getValue().recipes.entrySet()
                    .stream()
                    .flatMap(z -> z.getValue().versions.entrySet()
                        .stream()
                        .map(w -> Maps.immutableEntry(
                            RecipeVersionIdentifier.of(RecipeIdentifier.of(y.getKey(), z.getKey()), w.getKey()),
                            w.getValue())))))
                .filter(x -> x.getKey().equals(identifier))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static IO<Optional<IOException>> installDependencies(
            final String dependenciesDirectory,
            final ImmutableList<Cookbook> cookBooks,
            final ImmutableMap<RecipeIdentifier, SemanticVersion> versions) {
        Preconditions.checkNotNull(dependenciesDirectory);
        Preconditions.checkNotNull(cookBooks);
        Preconditions.checkNotNull(versions);
        return continueUntilPresent(versions.entrySet().stream()
                .map(entry -> RecipeVersionIdentifier.of(entry.getKey(), entry.getValue()))
                .map(recipe -> fetchRecipeVersion(cookBooks, recipe).map(
                        recipeVersion -> installDependency(dependenciesDirectory, recipe, recipeVersion, versions))
                        .orElseGet(() -> IO.value(Optional.of(new IOException("Unable to find " + recipe.encode())))))
                .collect(ImmutableList.toImmutableList()));
    }

    private static IO<Unit> resolveDependencies(
            final Project project, final BuckarooConfig config, final ImmutableList<Cookbook> cookBooks) {
        Preconditions.checkNotNull(project);
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(cookBooks);
        return context -> context.console().println("Not implemented");
//        final DependencyFetcher fetcher = CookbookDependencyFetcher.of(cookBooks);
//        return DependencyResolver.resolve(project.dependencies, fetcher).join(
//                IO::println,
//                resolvedDependencies -> continueUntilPresent(ImmutableList.of(
//                        IO.of(x -> x.fs().workingDirectory() + "/BUCKAROO_DEPS")
//                                .flatMap(path -> generateBuckarooDeps(
//                                        path,
//                                        refineDependencies(Optional.empty(), resolvedDependencies, project.dependencies))),
//                        IO.of(x -> x.fs().workingDirectory() + "/.buckconfig.local")
//                                .flatMap(path -> generateBuckConfig(path, "./buckaroo", resolvedDependencies)),
//                        IO.of(x -> x.fs().workingDirectory() + "/buckaroo/")
//                                .flatMap(path -> installDependencies(path, cookBooks, resolvedDependencies))))
//                        .flatMap(x -> Optionals.join(
//                                x,
//                                IO::println,
//                                () -> IO.println("Success! "))));
    }

    public static IO<Unit> routine =
        projectFilePath
            .flatMap(Routines::readProject)
            .flatMap(x -> x.join(
                e -> IO.println("Error reading project file... ").next(IO.println(e)),
                project -> Routines.ensureConfig.flatMap(e -> Optionals.join(
                    e,
                    i -> IO.println("Error installing default Buckaroo config... ").next(IO.println(i)),
                    () -> configFilePath.flatMap(Routines::readConfig).flatMap(y -> y.join(
                        IO::println,
                        config -> readCookBooks(config).flatMap(z -> z.join(
                            IO::println,
                            cookBooks -> resolveDependencies(project, config, cookBooks)))))))));

}
