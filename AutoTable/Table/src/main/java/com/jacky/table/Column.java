package com.jacky.table;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据对象的成员变量映射到数据库中的字段
 * @author lixinquan
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

	/**
	 * 表字段名称
	 * @return
	 */
	String value();
	/**
	 * 是否作为表主键
	 * @return
	 */
	boolean isPrimary() default false;
	/**
	 * 该成员的取值方式。不为空，则取对应函数的结果；为空，则取对应字段的值
	 * @return
	 */
	String get() default "";
	/**
	 * 该成员的设值方式。不为空，则用对应函数来设置；为空，则设置对应成员的值
	 * @return
	 */
	String set() default "";
	/**
	 * 该字段的类型。为空，则为该字段的类型
	 * @return
	 */
	DBType type() default DBType.NONE;

	/**
	 * 默认值
	 * @return
	 */
	String defValue() default "";
}
