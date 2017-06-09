package com.jacky.project.autotable;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import jacky.database.DBManager;

public class MyActivity extends Activity implements View.OnClickListener {
	
	private DBManager mDBManager;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mDBManager = new DBManager(this, "table.db");
         
        findViewById(R.id.create).setOnClickListener(this);
        findViewById(R.id.drop).setOnClickListener(this);
        findViewById(R.id.insert).setOnClickListener(this);
        findViewById(R.id.modify).setOnClickListener(this);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.create : {
            	mDBManager.createTables(this, Student.class);
            } break;
            case R.id.drop : {
//            	mDBManager.dropTables(Student.class);
            } break;
            case R.id.insert : {
                Student student1 = new Student();
                mDBManager.insert(student1);

                Student student2 = new Student();
                Student student3 = new Student();
                mDBManager.insert(student2,student3);

            } break;
            case R.id.modify : {
                Student student1 = new Student();
                mDBManager.update(student1);

                Student student2 = new Student();
                Student student3 = new Student();
                mDBManager.update(student2,student3);
            } break;
            case R.id.delete : {
            	mDBManager.deleteAll(Student.class);
            }break;
        }
    }
}
