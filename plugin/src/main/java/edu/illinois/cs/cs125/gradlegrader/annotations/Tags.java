package edu.illinois.cs.cs125.gradlegrader.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A collection of @Tag annotations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Tags {

    /**
     * Gets the tags on this test case.
     * @return the @Tag annotations applied
     */
    Tag[] value();

}
