/* Licensed under the Apache License, Version 2.0 (the "License");
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

package org.activiti.rest.api.history;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.engine.test.Deployment;
import org.activiti.rest.BaseRestTestCase;
import org.activiti.rest.api.RestUrls;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;


/**
 * Test for REST-operation related to the historic variable instance query resource.
 * 
 * @author Tijs Rademakers
 */
public class HistoricVariableInstanceCollectionResourceTest extends BaseRestTestCase {
  
  /**
   * Test querying historic variable instance. 
   * GET history/historic-variable-instances
   */
  @Deployment
  public void testQueryVariableInstances() throws Exception {
    HashMap<String, Object> processVariables = new HashMap<String, Object>();
    processVariables.put("stringVar", "Azerty");
    processVariables.put("intVar", 67890);
    processVariables.put("booleanVar", false);
    
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", processVariables);
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.complete(task.getId());
    task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.setVariableLocal(task.getId(), "taskVariable", "test");
    
    ProcessInstance processInstance2 = runtimeService.startProcessInstanceByKey("oneTaskProcess", processVariables);

    String url = RestUrls.createRelativeResourceUrl(RestUrls.URL_HISTORIC_VARIABLE_INSTANCES);
    
    assertResultsPresentInDataResponse(url + "?variableName=stringVar", 2, "stringVar", "Azerty");
    
    assertResultsPresentInDataResponse(url + "?variableName=booleanVar", 2, "booleanVar", false);
    
    assertResultsPresentInDataResponse(url + "?variableName=booleanVar2", 0, null, null);
    
    assertResultsPresentInDataResponse(url + "?processInstanceId=" + processInstance.getId(), 4, "taskVariable", "test");
    
    assertResultsPresentInDataResponse(url + "?processInstanceId=" + processInstance.getId() + "&excludeTaskVariables=true", 3, "intVar", 67890);
    
    assertResultsPresentInDataResponse(url + "?processInstanceId=" + processInstance2.getId(), 3, "stringVar", "Azerty");
    
    assertResultsPresentInDataResponse(url + "?taskId=" + task.getId(), 1, "taskVariable", "test");
    
    assertResultsPresentInDataResponse(url + "?taskId=" + task.getId() + "&variableName=booleanVar", 0, null, null);
    
    assertResultsPresentInDataResponse(url + "?variableNameLike=" + encode("%Var"), 6, "stringVar", "Azerty");
    
    assertResultsPresentInDataResponse(url + "?variableNameLike=" + encode("%Var2"), 0, null, null);
  }
  
  protected void assertResultsPresentInDataResponse(String url, int numberOfResultsExpected, String variableName, Object variableValue) throws JsonProcessingException, IOException {
    
    // Do the actual call
    ClientResource client = getAuthenticatedClient(url);
    Representation response = client.get();
    
    // Check status and size
    assertEquals(Status.SUCCESS_OK, client.getResponse().getStatus());
    JsonNode dataNode = objectMapper.readTree(response.getStream()).get("data");
    assertEquals(numberOfResultsExpected, dataNode.size());

    // Check presence of ID's
    if (variableName != null) {
      boolean variableFound = false;
      Iterator<JsonNode> it = dataNode.iterator();
      while(it.hasNext()) {
        JsonNode variableNode = it.next();
        String name = variableNode.get("variableName").getTextValue();
        if (variableName.equals(name)) {
          variableFound = true;
          if (variableValue instanceof Boolean) {
            assertTrue("Variable value is not equal", variableNode.get("value").asBoolean() == (Boolean) variableValue);
          } else if (variableValue instanceof Integer) {
            assertTrue("Variable value is not equal", variableNode.get("value").asInt() == (Integer) variableValue);
          } else {
            assertTrue("Variable value is not equal", variableNode.get("value").asText().equals((String) variableValue));
          }
        }
      }
      assertTrue("Variable " + variableName + " is missing", variableFound);
    }
    
    client.release();
  }
}
