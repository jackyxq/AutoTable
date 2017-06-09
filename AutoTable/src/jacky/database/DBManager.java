package jacky.database;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jacky.project.autotable.Logger;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

/**
 * 数据库操作类，一个实例对象对应一个db文件
 */
public final class DBManager {

    private SQLiteDatabase mDatabase;
    private boolean isBeginTransaction;

    /**
     *
     * @param context
     * @param name 数据库文件名
     */
    public DBManager(Context context, String name) {
        this(context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null));
    }

    public DBManager(SQLiteDatabase db) {
        mDatabase = db;
    }

    /**
     * 释放数据库操作
     */
    public void close() {
        if(mDatabase != null) {
            mDatabase.close();
        }
        mDatabase = null;
    }

    /**
     * 根据类名创建数据库
     * @param classes
     */
    public void createTables(Context context, Class<?>... classes) {
        if(mDatabase == null || classes == null || classes.length == 0) {
            return;
        }

        SharedPreferences preferences =
                context.getSharedPreferences("jacky_db", Context.MODE_PRIVATE);
//                PreferenceManager.getDefaultSharedPreferences(context);

        File file = new File(mDatabase.getPath());
        String dbName = file.getName();

        beginTransaction();
        for(Class<?> clazz : classes) {
            Table table = getTable(clazz);

            Field[] fields = clazz.getDeclaredFields();
            if(fields.length <= 0) continue;

            String tableName = table.value();
            String createSql = generateCreateTableSql(table, fields);
            int sqlVersion = createSql.hashCode();
            String preferencesKey = "SQL:" + dbName + '_' + tableName;

            Map<String, String> map = queryTableColumnsInfo(mDatabase, tableName);
            if(map == null) {
                mDatabase.execSQL(createSql);
            } else {
                int version = preferences.getInt(preferencesKey, 0);
                //判断建表语句是否发生了变更
                if(version == sqlVersion) continue;

                //比对原始表与新表之间共同的字段
                StringBuilder sb = new StringBuilder();
                for (Field field : fields) {
                    Column column = field.getAnnotation(Column.class);
                    if(column != null) {
                        DBType type = getDBType(column, field.getType());
                        String value = map.get(column.value());

                        if(type.value().equals(value)) {
                            sb.append(',').append(column.value());
                        }
                    }
                }//end for

                if(sb.length() == 0) {
                    mDatabase.execSQL(createSql);
                } else {
                    String t = sb.deleteCharAt(0).toString();
                    //通过创建临时表的方式来实现对表的字段的修改
                    mDatabase.execSQL("ALTER TABLE " + tableName + " RENAME TO TEMP");
                    mDatabase.execSQL(createSql);
                    mDatabase.execSQL("REPLACE INTO "+ tableName + "(" + t + ") SELECT " + t + " FROM TEMP");
                    mDatabase.execSQL("DROP TABLE TEMP");
                }
            }
            //缓存建表语句的版本号
            preferences.edit().putInt(preferencesKey, sqlVersion).commit();
        }
        setTransactionSuccessful();
        endTransaction();
    }

    private final Map<String, String> queryTableColumnsInfo(SQLiteDatabase db, String table) {
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
    private final String generateCreateTableSql(Table table, Field[] fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS '").append(table.value()).append("'(");

        boolean hasId = false;
        for (Field field : fields) {
            Column column = field.getAnnotation(Column.class);
            if(column != null) {
                DBType type = getDBType(column, field.getType());
                if(column.isPrimary()) {
                    if(hasId == true) {
                        throw new DatabaseException("Primary key had in the table,set isPrimary is false.");
                    } else {
                        hasId = true;
                        sb.append(column.value()).append(' ').append(type.value());
                        if(table.autoId()) {
                            sb.append(" PRIMARY KEY AUTOINCREMENT,");
                        } else {
                            sb.append(" PRIMARY KEY,");
                        }
                    }
                } else {
                    sb.append(column.value()).append(' ').append(type)
                            .append(" DEFAULT '").append(column.defValue()).append("',");
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
    public <T> void insert(List<T> list) {
        insert(list.toArray());
    }
    /**
     * 将数据插入数据库。如果表的主键设为自动增加，则数据的主键值会更改
     * @param list
     */
    public <T> void insert(T... list) {
        if(mDatabase == null || list == null || list.length <= 0 || list[0] == null) return;

        Class<?> clazz = list[0].getClass();
        Table table = getTable(clazz);
        Field[] fields = clazz.getDeclaredFields();

        beginTransaction();
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
            long id = mDatabase.insert(table.value(), null, values);
            if(idField != null && id != -1) {
                setObjectValue(idField, t, id);//更新 主键ID
            }
        }
        setTransactionSuccessful();
        endTransaction();
    }

    public <T> void replaceInto(List<T> list) {
        replaceInto(list, null);
    }

    public <T> void replaceInto(T... list) {
        replaceInto(list, null);
    }

    /**
     * 如果主键ID已存在则更新数据，不存在主键ID则重新插入一条数据（插入的ID会变）
     * @param list
     * @param ignoreColumn 不需要更新数据的字段，保留原有数据库中的数据
     * @param <T>
     */
    public <T> void replaceInto(List<T> list, String[] ignoreColumn) {
        replaceInto(list.toArray(), ignoreColumn);
    }

    /**
     * 如果主键ID已存在则更新数据，不存在主键ID则重新插入一条数据（插入的ID会变）
     * @param list
     * @param ignoreColumn 不需要更新数据的字段，保留原有数据库中的数据
     * @param <T>
     */
    public <T> void replaceInto(T[] list, String[] ignoreColumn) {
        if(mDatabase == null || list == null || list.length <= 0 || list[0] == null) return;

        Class<?> clazz = list[0].getClass();
        Table table = getTable(clazz);
        Field[] fields = clazz.getDeclaredFields();
        String whereClause = getPrimaryColumn(clazz, true).value() + "=?";

        String id = null;
        beginTransaction();
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
                if(isIgnoreColumn(ignoreColumn, column)) continue;

                String tmp = getFieldValue(clazz, column, field, t);
                if(tmp != null) {
                    values.put(column.value(), tmp);
                }
            }

            int i = mDatabase.update(table.value(), values, whereClause, new String[]{id});
            if(i == 0) { //没有数据，则insert
                long newid = mDatabase.insert(table.value(), null, values);
                if(idField != null && newid != -1) {
                    setObjectValue(idField, t, newid);//更新 主键ID
                }
            }
        }
        setTransactionSuccessful();
        endTransaction();
    }

    public <T> void update(T... list) {
        update(list, null);
    }
    /**
     * 根据 主键id 来更新数据内容
     * @param list
     * @throws DatabaseException 如果该表结构没有设置主键，则会 throw 异常
     */
    public <T> void update(T[] list, String[] ignoreColumn) {
        if(mDatabase == null || list == null || list.length == 0) return;

        Class<?> clazz = list[0].getClass();
        String whereClause = getPrimaryColumn(clazz, true).value() + "=?";

        String id = null,tmp = null, tableName = getTableName(clazz);;
        beginTransaction();
        for(T t : list) {
            if(t == null) continue;
            checkClass(t, clazz);
            ContentValues values = new ContentValues();
            for(Field field : clazz.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if(column == null) continue;
                if(isIgnoreColumn(ignoreColumn, column)) continue;

                tmp = getFieldValue(clazz, column, field, t);
                if(column.isPrimary()) {
                    id = tmp;
                } else if(tmp != null) {
                    values.put(column.value(), tmp);
                }
            }
            mDatabase.update(tableName, values, whereClause, new String[]{id});
        }
        setTransactionSuccessful();
        endTransaction();
    }
    /**
     * 根据Where条件来更新数据
     * @param t
     * @param ignoreColumn 不需要更新数据的字段<strong>如果有设置主键，则将主键设为忽悠字段，否则有很大概率出现主键冲突异常</strong>
     * @param whereClause
     * @param whereArgs
     */
    public <T> void updateByWhere(T t,String[] ignoreColumn,String whereClause, String[] whereArgs) {
        if(mDatabase == null) return;
        ContentValues values = getContentValues(t, ignoreColumn);
        mDatabase.update(getTableName(t.getClass()), values, whereClause, whereArgs);
    }

    public <T> void updateByValues(Class<T> clazz,ContentValues values, String whereClause, String[] whereArgs) {
        if(mDatabase == null) return;
        int i = mDatabase.update(getTableName(clazz), values, whereClause, whereArgs);
        Logger.d("update result:",i);
    }

    public <T> void replaceIntoValues(Class<T> clazz,ContentValues values, String whereClause, String[] whereArgs) {
        if(mDatabase == null) return;
        String table = getTableName(clazz);
        int i = mDatabase.update(table, values, whereClause, whereArgs);
        if(i == 0) { //没有数据，则insert
            mDatabase.insert(table, null, values);
        }
    }

    /**
     * 将类转换成 ContentValues 类型
     * @param t
     * @param <T>
     * @return
     */
    public static <T> ContentValues getContentValues(T t,String[] ignoreColumn) {
        Class<?> clazz = t.getClass();
        ContentValues values = new ContentValues();
        for(Field field : clazz.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if(column == null) continue;
            if(isIgnoreColumn(ignoreColumn, column)) continue;

            String tmp = getFieldValue(clazz, column, field, t);
            if(tmp != null) {
                values.put(column.value(), tmp);
            }
        }
        return values;
    }

    /**
     * 使用数据对象的字段作为删除条件
     * @param list
     */
    public <T> void delete(T... list) {
        if(mDatabase == null || list == null || list.length == 0) return;

        Class<?> clazz = list[0].getClass();
        Table table = getTable(clazz);
        Field[] fields = clazz.getDeclaredFields();

        beginTransaction();

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
            mDatabase.delete(table.value(), whereClause, whereArgs);
        }
        setTransactionSuccessful();
        endTransaction();
    }

    /**
     * 删除表中的全部数据
     * @param clazz
     */
    public void deleteAll(Class<?> clazz) {
    	deleteByWhere(clazz, null, null);
    }
    
    /**
     * 根据Where语句来执行数据删除操作
     * @param clazz
     * @param whereClause
     * @param whereArgs
     */
    public void deleteByWhere(Class<?> clazz, String whereClause, String[] whereArgs) {
        if(mDatabase == null) return;
        mDatabase.delete(getTableName(clazz), whereClause, whereArgs);
    }
    /**
     * 根据主键ID 执行数据删除操作
     * @param clazz
     * @param id
     */
    public void deleteByID(Class<?> clazz, String... id) {
        if(mDatabase == null) return;
        String whereClause = getPrimaryColumn(clazz, true).value() + "=?";
        String table = getTableName(clazz);
        beginTransaction();
        for(String i : id) {
            mDatabase.delete(table, whereClause, new String[]{i});
        }
        setTransactionSuccessful();
        endTransaction();
    }

    public <T> List<T> query(Class<T> clazz) {
        return queryByWhere(clazz, null, null);
    }

    public <T> T queryById(Class<T> clazz, String id) {
        if(mDatabase == null) return null;
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

        Cursor cursor = mDatabase.rawQuery(
                "SELECT * FROM " + getTableName(clazz) + " WHERE " + whereClause, new String[]{id});
        T t = null;
        while(cursor.moveToNext()) {
            t = buildObject(clazz, cursor, null);
            break;
        }
        cursor.close();
        return t;
    }

    public <T> List<T> queryByWhere(Class<T> clazz, String whereClause, String[] whereArgs) {
        if (mDatabase == null) return new ArrayList<T>();
        String table = getTableName(clazz);
        Cursor cursor = TextUtils.isEmpty(whereClause) ?
                mDatabase.rawQuery("SELECT * FROM " + table, null) :
                mDatabase.rawQuery("SELECT * FROM " + table + " WHERE " + whereClause, whereArgs);
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
    public <T> Cursor getQueryCursor(Class<T> clazz, String whereClause, String[] whereArgs) {
        if (mDatabase == null) return null;
        String table = getTableName(clazz);
        Cursor cursor = TextUtils.isEmpty(whereClause) ?
                mDatabase.rawQuery("SELECT * FROM " + table, null) :
                mDatabase.rawQuery("SELECT * FROM " + table + " WHERE " + whereClause, whereArgs);
        return cursor;
    }

    /**
     * 查询该表是否有数据
     * @param clazz
     * @return
     */
    public boolean isEmpty(Class<?> clazz) {
        Cursor cursor = mDatabase.rawQuery("SELECT 1 FROM " + getTableName(clazz) + " LIMIT 1", null);
        if(cursor == null) return true;
        int size = cursor.getCount();
        cursor.close();
        return size <= 0;
    }

    /**
     * 获取该表的总记录数
     * @param clazz
     * @return
     */
    public int getCount(Class<?> clazz) {
        Cursor cursor = mDatabase.rawQuery("SELECT COUNT(1) FROM " + getTableName(clazz), null);
        if(cursor == null) return 0;
        int i = 0;
        if(0 != cursor.getCount()) {
            cursor.moveToFirst();
            i = cursor.getInt(0);
        }
        cursor.close();
        return i;
    }

    public void beginTransaction() {
        if(isBeginTransaction) return;
        mDatabase.beginTransaction();
        isBeginTransaction = true;
    }

    public void endTransaction() {
        if(isBeginTransaction) {
            mDatabase.endTransaction();
        }
        isBeginTransaction = false;
    }

    public void setTransactionSuccessful() {
        if(isBeginTransaction) {
            mDatabase.setTransactionSuccessful();
        }
    }

    public void execSQL(String sql) {
//        Logger.e(sql);
        mDatabase.execSQL(sql);
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
                    switch (column.type()) {
                        case TEXT :
                            Method method = clazz.getMethod(column.set(), String.class);
                            method.invoke(t, cursor.getString(index));
                            break;
                        case INT:
                            method = clazz.getMethod(column.set(), Integer.TYPE);
                            method.invoke(t, cursor.getInt(index));
                            break;
                        case DOUBLE:
                            method = clazz.getMethod(column.set(), Double.TYPE);
                            method.invoke(t, cursor.getDouble(index));
                            break;
                        case FLOAT:
                            method = clazz.getMethod(column.set(), Float.TYPE);
                            method.invoke(t, cursor.getFloat(index));
                            break;
                        case BOOLEAN:
                            method = clazz.getMethod(column.set(), Boolean.TYPE);
                            String v = cursor.getString(index);
                            method.invoke(t, "1".equals(v) ? true : Boolean.parseBoolean(v));
                            break;
                        case CHAR:
                            method = clazz.getMethod(column.set(), Character.TYPE);
                            method.invoke(t, cursor.getString(index).charAt(0));
                            break;
                        case LONG:
                            method = clazz.getMethod(column.set(), Long.TYPE);
                            method.invoke(t, cursor.getLong(index));
                            break;
                        default:
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

    private static final DBType getDBType(Column column, Class<?> type) {
        if(column.type() != DBType.NONE) {
            return column.type();
        }

        if(Integer.TYPE == type || Integer.class == type) return DBType.INT;
        if(Double.TYPE == type || Double.class == type) return DBType.DOUBLE;
        if(Character.TYPE == type || Character.class == type) return DBType.CHAR;
        if(Long.TYPE == type || Long.class == type) return DBType.LONG;
        if(Short.TYPE == type || Short.class == type) return DBType.INT;
        if(Float.TYPE == type || Float.class == type) return DBType.FLOAT;
        if(Boolean.TYPE == type || Boolean.class == type) return DBType.BOOLEAN;
        if(Byte.TYPE == type || Byte.class == type) return DBType.INT;
        if(CharSequence.class.isAssignableFrom(type)) return DBType.TEXT;

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

    public static final String getTableName(Class<?> clazz) {
        return getTable(clazz).value();
    }

    private static final boolean isIgnoreColumn(String[] columns, Column column) {
        if(columns == null) return false;
        for(String s : columns) {
            if(s.equals(column.value())) return true;
        }
        return false;
    }

    /**
     *
     * @param clazz
     * @param throwError 没有找到主键信息，是否要抛出异常
     * @return
     */
    private Column getPrimaryColumn(Class<?> clazz, boolean throwError) {
        for(Field field : clazz.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if(column != null && column.isPrimary()) {
                return column;
            }
        }
        if(throwError) {
            throw new DatabaseException("This table no primary key.");
        }
        return null;
    }
}
