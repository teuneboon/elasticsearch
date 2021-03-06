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

package org.elasticsearch.search.aggregations.pipeline.derivative;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.rounding.DateTimeUnit;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.AggregationExecutionException;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.InternalAggregation.ReduceContext;
import org.elasticsearch.search.aggregations.InternalAggregation.Type;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.AbstractHistogramAggregatorFactory;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregatorFactory;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.InternalHistogram;
import org.elasticsearch.search.aggregations.pipeline.BucketHelpers.GapPolicy;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilder;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorStreams;
import org.elasticsearch.search.aggregations.support.format.ValueFormat;
import org.elasticsearch.search.aggregations.support.format.ValueFormatter;
import org.elasticsearch.search.aggregations.support.format.ValueFormatterStreams;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.search.aggregations.pipeline.BucketHelpers.resolveBucketValue;

public class DerivativePipelineAggregator extends PipelineAggregator {

    public final static Type TYPE = new Type("derivative");

    public final static PipelineAggregatorStreams.Stream STREAM = new PipelineAggregatorStreams.Stream() {
        @Override
        public DerivativePipelineAggregator readResult(StreamInput in) throws IOException {
            DerivativePipelineAggregator result = new DerivativePipelineAggregator();
            result.readFrom(in);
            return result;
        }
    };

    public static void registerStreams() {
        PipelineAggregatorStreams.registerStream(STREAM, TYPE.stream());
    }

    private ValueFormatter formatter;
    private GapPolicy gapPolicy;
    private Double xAxisUnits;

    public DerivativePipelineAggregator() {
    }

    public DerivativePipelineAggregator(String name, String[] bucketsPaths, ValueFormatter formatter, GapPolicy gapPolicy, Long xAxisUnits,
            Map<String, Object> metadata) {
        super(name, bucketsPaths, metadata);
        this.formatter = formatter;
        this.gapPolicy = gapPolicy;
        this.xAxisUnits = xAxisUnits == null ? null : (double) xAxisUnits;
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public InternalAggregation reduce(InternalAggregation aggregation, ReduceContext reduceContext) {
        InternalHistogram histo = (InternalHistogram) aggregation;
        List<? extends InternalHistogram.Bucket> buckets = histo.getBuckets();
        InternalHistogram.Factory<? extends InternalHistogram.Bucket> factory = histo.getFactory();

        List newBuckets = new ArrayList<>();
        Long lastBucketKey = null;
        Double lastBucketValue = null;
        for (InternalHistogram.Bucket bucket : buckets) {
            Long thisBucketKey = resolveBucketKeyAsLong(bucket);
            Double thisBucketValue = resolveBucketValue(histo, bucket, bucketsPaths()[0], gapPolicy);
            if (lastBucketValue != null) {
                double gradient = thisBucketValue - lastBucketValue;
                double xDiff = -1;
                if (xAxisUnits != null) {
                    xDiff = (thisBucketKey - lastBucketKey) / xAxisUnits;
                }
                final List<InternalAggregation> aggs = StreamSupport.stream(bucket.getAggregations().spliterator(), false).map((p) -> {
                    return (InternalAggregation) p;
                }).collect(Collectors.toList());
                aggs.add(new InternalDerivative(name(), gradient, xDiff, formatter, new ArrayList<PipelineAggregator>(), metaData()));
                InternalHistogram.Bucket newBucket = factory.createBucket(bucket.getKey(), bucket.getDocCount(), new InternalAggregations(
                        aggs), bucket.getKeyed(), bucket.getFormatter());
                newBuckets.add(newBucket);
            } else {
                newBuckets.add(bucket);
            }
            lastBucketKey = thisBucketKey;
            lastBucketValue = thisBucketValue;
        }
        return factory.create(newBuckets, histo);
    }

    private Long resolveBucketKeyAsLong(InternalHistogram.Bucket bucket) {
        Object key = bucket.getKey();
        if (key instanceof DateTime) {
            return ((DateTime) key).getMillis();
        } else if (key instanceof Number) {
            return ((Number) key).longValue();
        } else {
            throw new AggregationExecutionException("InternalBucket keys must be either a Number or a DateTime for aggregation " + name()
                    + ". Found bucket with key " + key);
        }
    }

    @Override
    public void doReadFrom(StreamInput in) throws IOException {
        formatter = ValueFormatterStreams.readOptional(in);
        gapPolicy = GapPolicy.readFrom(in);
        if (in.readBoolean()) {
            xAxisUnits = in.readDouble();
        } else {
            xAxisUnits = null;

        }
    }

    @Override
    public void doWriteTo(StreamOutput out) throws IOException {
        ValueFormatterStreams.writeOptional(formatter, out);
        gapPolicy.writeTo(out);
        boolean hasXAxisUnitsValue = xAxisUnits != null;
        out.writeBoolean(hasXAxisUnitsValue);
        if (hasXAxisUnitsValue) {
            out.writeDouble(xAxisUnits);
        }
    }

    public static class DerivativePipelineAggregatorBuilder extends PipelineAggregatorBuilder<DerivativePipelineAggregatorBuilder> {

        static final DerivativePipelineAggregatorBuilder PROTOTYPE = new DerivativePipelineAggregatorBuilder("", "");

        private String format;
        private GapPolicy gapPolicy = GapPolicy.SKIP;
        private String units;

        public DerivativePipelineAggregatorBuilder(String name, String bucketsPath) {
            this(name, new String[] { bucketsPath });
        }

        private DerivativePipelineAggregatorBuilder(String name, String[] bucketsPaths) {
            super(name, TYPE.name(), bucketsPaths);
        }

        public DerivativePipelineAggregatorBuilder format(String format) {
            if (format == null) {
                throw new IllegalArgumentException("[format] must not be null: [" + name + "]");
            }
            this.format = format;
            return this;
        }

        public String format() {
            return format;
        }

        public DerivativePipelineAggregatorBuilder gapPolicy(GapPolicy gapPolicy) {
            if (gapPolicy == null) {
                throw new IllegalArgumentException("[gapPolicy] must not be null: [" + name + "]");
            }
            this.gapPolicy = gapPolicy;
            return this;
        }

        public GapPolicy gapPolicy() {
            return gapPolicy;
        }

        public DerivativePipelineAggregatorBuilder unit(String units) {
            if (units == null) {
                throw new IllegalArgumentException("[units] must not be null: [" + name + "]");
            }
            this.units = units;
            return this;
        }

        public DerivativePipelineAggregatorBuilder unit(DateHistogramInterval units) {
            if (units == null) {
                throw new IllegalArgumentException("[units] must not be null: [" + name + "]");
            }
            this.units = units.toString();
            return this;
        }

        public String unit() {
            return units;
        }

        @Override
        protected PipelineAggregator createInternal(Map<String, Object> metaData) throws IOException {
            ValueFormatter formatter;
            if (format != null) {
                formatter = ValueFormat.Patternable.Number.format(format).formatter();
            } else {
                formatter = ValueFormatter.RAW;
            }
            Long xAxisUnits = null;
            if (units != null) {
                DateTimeUnit dateTimeUnit = DateHistogramAggregatorFactory.DATE_FIELD_UNITS.get(units);
                if (dateTimeUnit != null) {
                    xAxisUnits = dateTimeUnit.field().getDurationField().getUnitMillis();
                } else {
                    TimeValue timeValue = TimeValue.parseTimeValue(units, null, getClass().getSimpleName() + ".unit");
                    if (timeValue != null) {
                        xAxisUnits = timeValue.getMillis();
                    }
                }
            }
            return new DerivativePipelineAggregator(name, bucketsPaths, formatter, gapPolicy, xAxisUnits, metaData);
        }

        @Override
        public void doValidate(AggregatorFactory<?> parent, AggregatorFactory<?>[] aggFactories,
                List<PipelineAggregatorBuilder<?>> pipelineAggregatoractories) {
            if (bucketsPaths.length != 1) {
                throw new IllegalStateException(PipelineAggregator.Parser.BUCKETS_PATH.getPreferredName()
                        + " must contain a single entry for aggregation [" + name + "]");
            }
            if (!(parent instanceof AbstractHistogramAggregatorFactory<?>)) {
                throw new IllegalStateException("derivative aggregation [" + name
                        + "] must have a histogram or date_histogram as parent");
            } else {
                AbstractHistogramAggregatorFactory<?> histoParent = (AbstractHistogramAggregatorFactory<?>) parent;
                if (histoParent.minDocCount() != 0) {
                    throw new IllegalStateException("parent histogram of derivative aggregation [" + name
                            + "] must have min_doc_count of 0");
                }
            }
        }

        @Override
        protected DerivativePipelineAggregatorBuilder doReadFrom(String name, String[] bucketsPaths, StreamInput in) throws IOException {
            DerivativePipelineAggregatorBuilder factory = new DerivativePipelineAggregatorBuilder(name, bucketsPaths);
            factory.format = in.readOptionalString();
            if (in.readBoolean()) {
                factory.gapPolicy = GapPolicy.readFrom(in);
            }
            factory.units = in.readOptionalString();
            return factory;
        }

        @Override
        protected void doWriteTo(StreamOutput out) throws IOException {
            out.writeOptionalString(format);
            boolean hasGapPolicy = gapPolicy != null;
            out.writeBoolean(hasGapPolicy);
            if (hasGapPolicy) {
                gapPolicy.writeTo(out);
            }
            out.writeOptionalString(units);
        }

        @Override
        protected XContentBuilder internalXContent(XContentBuilder builder, Params params) throws IOException {
            if (format != null) {
                builder.field(DerivativeParser.FORMAT.getPreferredName(), format);
            }
            if (gapPolicy != null) {
                builder.field(DerivativeParser.GAP_POLICY.getPreferredName(), gapPolicy.getName());
            }
            if (units != null) {
                builder.field(DerivativeParser.UNIT.getPreferredName(), units);
            }
            return builder;
        }

        @Override
        protected boolean doEquals(Object obj) {
            DerivativePipelineAggregatorBuilder other = (DerivativePipelineAggregatorBuilder) obj;
            if (!Objects.equals(format, other.format)) {
                return false;
            }
            if (!Objects.equals(gapPolicy, other.gapPolicy)) {
                return false;
            }
            if (!Objects.equals(units, other.units)) {
                return false;
            }
            return true;
        }

        @Override
        protected int doHashCode() {
            return Objects.hash(format, gapPolicy, units);
        }

    }
}
