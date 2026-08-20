/* Authored by iqbserve.de */
package iqb.jps.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*********************************************************
 * Annotation Interfaces to annotate methods as WebServices.
 *********************************************************/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WebService {
    public String path() default "/";

    public String[] methods() default { "GET", "POST" };

    public String contentType() default "application/json";

    public String[] header() default {};
}
