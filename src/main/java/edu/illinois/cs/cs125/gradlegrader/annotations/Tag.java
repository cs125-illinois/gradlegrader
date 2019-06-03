package edu.illinois.cs.cs125.gradlegrader.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applied to a @Graded JUnit test to add a metadata entry.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Tags.class)
public @interface Tag {

    /**
     * Gets the name of the tag.
     * @return the tag name
     */
    String name();

    /**
     * Gets the value of the tag (optional).
     * @return the value
     */
    String value() default "";

}
