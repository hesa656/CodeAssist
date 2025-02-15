package com.tyron.builder.project.api;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface JavaProject extends Project {

    /**
     * @return a map of fully qualified name and its java file
     */
    @NonNull
    Map<String, File> getJavaFiles();

    File getJavaFile(@NonNull String packageName);

    void removeJavaFile(@NonNull String packageName);

    void addJavaFile(@NonNull File javaFile);

    List<File> getLibraries();

    void addLibrary(@NonNull File jar);

    /**
     * @return The fully qualified name of all classes in this projects including its
     * libraries
     */
    List<String> getAllClasses();

    /**
     * @return The resources directory of the project. Note that
     * this is different from android's res directory
     */
    @NonNull
    File getResourcesDir();

    /**
     * @return The directory on where java sources will be searched
     */
    @NonNull
    File getJavaDirectory();

    File getLibraryDirectory();

    /**
     * This is required if the project uses lambdas
     *
     * @return a jar file which contains stubs for lambda compilation
     */
    File getLambdaStubsJarFile();

    /**
     * @return the bootstrap jar file which contains the necessary classes.
     * This includes {@code java.lang} package and other classes
     */
    File getBootstrapJarFile();
}
