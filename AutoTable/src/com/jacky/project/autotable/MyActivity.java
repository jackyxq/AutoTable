package com.jacky.project.autotable;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import com.jacky.library.db.DBHelper;

public class MyActivity extends Activity implements View.OnClickListener{
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        findViewById(R.id.create).setOnClickListener(this);
        findViewById(R.id.drop).setOnClickListener(this);
        findViewById(R.id.insert).setOnClickListener(this);
        findViewById(R.id.modify).setOnClickListener(this);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.create : {
                DBHelper.createTables(Student.class);
            } break;
            case R.id.drop : {
                DBHelper.dropTables(Student.class);
            } break;
            case R.id.insert : {
                Student student1 = new Student();
                DBHelper.insert(student1);

                Student student2 = new Student();
                Student student3 = new Student();
                DBHelper.insert(student2,student3);

            } break;
            case R.id.modify : {
                Student student1 = new Student();
                DBHelper.update(student1);

                Student student2 = new Student();
                Student student3 = new Student();
                DBHelper.update(student2,student3);
            } break;
            case R.id.delete : {
                DBHelper.delete(Student.class);
            }break;
        }
    }
}
