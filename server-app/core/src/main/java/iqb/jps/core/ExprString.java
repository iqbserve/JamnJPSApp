/* Authored by iqbserve.de */
package iqb.jps.core;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * A simple class implementing template strings that include variable expressions.
 * </pre>
 */
public class ExprString {
    protected static String PatternStart = "${";
    protected static String PatternEnd = "}";
    // matches expressions like ${ name } accepting leading/ending whitespaces
    // BUT throwing RuntimeException - if name contains whitespaces
    protected static Pattern ExprPattern = Pattern.compile("\\$\\{\\s*([^\\s}]+)\\s*\\}");

    protected Map<String, String> values = null;
    protected ValueProvider provider = (String key, Object ctx) -> "unknown";

    /**
     */
    public static String applyValues(String template, ValueProvider provider) {
        return applyValues(template, provider, null);
    }

    /**
     */
    public static String applyValues(String template, ValueProvider provider, Object ctx) {
        StringBuilder result = new StringBuilder();
        String part = "";
        String name = "";
        String value = "";
        Matcher matcher = ExprPattern.matcher(template);

        int currentPos = 0;
        while (matcher.find()) {
            part = template.substring(currentPos, matcher.start());
            name = matcher.group().replace(PatternStart, "").replace(PatternEnd, "").trim();
            if (name.contains(" ")) {
                throw new UncheckedExprStringException(
                        String.format("ExprString contains whitespace(s) [%s]", name));
            }
            value = provider.getValueFor(name, ctx);
            result.append(part).append(value);
            currentPos = matcher.end();
        }
        if (currentPos < template.length()) {
            part = template.substring(currentPos, template.length());
            result.append(part);
        }
        return result.toString();
    }

    /**
     */
    protected ExprString() {
    }

    /**
     */
    public ExprString(ValueProvider provider) {
        this.provider = provider;
    }

    /**
     */
    public ExprString(Map<String, String> map) {
        this.values = map;
        this.provider = (String key, Object ctx) -> values.getOrDefault(key, "");
    }

    /**
     */
    public ExprString put(String key, String val) {
        values.put(key, val);
        return this;
    }

    /**
     */
    public ExprString clearValues() {
        if (values != null) {
            values.clear();
        }
        return this;
    }

    /**
     */
    public String applyTo(String template) {
        return applyTo(template, null);
    }

    /**
     */
    public String applyTo(String template, Object ctx) {
        return applyValues(template, this.provider, ctx);
    }

    /**
     * The Value Provider provides the values for the expression substitution.
     */
    public static interface ValueProvider {
        String getValueFor(String key, Object ctx);
    }

    /**
    */
    public static class UncheckedExprStringException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UncheckedExprStringException(String msg) {
            super(msg);
        }
    }
}
