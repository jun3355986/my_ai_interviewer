package com.aiinterviewer.admin.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAudit {

    String module();

    String operation();

    String targetType() default "";

    String targetId() default "";

    String targetIdParam() default "";

    boolean targetIdFromResult() default false;
}
