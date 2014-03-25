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

import java.io.Serializable;
import java.util.List;

/**
 * @author Nina Safonova (nsafonova)
 */
public class TermsResult implements Serializable {

    private final List<String> uniqueTerms;
    private final long total;
    private final long missing;
    private final long other;

    public TermsResult(List<String> uniqueTerms, long total, long missing, long other) {
        this.uniqueTerms = uniqueTerms;
        this.total = total;
        this.missing = missing;
        this.other = other;
    }

    public List<String> getUniqueTerms() {
        return uniqueTerms;
    }

    public long getTotalCount() {
        return total;
    }

    public long getMissingCount() {
        return missing;
    }

    public long getOtherCount() {
        return other;
    }
}
