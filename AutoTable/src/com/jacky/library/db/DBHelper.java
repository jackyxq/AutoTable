package com.jacky.library.db;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.jacky.project.autotable.BaseApplication;
import com.jacky.project.autotable.Logger;

/**
 * 数据库操作类
 * @author lixinquan
 *
 */
public final class DBHelper {

	public static void dropTables(Class<?>... classes) {
		dropTables(BaseApplication.getDefaultSqliteDatabase(), classes);
	}
	
	public static void dropTables(SQLiteDatabase db, Class<?>... classes) {
		if(db == null) return;
		if(classes == null || classes.length == 0) {
			throw new DatabaseException("No Table to drop.");
		}
		for(Class<?> clazz : classes) {
			Table table = getTable(clazz);
			db.execSQL("DROP TABLE IF EXISTS " + table.value());
		}
	}
	
	public static void dropTablesByName(String... tables) {
		dropTablesByName(BaseApplication.getDefaultSqliteDatabase(), tables);
	}
	
	public static void dropTablesByName(SQLiteDatabase db, String... tables) {
		if(db == null) return;
		
		if(tables == null || tables.length == 0) {
			throw new DatabaseException("No Table to drop.");
		}
		for(String table : tables) {
			db.execSQL("DROP TABLE IF EXISTS " + table);
		}
	}
	
	public static void createTables(Class<?>... classes) {
		createTables(BaseApplication.getDefaultSqliteDatabase(), classes);
	}
	
	public static void createTables(SQLiteDatabase db, Class<?>... classes) {
		if(db == null) return;
		
		if(classes == null || classes.length == 0) {
			return;
//			throw new DatabaseException("No Table to create.");
		}
		
		db.beginTransaction();
		for(Class<?> clazz : classes) {
			Table table = getTable(clazz);
			
			Field[] fields = clazz.getDeclaredFields();
			if(fields.length <= 0) continue;
			
			String tableName = table.value();
			Map<String, String> map = queryTableColumnsInfo(db, tableName);
			if(map == null) {
				db.execSQL(generateCreateTableSql(table, fields));
			} else {
				//比对原始表与新表之间共同的字段
				StringBuilder sb = new StringBuilder();
				for (Field field : fields) {
					Column column = field.getAnnotation(Column.class);
					if(column != null) {
						String type = getDBType(column, field.getType());
						String value = map.get(column.value());
						
						if(type.equals(value)) {
							sb.append(',').append(column.value());
						}
					}
				}//end for
				
				if(sb.length() == 0) {
					db.execSQL(generateCreateTableSql(table, fields));
				} else {
					String t = sb.deleteCharAt(0).toString();
					//通过创建临时表的方式来实现对表的字段的修改
					db.execSQL("ALTER TABLE " + tableName + " RENAME TO TEMP");
					db.execSQL(generateCreateTableSql(table, fields));
					db.execSQL("INSERT INTO "+ tableName + "(" + t + ") SELECT " + t + " FROM TEMP");
					db.execSQL("DROP TABLE TEMP");
				}
			}
		}
		db.setTransactionSuccessful();
		db.endTransaction();
	}
	
	private static final Map<String, String> queryTableColumnsInfo(SQLiteDatabase db, String table) {
		Cursor cursor = null;
		try{
			cursor = db.rawQuery("PRAGMA TABLE_INFO(" + table + ")", null);
			if(cursor.getCount() > 0) {
				Map<String, String> map = new HashMap<String, String>();
				while(cursor.moveToNext()) {
					int index = cursor.getColumnIndex("name");
					String key = cursor.getString(index);
					index = cursor.getColumnIndex("type");
					map.put(key, cursor.getString(index));
				}
				return map;
			}
			return null;
		} finally {
			if(cursor != null) {
				cursor.close();
			}
		}
	}
	private static final String generateCreateTableSql(Table table, Field[] fields) {
		StringBuilder sb = new StringBuilder();
		sb.append("CREATE TABLE IF NOT EXISTS '").append(table.value()).append("'(");
		
		boolean hasId = false;
		for (Field field : fields) {
			Column column = field.getAnnotation(Column.class);
			if(column != null) {
				String type = getDBType(column, field.getType());
				if(column.isPrimary()) {
					if(hasId == true) {
						throw new DatabaseException("Primary key had in the table,set isPrimary is false.");
					} else {
						hasId = true;
						sb.append(column.value()).append(' ').append(type);
						if(table.autoId()) {
							sb.append(" PRIMARY KEY AUTOINCREMENT,");
						} else {
							sb.append(" PRIMARY KEY,");
						}
					}
				} else {
					sb.append(column.value()).append(' ').append(type).append(',');
				}
			}
		}//end for fields
		
		if(hasId == false && table.autoId()) {
			throw new DatabaseException("No primary key in the Table " + table.value());
		}
		
		sb.deleteCharAt(sb.length() - 1); //删除最后一个 逗号，如果没有逗号，表示没有字段，则会崩溃
		sb.append(")");
		return sb.toString();
	}
	
	/**
	 * 将数据插入数据库。如果表的主键设为自动增加，则数据的主键值会更改
	 * @param list
	 */
	public static <T> void insert(T... list) {
		insert(BaseApplication.getDefaultSqliteDatabase(), list);
	}
	
	/**
	 * 将数据插入数据库。如果表的主键设为自动增加，则数据的主键值会更改
	 * @param db
	 * @param list
	 */
	public static <T> void insert(SQLiteDatabase db, T... list) {
		if(db == null || list == null || list.length <= 0 || list[0] == null) return;
		
		Class<?> clazz = list[0].getClass();
		Table table = getTable(clazz);
		Field[] fields = clazz.getDeclaredFields();
		db.beginTransaction();
		for(T t : list) {
			if(t == null) continue;
			checkClass(t, clazz);
			Field idField = null;
			ContentValues values = new ContentValues();
			for(Field field : fields) {
				Column column = field.getAnnotation(Column.class);
				if(column == null) continue;
				if(table.autoId() && column.isPrimary()) {
					idField = field;
					continue;//自动生成主键，则不添加主键信息
				}
				
				String tmp = getFieldValue(clazz, column, field, t);
				if(tmp != null) {
					values.put(column.value(), tmp);
				}
			}
			long id = db.insert(table.value(), null, values);
			if(idField != null && id != -1) {
				setObjectValue(idField, t, id);//更新 主键ID
			}
		}
		db.setTransactionSuccessful();
		db.endTransaction();
	}
	
	public static <T> void insertOrUpdate(T... list) {
		insertOrUpdate(BaseApplication.getDefaultSqliteDatabase(), list);
	}
	
	public static <T> void insertOrUpdate(SQLiteDatabase db, T... list) {
		if(db == null || list == null || list.length <= 0 || list[0] == null) return;
		
		Class<?> clazz = list[0].getClass();
		Table table = getTable(clazz);
		Field[] fields = clazz.getDeclaredFields();
		
		String whereClause = null;
		for(Field field : fields) {
			Column column = field.getAnnotation(Column.class);
			if(column != null && column.isPrimary()) {
				whereClause = column.value() + "=?";
				break;
			}
		}
		
		if(whereClause == null) {
			throw new DatabaseException("This table no primary key.");
		}
		
		String id = null;
		db.beginTransaction();
		for(T t : list) {
			if(t == null) continue;
			checkClass(t, clazz);
			Field idField = null;
			ContentValues values = new ContentValues();
			for(Field field : fields) {
				Column column = field.getAnnotation(Column.class);
				if(column == null) continue;
				
				if(column.isPrimary()) {
					id = getFieldValue(clazz, column, field, t);
					
					if(table.autoId()) {
						idField = field;
						continue;//自动生成主键，则不添加主键信息
					}
				}
				
				String tmp = getFieldValue(clazz, column, field, t);
				if(tmp != null) {
					values.put(column.value(), tmp);
				}
			}
			
			int i = db.update(table.value(), values, whereClause, new String[]{id});
			if(i == 0) { //没有数据，则insert
				long newid = db.insert(table.value(), null, values);
				if(idField != null && newid != -1) {
					setObjectValue(idField, t, newid);//更新 主键ID
				}
			}
		}
		db.setTransactionSuccessful();
		db.endTransaction();
	}
	
	/**
	 * 根据 主键id 来更新数据内容
	 * @param list
	 * @throws DatabaseException 如果该表结构没有设置主键，则会 throw 异常
	 */
	public static <T> void update(T... list) throws DatabaseException {
		update(BaseApplication.getDefaultSqliteDatabase(), list);
	}
	
	/**
	 * 根据 主键id 来更新数据内容
	 * @param db
	 * @param list
	 * @throws DatabaseException 如果该表结构没有设置主键，则会 throw 异常
	 */
	public static <T> void update(SQLiteDatabase db, T... list) {
		if(db == null || list == null || list.length == 0) return;
		
		Class<?> clazz = list[0].getClass();
		Table table = getTable(clazz);
		Field[] fields = clazz.getDeclaredFields();
		
		String whereClause = null;
		for(Field field : fields) {
			Column column = field.getAnnotation(Column.class);
			if(column != null && column.isPrimary()) {
				whereClause = column.value() + "=?";
				break;
			}
		}
		
		if(whereClause == null) {
			throw new DatabaseException("This table no primary key.");
		}
		
		db.beginTransaction();
		String id = null,tmp = null;
		for(T t : list) {
			if(t == null) continue;
			checkClass(t, clazz);
			ContentValues values = new ContentValues();
			for(Field field : clazz.getDeclaredFields()) {
				Column column = field.getAnnotation(Column.class);
				if(column == null) continue;
				
				tmp = getFieldValue(clazz, column, field, t);
				if(column.isPrimary()) {
					id = tmp;
				} else if(tmp != null) {
					values.put(column.value(), tmp);
				}
			}
			db.update(table.value(), values, whereClause, new String[]{id});
		}
		db.setTransactionSuccessful();
		db.endTransaction();
	}
	/**
	 * 根据Where条件来更新数据
	 * @param t
	 * @param ignoreKey 是否忽略主键更新。true 则保留主键ID；false 则替换主键ID，可能会引发主键冲突异常
	 * @param whereClause
	 * @param whereArgs
	 */
	public static <T> void updateByWhere(T t,boolean ignoreKey,String whereClause, String[] whereArgs) {
		updateByWhere(BaseApplication.getDefaultSqliteDatabase(), t, ignoreKey, whereClause, whereArgs);
	}
	
	/**
	 * 根据Where条件来更新数据
	 * @param db
	 * @param t
	 * @param ignoreKey 是否忽略主键更新。true 则保留主键ID；false 则替换主键ID，可能会引发主键冲突异常
	 * @param whereClause
	 * @param whereArgs
	 */
	public static <T> void updateByWhere(SQLiteDatabase db, T t,boolean ignoreKey, String whereClause, String[] whereArgs) {
		if(db == null) return;
		
		Class<?> clazz = t.getClass();
		Table table = getTable(clazz);
		ContentValues values = new ContentValues();
		for(Field field : clazz.getDeclaredFields()) {
			Column column = field.getAnnotation(Column.class);
			if(column == null) continue;
			if(ignoreKey && column.isPrimary()) continue;
			
			String tmp = getFieldValue(clazz, column, field, t);
			if(tmp != null) {
				values.put(column.value(), tmp);
			}
		}
		db.update(table.value(), values, whereClause, whereArgs);
	}
	
	public static <T> void updateByValues(Class<T> clazz,ContentValues values, String whereClause, String[] whereArgs) {
		updateByValues(BaseApplication.getDefaultSqliteDatabase(), clazz, values, whereClause, whereArgs);
	}
	
	public static <T> void updateByValues(SQLiteDatabase db, Class<T> clazz,ContentValues values, String whereClause, String[] whereArgs) {
		if(db == null) return;
		
		Table table = getTable(clazz);
		db.update(table.value(), values, whereClause, whereArgs);
	}
	
	/**
	 * 使用数据对象的字段作为删除条件
	 * @param list
	 */
	public static <T> void delete(T... list) {
		delete(BaseApplication.getDefaultSqliteDatabase(), list);
	}
	
	/**
	 * 使用数据对象的字段作为删除条件
	 * @param db
	 * @param list
	 */
	public static <T> void delete(SQLiteDatabase db, T... list) {
		if(db == null || list == null || list.length == 0) return;
		
		Class<?> clazz = list[0].getClass();
		Table table = getTable(clazz);
		Field[] fields = clazz.getDeclaredFields();
		
		db.beginTransaction();
		
		String whereClause = null;
		String[] whereArgs = null;
		for(T t : list) {
			if(t == null) continue;
			checkClass(t, clazz);
			
			if(whereClause == null) {
				int i = 0;
				StringBuilder clause = new StringBuilder();
				for(Field field : fields) {
					Column column = field.getAnnotation(Column.class);
					if(column != null) {
						clause.append(column.value()).append("=? and ");
						i++;
					}
				}
				clause.append("1=1");//以免多出一个 and 关键字
				whereClause = clause.toString();
				whereArgs = new String[i];
			}
			
			int i = 0;
			for(Field field : fields) {
				Column column = field.getAnnotation(Column.class);
				if(column != null) {
					whereArgs[i++] = getFieldValue(clazz, column, field, t);
				}
			}
			db.delete(table.value(), whereClause, whereArgs);
		}
		db.setTransactionSuccessful();
		db.endTransaction();
	}
	
	/**
	 * 根据Where语句来执行数据删除操作
	 * @param db
	 * @param clazz
	 * @param whereClause
	 * @param whereArgs
	 */
	public static void deleteByWhere(Class<?> clazz, String whereClause, String[] whereArgs) {
		deleteByWhere(BaseApplication.getDefaultSqliteDatabase(), clazz, whereClause, whereArgs);
	}
	
	/**
	 * 根据Where语句来执行数据删除操作
	 * @param db
	 * @param clazz
	 * @param whereClause
	 * @param whereArgs
	 */
	public static void deleteByWhere(SQLiteDatabase db, Class<?> clazz, String whereClause, String[] whereArgs) {
		if(db == null) return;
		
		Table table = getTable(clazz);
		db.delete(table.value(), whereClause, whereArgs);
	}
	/**
	 * 根据主键ID 执行数据删除操作
	 * @param clazz
	 * @param id
	 */
	public static void deleteByID(Class<?> clazz, String... id) {
		deleteByID(BaseApplication.getDefaultSqliteDatabase(), clazz, id);
	}
	/**
	 * 根据主键ID 执行数据删除操作
	 * @param db
	 * @param clazz
	 * @param id
	 */
	public static void deleteByID(SQLiteDatabase db, Class<?> clazz,String... id) {
		if(db == null) return;
		
		Table table = getTable(clazz);
		String whereClause = null;
		for(Field field : clazz.getDeclaredFields()) {
			Column column = field.getAnnotation(Column.class);
			if(column != null && column.isPrimary()) {
				whereClause = column.value() + "=?";
				break;
			}
		}
		if(whereClause == null) {
			throw new DatabaseException("This table no primary key.");
		}
		
		db.beginTransaction();
		for(String i : id) {
			db.delete(table.value(), whereClause, new String[]{i});
		}
		db.setTransactionSuccessful();
		db.endTransaction();
	}
	
	public static <T> List<T> query(Class<T> clazz) {
		return query(BaseApplication.getDefaultSqliteDatabase(), clazz);
	}
	
	public static <T> List<T> query(SQLiteDatabase db, Class<T> clazz) {
		return queryByWhere(db, clazz, null, null);
	}
	
	public static <T> T queryById(Class<T> clazz, String id) {
		return queryById(BaseApplication.getDefaultSqliteDatabase(), clazz, id);
	}
	
	public static <T> T queryById(SQLiteDatabase db, Class<T> clazz, String id) {
		Table table = getTable(clazz);
		String whereClause = null;
		for(Field field : clazz.getDeclaredFields()) {
			Column column = field.getAnnotation(Column.class);
			if(column != null && column.isPrimary()) {
				whereClause = column.value() + "=? limit 1"; //limit 1 表示只查询一条结果，只为提高性能
				break;
			}
		}
		if(whereClause == null) {
			throw new DatabaseException("This table no primary key.");
		}
		
		Cursor cursor = db.rawQuery("SELECT * FROM " + table.value() + " WHERE " + whereClause, new String[]{id});
		T t = null;
		while(cursor.moveToNext()) {
			t = buildObject(clazz, cursor, null);
			break;
		}
		cursor.close();
		return t;
	}
	
	public static <T> List<T> queryByWhere(Class<T> clazz, String whereClause, String[] whereArgs) {
		return queryByWhere(BaseApplication.getDefaultSqliteDatabase(), clazz, whereClause, whereArgs);
	}
	
	public static <T> List<T> queryByWhere(SQLiteDatabase db, Class<T> clazz, String whereClause, String[] whereArgs) {
		if (db == null) return null;
			
		Table table = getTable(clazz);
		
		Cursor cursor = TextUtils.isEmpty(whereClause) ? 
				db.rawQuery("SELECT * FROM " + table.value(), null) :
				db.rawQuery("SELECT * FROM " + table.value() + " WHERE " + whereClause, whereArgs);
		List<T> list = reflectObject(clazz, cursor);
		cursor.close();
		return list;
	}
	
	/**
	 * @param clazz
	 * @param whereClause
	 * @param whereArgs
	 * @return Cursor 需要执行 close() 操作
	 */
	public static <T> Cursor getQueryCursor(Class<T> clazz, String whereClause, String[] whereArgs) {
		return getQueryCursor(BaseApplication.getDefaultSqliteDatabase(), clazz, whereClause, whereArgs);
	}
	
	/**
	 * 
	 * @param db
	 * @param clazz
	 * @param whereClause
	 * @param whereArgs
	 * @return Cursor 需要执行 close() 操作
	 */
	public static <T> Cursor getQueryCursor(SQLiteDatabase db, Class<T> clazz, String whereClause, String[] whereArgs) {
		Table table = getTable(clazz);
		
		Cursor cursor = TextUtils.isEmpty(whereClause) ? 
				db.rawQuery("SELECT * FROM " + table.value(), null) :
				db.rawQuery("SELECT * FROM " + table.value() + " WHERE " + whereClause, whereArgs);
		return cursor;
	}
	
	public static boolean isEmpty(Class<?> clazz) {
		return isEmpty(BaseApplication.getDefaultSqliteDatabase(), clazz);
	}
	
	public static boolean isEmpty(SQLiteDatabase db, Class<?> clazz) {
		Table table = getTable(clazz);
		
		Cursor cursor = db.rawQuery("SELECT 1 FROM " + table.value() + " LIMIT 1", null);
		if(cursor == null) return true;
		int size = cursor.getCount();
		cursor.close();
		return size <= 0;
	}
	
	public static int getSize(Class<?> clazz) {
		return getSize(BaseApplication.getDefaultSqliteDatabase(), clazz);
	}
	
	public static int getSize(SQLiteDatabase db, Class<?> clazz) {
		Table table = getTable(clazz);
		
		Cursor cursor = db.rawQuery("SELECT COUNT(1) FROM " + table.value(), null);
		if(cursor == null) return 0;
		int i = cursor.getInt(0);
		cursor.close();
		return i;
	}
	
	/**
	 * 将查询结果映射为数据对象
	 * @param clazz
	 * @param cursor
	 * @return
	 */
	public static final <T> List<T> reflectObject(Class<T> clazz, Cursor cursor) {
		List<T> list = new ArrayList<T>();
		Field[] fields = clazz.getDeclaredFields();
		while(cursor.moveToNext()) {
			T t = buildObject(clazz, cursor, fields);
			if(t != null) {
				list.add(t);
			}
		}
		return list;
	}
	
	public static final <T> T buildObject(Class<T> clazz, Cursor cursor, Field[] fields) {
		T t;
		try {
			t = clazz.newInstance();
		} catch (Exception e) {
			Logger.e(e);
			return null;
		}
		if(fields == null) {
			fields = clazz.getDeclaredFields();
		}
		
		int index;
		for(Field field : fields) {
			Column column = field.getAnnotation(Column.class);
			if(column == null) continue;

			index = cursor.getColumnIndex(column.value());
			if(index == -1) continue;
			
			if(!TextUtils.isEmpty(column.set())) {
				try {
					String type = column.type();
					if("TEXT".equals(type)) {
						Method method = clazz.getMethod(column.set(), String.class);
						method.invoke(t, cursor.getString(index));
					} else if("INTEGER".equals(type)) {
						Method method = clazz.getMethod(column.set(), Integer.TYPE);
						method.invoke(t, cursor.getInt(index));
					} else if("DOUBLE".equals(type)) {
						Method method = clazz.getMethod(column.set(), Double.TYPE);
						method.invoke(t, cursor.getDouble(index));
					} else if("FLOAT".equals(type)) {
						Method method = clazz.getMethod(column.set(), Float.TYPE);
						method.invoke(t, cursor.getFloat(index));
					} else if("BOOLEAN".equals(type)) {
						Method method = clazz.getMethod(column.set(), Boolean.TYPE);
						String v = cursor.getString(index);
						method.invoke(t, "1".equals(v) ? true : Boolean.parseBoolean(v));
					} else if("CHAR(1)".equals(type)) {
						Method method = clazz.getMethod(column.set(), Character.TYPE);
						method.invoke(t, cursor.getString(index).charAt(0));
					} else if("BIGINT".equals(type)) {
						Method method = clazz.getMethod(column.set(), Long.TYPE);
						method.invoke(t, cursor.getLong(index));
					} else {
						throw new DatabaseException("This Field not set type.");
					}
				} catch (Exception e) {
					Logger.w(field);
					Logger.e(e);
				}
				continue;
			}
			
			field.setAccessible(true);
			Class<?> type = field.getType();
			try {
				if(type == Integer.TYPE) {
					field.setInt(t, cursor.getInt(index));
				}else if(type == Integer.class) {
					field.set(t, cursor.getInt(index));
				} else if(type == Long.TYPE) {
					field.setLong(t, cursor.getLong(index));
				} else if(type == Long.class) {
					field.set(t, cursor.getLong(index));
				} else if(type == String.class) {
					field.set(t, cursor.getString(index));
				} else if(type == Character.TYPE) {
					field.setChar(t, cursor.getString(index).charAt(0));
				} else if(type == Character.class) {
					field.set(t, cursor.getString(index).charAt(0));
				} else if(type == Double.TYPE) {
					field.setDouble(t, cursor.getDouble(index));
				} else if(type == Double.class) {
					field.set(t, cursor.getDouble(index));
				} else if(type == Boolean.TYPE || type == Boolean.class) {
					String v = cursor.getString(index);
					field.setBoolean(t, "1".equals(v) ? true : Boolean.parseBoolean(v));
				} else if(type == Float.TYPE) {
					field.setFloat(t, cursor.getFloat(index));
				} else if(type == Float.class) {
					field.set(t, cursor.getFloat(index));
				} else if(type == Short.TYPE) {
					field.setShort(t, cursor.getShort(index));
				} else if(type == Short.class) {
					field.set(t, cursor.getShort(index));
				} else if(type == Byte.TYPE) {
					field.setByte(t, (byte)cursor.getInt(index));
				} else if(type == Byte.class) {
					field.set(t, (byte)cursor.getInt(index));
				} else {
					//TODO... 其他类型
				}
			} catch (Exception e) {
				Logger.w(field);
				Logger.e(e);
			}
		}
		return t;
	}
	
	private static final String getDBType(Column column, Class<?> type) {
		if(TextUtils.isEmpty(column.type()) == false) {
			return column.type();
		}
		
		if(Integer.TYPE == type || Integer.class == type) return "INTEGER";
		if(Double.TYPE == type || Double.class == type) return "DOUBLE";
		if(Character.TYPE == type || Character.class == type) return "CHAR(1)";
		if(Long.TYPE == type || Long.class == type) return "BIGINT";
		if(Short.TYPE == type || Short.class == type) return "INTEGER";
		if(Float.TYPE == type || Float.class == type) return "FLOAT";
		if(Boolean.TYPE == type || Boolean.class == type) return "BOOLEAN";
		if(Byte.TYPE == type || Byte.class == type) return "INTEGER";
		if(CharSequence.class.isAssignableFrom(type)) return "TEXT";
		
		throw new DatabaseException(type + " no support to mapping.");
	}
	
	private static final <T> void setObjectValue(Field field, T t, Object value) {
		field.setAccessible(true);
		Class<?> type = field.getType();
		try {
			if(type == Integer.TYPE) {
				field.setInt(t, Integer.parseInt(value.toString()));
			} else if(type == Long.TYPE) {
				field.setLong(t, Long.parseLong(value.toString()));
			} else if(type == String.class) {
				field.set(t, value);
			} else if(type == Character.TYPE) {
				field.setChar(t, (Character) value);
			} else if(type == Double.TYPE) {
				field.setDouble(t, (Double) value);
			} else if(type == Boolean.TYPE) {
				field.setBoolean(t, (Boolean) value);
			} else if(type == Float.TYPE) {
				field.setFloat(t, (Float) value);
			} else if(type == Short.TYPE) {
				field.setShort(t, (Short) value);
			} else if(type == Byte.TYPE) {
				field.setByte(t, (Byte) value);
			} else {
				field.set(t, value);
			}
		} catch (Exception e) {
			Logger.w(field);
			Logger.e(e);
		}
	}
	
	private static final <T> String getFieldValue(Class<?> clazz, Column column, Field field,T t) {
		if(!TextUtils.isEmpty(column.get())) {
			try {
				Method method = clazz.getMethod(column.get());
				Object obj = method.invoke(t);
				if(obj == null) return null;
				return obj.toString();
			} catch (Exception e) {
				Logger.e(e);
				return null;
			}
		}

		field.setAccessible(true);
		try {
			Object obj = field.get(t);
			return obj == null ? "" : obj.toString();
		} catch (IllegalAccessException e1) {
			Logger.e(e1);
		} catch (IllegalArgumentException e1) {
			Logger.e(e1);
		}
		return null;
	}
	
	private static final Table getTable(Class<?> clazz) {
		Table table = clazz.getAnnotation(Table.class);
		if(table == null) {
			throw new DatabaseException("Class " + clazz + " not have Table Annotation!");
		}
		return table;
	}
	
	private static final <T> void checkClass(T t, Class<?> clazz) {
		if(!clazz.isInstance(t)) {
			throw new DatabaseException("Class - Type mismatch.");
		}
	}
}
