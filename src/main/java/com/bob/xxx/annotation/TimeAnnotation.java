package com.bob.xxx.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface TimeAnnotation {

  /**
   * 打印入参
   */
  boolean printInput() default false;

  /**
   * 打印入参
   */
  boolean printOutput() default false;
}