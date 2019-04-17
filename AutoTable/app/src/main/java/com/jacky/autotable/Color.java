package com.jacky.autotable;

/**
 * Created by lixinquan on 2019/4/17.
 */
public enum Color {
        RED(113001, "红色"),
        BLUE(113002, "蓝色");
        private int code;
        private String name;

    Color(int code,String name){
            this.code= code;
            this.name= name;
        }

        public String toName() {
            return this.name;
        }

        public int toCode() {
            return this.code;
        }
}
