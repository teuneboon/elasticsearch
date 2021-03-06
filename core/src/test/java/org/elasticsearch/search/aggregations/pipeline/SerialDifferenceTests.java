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
import org.elasticsearch.search.aggregations.pipeline.BucketHelpers.GapPolicy;
import org.elasticsearch.search.aggregations.pipeline.serialdiff.SerialDiffPipelineAggregator;
import org.elasticsearch.search.aggregations.pipeline.serialdiff.SerialDiffPipelineAggregator.SerialDiffPipelineAggregatorBuilder;

public class SerialDifferenceTests
        extends BasePipelineAggregationTestCase<SerialDiffPipelineAggregator.SerialDiffPipelineAggregatorBuilder> {

    @Override
    protected SerialDiffPipelineAggregatorBuilder createTestAggregatorFactory() {
        String name = randomAsciiOfLengthBetween(3, 20);
        String bucketsPath = randomAsciiOfLengthBetween(3, 20);
        SerialDiffPipelineAggregatorBuilder factory = new SerialDiffPipelineAggregatorBuilder(name, bucketsPath);
        if (randomBoolean()) {
            factory.format(randomAsciiOfLengthBetween(1, 10));
        }
        if (randomBoolean()) {
            factory.gapPolicy(randomFrom(GapPolicy.values()));
        }
        if (randomBoolean()) {
            factory.lag(randomIntBetween(1, 1000));
        }
        return factory;
    }

}
