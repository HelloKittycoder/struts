package com.opensymphony.xwork2.util;

/**
 * Created by shucheng on 2020/1/11 22:15
 */
public class User {

    private int id;
    private String name;
    private int age;
    private Group group = new Group();

    public static int testStaticValue = 1;

    public User() {
    }

    public User(int id, String name, int age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

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

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public static int getTestStaticValue() {
        return testStaticValue;
    }

    public void print(int type) {
        if (type == 1) {
            System.out.println("id为" + id);
        } else if (type == 2) {
            System.out.println("name为" + name);
        }
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", age=" + age +
                '}';
    }

    public static class Group {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
