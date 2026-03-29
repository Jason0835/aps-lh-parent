package com.zlt.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ImportExcelValidated {
    boolean required() default false;

    boolean digits() default false;

    boolean number() default false;

    boolean date() default false;

    boolean isCode() default false;

    long min() default Long.MIN_VALUE;

    long max() default Long.MAX_VALUE;

    int maxLength() default Integer.MAX_VALUE;
}
