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

package org.elasticsearch.search.aggregations.pipeline.bucketmetrics.avg;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.InternalAggregation.Type;
import org.elasticsearch.search.aggregations.pipeline.BucketHelpers.GapPolicy;
import org.elasticsearch.search.aggregations.pipeline.InternalSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilder;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorStreams;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.BucketMetricsPipelineAggregatorBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.BucketMetricsPipelineAggregator;
import org.elasticsearch.search.aggregations.support.format.ValueFormatter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AvgBucketPipelineAggregator extends BucketMetricsPipelineAggregator {

    public final static Type TYPE = new Type("avg_bucket");

    public final static PipelineAggregatorStreams.Stream STREAM = new PipelineAggregatorStreams.Stream() {
        @Override
        public AvgBucketPipelineAggregator readResult(StreamInput in) throws IOException {
            AvgBucketPipelineAggregator result = new AvgBucketPipelineAggregator();
            result.readFrom(in);
            return result;
        }
    };

    public static void registerStreams() {
        PipelineAggregatorStreams.registerStream(STREAM, TYPE.stream());
    }

    private int count = 0;
    private double sum = 0;

    private AvgBucketPipelineAggregator() {
    }

    protected AvgBucketPipelineAggregator(String name, String[] bucketsPaths, GapPolicy gapPolicy, ValueFormatter formatter,
            Map<String, Object> metaData) {
        super(name, bucketsPaths, gapPolicy, formatter, metaData);
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    protected void preCollection() {
        count = 0;
        sum = 0;
    }

    @Override
    protected void collectBucketValue(String bucketKey, Double bucketValue) {
        count++;
        sum += bucketValue;
    }

    @Override
    protected InternalAggregation buildAggregation(List<PipelineAggregator> pipelineAggregators, Map<String, Object> metadata) {
        double avgValue = count == 0 ? Double.NaN : (sum / count);
        return new InternalSimpleValue(name(), avgValue, formatter, pipelineAggregators, metadata);
    }

    public static class AvgBucketPipelineAggregatorBuilder extends BucketMetricsPipelineAggregatorBuilder<AvgBucketPipelineAggregatorBuilder> {

        static final AvgBucketPipelineAggregatorBuilder PROTOTYPE = new AvgBucketPipelineAggregatorBuilder("", "");

        public AvgBucketPipelineAggregatorBuilder(String name, String bucketsPath) {
            this(name, new String[] { bucketsPath });
        }

        private AvgBucketPipelineAggregatorBuilder(String name, String[] bucketsPaths) {
            super(name, TYPE.name(), bucketsPaths);
        }

        @Override
        protected PipelineAggregator createInternal(Map<String, Object> metaData) throws IOException {
            return new AvgBucketPipelineAggregator(name, bucketsPaths, gapPolicy(), formatter(), metaData);
        }

        @Override
        public void doValidate(AggregatorFactory<?> parent, AggregatorFactory<?>[] aggFactories,
                List<PipelineAggregatorBuilder<?>> pipelineAggregatorFactories) {
            if (bucketsPaths.length != 1) {
                throw new IllegalStateException(PipelineAggregator.Parser.BUCKETS_PATH.getPreferredName()
                        + " must contain a single entry for aggregation [" + name + "]");
            }
        }

        @Override
        protected XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
            return builder;
        }

        @Override
        protected AvgBucketPipelineAggregatorBuilder innerReadFrom(String name, String[] bucketsPaths, StreamInput in) throws IOException {
            return new AvgBucketPipelineAggregatorBuilder(name, bucketsPaths);
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
        protected boolean innerEquals(BucketMetricsPipelineAggregatorBuilder<AvgBucketPipelineAggregatorBuilder> other) {
            return true;
        }

    }

}
