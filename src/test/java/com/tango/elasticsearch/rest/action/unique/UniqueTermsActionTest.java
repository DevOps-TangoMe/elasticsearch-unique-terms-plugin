/**
 *  Copyright 2014 TangoMe Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.tango.elasticsearch.rest.action.unique;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import org.junit.Test;

/**
 * @author Nina Safonova (nsafonova)
 */
public class UniqueTermsActionTest {

    @Test
    public void testRequestSourceParsing() throws Exception {
        String requestSource = "{\"facets\":{\"terms\":{\"terms\":{\"field\":\"@fields.uid\",\"size\":10000000,\"order\":\"count\",\"exclude\":[]},\"facet_filter\":{\"fquery\":{\"query\":{\"filtered\":{\"query\":{\"bool\":{\"should\":[{\"query_string\":{\"query\":\"*\"}}]}},\"filter\":{\"bool\":{\"must\":[{\"range\":{\"@timestamp\":{\"from\":1395275639569,\"to\":\"now\"}}},{\"fquery\":{\"query\":{\"query_string\":{\"query\":\"@fields.tracer.service.name:(\\\"Like\\\")\"}},\"_cache\":true}},{\"terms\":{\"@fields.tracer.ip.country.name\":[\"UNITED STATES\"]}}]}}}}}}}},\"size\":0}";
        UniqueTermsAction action = new UniqueTermsAction();
        UniqueTermsAction.RequestParamsInfo info = action.getRequestInfo(requestSource);
        assertNotNull(info);
        assertTrue(info.getFromTime() != 0);
        assertTrue(info.getToTime() != 0);
        assertNotNull(info.getRequestCacheKey());
    }

}
