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

package org.elasticsearch.search.aggregations.pipeline.bucketmetrics.stats;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.InternalAggregation.Type;
import org.elasticsearch.search.aggregations.pipeline.BucketHelpers.GapPolicy;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilder;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorStreams;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.BucketMetricsPipelineAggregatorBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.BucketMetricsPipelineAggregator;
import org.elasticsearch.search.aggregations.support.format.ValueFormatter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class StatsBucketPipelineAggregator extends BucketMetricsPipelineAggregator {

    public final static Type TYPE = new Type("stats_bucket");

    public final static PipelineAggregatorStreams.Stream STREAM = new PipelineAggregatorStreams.Stream() {
        @Override
        public StatsBucketPipelineAggregator readResult(StreamInput in) throws IOException {
            StatsBucketPipelineAggregator result = new StatsBucketPipelineAggregator();
            result.readFrom(in);
            return result;
        }
    };

    public static void registerStreams() {
        PipelineAggregatorStreams.registerStream(STREAM, TYPE.stream());
        InternalStatsBucket.registerStreams();
    }

    private double sum = 0;
    private long count = 0;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;

    protected StatsBucketPipelineAggregator(String name, String[] bucketsPaths, GapPolicy gapPolicy, ValueFormatter formatter,
                                            Map<String, Object> metaData) {
        super(name, bucketsPaths, gapPolicy, formatter, metaData);
    }

    StatsBucketPipelineAggregator() {
        // For serialization
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    protected void preCollection() {
        sum = 0;
        count = 0;
        min = Double.POSITIVE_INFINITY;
        max = Double.NEGATIVE_INFINITY;
    }

    @Override
    protected void collectBucketValue(String bucketKey, Double bucketValue) {
        sum += bucketValue;
        min = Math.min(min, bucketValue);
        max = Math.max(max, bucketValue);
        count += 1;
    }

    @Override
    protected InternalAggregation buildAggregation(List<PipelineAggregator> pipelineAggregators, Map<String, Object> metadata) {
        return new InternalStatsBucket(name(), count, sum, min, max, formatter, pipelineAggregators, metadata);
    }

    public static class StatsBucketPipelineAggregatorBuilder
            extends BucketMetricsPipelineAggregatorBuilder<StatsBucketPipelineAggregatorBuilder> {

        static final StatsBucketPipelineAggregatorBuilder PROTOTYPE = new StatsBucketPipelineAggregatorBuilder("", "");

        public StatsBucketPipelineAggregatorBuilder(String name, String bucketsPath) {
            this(name, new String[] { bucketsPath });
        }

        private StatsBucketPipelineAggregatorBuilder(String name, String[] bucketsPaths) {
            super(name, TYPE.name(), bucketsPaths);
        }

        @Override
        protected PipelineAggregator createInternal(Map<String, Object> metaData) throws IOException {
            return new StatsBucketPipelineAggregator(name, bucketsPaths, gapPolicy(), formatter(), metaData);
        }

        @Override
        public void doValidate(AggregatorFactory<?> parent, AggregatorFactory<?>[] aggFactories,
                List<PipelineAggregatorBuilder<?>> pipelineAggregatorFactories) {
            if (bucketsPaths.length != 1) {
                throw new IllegalStateException(Parser.BUCKETS_PATH.getPreferredName()
                        + " must contain a single entry for aggregation [" + name + "]");
            }
        }

        @Override
        protected XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
            return builder;
        }

        @Override
        protected StatsBucketPipelineAggregatorBuilder innerReadFrom(String name, String[] bucketsPaths, StreamInput in)
                throws IOException {
            return new StatsBucketPipelineAggregatorBuilder(name, bucketsPaths);
        }

        @Override
        protected void innerWriteTo(StreamOutput out) throws IOException {
            // Do nothing, no extra state to write to stream
        }

        @Override
        protected int innerHashCode() {
            return 0;
        }

        @Override
        protected boolean innerEquals(BucketMetricsPipelineAggregatorBuilder<StatsBucketPipelineAggregatorBuilder> other) {
            return true;
        }

    }

}
