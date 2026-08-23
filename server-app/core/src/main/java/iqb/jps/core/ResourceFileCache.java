/* Authored by iqbserve.de */
package iqb.jps.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * <pre>
 * A generic resource file cache that loads files from a specified source folder.
 * 
 * Supports loading resources from both the filesystem and JAR files.
 * The cache is used by the JamnServer.WebContentProvider as a file system abstraction.
 * 
 * The cache also supports a user root folder for user local development, extensions or customizations.
 * </pre>
 */
public class ResourceFileCache<T> {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceFileCache.class);

    // cache to store loaded resource files of type <T>
    private final ConcurrentHashMap<String, T> fileCache = new ConcurrentHashMap<>();

    private FileSystem fileSystem;

    private String sourceFolder;
    private String userSourceFolder;
    // supplier factory function to create T objects from file content
    private BiFunction<String, byte[], T> cacheObjctSupplier;
    private String httpPrefix = "/";
    private Path rootPath;
    private Path userRootPath = null;

    private boolean loadOnStartup = false;
    private boolean isFromJar = false;
    private boolean cachingEnabled = true;
    private boolean userRootEnabled = false;

    /**
     * Constructs a new ResourceFileCache.
     *
     * @param cacheObjctSupplier a factory function to create T objects from file
     *                           content
     * @param appConfig          the application configuration object
     */
    public ResourceFileCache(BiFunction<String, byte[], T> cacheObjctSupplier,
            AppConfig appConfig) throws URISyntaxException, IOException {
        this.sourceFolder = appConfig.getWebRoot();
        this.loadOnStartup = appConfig.webCacheLoadOnStartup();
        this.cachingEnabled = appConfig.webCacheCachingEnabled();
        this.userSourceFolder = appConfig.getUserWebLocalRoot();
        this.cacheObjctSupplier = cacheObjctSupplier;

        initialize();

        if (loadOnStartup) {
            doStartupLoad();
        }
        LOG.info("Resource cache initialization done: enabled: [{}] startload: [{}] system-root: [{}] user-root: [{}]",
                cachingEnabled,
                loadOnStartup, rootPath.toUri(), userRootPath != null ? userRootPath.toUri() : "none");

    }

    /**
     * Checks if the application is running from a JAR file.
     */
    public static boolean isRunningFromJar() {
        String classPath = ResourceFileCache.class.getResource(ResourceFileCache.class.getSimpleName() + ".class")
                .toString();
        return classPath.startsWith("jar:");
    }

    /**
     * Retrieves a cached resource file as T object.
     */
    public T getResource(String path) throws IOException {
        T resource = fileCache.get(path);
        if (resource == null) {
            if (loadOnStartup && cachingEnabled) {
                throw new FileNotFoundException("Resource file not found in cache: " + path);
            }
            resource = loadResource(path);
            if (resource == null) {
                throw new FileNotFoundException("Resource file not found: " + path);
            }
        }
        return resource;
    }

    /**
     */
    public Path getRootPath() {
        return rootPath;
    }

    /**
     * Loads all resources from the root folder into the cache.
     */
    private void doStartupLoad() throws IOException {

        if (!loadOnStartup) {
            throw new IllegalStateException("Cannot load all resources when loadOnStartup is [false]");
        }

        // walk through the directory tree recursively
        try (Stream<Path> pathStream = Files.walk(rootPath)) {
            pathStream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    byte[] content = Files.readAllBytes(path);
                    String httpKey = httpPrefix + rootPath.relativize(path).toString().replace("\\", "/");
                    T resource = cacheObjctSupplier.apply(httpKey, content);
                    fileCache.put(httpKey, resource);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read file: " + path, e);
                }
            });
        } finally {
            // close zip file system if explicitly created for a JAR
            if (fileSystem != null && isFromJar && fileSystem.isOpen()) {
                fileSystem.close();
            }
        }
        LOG.info("Loaded [{}] System resource files into cache", fileCache.size());
    }

    /**
     * Loads a single resource file into the cache.
     */
    private T loadResource(String resourceName) throws IOException {

        T resource = null;

        // first try user resource if enabled
        resource = loadUserResource(resourceName);
        if (resource != null) {
            return resource;
        }

        // else do standard lookup
        Path filePath = rootPath.resolve(
                resourceName.startsWith(httpPrefix) ? resourceName.substring(1) : resourceName);
        byte[] content = Files.readAllBytes(filePath);
        String httpKey = httpPrefix + rootPath.relativize(filePath).toString().replace("\\", "/");

        resource = cacheObjctSupplier.apply(httpKey, content);

        if (cachingEnabled) {
            fileCache.put(httpKey, resource);
        }
        return resource;
    }

    /**
     * If user root path is defined, loads a single user resource file.
     */
    private T loadUserResource(String resourceName) throws IOException {

        if (userRootEnabled) {
            Path userFilePath = userRootPath.resolve(
                    resourceName.startsWith(httpPrefix) ? resourceName.substring(1) : resourceName);
            // existence must be checked,
            // because user root is called first and may not contain any files
            if (Files.exists(userFilePath) && Files.isRegularFile(userFilePath)) {
                byte[] content = Files.readAllBytes(userFilePath);
                String httpKey = httpPrefix + userRootPath.relativize(userFilePath).toString().replace("\\", "/");

                T resource = cacheObjctSupplier.apply(httpKey, content);
                if (cachingEnabled) {
                    fileCache.put(httpKey, resource);
                }
                return resource;
            }
        }
        return null;
    }

    /**
     */
    private void initialize() throws URISyntaxException, IOException {

        initUserRootPath();

        URL resourceUrl = getClass().getClassLoader().getResource(sourceFolder);
        if (resourceUrl == null) {
            Path externalPath = Paths.get(sourceFolder);
            if (Files.exists(externalPath) && Files.isDirectory(externalPath)) {
                resourceUrl = externalPath.toUri().toURL();
            } else {
                throw new IllegalArgumentException("Resource folder not found [" + sourceFolder + "]");
            }
        }

        URI rootFolderUri = resourceUrl.toURI();
        isFromJar = rootFolderUri.getScheme().equals("jar");

        // check if running from inside a JAR file or filesystem
        // (IDE /target/classes)
        if (isFromJar) {
            try {
                fileSystem = FileSystems.getFileSystem(rootFolderUri);
            } catch (FileSystemNotFoundException _) {
                // If the JAR file system isn't open yet, create it
                fileSystem = FileSystems.newFileSystem(rootFolderUri, Collections.emptyMap());
            }
            rootPath = fileSystem.getPath(sourceFolder);
        } else {
            rootPath = Paths.get(rootFolderUri);
        }
    }

    /**
     * Initializes the user root path if a user source folder is defined in the
     * configuration.
     * If defined first resource lookup is in the user root.
     */
    private void initUserRootPath() {

        userRootPath = null;
        userRootEnabled = false;

        if (userSourceFolder != null && !userSourceFolder.trim().isEmpty()) {
            Path userPath = null;
            if (userSourceFolder.startsWith("/")) {
                userPath = Paths.get(userSourceFolder);
            } else {
                userPath = Paths.get(Paths.get("").toString(), userSourceFolder);
            }

            if (Files.exists(userPath) && Files.isDirectory(userPath)) {
                userRootPath = userPath;
                userRootEnabled = true;
            } else {
                LOG.warn("Defined User Web root path does not exist: [{}]", userPath);
            }
        }
    }
}