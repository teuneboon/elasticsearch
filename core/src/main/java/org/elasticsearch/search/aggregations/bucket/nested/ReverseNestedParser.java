/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.search.aggregations.bucket.nested;

import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.search.aggregations.Aggregator;
import java.io.IOException;

/**
 *
 */
public class ReverseNestedParser implements Aggregator.Parser {

    @Override
    public String type() {
        return InternalReverseNested.TYPE.name();
    }

    @Override
    public ReverseNestedAggregator.ReverseNestedAggregatorBuilder parse(String aggregationName, XContentParser parser,
            QueryParseContext context) throws IOException {
        String path = null;

        XContentParser.Token token;
        String currentFieldName = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.VALUE_STRING) {
                if ("path".equals(currentFieldName)) {
                    path = parser.text();
                } else {
                    throw new ParsingException(parser.getTokenLocation(),
                            "Unknown key for a " + token + " in [" + aggregationName + "]: [" + currentFieldName + "].");
                }
            } else {
                throw new ParsingException(parser.getTokenLocation(), "Unexpected token " + token + " in [" + aggregationName + "].");
            }
        }

        ReverseNestedAggregator.ReverseNestedAggregatorBuilder factory = new ReverseNestedAggregator.ReverseNestedAggregatorBuilder(
                aggregationName);
        if (path != null) {
            factory.path(path);
        }
        return factory;
    }

    @Override
    public ReverseNestedAggregator.ReverseNestedAggregatorBuilder getFactoryPrototypes() {
        return ReverseNestedAggregator.ReverseNestedAggregatorBuilder.PROTOTYPE;
    }
}
