package edu.illinois.cs.cs125.gradlegrader.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applied to a JUnit test to indicate that it counts in grading.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Graded {

    /**
     * Gets how many points the test is worth.
     * @return the value of the test case
     */
    int points();

    /**
     * Gets an additional description of the test case.
     * @return a human-readable description of the test
     */
    String friendlyName() default "";

}
