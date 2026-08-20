/* Authored by iqbserve.de */
package iqb.jps.appcomp;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import iqb.jps.JPSApp;
import iqb.jps.annotation.WebService;
import iqb.jps.core.AppConfig;
import iqb.jps.core.HelperTool;
import iqb.jps.core.JsonTool;
import iqb.jps.core.WebServiceRegistry;

/**
 * <pre>
 * Extensions are the way to dynamically load java "modules" 
 * that provide functionality that can be offered as command functions
 * and/or as HTTP rest service endpoints.
 * 
 * Each extension is defined in a JSON file located in the extensions directory.
 * The name of the JSON file is the name of the extension.
 * </pre>
 * 
 */
public class ExtensionHandler {

    protected static final Logger LOG = LoggerFactory.getLogger(ExtensionHandler.class);
    protected static final HelperTool Tool = HelperTool.getInstance();

    protected final Path pathBase;
    protected final Charset encoding;
    protected final Map<String, ExtensionCartridge> extensions;
    protected final JPSApp jpsApp;
    protected final JsonTool json;
    protected final AppConfig config;

    protected WebServiceRegistry webServiceRegistry;

    /**
     */
    public ExtensionHandler(Path extensionRoot, JPSApp jpsApp) {
        this.pathBase = extensionRoot;
        this.extensions = new HashMap<>();
        this.jpsApp = jpsApp;
        this.encoding = jpsApp.getStandardEncoding();
        this.json = jpsApp.getJsonTool();
        this.config = jpsApp.getAppConfig();
    }

    /**
     */
    public void setWebServiceRegistry(WebServiceRegistry webServiceRegistry) {
        this.webServiceRegistry = webServiceRegistry;
    }

    /**
     */
    public Path getRootPath() {
        return pathBase;
    }

    /**
     */
    public void loadExtension(String name) {
        getExtensionCat(name);
    }

    /**
     */
    public String run(ExtensionCallContext ctx, String name, Object... args) {
        String result = "";
        ExtensionCartridge cart = getExtensionCat(name);

        try {
            Object instance = cart.getRunInstance(Optional.ofNullable(ctx));
            result = (String) cart.runMethod.invoke(instance, (Object) args);
        } catch (Exception e) {
            throw new UncheckedExtensionException(
                    String.format("Extension execution failed [%s]", name), e);
        }
        if (ctx != null) {
            ctx.result = result != null ? result : "";
            return ctx.getResult();
        }
        return result;
    }

    /********************************************************************************/
    /********************************************************************************/
    /**
     */
    protected ExtensionCartridge getExtensionCat(String name) {
        // normalize the name to avoid case-sensitivity issues
        name = name.trim().toLowerCase();
        return extensions.computeIfAbsent(name, key -> {
            ExtensionDef def = readDefinition(key);
            ExtensionCartridge cart = new ExtensionCartridge(key, def);
            initExtension(cart);
            return cart;
        });
    }

    /**
     */
    protected void initExtension(ExtensionCartridge cart) {
        ExtensionDef def = cart.def;
        Path file = null;
        List<URL> urls = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try {
            file = resolvePath(def.hasDevPath() ? def.getDevPath() : def.getBinPath(), def);
            if (def.hasDevPath()) {
                String path = file.toString();
                LOG.info("HINT use extension development path for: [{}] devPath=[{}]", def, path);
            }
            Tool.createFileURL(file, urls, def, errors);

            for (String lib : def.getLibs()) {
                file = resolvePath(lib, def);
                Tool.createFileURL(file, urls, def, errors);
            }

            if (!errors.isEmpty()) {
                throw new UncheckedExtensionException(
                        String.format("Extension binary init failed: %s", errors));
            } else {
                ClassLoader rootLoader = def.hasAppScope() ? Thread.currentThread().getContextClassLoader()
                        : ClassLoader.getPlatformClassLoader();
                cart.loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), rootLoader);

                cart.clazz = cart.loader.loadClass(def.getClassName());
                cart.initConstructor();
                cart.initRunMethod();
                cart.initHttpEndpoints();
            }
            LOG.info("Extension installed: {} : {}", cart.name, cart.def);
        } catch (UncheckedExtensionException e) {
            cart.close();
            throw e;
        } catch (Exception e) {
            cart.close();
            throw new UncheckedExtensionException(
                    String.format("Extension basic initialization failed for [%s] - %s", cart.def,
                            Tool.getStackTraceFrom(e)),
                    e);
        }
    }

    /**
     */
    protected ExtensionDef readDefinition(String name) {
        String jsonDef;
        Path defFile = null;
        ExtensionDef def = null;

        try {
            defFile = Paths.get(pathBase.toString(), name + ".json");
            if (Files.exists(defFile)) {
                jsonDef = new String(Files.readAllBytes(defFile), encoding);
                def = json.toObject(jsonDef, ExtensionDef.class);
            } else {
                throw new UncheckedExtensionException(
                        String.format("NO extension definition-file found [%s]", defFile.getFileName()));
            }
        } catch (IOException e) {
            throw new UncheckedExtensionException(
                    String.format("Error reading extension definition-file [%s]", defFile.getFileName()), e);
        }
        return def;
    }

    /**
     */
    protected Path resolvePath(String path, ExtensionDef def) {
        String pathString = path.trim();
        Path resPath = pathString.startsWith(".") ? Paths.get(pathBase.toString(), pathString.substring(1))
                : Paths.get(pathString);
        if (!Files.exists(resPath)) {
            throw new UncheckedExtensionException(
                    String.format("Unknown extension file path [%s] [%s]", resPath, def));
        }
        return resPath;
    }

    /**
     */
    protected class ExtensionCartridge {
        protected String name;
        protected ExtensionDef def;
        protected URLClassLoader loader;
        protected Class<?> clazz;
        protected Constructor<?> constructor = null;
        protected boolean useContext = false;
        protected Class<?> contextClass;

        protected Method runMethod = null;
        protected Object extensionInstance = null;

        protected ExtensionCartridge(String name, ExtensionDef def) {
            this.name = name;
            this.def = def;
        }

        /**
         */
        public String toString() {
            return String.format("[%s] - [%s]", name, def);
        }

        /**
         */
        protected void close() {
            try {
                if (loader != null) {
                    loader.close();
                }
            } catch (Exception e) {
                throw new UncheckedExtensionException(
                        String.format("Closing Extension classloader failed [%s]", def), e);
            } finally {
                loader = null;
                extensionInstance = null;
            }
        }

        /**
         */
        protected void initConstructor() throws SecurityException {
            useContext = false;
            contextClass = null;

            if (def.hasAppScope()) {
                if (!getConstructorForContext(ExtensionInstanceContext.class)
                        && !getConstructorForContext(Map.class)) {
                    getConstructorForContext(null);
                }
            } else {
                if (!getConstructorForContext(Map.class)) {
                    getConstructorForContext(null);
                }
            }

            if (constructor == null) {
                throw new UncheckedExtensionException(
                        String.format("Error initializing extension constructor for [%s] - No constructor found", def));
            }
        }

        /**
         */
        protected boolean getConstructorForContext(Class<?> ctxClass) {
            constructor = null;
            contextClass = ctxClass;
            try {
                if (contextClass != null) {
                    constructor = clazz.getConstructor(contextClass);
                    useContext = true;
                } else {
                    constructor = clazz.getConstructor();
                    useContext = false;
                }
            } catch (Exception _) {
                contextClass = null;
                useContext = false;
                return false;
            }
            return true;
        }

        /**
         */
        protected void initRunMethod() throws NoSuchMethodException, SecurityException {
            if (def.hasRunMethod()) {
                runMethod = clazz.getMethod(def.getRunMethod(), String[].class);
            } else {
                runMethod = null;
            }
        }

        /**
         */
        protected void initHttpEndpoints() {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(WebService.class)) {
                    webServiceRegistry.registerServices(() -> getRunInstance(Optional.empty()));
                    break;
                }
            }
        }

        /**
         */
        protected Object newInstance(Optional<ExtensionCallContext> callCtx)
                throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                InvocationTargetException {
            Object newInstance = null;

            Consumer<String> outputConsumer = callCtx.isPresent() ? callCtx.get().getOutputConsumer() : null;

            if (useContext) {
                if (this.contextClass == Map.class) {
                    Map<String, Object> ctx = new HashMap<>();
                    ctx.put("name", this.name);
                    ctx.put("config", this.def.config);
                    ctx.put("output", outputConsumer);
                    newInstance = constructor.newInstance(ctx);
                } else if (this.contextClass == ExtensionInstanceContext.class) {
                    ExtensionInstanceContext ctx = new ExtensionInstanceContext(outputConsumer, 
                        Paths.get(pathBase.toString(), config.getExtensionData()), jpsApp);
                    newInstance = constructor.newInstance(ctx);
                }
            } else {
                newInstance = constructor.newInstance();
            }

            return newInstance;
        }

        /**
         */
        protected Object getRunInstance(Optional<ExtensionCallContext> callCtx) {
            try {
                if (def.isSingleton()) {
                    if (extensionInstance == null) {
                        extensionInstance = newInstance(callCtx);
                    }
                    return extensionInstance;
                } else {
                    return newInstance(callCtx);
                }
            } catch (Exception e) {
                throw new UncheckedExtensionException(
                        String.format("Extension instance creation failed [%s]", def), e);
            }
        }
    }

    /**
     */
    public static class ExtensionDef {
        protected String binPath = "";
        protected String devPath = "";
        protected List<String> libs = new ArrayList<>();
        protected String className = "";
        protected String runMethod = "";
        protected String scope = "";
        protected boolean singleton = false;

        protected Map<String, String> config = new HashMap<>();

        public String getBinPath() {
            return binPath;
        }

        @JsonProperty("singleton")
        public boolean isSingleton() {
            return singleton;
        }

        public String getDevPath() {
            return devPath;
        }

        public boolean hasDevPath() {
            String path = devPath.trim();
            // simulate a commenting out
            return (!path.isEmpty() && !path.startsWith("#"));
        }

        public boolean hasAppScope() {
            return scope.trim().equalsIgnoreCase("app");
        }

        public boolean hasRunMethod() {
            return !runMethod.trim().isEmpty();
        }

        public String getClassName() {
            return className;
        }

        public List<String> getLibs() {
            return libs;
        }

        public String getRunMethod() {
            return runMethod;
        }

        public String getScope() {
            return scope;
        }

        @Override
        public String toString() {
            BinaryOperator<String> field = (name, value) -> name + "=" + value;
            return String.format("[%s]",
                    String.join(", ",
                            field.apply("className", this.className),
                            field.apply("runMethod", this.runMethod),
                            field.apply("scope", this.scope)));
        }

        @JsonIgnore
        public boolean isEmpty() {
            return (binPath.isEmpty() || className.isEmpty());
        }

    }

    /**
     */
    public static class ExtensionCallContext {

        // the output consumer is used to forward extension output
        private Consumer<String> outputConsumer = null;
        private String result = null;

        public ExtensionCallContext() {
        }

        public ExtensionCallContext(Consumer<String> outputConsumer) {
            this.outputConsumer = outputConsumer;
        }

        public Consumer<String> getOutputConsumer() {
            return outputConsumer;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }

    /**
     */
    public static class ExtensionInstanceContext {
        // the output consumer is used to forward extension output
        private Consumer<String> outputConsumer = null;
        private final JPSApp jpsApp;
        private final Path dataPath;

        public ExtensionInstanceContext(Consumer<String> outputConsumer, Path dataPath, JPSApp jpsApp) {
            this.outputConsumer = outputConsumer;
            this.jpsApp = jpsApp;
            this.dataPath = dataPath;
        }

        public Consumer<String> getOutputConsumer() {
            return outputConsumer;
        }

        public JsonTool getJsonTool() {
            return jpsApp.getJsonTool();
        }

        public AppConfig getAppConfig() {
            return jpsApp.getAppConfig();
        }

        public Path getDataPath() {
            return dataPath;
        }

        public Charset getEncoding() {
            return jpsApp.getStandardEncoding();
        }

    }

    /**
     */
    public static class UncheckedExtensionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedExtensionException(String msg) {
            super(msg);
        }

        public UncheckedExtensionException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
