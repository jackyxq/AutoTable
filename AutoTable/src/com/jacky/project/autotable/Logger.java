package com.jacky.project.autotable;

import java.lang.reflect.Array;

import android.util.Log;

public final class Logger {

	public static final int VERBOSE = Log.VERBOSE;
	public static final int DEBUG = Log.DEBUG;
	public static final int INFO = Log.INFO;
	public static final int WARN = Log.WARN;
	public static final int ERROR = Log.ERROR;
	public static final int ASSERT = Log.ASSERT;
	
	private static final String ClassName = Logger.class.getName();
	
	/* 允许输出的日志级别 */
	private static int LEVEL = VERBOSE;
	/* 日志输出TAG */
	private static String TAG = "Logger";
	
	/**
	 * 设置日志输出级别,低于该{@code level}的日志信息将不会显示在 logcat中
	 * @param level 显示级别从低到高依次为
	 * <ol>
	 * <li>{@link #VERBOSE}</li>
	 * <li>{@link #DEBUG}</li>
	 * <li>{@link #INFO}</li>
	 * <li>{@link #WARN}</li>
	 * <li>{@link #ERROR}</li>
	 * <li>{@link #ASSERT}</li>
	 * </ol>
	 * 默认日志级别为最低的{@link #VERBOSE}
	 */
	public static void setLogLevel(int level){
		LEVEL = level;
	}
	/**
	 * 设置日志输出TAG
	 * @param tag
	 */
	public static void setTag(String tag) {
		TAG = tag;
	}
	
	public static <T> void v(T msg){
		printLog(VERBOSE,msg);
	}
	
	public static <T> void d(T msg){
		printLog(DEBUG,msg);
	}
	
	public static <T> void i(T msg){
		printLog(INFO,msg);
	}
	
	public static <T> void w(T msg){
		printLog(WARN,msg);
	}
	
	public static <T> void e(T msg){
		printLog(ERROR,msg);
	}
	
	public static void v(Object... msg){
		printLog(VERBOSE,msg);
	}
	
	public static void d(Object... msg){
		printLog(DEBUG,msg);
	}
	
	public static void i(Object... msg){
		printLog(INFO,msg);
	}
	
	public static void w(Object... msg){
		printLog(WARN,msg);
	}
	
	public static void e(Object... msg){
		printLog(ERROR,msg);
	}
	
	private static void printLog(int p,Object... msg) {
		if(isLoggable(p) == false) return;
		if(msg == null) return;
		
		StringBuilder sb = new StringBuilder();
		
		StackTraceElement[] statck = Thread.currentThread().getStackTrace();
		if(statck != null) {
			boolean isOk = false;
			for(StackTraceElement element : statck) {
				if(ClassName.equals(element.getClassName())) {
					isOk = true;
				} else if(isOk) {
					sb.append('[').append(element.getClassName()).append(':').append(':')
						.append(element.getMethodName()).append('(')
						.append(element.getLineNumber()).append(')').append(']');
					break;
				}
			}
		}
		
		for(Object obj : msg) {
			if(obj == null) continue;

			sb.append(" ");
			if(obj instanceof Throwable) {
				sb.append(Log.getStackTraceString((Throwable) obj));
			} else if(obj.getClass().isArray()) {
				int size = Array.getLength(obj);
				sb.append('[');
				for(int i = 0;i < size;i++ ) {
					sb.append(Array.get(obj, i)).append(',');
				}
				sb.deleteCharAt(sb.length());
				sb.append(']');
			} else {
				sb.append(obj);
			}
		}
		Log.println(p, TAG, sb.toString());
	}
	
	private static boolean isLoggable(int level) {
		return level >= LEVEL;
	}
}
