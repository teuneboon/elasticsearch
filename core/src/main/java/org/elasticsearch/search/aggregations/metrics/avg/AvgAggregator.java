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
package org.elasticsearch.search.aggregations.metrics.avg;

import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.DoubleArray;
import org.elasticsearch.common.util.LongArray;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.fielddata.SortedNumericDoubleValues;
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
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregatorBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;
import org.elasticsearch.search.aggregations.support.ValuesSource.Numeric;
import org.elasticsearch.search.aggregations.support.format.ValueFormatter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class AvgAggregator extends NumericMetricsAggregator.SingleValue {

    final ValuesSource.Numeric valuesSource;

    LongArray counts;
    DoubleArray sums;
    ValueFormatter formatter;

    public AvgAggregator(String name, ValuesSource.Numeric valuesSource, ValueFormatter formatter, AggregationContext context,
            Aggregator parent, List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData) throws IOException {
        super(name, context, parent, pipelineAggregators, metaData);
        this.valuesSource = valuesSource;
        this.formatter = formatter;
        if (valuesSource != null) {
            final BigArrays bigArrays = context.bigArrays();
            counts = bigArrays.newLongArray(1, true);
            sums = bigArrays.newDoubleArray(1, true);
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
        final SortedNumericDoubleValues values = valuesSource.doubleValues(ctx);
        return new LeafBucketCollectorBase(sub, values) {
            @Override
            public void collect(int doc, long bucket) throws IOException {
                counts = bigArrays.grow(counts, bucket + 1);
                sums = bigArrays.grow(sums, bucket + 1);

                values.setDocument(doc);
                final int valueCount = values.count();
                counts.increment(bucket, valueCount);
                double sum = 0;
                for (int i = 0; i < valueCount; i++) {
                    sum += values.valueAt(i);
                }
                sums.increment(bucket, sum);
            }
        };
    }

    @Override
    public double metric(long owningBucketOrd) {
        return valuesSource == null ? Double.NaN : sums.get(owningBucketOrd) / counts.get(owningBucketOrd);
    }

    @Override
    public InternalAggregation buildAggregation(long bucket) {
        if (valuesSource == null || bucket >= sums.size()) {
            return buildEmptyAggregation();
        }
        return new InternalAvg(name, sums.get(bucket), counts.get(bucket), formatter, pipelineAggregators(), metaData());
    }

    @Override
    public InternalAggregation buildEmptyAggregation() {
        return new InternalAvg(name, 0.0, 0L, formatter, pipelineAggregators(), metaData());
    }

    public static class AvgAggregatorBuilder extends ValuesSourceAggregatorBuilder.LeafOnly<ValuesSource.Numeric, AvgAggregatorBuilder> {

        static final AvgAggregatorBuilder PROTOTYPE = new AvgAggregatorBuilder("");

        public AvgAggregatorBuilder(String name) {
            super(name, InternalAvg.TYPE, ValuesSourceType.NUMERIC, ValueType.NUMERIC);
        }

        @Override
        protected AvgAggregatorFactory innerBuild(AggregationContext context, ValuesSourceConfig<Numeric> config,
                AggregatorFactory<?> parent, Builder subFactoriesBuilder) throws IOException {
            return new AvgAggregatorFactory(name, type, config, context, parent, subFactoriesBuilder, metaData);
        }

        @Override
        protected AvgAggregatorBuilder innerReadFrom(String name, ValuesSourceType valuesSourceType,
                ValueType targetValueType, StreamInput in) {
            return new AvgAggregator.AvgAggregatorBuilder(name);
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
        Releasables.close(counts, sums);
    }

}
