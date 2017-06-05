package com.loopperfect.buckaroo.cli;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.loopperfect.buckaroo.*;
import com.loopperfect.buckaroo.io.IO;
import com.loopperfect.buckaroo.tasks.UninstallTasks;
import io.reactivex.Observable;

import java.util.Objects;

public final class UninstallCommand implements CLICommand {

    public final PartialRecipeIdentifier project;

    private UninstallCommand(final PartialRecipeIdentifier project) {
        Preconditions.checkNotNull(project);
        this.project = project;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof UninstallCommand)) {
            return false;
        }
        final UninstallCommand other = (UninstallCommand) obj;
        return Objects.equals(project, other.project);
    }

    @Override
    public int hashCode() {
        return Objects.hash(project);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("project", project)
            .toString();
    }

    @Override
    public IO<Unit> routine() {
        return context -> {

            final Observable<Event> task = UninstallTasks.uninstallInWorkingDirectory(
                context.fs().fileSystem(),
                ImmutableList.of(project));

            task.subscribe(
                next -> {
                    System.out.println(next);
                },
                error -> {
                    error.printStackTrace();
                },
                () -> {
                    System.out.println("Done. ");
                }
            );

            return Unit.of();
        };
    }

    public static UninstallCommand of(final PartialRecipeIdentifier project) {
        return new UninstallCommand(project);
    }

    public static UninstallCommand of(final Identifier project) {
        return new UninstallCommand(PartialRecipeIdentifier.of(project));
    }

    public static UninstallCommand of(final RecipeIdentifier project) {
        return new UninstallCommand(PartialRecipeIdentifier.of(project));
    }
}
