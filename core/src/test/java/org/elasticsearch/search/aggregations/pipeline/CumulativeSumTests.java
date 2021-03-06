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

package org.elasticsearch.search.aggregations.pipeline;

import org.elasticsearch.search.aggregations.BasePipelineAggregationTestCase;
import org.elasticsearch.search.aggregations.pipeline.cumulativesum.CumulativeSumPipelineAggregator;
import org.elasticsearch.search.aggregations.pipeline.cumulativesum.CumulativeSumPipelineAggregator.CumulativeSumPipelineAggregatorBuilder;

public class CumulativeSumTests
        extends BasePipelineAggregationTestCase<CumulativeSumPipelineAggregator.CumulativeSumPipelineAggregatorBuilder> {

    @Override
    protected CumulativeSumPipelineAggregatorBuilder createTestAggregatorFactory() {
        String name = randomAsciiOfLengthBetween(3, 20);
        String bucketsPath = randomAsciiOfLengthBetween(3, 20);
        CumulativeSumPipelineAggregatorBuilder factory = new CumulativeSumPipelineAggregatorBuilder(name, bucketsPath);
        if (randomBoolean()) {
            factory.format(randomAsciiOfLengthBetween(1, 10));
        }
        return factory;
    }

}
