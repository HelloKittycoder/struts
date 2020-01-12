package com.opensymphony.xwork2.ognl;

import com.opensymphony.xwork2.conversion.NullHandler;

import java.util.Map;

public class OgnlNullHandlerWrapper implements ognl.NullHandler {

    private NullHandler wrapped;
    
    public OgnlNullHandlerWrapper(NullHandler target) {
        this.wrapped = target;
    }
    
    public Object nullMethodResult(Map context, Object target,
            String methodName, Object[] args) {
        // 调用XWork的NullHandler实现，支持自定义的实现替换
        return wrapped.nullMethodResult(context, target, methodName, args);
    }

    public Object nullPropertyValue(Map context, Object target, Object property) {
        // 调用XWork的NullHandler实现，支持自定义的实现替换
        return wrapped.nullPropertyValue(context, target, property);
    }

}
