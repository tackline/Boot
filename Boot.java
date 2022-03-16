/*
 * Copyright 2022 Thomas Hawtin
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import javax.tools.*;

/**
 * Launch a Multi-File Source-Code Program
 *    with code from https://github.com/tackline/Boot/blob/main/Boot.java .
 * For use as a Single-File Source-Code Program.
 * Runs Main.main(String...),
 *   from Java source code,
 *   from src directory within directory with same name as this class
 *   (you may want to rename it).
 *   
 * Compiles to .class files, as in-memory compilation is a palaver.
 */
public class Boot {
    public static void main(
       String... args
    ) throws
        URISyntaxException,
        IOException,
        ClassNotFoundException,
        NoSuchMethodException,
        IllegalAccessException,
        InvocationTargetException
    {
        launchFromSource("src", "Main", args);
    }
    /**
     * Runs code from source, located relative to this class.
     * @throws URISyntaxException if code source (classpath) really weird
     */
    public static void launchFromSource(
       String relativeSrcDir, String mainClass, String... args
    ) throws
        URISyntaxException,
        IOException,
        ClassNotFoundException,
        NoSuchMethodException,
        IllegalAccessException,
        InvocationTargetException
    {
        Class<?> anchorClass = MethodHandles.lookup().lookupClass();
        launchFromSource(anchorClass, relativeSrcDir, mainClass, args);
    }
    public static void launchFromSource(
       Class<?> anchor, String relativeSrcDir, String mainClass, String... args
    ) throws
        URISyntaxException,
        IOException,
        ClassNotFoundException,
        NoSuchMethodException,
        IllegalAccessException,
        InvocationTargetException
    {
        runMain(classFromSource(anchor, relativeSrcDir, mainClass), args);
    }
    public static Class<?> classFromSource(
        Class<?> anchor, String relativeSrcDir, String className
    ) throws URISyntaxException, IOException, ClassNotFoundException {
        ClassLoader loader = classLoaderFromSource(anchor, relativeSrcDir);
        // Java use to initialise the class before testing for
        //    the main method (change argument to true).
        return Class.forName(className, false, loader);
    }
    public static ClassLoader classLoaderFromSource(
        Class<?> anchor, String relativeSrcDir
    ) throws URISyntaxException, IOException, ClassNotFoundException {
        Path sub = getSubDir(anchor);
        Path src = sub.resolve(relativeSrcDir);
        Path classes = src.resolve("../classes");
        
        compile(src, classes);
        
        ClassLoader parentLoader = anchor.getClassLoader().getParent();
        return URLClassLoader.newInstance(
            new URL[] { classes.toUri().toURL() },
            parentLoader
        );
    }
    /**
     * Returns the subdirectory everything else is under,
     *    based on anchor's code source location.
     * MyClass.java -> MyClass/
     * myproject/ -> myproject/MyClass/
     * myjar.jar -> MyClass/
     * unreadable -> unreadable/MyClass/
     */
    private static Path getSubDir(
        Class<?> anchor
    ) throws URISyntaxException {
        URL rootUrl = anchor
            .getProtectionDomain().getCodeSource().getLocation();
        Path root = toFilePath(rootUrl);
        // You might think the code source would be the directory,
        //   like for a normal .class file,
        //   but no it is the .java file like .jar.
        if (Files.isRegularFile(root)) {
           root = root.getParent();
        }
        return root.resolve(anchor.getSimpleName());
    }
    /**
     * Convert URL to File, even with UNC paths (not tested).
     */
    private static Path toFilePath(
        URL url
    ) throws URISyntaxException {
        // Based an idea from https://stackoverflow.com/a/18528710/4725
        URI uri = url.toURI();
        String scheme = uri.getScheme();
        must(
            "file".equals(scheme),
            10, "Must be run from file protocol, found scheme "+scheme
        ); 
        String authority = uri.getAuthority();
        if (authority != null && !authority.isEmpty()) {
            // Hack for UNC Path
            try {
               uri = new URL(
                   "file://" + url.toString().substring("file:".length())
               ).toURI();
            } catch (MalformedURLException e) {
               // Weird - don't adjust.
            }
        }
        return Path.of(uri);
    }
    public static void compile(
        Path src, Path classes
    ) throws IOException {
        Path[] srcFiles = listJavaFiles(src);
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        try (
            StandardJavaFileManager fileManager =
                javaCompiler.getStandardFileManager(
                    null, Locale.UK, java.nio.charset.StandardCharsets.UTF_8
                )
        ) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjects(srcFiles);
            JavaCompiler.CompilationTask task = javaCompiler.getTask(
                null,
                fileManager,
                null,
                List.of(
                    "-cp", src.toString(),
                    "-d", classes.toString(),
                    "-Xlint:all", "-Werror"
                ),
                null,
                units
            );
            must(task.call(), 20, null);
        }
    }
    private static Path[] listJavaFiles(Path src) throws IOException {
        PathMatcher isJava =
            src.getFileSystem().getPathMatcher("glob:*.java");
        final Path[] srcFiles;
        try (
            Stream<Path> walkedPaths =
                Files.walk(src, FileVisitOption.FOLLOW_LINKS)
                    .filter(path ->
                        Files.isRegularFile(path) &&
                        isJava.matches(path.getFileName())
                    )
        ) {
            srcFiles = walkedPaths.toArray(Path[]::new);
        }
        must(
           srcFiles.length != 0,
           30, "No Java source files found under "+src
        );
        return srcFiles;
    }
    private static void runMain(
        Class<?> main, String... args
    ) throws
        NoSuchMethodException,
        IllegalAccessException,
        InvocationTargetException
    {
        Method method = main.getMethod("main", String[].class);
        must(
            method.getReturnType() == void.class,
            40, "Method "+method+" must return void"
        );
        int methodMods = method.getModifiers();
        must(
            Modifier.isStatic(methodMods),
            41, "Method "+method+" must be static"
        );
        method.setAccessible(true); // Because class may be default access.
        method.invoke(null, new Object[] { args });
    }
    /**
     * Print message and exits if not a success.
     */
    private static void must(boolean success, int exitCode, String message) {
        if (!success) {
            if (message != null) {
                System.err.println(message);
            }
            System.exit(exitCode);
        }
    }
}
