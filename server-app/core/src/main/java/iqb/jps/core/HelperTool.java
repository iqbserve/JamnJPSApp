/* Authored by iqbserve.de */
package iqb.jps.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.core.ExprString.ValueProvider;

public class HelperTool {

    public static final Pattern RegexNewLine = Pattern.compile("\\r?\\n|\\r");
    public static final Pattern RegexWhiteSpaces = Pattern.compile("\\s+");

    public static final String CDATA_START = "<![CDATA[";
    public static final String CDATA_END = "]]>";

    protected static Charset StandardEncoding = StandardCharsets.UTF_8;

    protected static void setEncoding(Charset encoding) {
        StandardEncoding = encoding;
    }

    private static final Logger LOG = LoggerFactory.getLogger(HelperTool.class);
    private static final HelperTool INSTANCE = new HelperTool();

    public static HelperTool getInstance() {
        return INSTANCE;
    }

    /**
     */
    public Map<String, Object> toMap(Properties props) {
        Map<String, Object> map = new HashMap<>();
        for (String name : props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
        return map;
    }

    /**
     */
    public Path ensureSubDir(String name, Path root) throws IOException {
        Path path = Paths.get(root.toString(), name);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            LOG.info("Directories created [{}]", path);
        }
        return path;
    }

    /**
     */
    public String getStackTraceFrom(Throwable t) {
        if (t instanceof InvocationTargetException te) {
            t = te.getTargetException();
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    /**
     */
    public String createErrorInfo(String text, Throwable t) {
        return new StringBuilder(text).append(": [")
                .append(t.getMessage()).append("] - [")
                .append(getStackTraceFrom(t)).append("]").toString();
    }

    /**
     */
    public Map<String, String> argsToConfigValues(String[] args) {
        Map<String, String> values = new HashMap<>();
        if (args != null && args.length > 0) {
            String[] keyValue;
            for (String arg : args) {
                if (arg.contains("=") && arg.startsWith("-")) {
                    while (arg.startsWith("-")) {
                        arg = arg.substring(1);
                    }
                    keyValue = arg.split("=");
                    values.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }
        return values;
    }

    /**
     */
    public String[] rebuildQuotedWhitespaceStrings(String[] token) {// NOSONAR
        List<String> newToken = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String tok = "";
        boolean inQuote = false;

        for (int i = 0; i < token.length; i++) {
            tok = token[i];

            if (tok.trim().startsWith("\"") && tok.trim().endsWith("\"")) {
                newToken.add(tok);
            } else {
                if (!inQuote && tok.contains("\"")) {
                    inQuote = true;
                    buffer = new StringBuilder(tok);
                    continue;
                }
                if (inQuote && tok.contains("\"")) {
                    inQuote = false;
                    buffer.append(" ").append(tok);
                    newToken.add(buffer.toString());
                } else if (inQuote) {
                    buffer.append(" ").append(tok);
                } else {
                    newToken.add(tok);
                }
            }
        }

        if (inQuote) {
            throw new UncheckedAppException("Missing start/end quote in command line string");
        }

        return newToken.toArray(new String[newToken.size()]);
    }

    /**
     */
    public String[] parseCommandLine(String text) {
        text = RegexWhiteSpaces.matcher(text).replaceAll(" ");
        text = RegexNewLine.matcher(text).replaceAll("").trim();

        List<String> args = new ArrayList<>();
        String startMark = CDATA_START;
        String endMark = CDATA_END;

        int startOffset = startMark.length();
        int endOffset = endMark.length();
        String block;
        int srcStart = 0;
        int start = text.indexOf(startMark);
        int end;
        String[] token;

        while (start > -1) {
            if (start > srcStart) {
                block = text.substring(srcStart, start);
                token = block.trim().split(" ");
                args.addAll(Arrays.asList(rebuildQuotedWhitespaceStrings(token)));
            }
            end = text.indexOf(endMark, start);
            if (end > -1) {
                block = text.substring(start + startOffset, end);
                args.add(block);
            } else {
                throw new UncheckedAppException("Missing block end mark [" + endMark + "] for [" + startMark + "]");
            }
            srcStart = end + endOffset;
            start = text.indexOf(startMark, srcStart);
        }

        if (srcStart < text.length()) {
            block = text.substring(srcStart);
            token = block.trim().split(" ");
            args.addAll(Arrays.asList(rebuildQuotedWhitespaceStrings(token)));
        }

        return args.toArray(new String[args.size()]);
    }

    /**
     * Parse arguments for JavaScript and Extension execution.
     */
    public String[] parseArgsFrom(String argsSrc, Map<String, String> msgData) {
        String[] args = parseCommandLine(argsSrc);

        // create an ExprString value provider
        ValueProvider provider = (String key, Object ctx) -> {
            String value = msgData.getOrDefault(key, "");
            // also accept indices
            if (value.isEmpty() && Character.isDigit(key.trim().charAt(0))) {
                int idx = Integer.parseInt(key.trim().substring(0, 1)) - 1;
                String[] data = msgData.values().toArray(new String[] {});
                if (idx >= 0 && idx < data.length) {
                    value = data[idx];
                }
            }
            return value;
        };

        // replace ${name} expressions with data from the message e.g. file content
        for (int i = 0; i < args.length; i++) {
            args[i] = ExprString.applyValues(args[i], provider);
        }
        return args;
    }

    /**
     */
    public String resolvePlaceholder(String text, Properties values) {
        return ExprString.applyValues(text, (String key, Object ctx) -> values.getProperty(key, ""));
    }

    /**
     */
    public void createFileURL(Path file, List<URL> urls, Object info, List<String> errors)
            throws MalformedURLException {

        if (Files.exists(file)) {
            urls.add(file.toUri().toURL());
        } else {
            errors.add(String.format("File does NOT exist [%s] [%s]", file, info));
        }
    }

    /**
     */
    public Path lastPartsOf(Path path, int n) {
        int partsCount = path.getNameCount();
        if (n < partsCount && partsCount > 1) {
            return path.subpath(partsCount - n, partsCount);
        }
        return path.getFileName();
    }

    /**
     */
    public Method findMethod(Class<?> target, String name, Class<?>... parameterTypes) {
        Method[] methods = target.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(name) && Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                return method;
            }
        }
        return null;
    }

    /**
     */
    public void getPropertiesFrom(Properties source, String[] keys, Properties target) {
        for (String key : keys) {
            target.setProperty(key, source.getProperty(key));
        }
    }

    /**
     * Reads the content of a string resource from the specified class and resource
     * path.
     */
    public String readStringResourceFrom(Class<?> clazz, String resourcePath) throws IOException {
        return new String(this.readResourceFrom(clazz, resourcePath), StandardEncoding);
    }

    /**
     * Reads the content of a resource from the specified class and resource path.
     */
    public byte[] readResourceFrom(Class<?> clazz, String resourcePath) throws IOException {
        try (InputStream is = clazz.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return is.readAllBytes();
        }
    }

}
