/*
 * Copyright 2002-2006,2009 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opensymphony.xwork2.ognl.accessor;

import com.opensymphony.xwork2.XWorkConstants;
import com.opensymphony.xwork2.XWorkException;
import com.opensymphony.xwork2.inject.Inject;
import com.opensymphony.xwork2.ognl.OgnlValueStack;
import com.opensymphony.xwork2.util.CompoundRoot;
import com.opensymphony.xwork2.util.ValueStack;
import com.opensymphony.xwork2.util.logging.Logger;
import com.opensymphony.xwork2.util.logging.LoggerFactory;
import ognl.*;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;
import static org.apache.commons.lang3.BooleanUtils.toBoolean;

/**
 * A stack that is able to call methods on objects in the stack.
 *
 * @author $Author$
 * @author Rainer Hermanns
 * @version $Revision$
 */
public class CompoundRootAccessor implements PropertyAccessor, MethodAccessor, ClassResolver {

    /**
     * Used by OGNl to generate bytecode
     */
    public String getSourceAccessor(OgnlContext context, Object target, Object index) {
        return null;
    }

    /**
     * Used by OGNl to generate bytecode
     */
    public String getSourceSetter(OgnlContext context, Object target, Object index) {
        return null;
    }

    private final static Logger LOG = LoggerFactory.getLogger(CompoundRootAccessor.class);
    private final static Class[] EMPTY_CLASS_ARRAY = new Class[0];
    private static Map<MethodCall, Boolean> invalidMethods = new ConcurrentHashMap<MethodCall, Boolean>();

    static boolean devMode = false;

    @Inject(XWorkConstants.DEV_MODE)
    public static void setDevMode(String mode) {
        devMode = "true".equals(mode);
    }

    // 实现OGNL的PropertyAccessor中的setProperty方法
    public void setProperty(Map context, Object target, Object name, Object value) throws OgnlException {
        // 类型转化，得到OGNL的根对象和上下文
        CompoundRoot root = (CompoundRoot) target;
        OgnlContext ognlContext = (OgnlContext) context;

        // 循环查找CompoundRoot堆栈中的所有元素
        for (Object o : root) {
            if (o == null) {
                continue;
            }

            try {
                // 调用OgnlRuntime的方法判定当前元素是否能够被写值
                if (OgnlRuntime.hasSetProperty(ognlContext, o, name)) {
                    OgnlRuntime.setProperty(ognlContext, o, name, value);

                    // 设值完成，停止查找下一个元素，直接返回
                    return;
                } else if (o instanceof Map) {
                    // 如果是一个Map对象，直接往Map对象中写值即可
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> map = (Map<Object, Object>) o;
                    try {
                        map.put(name, value);
                        return;
                    } catch (UnsupportedOperationException e) {
                        // This is an unmodifiable Map, so move on to the next element in the stack
                    }
                }
//            } catch (OgnlException e) {
//                if (e.getReason() != null) {
//                    final String msg = "Caught an Ognl exception while setting property " + name;
//                    log.error(msg, e);
//                    throw new RuntimeException(msg, e.getReason());
//                }
            } catch (IntrospectionException e) {
                // this is OK if this happens, we'll just keep trying the next
            }
        }

        // 如果在OGNL计算中发生异常，OGNL上下文中会决定是否抛出这个异常
        boolean reportError = toBoolean((Boolean) context.get(ValueStack.REPORT_ERRORS_ON_NO_PROP));

        if (reportError || devMode) {
            final String msg = format("No object in the CompoundRoot has a publicly accessible property named '%s' " +
                    "(no setter could be found).", name);
            if (reportError) {
                throw new XWorkException(msg);
            } else {
                LOG.warn(msg);
            }
        }
    }

    // 实现OGNL的PropertyAccessor中的getProperty方法
    public Object getProperty(Map context, Object target, Object name) throws OgnlException {
        // 类型转化，得到OGNL的根对象和上下文
        CompoundRoot root = (CompoundRoot) target;
        OgnlContext ognlContext = (OgnlContext) context;

        // 如果访问表达式是数字，表示获取ValueStack的一个子栈
        if (name instanceof Integer) {
            Integer index = (Integer) name;

            return root.cutStack(index);
        } else if (name instanceof String) {
            // 处理关键字top，返回的是ValueStack的栈顶元素
            if ("top".equals(name)) {
                if (root.size() > 0) {
                    return root.get(0);
                } else {
                    return null;
                }
            }

            // 对于普通的属性名称，需要循环整个栈，找到第一个匹配的元素并返回
            for (Object o : root) {
                if (o == null) {
                    continue;
                }

                try {
                    // 调用OgnlRuntime的方法判定是否包含该属性
                    if ((OgnlRuntime.hasGetProperty(ognlContext, o, name)) || ((o instanceof Map) && ((Map) o).containsKey(name))) {
                        // 找到属性后，停止查找，并直接返回属性值作为结果
                        return OgnlRuntime.getProperty(ognlContext, o, name);
                    }
                } catch (OgnlException e) {
                    if (e.getReason() != null) {
                        final String msg = "Caught an Ognl exception while getting property " + name;
                        throw new XWorkException(msg, e);
                    }
                } catch (IntrospectionException e) {
                    // this is OK if this happens, we'll just keep trying the next
                }
            }

            //property was not found
            if (context.containsKey(OgnlValueStack.THROW_EXCEPTION_ON_FAILURE))
                throw new NoSuchPropertyException(target, name);
            else
                return null;
        } else {
            return null;
        }
    }

    public Object callMethod(Map context, Object target, String name, Object[] objects) throws MethodFailedException {
        CompoundRoot root = (CompoundRoot) target;

        if ("describe".equals(name)) {
            Object v;
            if (objects != null && objects.length == 1) {
                v = objects[0];
            } else {
                v = root.get(0);
            }


            if (v instanceof Collection || v instanceof Map || v.getClass().isArray()) {
                return v.toString();
            }

            try {
                Map<String, PropertyDescriptor> descriptors = OgnlRuntime.getPropertyDescriptors(v.getClass());

                int maxSize = 0;
                for (String pdName : descriptors.keySet()) {
                    if (pdName.length() > maxSize) {
                        maxSize = pdName.length();
                    }
                }

                SortedSet<String> set = new TreeSet<String>();
                StringBuffer sb = new StringBuffer();
                for (PropertyDescriptor pd : descriptors.values()) {

                    sb.append(pd.getName()).append(": ");
                    int padding = maxSize - pd.getName().length();
                    for (int i = 0; i < padding; i++) {
                        sb.append(" ");
                    }
                    sb.append(pd.getPropertyType().getName());
                    set.add(sb.toString());

                    sb = new StringBuffer();
                }

                sb = new StringBuffer();
                for (Object aSet : set) {
                    String s = (String) aSet;
                    sb.append(s).append("\n");
                }

                return sb.toString();
            } catch (IntrospectionException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Got exception in callMethod", e);
                }
            } catch (OgnlException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Got exception in callMethod", e);
                }
            }

            return null;
        }

        for (Object o : root) {
            if (o == null) {
                continue;
            }

            Class clazz = o.getClass();
            Class[] argTypes = getArgTypes(objects);

            MethodCall mc = null;

            if (argTypes != null) {
                mc = new MethodCall(clazz, name, argTypes);
            }

            if ((argTypes == null) || !invalidMethods.containsKey(mc)) {
                try {
                    Object value = OgnlRuntime.callMethod((OgnlContext) context, o, name, objects);

                    if (value != null) {
                        return value;
                    }
                } catch (OgnlException e) {
                    // try the next one
                    Throwable reason = e.getReason();

                    if (!context.containsKey(OgnlValueStack.THROW_EXCEPTION_ON_FAILURE) && (mc != null) && (reason != null) && (reason.getClass() == NoSuchMethodException.class)) {
                        invalidMethods.put(mc, Boolean.TRUE);
                    } else if (reason != null) {
                        throw new MethodFailedException(o, name, e.getReason());
                    }
                }
            }
        }

        return null;
    }

    public Object callStaticMethod(Map transientVars, Class aClass, String s, Object[] objects) throws MethodFailedException {
        return null;
    }

    public Class classForName(String className, Map context) throws ClassNotFoundException {
        // 获取OGNL的Root对象
        Object root = Ognl.getRoot(context);

        try {
            // 如果Root对象是CompoundRoot类型，则需要处理特殊名称的className
            if (root instanceof CompoundRoot) {
                if (className.startsWith("vs")) {
                    CompoundRoot compoundRoot = (CompoundRoot) root;

                    // 支持直接使用vs作为className
                    if ("vs".equals(className)) {
                        return compoundRoot.peek().getClass();
                    }

                    // 支持使用vs2、vs3等形式作为className
                    int index = Integer.parseInt(className.substring(2));

                    return compoundRoot.get(index - 1).getClass();
                }
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Got exception when tried to get class for name [#0]", e, className);
            }
        }

        return Thread.currentThread().getContextClassLoader().loadClass(className);
    }

    private Class[] getArgTypes(Object[] args) {
        if (args == null) {
            return EMPTY_CLASS_ARRAY;
        }

        Class[] classes = new Class[args.length];

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            classes[i] = (arg != null) ? arg.getClass() : Object.class;
        }

        return classes;
    }


    static class MethodCall {
        Class clazz;
        String name;
        Class[] args;
        int hash;

        public MethodCall(Class clazz, String name, Class[] args) {
            this.clazz = clazz;
            this.name = name;
            this.args = args;
            this.hash = clazz.hashCode() + name.hashCode();

            for (Class arg : args) {
                hash += arg.hashCode();
            }
        }

        @Override
        public boolean equals(Object obj) {
            MethodCall mc = (CompoundRootAccessor.MethodCall) obj;

            return (mc.clazz.equals(clazz) && mc.name.equals(name) && Arrays.equals(mc.args, args));
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
