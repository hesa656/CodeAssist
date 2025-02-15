package com.tyron.builder.compiler2.api;

import androidx.annotation.Nullable;

import java.util.Set;

/**
 * <p>A <code>TaskDependency</code> represents an <em>unordered</em> set of tasks which a {@link Task} depends on.
 * Gradle ensures that all the dependencies of a task are executed before the task itself is executed.</p>
 *
 * <p>You can add a <code>TaskDependency</code> to a task by calling the task's {@link Task#dependsOn(Object...)}
 * method.</p>
 */
public interface TaskDependency {
    /**
     * <p>Determines the dependencies for the given {@link Task}. This method is called when Gradle assembles the task
     * execution graph for a build. This occurs after all the projects have been evaluated, and before any task
     * execution begins.</p>
     *
     * @param task The task to determine the dependencies for.
     * @return The tasks which the given task depends on. Returns an empty set if the task has no dependencies.
     */
    Set<? extends Task> getDependencies(@Nullable Task task);
}