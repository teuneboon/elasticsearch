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
package org.elasticsearch.search.aggregations.metrics.max;

import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.DoubleArray;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.fielddata.NumericDoubleValues;
import org.elasticsearch.index.fielddata.SortedNumericDoubleValues;
import org.elasticsearch.search.MultiValueMode;
import org.elasticsearch.search.aggregations.Aggregator;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.LeafBucketCollector;
import org.elasticsearch.search.aggregations.LeafBucketCollectorBase;
import org.elasticsearch.search.aggregations.AggregatorFactories.Builder;
import org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregator;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.support.AggregationContext;
import org.elasticsearch.search.aggregations.support.ValueType;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.aggregations.support.ValuesSource.Numeric;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregatorBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;
import org.elasticsearch.search.aggregations.support.format.ValueFormatter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class MaxAggregator extends NumericMetricsAggregator.SingleValue {

    final ValuesSource.Numeric valuesSource;
    final ValueFormatter formatter;

    DoubleArray maxes;

    public MaxAggregator(String name, ValuesSource.Numeric valuesSource, ValueFormatter formatter,
 AggregationContext context,
            Aggregator parent, List<PipelineAggregator> pipelineAggregators,
            Map<String, Object> metaData) throws IOException {
        super(name, context, parent, pipelineAggregators, metaData);
        this.valuesSource = valuesSource;
        this.formatter = formatter;
        if (valuesSource != null) {
            maxes = context.bigArrays().newDoubleArray(1, false);
            maxes.fill(0, maxes.size(), Double.NEGATIVE_INFINITY);
        }
    }

    @Override
    public boolean needsScores() {
        return valuesSource != null && valuesSource.needsScores();
    }

    @Override
    public LeafBucketCollector getLeafCollector(LeafReaderContext ctx,
            final LeafBucketCollector sub) throws IOException {
        if (valuesSource == null) {
            return LeafBucketCollector.NO_OP_COLLECTOR;
    }
        final BigArrays bigArrays = context.bigArrays();
        final SortedNumericDoubleValues allValues = valuesSource.doubleValues(ctx);
        final NumericDoubleValues values = MultiValueMode.MAX.select(allValues, Double.NEGATIVE_INFINITY);
        return new LeafBucketCollectorBase(sub, allValues) {

            @Override
            public void collect(int doc, long bucket) throws IOException {
                if (bucket >= maxes.size()) {
                    long from = maxes.size();
                    maxes = bigArrays.grow(maxes, bucket + 1);
                    maxes.fill(from, maxes.size(), Double.NEGATIVE_INFINITY);
                }
                final double value = values.get(doc);
                double max = maxes.get(bucket);
                max = Math.max(max, value);
                maxes.set(bucket, max);
            }

        };
    }

    @Override
    public double metric(long owningBucketOrd) {
        return valuesSource == null ? Double.NEGATIVE_INFINITY : maxes.get(owningBucketOrd);
    }

    @Override
    public InternalAggregation buildAggregation(long bucket) {
        if (valuesSource == null || bucket >= maxes.size()) {
            return buildEmptyAggregation();
        }
        return new InternalMax(name, maxes.get(bucket), formatter, pipelineAggregators(),  metaData());
    }

    @Override
    public InternalAggregation buildEmptyAggregation() {
        return new InternalMax(name, Double.NEGATIVE_INFINITY, formatter, pipelineAggregators(), metaData());
    }

    public static class MaxAggregatorBuilder extends ValuesSourceAggregatorBuilder.LeafOnly<ValuesSource.Numeric, MaxAggregatorBuilder> {

        static final MaxAggregatorBuilder PROTOTYPE = new MaxAggregatorBuilder("");

        public MaxAggregatorBuilder(String name) {
            super(name, InternalMax.TYPE, ValuesSourceType.NUMERIC, ValueType.NUMERIC);
        }

        @Override
        protected MaxAggregatorFactory innerBuild(AggregationContext context, ValuesSourceConfig<Numeric> config,
                AggregatorFactory<?> parent, Builder subFactoriesBuilder) throws IOException {
            return new MaxAggregatorFactory(name, type, config, context, parent, subFactoriesBuilder, metaData);
        }

        @Override
        protected MaxAggregatorBuilder innerReadFrom(String name, ValuesSourceType valuesSourceType,
                ValueType targetValueType, StreamInput in) {
            return new MaxAggregator.MaxAggregatorBuilder(name);
        }

        @Override
        protected void innerWriteTo(StreamOutput out) {
            // Do nothing, no extra state to write to stream
        }

        @Override
        public XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
            return builder;
        }

        @Override
        protected int innerHashCode() {
            return 0;
        }

        @Override
        protected boolean innerEquals(Object obj) {
            return true;
        }

    }

    @Override
    public void doClose() {
        Releasables.close(maxes);
    }
}
