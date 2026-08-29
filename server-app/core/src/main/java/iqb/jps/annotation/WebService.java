/* Authored by iqbserve.de */
package iqb.jps.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 * Interfaces to annotate methods as WebServices.
 * A WebService is an endpoint for a http GET or POST call,
 * with arguments passed via query parameter or request body.
 * By default the return value is a string resp. a serialized JSON object "application/json".
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WebService {
    public String path() default "/";

    public String[] methods() default { "GET", "POST" };

    public String contentType() default "application/json";

    public String[] header() default {};
}
