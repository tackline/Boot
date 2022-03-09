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
 * Launch a Multi-File Source-Code Program.
 * For use as a Single-File Source-Code Program.
 * Runs Main.main(String...),
 *   from Java source code,
 *   from src directory within directory with same name as this class
 *   (you may want to rename it).
 *   
 * Compiles to .class files, as in-memory compilation is a palaver.
 */
class Boot {
    private static final String mainClassName = "Main";
    private static final String srcDirName = "src";
    public static void main(
       String[] args
    ) throws
        URISyntaxException,
        IOException,
        ClassNotFoundException,
        NoSuchMethodException,
        IllegalAccessException,
        IllegalArgumentException,
        InvocationTargetException
    {
        Class<?> thisClass = MethodHandles.lookup().lookupClass();
        Path sub = getSubDir(thisClass);
        Path src = sub.resolve(srcDirName);
        Path classes = sub.resolve("classes");
        
        compile(src, classes);
        
        run(thisClass.getClassLoader().getParent(), classes, args);
    }
    private static Path getSubDir(
        Class<?> thisClass
    ) throws URISyntaxException, MalformedURLException {
        URL rootUrl = thisClass
            .getProtectionDomain().getCodeSource().getLocation();
        Path root = toFilePath(rootUrl);
        // You might think the code source would be the directory,
        //   like for a normal .class file,
        //   but no it is the .java file like .jar.
        if (isJava(root)) {
           root = root.getParent();
        }
        System.err.println(root);
        return root.resolve(thisClass.getSimpleName());
    }
    /**
     * Convert URL to File, even with UNC paths (not tested).
     */
    private static Path toFilePath(
        URL url
    ) throws URISyntaxException, MalformedURLException {
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
            uri = new URL(
                "file://" + url.toString().substring("file:".length())
            ).toURI();
        }
        return Path.of(uri);
    }
    /**
     * Compile booted classes.
     */
    private static void compile(
        Path src, Path classes
    ) throws IOException {
        Path[] srcFiles = findSource(src);
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
    /**
     * Find *.java source files.
     */
    private static Path[] findSource(Path src) throws IOException {
        final Path[] srcFiles;
        try (
            Stream<Path> walkedPaths =
                Files.walk(src, FileVisitOption.FOLLOW_LINKS)
                    .filter(path -> isJava(path))
        ) {
            srcFiles = walkedPaths.toArray(Path[]::new);
        }
        must(
           srcFiles.length != 0,
           30, "No Java source files found under "+src
        );
        return srcFiles;
    }
    /**
     * Indicates path is a .java file.
     */
    private static boolean isJava(Path path) {
        PathMatcher isJava =
            path.getFileSystem().getPathMatcher("glob:*.java");
        return 
            Files.isRegularFile(path) &&
            isJava.matches(path.getFileName());
    }
    /**
     * Execute booted classes.
     */
    private static void run(
        ClassLoader parentLoader, Path classes, String[] args
    ) throws
        MalformedURLException,
        ClassNotFoundException,
        NoSuchMethodException,
        IllegalAccessException,
        IllegalArgumentException,
        InvocationTargetException
    {
        ClassLoader loader = URLClassLoader.newInstance(
            new URL[] { classes.toUri().toURL() },
            parentLoader
        );
        // Java use to initialise the class before testing for
        //    the main method (change argument to true).
        Class<?> main = Class.forName(mainClassName, false, loader);
        Method method = main.getMethod("main", String[].class);
        must(
            method.getReturnType() == void.class,
            40, "Method "+method+" must return void"
        );
        int methodMods = method.getModifiers();
        must(
            Modifier.isStatic(methodMods),
            41, "Method "+method+" must be public"
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
