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

public class ResourceFileCache<T> {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceFileCache.class);

    private final ConcurrentHashMap<String, T> fileCache = new ConcurrentHashMap<>();

    private boolean loadOnStartup = false;
    private FileSystem fileSystem;

    private String sourceFolder;
    private BiFunction<String, byte[], T> cacheObjctSupplier;
    private String httpPrefix = "/";
    private Path rootPath;
    private boolean isFromJar = false;

    /**
     */
    public ResourceFileCache(String sourceFolder, BiFunction<String, byte[], T> cacheObjctSupplier,
            boolean loadOnStartup) throws URISyntaxException, IOException {
        this.sourceFolder = sourceFolder;
        this.cacheObjctSupplier = cacheObjctSupplier;
        this.loadOnStartup = loadOnStartup;
        initialize();

        if (loadOnStartup) {
            loadAllResources();
        } else {
            LOG.info("Resource file cache is set to load on demand [{}]", rootPath.toUri());
        }
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
            if (loadOnStartup) {
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
    private void loadAllResources() throws IOException {

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
        LOG.info("Loaded {} resource files into cache from [{}]", fileCache.size(), rootPath.toUri());
    }

    /**
     * Loads a single resource file into the cache.
     */
    private T loadResource(String resourceName) throws IOException {

        Path filePath = rootPath.resolve(
                resourceName.startsWith(httpPrefix) ? resourceName.substring(1) : resourceName);
        byte[] content = Files.readAllBytes(filePath);
        String httpKey = httpPrefix + rootPath.relativize(filePath).toString().replace("\\", "/");

        T resource = cacheObjctSupplier.apply(httpKey, content);
        fileCache.put(httpKey, resource);
        return resource;
    }

    /**
     */
    private void initialize() throws URISyntaxException, IOException {
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

}