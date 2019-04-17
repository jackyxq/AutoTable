package com.jacky.autotable;

import com.jacky.table.Column;
import com.jacky.table.Table;

/**
 * Created by Administrator on 2014-11-20.
 */
@Table(value = "student", autoId = true)
public class Student {
    @Column(value = "id",isPrimary = true)
    private int id;
    @Column("name")
    private String name;
    @Column("age")
    private int age;
    @Column("sex")
    private boolean sex;
    @Column("color")
    public Color color = Color.RED;

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

    @Override
    public String toString() {
        return "Student{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", age=" + age +
                ", sex=" + sex +
                ", color=" + color +
                '}';
    }
}
