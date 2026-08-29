/* Authored by iqbserve.de */
package iqb.jps.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 * Interfaces to annotate methods as WebResource.
 * A WebResource is any usual web content requested by a http GET <path> call.
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WebResource {

    public String path() default "/";

}
