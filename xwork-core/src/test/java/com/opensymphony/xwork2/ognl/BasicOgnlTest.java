package com.opensymphony.xwork2.ognl;

import com.opensymphony.xwork2.util.User;
import junit.framework.TestCase;
import ognl.Ognl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by shucheng on 2020/1/11 22:12
 */
public class BasicOgnlTest extends TestCase {

    public void testGetValue() throws Exception {
        // 创建Root对象
        User user = new User();
        user.setId(1);
        user.setName("downpour");

        // 创建上下文环境
        Map context = new HashMap();
        context.put("introduction", "My name is ");

        // 测试从Root对象中进行表达式计算并并获取结果
        Object name = Ognl.getValue(Ognl.parseExpression("name"), user);
        assertEquals("downpour", name);

        // 测试从上下文环境中进行表达式计算并获取结果
        Object contextValue = Ognl.getValue(Ognl.parseExpression("#introduction"), context, user);
        assertEquals("My name is ", contextValue);

        // 测试同时从将Root对象和上下文环境作为表达式的一部分进行计算
        Object hello = Ognl.getValue(Ognl.parseExpression("#introduction + name"), context, user);
        assertEquals("My name is downpour", hello);
    }

    public void testSetValue() throws Exception {
        // 创建Root对象
        User user = new User();
        user.setId(1);
        user.setName("downpour");

        // 对Root对象进行写值操作
        Ognl.setValue("group.name", user, "dev");
        Ognl.setValue("age", user, "18");

        assertEquals("dev", user.getGroup().getName());
    }

    // 对象成员访问
    public void testAccessClassMember() throws Exception {
        // 创建Root对象
        User user = new User();
        user.setId(1);
        user.setName("downpour");
        user.getGroup().setName("dev");

        // 访问静态变量（传User.class或user都可以）
        Object testStaticValue = Ognl.getValue("@com.opensymphony.xwork2.util.User@testStaticValue", user);
        assertEquals(testStaticValue, 1);
        // 访问静态方法（传User.class或user都可以）
        testStaticValue = Ognl.getValue("@com.opensymphony.xwork2.util.User@getTestStaticValue()", user);
        assertEquals(testStaticValue, 1);

        // 方法调用
        assertEquals(Ognl.getValue("group.getName()", user), "dev");
        Map root = new HashMap();
        root.put("type", 2);
        Ognl.getValue("print(#type)", root, user);
    }

    // 简单计算
    public void testSimpleCalculation() throws Exception {
        Map root = new HashMap();
        root.put("foo", 2);
        assertEquals(Ognl.getValue("2+4", root), 6);
        assertEquals(Ognl.getValue("5-3", root), 2);
        assertEquals(Ognl.getValue("'hello ' + 'world'", root), "hello world");
        assertEquals(Ognl.getValue("foo", root), 2);
        assertEquals(Ognl.getValue("foo==2", root), true);
    }

    // 对数组和容器的访问
    public void testAccessCollection() throws Exception {
        Map root = new HashMap();
        List<String> list = new ArrayList<String>(){
            {
                add("aaa");
            }
        };
        root.put("list", list);
        int[] arr = {11, 22, 33};
        root.put("arr", arr);
        Map<String, String> namesMap = new HashMap<String, String>(){
            {
                put("zs", "张三");
                put("ls", "李四");
            }
        };
        root.put("namesMap", namesMap);
        assertEquals(Ognl.getValue("'aaa' in list", root), true);
        assertEquals(Ognl.getValue("list[0]", root), "aaa");
        assertEquals(Ognl.getValue("arr[0]", root), 11);
        assertEquals(Ognl.getValue("namesMap['zs']", root), "张三");
    }

    // 投影与选择
    public void testProjection() throws Exception {
        Map root = new HashMap();
        List<User> userList = new ArrayList<User>(){
            {
                add(new User(101, "张三", 10));
                add(new User(102, "张四", 11));
                add(new User(201, "李三", 20));
                add(new User(202, "李四", 21));
            }
        };
        root.put("userList", userList);
        // 新的以name为元素的集合
        List<String> nameList = (List<String>) Ognl.getValue("userList.{name}", root);
        System.out.println(nameList);
        // 将userList这个集合中的元素的id和name用-连接符拼接起来
        nameList = (List<String>) Ognl.getValue("userList.{id + '-' + name}", root);
        System.out.println(nameList);
        // 返回Root对象的userList这个集合所有元素中id不为101的元素构成的集合
        List<User> userList2 = (List<User>) Ognl.getValue("userList.{?id != 101}", root);
        System.out.println(userList2);
    }

    // 构造对象
    public void testCreateObject() throws Exception {
        Map root = new HashMap();
        // 构造一个List
        List<String> strList = (List<String>) Ognl.getValue("{'red', 'green', 'blue'}", root);
        System.out.println(strList);
        // 构造一个Map
        Map<String, String> map = (Map<String, String>) Ognl.getValue("#{'key1':'value1', 'key2':'value2'}", root);
        System.out.println(map);
    }
}
