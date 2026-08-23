/* Authored by iqbserve.de */

package iqb.jps.boot;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * <pre>
 * The AppLauncher is responsible for launching the main application from the executable JAR file.
 * It locates the executable JAR, extracts embedded module JARs, builds a class loader with the necessary URLs,
 * and invokes the main method of the application class.
 * 
 * The embedded JARs get extracted to a temp directory and are cleaned up on shutdown.
 * A command in /users/<my-name>/ like: dir /s /b /ad <jar.temp.dir.prefix>*
 * should find nothing after the app is closed.
 * 
 * The Launcher is configered in the executable jar manifest.mf as the Main-Class.
 * 
 * </pre>
 */
public class ApplicationLauncher {

    public static final String BUILD_INFO_PROPERTIES = "/build.info.properties";

    private static final Logger LOG = Logger.getLogger(ApplicationLauncher.class.getName());

    private static final String[] REQUIRED_PROPERTIES = {
            "appname",
            "app.class.name",
            "jar.libsdir",
            "jar.temp.dir.prefix" // prefix for the temporary directory where embedded JARs are extracted
    };

    private ApplicationLauncher() {
        // private constructor to prevent instantiation
    }

    /**
     * Manifest Main-Class start method of the executable jar.
     */
    public static void main(String[] args) throws Exception {
        try {
            Properties buildProps = loadBuildProperties();
            for (String key : REQUIRED_PROPERTIES) {
                if (!buildProps.containsKey(key) || buildProps.getProperty(key).isBlank()) {
                    throw new IllegalStateException(
                            String.format("Missing required property [%s] in [%s]", key, BUILD_INFO_PROPERTIES));
                }
            }

            String appName = buildProps.getProperty(REQUIRED_PROPERTIES[0]);
            String appClassName = buildProps.getProperty(REQUIRED_PROPERTIES[1]);
            String libsDir = buildProps.getProperty(REQUIRED_PROPERTIES[2]);
            String tempDirPrefix = buildProps.getProperty(REQUIRED_PROPERTIES[3]);

            Path appJarPath = locateExecutableJar();

            // use IDE / unpacked (../target/classes) execution
            // with the system class loader
            if (Files.isDirectory(appJarPath)) {
                LOG.log(Level.INFO, "Launching [{0}] in IDE/unpacked mode. Delegating to System ClassLoader.", appName);

                ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
                Thread.currentThread().setContextClassLoader(systemLoader);

                Class<?> appClass = Class.forName(appClassName, true, systemLoader);
                Method mainMethod = appClass.getMethod("main", String[].class);
                mainMethod.invoke(null, (Object) args);
                return;
            }

            LOG.log(Level.INFO, "Launching [{0}] in standard executable JAR mode.", appName);

            // standard executable JAR Mode
            Path modulesDir = extractEmbeddedJars(appJarPath, libsDir, tempDirPrefix);
            URL[] urls = buildClassLoaderUrls(appJarPath, modulesDir);

            // using the PlatformClassLoader as parent to maintain access to Java platform
            // APIs
            ClassLoader parentLoader = ClassLoader.getPlatformClassLoader();
            URLClassLoader appClassLoader = new URLClassLoader(urls, parentLoader);

            // register a recursive cleanup hook on shutdown
            // to delete the temporary directory and close the class loader
            registerShutdownCleanup(appClassLoader, modulesDir, appName);

            Thread currentThread = Thread.currentThread();
            currentThread.setContextClassLoader(appClassLoader);

            Class<?> appClass = Class.forName(appClassName, true, appClassLoader);
            Method mainMethod = appClass.getMethod("main", String[].class);

            // call the app main method
            mainMethod.invoke(null, (Object) args);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to launch application", e);
            System.exit(1);
        }
    }

    /**
     */
    private static Properties loadBuildProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream input = ApplicationLauncher.class.getResourceAsStream(BUILD_INFO_PROPERTIES)) {
            if (input == null) {
                throw new IOException("Resource not found: " + BUILD_INFO_PROPERTIES);
            }
            props.load(input);
        }
        return props;
    }

    /**
     */
    private static Path locateExecutableJar() throws URISyntaxException {
        return Path.of(ApplicationLauncher.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    }

    /**
     */
    private static Path extractEmbeddedJars(Path sourcePath, String libsDir, String tempDirPrefix) throws IOException {
        Path tempDir = Files.createTempDirectory(tempDirPrefix); //NOSONAR - TODO: check potential issue (java:S5443)

        // standard execution from the app JAR file
        try (JarFile jarFile = new JarFile(sourcePath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()
                        && entry.getName().startsWith(libsDir)
                        && entry.getName().endsWith(".jar")) {

                    Path fileName = Path.of(entry.getName()).getFileName();
                    Path target = tempDir.resolve(fileName);

                    try (InputStream is = jarFile.getInputStream(entry)) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        return tempDir;
    }

    /**
     */
    private static URL[] buildClassLoaderUrls(Path sourceJar, Path modulesDir) throws IOException {
        List<URL> urls = new ArrayList<>();

        // include the main executable jar/directory
        urls.add(sourceJar.toUri().toURL());

        // include the embedded module JARs
        if (Files.exists(modulesDir)) {
            try (Stream<Path> stream = Files.list(modulesDir)) {
                List<Path> moduleJars = stream
                        .filter(path -> path.toString().endsWith(".jar"))
                        .sorted()
                        .toList();
                for (Path jar : moduleJars) {
                    urls.add(jar.toUri().toURL());
                }
            }
        }
        return urls.toArray(new URL[0]);
    }

    /**
     */
    private static void registerShutdownCleanup(URLClassLoader classLoader, Path directory, String appName) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            hookLog(String.format("Shutdown initiated for: [%s]", appName));
            try {
                // close the ClassLoader to release file handles on extracted JARs
                if (classLoader != null) {
                    classLoader.close();
                }

                // perform recursive deletion
                deleteRecursively(directory);
                hookLog(String.format("Cleaned up temporary directory: [%s]", directory));
            } catch (IOException _) {
                hookLog(String.format("Failed to clean up temporary directory: [%s]", directory));
            }
        }));
    }

    /**
     */
    private static void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                List<Path> pathsToDelete = walk.sorted(Comparator.reverseOrder()).toList();
                for (Path file : pathsToDelete) {
                    // throws IOException if fails
                    Files.delete(file);
                }
            }
        }
    }

    /**
     * used for vm cleanup hook
     */
    private static void hookLog(Object message) {
        System.out.println(message); //NOSONAR - logging will not reach console out on hook
    }
}
