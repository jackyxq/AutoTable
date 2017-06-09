package jacky.database;

/**
 * Created by Administrator on 2016/10/30.
 */
public enum DBType implements Unproguard {

    NONE(""),
    TEXT("TEXT"),
    INT("INTEGER"),
    LONG("BIGINT"),
    BOOLEAN("BOOLEAN"),
    CHAR("CHAR(1)"),
    FLOAT("FLOAT"),
    DOUBLE("DOUBLE");

    private String value;

    DBType(String v) {
        this.value = v;
    }

    public String value() {
        return  value;
    }

}
