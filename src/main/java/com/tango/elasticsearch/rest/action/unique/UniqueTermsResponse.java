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

import java.io.IOException;
import java.util.List;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;

/**
 * @author Nina Safonova (nsafonova)
 */
public class UniqueTermsResponse extends ActionResponse implements ToXContent {

    private List<UniqueTerms> terms;

    public UniqueTermsResponse(List<UniqueTerms> terms) {
        this.terms = terms;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("facets");
        for (UniqueTerms term : terms) {
            term.toXContent(builder, params);
        }
        builder.endObject();
        return builder;
    }

    public static class UniqueTerms implements ToXContent, Streamable {

        private String name;
        private int unique;
        private int total;
        private int missing;
        private int other;

        public UniqueTerms(String name, int unique, int total, int missing, int other) {
            this.name = name;
            this.unique = unique;
            this.total = total;
            this.missing = missing;
            this.other = other;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            name = in.readString();
            unique = in.readVInt();
            total = in.readVInt();
            missing = in.readVInt();
            other = in.readVInt();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(name);
            out.writeVInt(unique);
            out.writeVInt(total);
            out.writeVInt(missing);
            out.writeVInt(other);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject(name);
            builder.field("unique", unique);
            builder.field("total", total);
            builder.field("missing", missing);
            builder.field("other", other);
            builder.endObject();
            return builder;
        }
    }

}
