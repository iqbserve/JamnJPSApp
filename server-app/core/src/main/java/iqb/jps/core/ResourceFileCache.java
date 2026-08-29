/* Authored by iqbserve.de */
package iqb.jps.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * <pre>
 * A generic resource file cache that loads files from the specified source folders.
 * The main task is to make web app files available.
 * 
 * It supports loading resources from both the filesystem and a hosting JAR file.
 * The cache is used by the JamnServer.WebContentProvider as its file system abstraction.
 * 
 * The cache also supports a user root folder for user local development, extensions or customizations.
 * 
 * The cache expects a resource path as 
 *  - a path starting with a slash: "/path/to/resource"
 * as it is submitted by a http request.
 * 
 * </pre>
 */
public class ResourceFileCache<T> implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceFileCache.class);
    // this separator should not be changed and not used for path creation
    // it is only used to create the resource path keys in the cache
    private static final String RESPATH_SPTR = "/";

    // cache to store loaded resource files of type <T>
    private final ConcurrentHashMap<String, T> fileCache = new ConcurrentHashMap<>();

    private FileSystem fileSystem;

    // the folders or paths from which resources are loaded
    // defined in the app properties (e.g., jps.web.root and
    // jps.user.web.local.root)
    private String sourceFolder;
    private String userSourceFolder;
    // the equivalent Path objects for the source folders
    private Path rootPath;
    private Path userRootPath = null;
    // supplier factory function to create T objects from file content
    private BiFunction<String, byte[], T> cacheObjctSupplier;

    private boolean loadOnStartup = false;
    private boolean isFromJar = false;
    private boolean cachingEnabled = true;
    private boolean userRootEnabled = false;
    // Only true if this instance created the zip file system itself
    private boolean fileSystemOwned = false;

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

        doStartupLoad();

        LOG.info("Resource cache initialization done: enabled: [{}] startload: [{}] system-root: [{}] user-root: [{}]",
                cachingEnabled,
                loadOnStartup, rootPath != null ? rootPath.toUri() : "none",
                userRootPath != null ? userRootPath.toUri() : "none");
    }

    /**
     */
    private void initialize() throws URISyntaxException, IOException {

        initUserRootPath();

        if (sourceFolder != null && !sourceFolder.trim().isEmpty()) {
            URL resourceUrl = getClass().getClassLoader().getResource(sourceFolder);
            if (resourceUrl == null) {
                Path externalPath = Paths.get(sourceFolder);
                if (Files.exists(externalPath) && Files.isDirectory(externalPath)) {
                    resourceUrl = externalPath.toUri().toURL();
                } else {
                    throw new IllegalArgumentException("Resource folder not found [" + sourceFolder + "]");
                }
            }

            // check if the resource URL points to the JAR or to the filesystem
            URI rootFolderUri = resourceUrl.toURI();
            isFromJar = rootFolderUri.getScheme().equals("jar");

            // check if running from inside a JAR file or filesystem
            // (IDE /target/classes)
            if (isFromJar) {
                try {
                    fileSystem = FileSystems.getFileSystem(rootFolderUri);
                    fileSystemOwned = false;
                } catch (FileSystemNotFoundException _) {
                    // If the JAR file system isn't open yet, create it; we own it and may close it
                    // later
                    fileSystem = FileSystems.newFileSystem(rootFolderUri, Collections.emptyMap());
                    fileSystemOwned = true;
                }
                rootPath = fileSystem.getPath(sourceFolder);
            } else {
                rootPath = Paths.get(rootFolderUri);
            }
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

    /**
     * Retrieves a cached resource file as T object.
     */
    public T getResource(String path) throws IOException {
        T resource = fileCache.get(path);
        if (resource == null) {
            // if cache is fully preloaded - resource is savely not available here
            // avoid touching a possibly closed file system in loadResource
            if (loadOnStartup && cachingEnabled) {
                throw new FileNotFoundException("Resource file not found in cache: " + path);
            }
            resource = loadResource(path);
        }
        return resource;
    }

    /**
     * Imperative push method for putting a resource into the cache.
     * The method is intended for external use.
     * In this way e.g. extensions can register resources at runtime.
     */
    public void registerResource(String path, byte[] resourceData) {
        T resource = cacheObjctSupplier.apply(path, resourceData);
        // enforce put to cache even if caching is disabled for this operation
        fileCache.put(path, resource);
    }

    /**
     * Closes the zip file system if it was created by this instance.
     */
    @Override
    public void close() throws IOException {
        if (fileSystemOwned && isFromJar && fileSystem != null && fileSystem.isOpen()) {
            fileSystem.close();
        }
    }

    /**
     * Puts a resource into the cache if caching is enabled.
     */
    private void putToCache(String path, T resource) {
        if (cachingEnabled) {
            fileCache.put(path, resource);
        }
    }

    /**
     * Loads all resources into the cache - if enabled.
     */
    private void doStartupLoad() throws IOException {

        // check for the legal but senseless combination to warn about
        if (loadOnStartup && !cachingEnabled) {
            LOG.warn("Skipping resource startup load: loadOnStartup is true but caching is disabled.");
            return;
        } else if (!loadOnStartup) {
            return;
        }

        AtomicInteger resourceCounter = new AtomicInteger();

        // walk through the directory tree recursively
        try (Stream<Path> pathStream = Files.walk(rootPath)) {
            pathStream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    byte[] content = Files.readAllBytes(path);
                    String cacheKey = createCacheKeyFor(rootPath, path);
                    T resource = cacheObjctSupplier.apply(cacheKey, content);
                    putToCache(cacheKey, resource);
                    resourceCounter.incrementAndGet();
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read file: " + path, e);
                }
            });
        } finally {
            // reaching here implies cachingEnabled=true (checked above),
            // so rootPath is fully preloaded - and no longer accessed
            // an owned zip file system can now be closed
            close();
        }
        LOG.info("Loaded [{}] System resource files into cache", resourceCounter.get());

        if (userRootEnabled) {
            resourceCounter.set(0);
            // load user root resources for overwriting
            // walk through the directory tree recursively
            try (Stream<Path> pathStream = Files.walk(userRootPath)) {
                pathStream.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        byte[] content = Files.readAllBytes(path);
                        String cacheKey = createCacheKeyFor(userRootPath, path);
                        T resource = cacheObjctSupplier.apply(cacheKey, content);
                        putToCache(cacheKey, resource);
                        resourceCounter.incrementAndGet();
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to read file: " + path, e);
                    }
                });
            }
            LOG.info("Loaded [{}] User resource files into cache", resourceCounter.get());
        }
    }

    /**
     * Loads a single resource file from the user or system root.
     */
    private T loadResource(String path) throws IOException {

        T resource = null;

        // first try user resource if enabled
        resource = loadUserResource(path);
        if (resource != null) {
            return resource;
        }

        try {
            // else do standard lookup
            Path filePath = resolveWithinRoot(rootPath, path);
            byte[] content = Files.readAllBytes(filePath);
            String cacheKey = createCacheKeyFor(rootPath, filePath);
            resource = cacheObjctSupplier.apply(cacheKey, content);
            putToCache(cacheKey, resource);

        } catch (IOException e) {
            LOG.debug("Resource file not found or not accessible: [{}]", path, e);
            throw new FileNotFoundException("Resource file not found: [" + path + "]");
        }
        return resource;
    }

    /**
     * If user root path is defined, loads a single user resource file.
     */
    private T loadUserResource(String resourceName) throws IOException {

        if (userRootEnabled) {
            Path userFilePath = resolveWithinRoot(userRootPath, resourceName);
            // existence must be checked,
            // because user root is called first and may not contain any files
            if (Files.exists(userFilePath) && Files.isRegularFile(userFilePath)) {
                byte[] content = Files.readAllBytes(userFilePath);
                String cacheKey = createCacheKeyFor(userRootPath, userFilePath);

                T resource = cacheObjctSupplier.apply(cacheKey, content);
                putToCache(cacheKey, resource);
                return resource;
            }
        }
        return null;
    }

    /**
     * Create the key used by the cache to store files.
     * The cache key is equivalent to the resource name used to access the resource.
     * However - there are two situations where the key is needed
     * - when walking a file root for loading - key must be created from file path
     * - and when accessing a resource by its name - name is the key
     * expected to be like: "/path/to/resource"
     * The function provides a unique generation method.
     */
    private String createCacheKeyFor(Path root, Path filePath) {
        return RESPATH_SPTR + root.relativize(filePath).toString().replace("\\", RESPATH_SPTR);
    }

    /**
     * Resolves a resource name against a root path and rejects any path traversal
     * attempt
     * (e.g. via "../" segments) that would escape that root.
     */
    private Path resolveWithinRoot(Path root, String resourceName) throws IOException {
        // use hardcoded 1 because RESPATH_SPTR is always a single character and should
        // never change
        String relative = resourceName.startsWith(RESPATH_SPTR) ? resourceName.substring(1)
                : resourceName;
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Resource path escapes root folder: " + resourceName);
        }
        return resolved;
    }
}