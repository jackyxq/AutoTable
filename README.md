AutoTable
=========
### example

	implementation 'com.jacky.table:table:1.0.1'


This is a simple android sqlite Object Relational Mapping.

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

Use Class DBManager to handle database operator.
You don't need to care about the syntax of SQL and the database is updated, it will help you to achieve the data field update.

### example

	DBManager mDBManager = new DBManager(this, "table.db");
    mDBManager.createTables(getContext(), Student.class);
 
    mDBManager.deleteAll(Student.class);
 
    Student student1 = new Student();
    mDBManager.insert(student1);
 
    List<Student>  list = mDBManager.query(Student.class);
 
