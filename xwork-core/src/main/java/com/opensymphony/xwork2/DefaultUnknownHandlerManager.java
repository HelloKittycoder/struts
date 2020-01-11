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
package com.opensymphony.xwork2;

import com.opensymphony.xwork2.config.Configuration;
import com.opensymphony.xwork2.config.ConfigurationException;
import com.opensymphony.xwork2.config.entities.ActionConfig;
import com.opensymphony.xwork2.config.entities.UnknownHandlerConfig;
import com.opensymphony.xwork2.inject.Container;
import com.opensymphony.xwork2.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Default implementation of UnknownHandlerManager
 *
 * @see com.opensymphony.xwork2.UnknownHandlerManager
 */
public class DefaultUnknownHandlerManager implements UnknownHandlerManager {

    private Container container;

    protected ArrayList<UnknownHandler> unknownHandlers;

    @Inject
    public void setContainer(Container container) {
        this.container = container;
        try {
            build();
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }
    }

    /**
     * Builds a list of UnknownHandlers in the order specified by the configured "unknown-handler-stack".
     * If "unknown-handler-stack" was not configured, all UnknownHandlers will be returned, in no specific order
     */
    protected void build() throws Exception {
        Configuration configuration = container.getInstance(Configuration.class);
        ObjectFactory factory = container.getInstance(ObjectFactory.class);

        // 如果configuration对象不为空，则尝试获取UnknownHandler的实例
        if (configuration != null && container != null) {
            List<UnknownHandlerConfig> unkownHandlerStack = configuration.getUnknownHandlerStack();
            unknownHandlers = new ArrayList<UnknownHandler>();

            // 先尝试从configuration中获取
            if (unkownHandlerStack != null && !unkownHandlerStack.isEmpty()) {
                //get UnknownHandlers in the specified order
                // 根据一定顺序获取UnknownHandler的实例
                for (UnknownHandlerConfig unknownHandlerConfig : unkownHandlerStack) {
                    // 调用container对象的getInstance方法获取UnknownHandler
                    // 这里最终调用的就是container.getInstance(UnknownHandler.class, unknownHandlerName)
                    UnknownHandler uh = factory.buildUnknownHandler(unknownHandlerConfig.getName(), new HashMap<String, Object>());
                    unknownHandlers.add(uh);
                }
            } else {
                // 然后尝试从container中获取
                //add all available UnknownHandlers
                // 调用container对象的getInstanceNames方法获取所有受到容器管理的UnknownHandler实例名称
                Set<String> unknowHandlerNames = container.getInstanceNames(UnknownHandler.class);
                for (String unknowHandlerName : unknowHandlerNames) {
                    // 根据名称调用container对象的getInstance方法获取实例
                    UnknownHandler uh = container.getInstance(UnknownHandler.class, unknowHandlerName);
                    unknownHandlers.add(uh);
                }
            }
        }
    }

    /**
     * Iterate over UnknownHandlers and return the result of the first one that can handle it
     */
    public Result handleUnknownResult(ActionContext actionContext, String actionName, ActionConfig actionConfig, String resultCode) {
        for (UnknownHandler unknownHandler : unknownHandlers) {
            Result result = unknownHandler.handleUnknownResult(actionContext, actionName, actionConfig, resultCode);
            if (result != null)
                return result;
        }

        return null;
    }

    /**
     * Iterate over UnknownHandlers and return the result of the first one that can handle it
     *
     * @throws NoSuchMethodException
     */
    public Object handleUnknownMethod(Object action, String methodName) throws NoSuchMethodException {
        for (UnknownHandler unknownHandler : unknownHandlers) {
            Object result = unknownHandler.handleUnknownActionMethod(action, methodName);
            if (result != null)
                return result;
        }

        return null;
    }

    /**
     * Iterate over UnknownHandlers and return the result of the first one that can handle it
     */
    public ActionConfig handleUnknownAction(String namespace, String actionName) {
        for (UnknownHandler unknownHandler : unknownHandlers) {
            ActionConfig result = unknownHandler.handleUnknownAction(namespace, actionName);
            if (result != null)
                return result;
        }

        return null;
    }

    public boolean hasUnknownHandlers() {
        return unknownHandlers != null && !unknownHandlers.isEmpty();
    }

    public List<UnknownHandler> getUnknownHandlers() {
        return unknownHandlers;
    }
}
