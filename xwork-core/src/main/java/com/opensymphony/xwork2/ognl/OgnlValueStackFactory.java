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
package com.opensymphony.xwork2.ognl;

import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.TextProvider;
import com.opensymphony.xwork2.conversion.NullHandler;
import com.opensymphony.xwork2.conversion.impl.XWorkConverter;
import com.opensymphony.xwork2.inject.Container;
import com.opensymphony.xwork2.inject.Inject;
import com.opensymphony.xwork2.ognl.accessor.CompoundRootAccessor;
import com.opensymphony.xwork2.util.CompoundRoot;
import com.opensymphony.xwork2.util.ValueStack;
import com.opensymphony.xwork2.util.ValueStackFactory;
import ognl.MethodAccessor;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import java.util.Map;
import java.util.Set;

/**
 * Creates an Ognl value stack
 */
public class OgnlValueStackFactory implements ValueStackFactory {
    
    private XWorkConverter xworkConverter;
    private CompoundRootAccessor compoundRootAccessor;
    private TextProvider textProvider;
    private Container container;
    private boolean allowStaticMethodAccess;

    @Inject
    public void setXWorkConverter(XWorkConverter conv) {
        this.xworkConverter = conv;
    }
    
    @Inject("system")
    public void setTextProvider(TextProvider textProvider) {
        this.textProvider = textProvider;
    }
    
    @Inject(value="allowStaticMethodAccess", required=false)
    public void setAllowStaticMethodAccess(String allowStaticMethodAccess) {
        this.allowStaticMethodAccess = "true".equalsIgnoreCase(allowStaticMethodAccess);
    }

    public ValueStack createValueStack() {
        ValueStack stack = new OgnlValueStack(xworkConverter, compoundRootAccessor, textProvider, allowStaticMethodAccess);
        container.inject(stack);
        stack.getContext().put(ActionContext.CONTAINER, container);
        return stack;
    }

    public ValueStack createValueStack(ValueStack stack) {
        // 初始化OgnlValueStack，设定OGNL计算时需要的参数等
        ValueStack result = new OgnlValueStack(stack, xworkConverter, compoundRootAccessor, allowStaticMethodAccess);
        container.inject(result);
        // 对OgnlValueStack进行参数注入
        stack.getContext().put(ActionContext.CONTAINER, container);
        return result;
    }

    // 初始化Container，同时初始化OGNL的相关设置
    // 这里主要是初始化OgnlRuntime中的三大规则
    @Inject
    public void setContainer(Container container) throws ClassNotFoundException {
        // 从XWork的container中获取所有ognl.PropertyAccessor的实现类
        Set<String> names = container.getInstanceNames(PropertyAccessor.class);
        for (String name : names) {
            Class cls = Class.forName(name);
            if (cls != null) {
                if (Map.class.isAssignableFrom(cls)) {
                    PropertyAccessor acc = container.getInstance(PropertyAccessor.class, name);
                }
                // 根据不同的类名，为OGNL分配对应的PropertyAccessor实现
                OgnlRuntime.setPropertyAccessor(cls, container.getInstance(PropertyAccessor.class, name));
                // 找到CompoundRootAccessor的实现，并初始化
                if (compoundRootAccessor == null && CompoundRoot.class.isAssignableFrom(cls)) {
                    compoundRootAccessor = (CompoundRootAccessor) container.getInstance(PropertyAccessor.class, name);
                }
            }
        }

        // 从XWork的container中获取所有MethodAccessor的实现类
        names = container.getInstanceNames(MethodAccessor.class);
        for (String name : names) {
            Class cls = Class.forName(name);
            if (cls != null) {
                // 根据不同的类名，为OGNL分配对应的MethodAccessor实现
                OgnlRuntime.setMethodAccessor(cls, container.getInstance(MethodAccessor.class, name));
            }
        }

        // 从XWork的container中获取所有ognl.NullHandler的实现类
        names = container.getInstanceNames(NullHandler.class);
        for (String name : names) {
            Class cls = Class.forName(name);
            if (cls != null) {
                // 根据不同的类名，为OGNL分配对应的NullHandler实现
                OgnlRuntime.setNullHandler(cls, new OgnlNullHandlerWrapper(container.getInstance(NullHandler.class, name)));
            }
        }
        if (compoundRootAccessor == null) {
            throw new IllegalStateException("Couldn't find the compound root accessor");
        }
        // 初始化container
        this.container = container;
    }
}
