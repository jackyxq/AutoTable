package com.jacky.library.db;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据对象的类映射到数据库中的表
 * @author lixinquan
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {
	/**
	 * 表名
	 * @return
	 */
	String value();
	/**
	 * 表主键的值是否自动增加
	 * @return
	 */
	boolean autoId() default true;
}
