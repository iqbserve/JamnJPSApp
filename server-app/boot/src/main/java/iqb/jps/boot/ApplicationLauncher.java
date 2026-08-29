/* Authored by iqbserve.de */

package iqb.jps.boot;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * <pre>
 * The AppLauncher launches the main application from the executable JAR file.
 * It locates the executable JAR, extracts embedded module JARs, builds a class loader with the necessary URLs,
 * and invokes the main method of the application class.
 * 
 * The embedded JARs get extracted to a system temp directory
 * and are deleted on shutdown and on startup.
 * A command in /users/<my-name>/ like: dir /s /b /ad <jar.temp.dir.prefix>* (e.g. jpsapp-modules)
 * should show currently still existing folders.
 * 
 * The Launcher is configered in the executable jar manifest.mf as the Main-Class.
 * 
 * </pre>
 */
public class ApplicationLauncher {

    public static final String BUILD_INFO_PROPERTIES = "/build.info.properties";

    private static final Logger LOG = Logger.getLogger(ApplicationLauncher.class.getName());

    //false for suppressing hook warnings
    private static boolean HookWarnings = true;

    //maintain the order
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
     * <pre>
     * Related to: sonarqube(java:S5443)
     * Creates a temporary directory that is only accessible by the current user.
     * On POSIX systems (Linux, macOS), directory permissions are set to {rwx------}.
     * On Windows, an explicit ACL is applied to restrict access to the current user only.
     * </pre>
     */
    private static Path createPrivateTempDirectory(String prefix) throws IOException {
        // Compliant with POSIX file permission requirements
        if (FileSystems.getDefault()
                .supportedFileAttributeViews()
                .contains("posix")) {

            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwx------");
            FileAttribute<Set<PosixFilePermission>> attributes = PosixFilePermissions.asFileAttribute(permissions);
            return Files.createTempDirectory(prefix, attributes);
        }

        // on windows: directory creation and ACL applying are two steps
        Path tempDir = Files.createTempDirectory(prefix); // sonarqube(java:S5443)
        applyOwnerOnlyAcl(tempDir);
        return tempDir;
    }

    /**
     * <pre>
     * Related to: sonarqube(java:S5443)
     * Applies a Windows ACL to path that grants full control exclusively
     * to the current OS user and removes all inherited/other entries.
     * </pre>
     */
    private static void applyOwnerOnlyAcl(Path path) throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (aclView == null) {
            // ACL view not supported on this file system — nothing more we can do
            return;
        }

        UserPrincipal owner = aclView.getOwner();

        AclEntry ownerEntry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();

        // Replace the entire ACL with a single entry for the current user only
        aclView.setAcl(List.of(ownerEntry));
    }

    /**
     */
    private static Path extractEmbeddedJars(Path sourcePath, String libsDir, String tempDirPrefix) throws IOException {
        // remove leftovers from previous runs whose files were still locked
        // (e.g. GraalVM/Truffle's) at shutdown time
        cleanupStaleTempDirectories(tempDirPrefix);

        Path tempDir = createPrivateTempDirectory(tempDirPrefix);

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
     * <pre>
     * The method deletes directories and files from previous runs that could not be removed by the shutdown hook.
     * In the JPSApp context, these are typically the GraalVM/Truffle libs
     * because GraalJS keeps its native compiler library memory-mapped and therefor this lib(s) cannot be deleted immediately.
     * By the time this method runs on the next launch, the
     * owning JVM has fully exited, the lock is gone and files and folder can be deleted successfully.
     * </pre>
     */
    private static void cleanupStaleTempDirectories(String tempDirPrefix) {
        Path tempRoot = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> stream = Files.list(tempRoot)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(tempDirPrefix))
                    .forEach(dir -> {
                        List<String> failed = ApplicationLauncher.deleteRecursivelyBestEffort(dir);
                        if (!failed.isEmpty()) {
                            LOG.log(Level.WARNING, "Failed to fully delete stale temp directorie: [{}]", dir);
                        }
                    });
            LOG.log(Level.INFO, "Successful cleanup for stale temporary directories.");
        } catch (IOException e) {
            LOG.log(Level.FINE, "Could not scan for stale temp directories in [{0}]: {1}",
                    new Object[] { tempRoot, e.getMessage() });
        }
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
            hookLog(Level.INFO, String.format("Shutdown initiated for: [%s]", appName));
            try {
                // get whether shutdown warnings are enabled
                HookWarnings = Boolean.parseBoolean(System.getProperty("jps.shutdown.warnings.enabled", "true"));

                // close the ClassLoader to release file handles on extracted JARs
                if (classLoader != null) {
                    classLoader.close();
                }

                // perform recursive deletion; best-effort so a single locked file
                // (e.g. held by GraalJS) doesn't leave the
                // rest of the tree undeleted. Any leftover is swept up on next launch.
                List<String> failedToDelete = deleteRecursivelyBestEffort(directory);

                if (failedToDelete.isEmpty()) {
                    hookLog(Level.INFO, String.format("Cleaned up temporary directory: [%s]", directory));
                } else {
                    hookLog(Level.WARNING, String.format(
                            "Standard-Information about known behavior. Could not fully clean up temporary directory on shutdown: [%s]. "
                                    + "Some files (e.g. GraalJS/Truffle) are still locked by the JVM "
                                    + "and will be removed on the next application start. Undeleted [ %s ]",
                            directory, String.join(", ", failedToDelete)));
                }
            } catch (Exception e) {
                hookLog(Level.SEVERE, String.format("Failed to clean up temporary directory: [%s] due to: [%s]",
                        directory, e.getMessage()));
            }
        }));
    }

    /**
     * <pre>
     * Deletes a directory tree, skipping and caching any file that cannot be removed.
     * </pre>
     * 
     * @return a list of file names that could not be deleted, empty if all files
     *         were successfully removed
     */
    private static List<String> deleteRecursivelyBestEffort(Path path) {
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }

        List<String> failedToDelete = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> pathsToDelete = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path file : pathsToDelete) {
                try { // NOSONAR
                    Files.delete(file);
                } catch (IOException _) {
                    failedToDelete.add(file.getFileName().toString());
                }
            }
        } catch (IOException e) {
            failedToDelete.add(String.format("Could not walk directory [%s]: %s", path, e.getMessage()));
        }
        return failedToDelete;
    }

    /**
     * <pre>
     * Used for vm cleanup hook
     * because standard logging is not reliably available during JVM shutdown.
     * </pre>
     */
    private static void hookLog(Level logLevel, Object message) {

        //suppress warnings if enabled
        if (!HookWarnings && Level.WARNING.equals(logLevel)) {
            return;
        }
        System.out.println("[" + logLevel.getName() + "] " + message); // NOSONAR
    }
}
