package com.jacky.project.autotable;

import com.jacky.library.db.Column;
import com.jacky.library.db.Table;

/**
 * Created by Administrator on 2014-11-20.
 */
@Table("student")
public class Student {
    @Column(value = "id",isPrimary = true)
    private int id;
    @Column("name")
    private String name;
    @Column("age")
    private int age;
    @Column("sex")
    private boolean sex;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public boolean isSex() {
        return sex;
    }

    public void setSex(boolean sex) {
        this.sex = sex;
    }
}
