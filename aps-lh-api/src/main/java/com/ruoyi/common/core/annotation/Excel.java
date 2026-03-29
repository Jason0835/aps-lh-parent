package com.ruoyi.common.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Excel {
    String name() default "";

    String dictType() default "";

    int width() default 16;

    String dateFormat() default "";

    ColumnType cellType() default ColumnType.STRING;

    int sort() default Integer.MAX_VALUE;

    enum ColumnType {
        NUMERIC, STRING, IMAGE
    }
}
