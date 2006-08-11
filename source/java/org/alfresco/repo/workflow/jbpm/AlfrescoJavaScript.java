/*
 * Copyright (C) 2005 Alfresco, Inc.
 *
 * Licensed under the Mozilla Public License version 1.1 
 * with a permitted attribution clause. You may obtain a
 * copy of the License at
 *
 *   http://www.alfresco.org/legal/license.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.alfresco.repo.workflow.jbpm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ScriptService;
import org.dom4j.Element;
import org.jbpm.context.def.VariableAccess;
import org.jbpm.context.exe.ContextInstance;
import org.jbpm.graph.exe.ExecutionContext;
import org.jbpm.graph.exe.Token;
import org.jbpm.jpdl.xml.JpdlXmlReader;
import org.springframework.beans.factory.BeanFactory;
import org.xml.sax.InputSource;

/**
 * A jBPM Action Handler for executing Alfresco Script
 *
 * The configuration of this action is as follows:
 *  <script language="javascript">
 *     <expression>
 *        the script to execute
 *     </expression>
 *     <variable name="watcha" access="write"/>
 *  </script>
 *  
 * It's exactly the same as jBPM's own script configuration.
 *  
 * @author davidc
 */
public class AlfrescoJavaScript extends JBPMSpringActionHandler
{
    private static final long serialVersionUID = -2908748080671212745L;
    
    private static JpdlXmlReader jpdlReader = new JpdlXmlReader((InputSource)null);
    private ScriptService scriptService;
    private Element script;
    

    /* (non-Javadoc)
     * @see org.alfresco.repo.workflow.jbpm.JBPMSpringActionHandler#initialiseHandler(org.springframework.beans.factory.BeanFactory)
     */
    @Override
    protected void initialiseHandler(BeanFactory factory)
    {
        scriptService = (ScriptService)factory.getBean(ServiceRegistry.SCRIPT_SERVICE.getLocalName());
    }

    
    /* (non-Javadoc)
     * @see org.jbpm.graph.def.ActionHandler#execute(org.jbpm.graph.exe.ExecutionContext)
     */
    @SuppressWarnings("unchecked")
    public void execute(ExecutionContext executionContext) throws Exception
    {
        // extract action configuration
        String expression = null;
        List<VariableAccess> variableAccesses = null;        
        if (script.isTextOnly())
        {
            expression = script.getTextTrim();
        }
        else
        {
            variableAccesses = jpdlReader.readVariableAccesses(script);
            expression = script.element("expression").getTextTrim();
        }

        // construct script arguments and execute
        Map<String, Object> inputMap = createInputMap(executionContext, variableAccesses);
        Object result = scriptService.executeScriptString(expression, inputMap);

        // map script return variable to process context
        VariableAccess returnVariable = getWritableVariable(variableAccesses);
        if (returnVariable != null)
        {
            ContextInstance contextInstance = executionContext.getContextInstance();
            Token token = executionContext.getToken();
            contextInstance.setVariable(returnVariable.getVariableName(), result, token);            
        }
    }


    /**
     * Construct map of arguments to pass to script
     * 
     * Based on the <variable> elements of the action configuration.
     * 
     * @param executionContext  the execution context
     * @param variableAccesses  the variable configuration
     * @return  the map of script arguments
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createInputMap(ExecutionContext executionContext, List<VariableAccess> variableAccesses)
    {
        Map<String, Object> inputMap = new HashMap<String, Object>();

        // initialise process variables
        Token token = executionContext.getToken();
        inputMap.put("executionContext", executionContext);
        inputMap.put("token", token);
        if (executionContext.getNode() != null)
        {
            inputMap.put("node", executionContext.getNode());
        }
        if (executionContext.getTask() != null)
        {
            inputMap.put("task", executionContext.getTask());
        }
        if (executionContext.getTaskInstance() != null)
        {
            inputMap.put("taskInstance", executionContext.getTaskInstance());
        }

        // if no readable variableInstances are specified,
        ContextInstance contextInstance = executionContext.getContextInstance();
        if (!hasReadableVariable(variableAccesses))
        {
            // copy all the variableInstances of the context into the interpreter
            Map<String, Object> variables = contextInstance.getVariables(token);
            if (variables != null)
            {
                for (Map.Entry entry : variables.entrySet())
                {
                    String variableName = (String) entry.getKey();
                    Object variableValue = entry.getValue();
                    inputMap.put(variableName, variableValue);
                }
            }
        }
        else
        {
            // copy the specified variableInstances into the interpreterz
            for (VariableAccess variableAccess : variableAccesses)
            {
                if (variableAccess.isReadable())
                {
                    String variableName = variableAccess.getVariableName();
                    String mappedName = variableAccess.getMappedName();
                    Object variableValue = contextInstance.getVariable(variableName, token);
                    inputMap.put(mappedName, variableValue);
                }
            }
        }

        return inputMap;
    }
    
    
    /**
     * Determine if there are variables to read from the process context
     * 
     * @param variableAccesses  the variables configuration
     * @return  true => there are variables to read
     */
    private boolean hasReadableVariable(List<VariableAccess> variableAccesses)
    {
        if (variableAccesses != null)
        {
            for (VariableAccess variableAccess : variableAccesses)
            {
                if (variableAccess.isReadable())
                {
                    return true;
                }
            }
        }
        return false;
    }

    
    /**
     * Determine if there is a variable to write back to the process context
     * 
     * @param variableAccesses  the variables configuration
     * @return  true => there is a variable to write
     */
    private VariableAccess getWritableVariable(List<VariableAccess> variableAccesses)
    {
        if (variableAccesses != null)
        {
            for (VariableAccess variableAccess : variableAccesses)
            {
                if (variableAccess.isWritable())
                {
                    return variableAccess;
                }
            }
        }
        return null;
    }

}
