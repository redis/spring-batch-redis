package com.redis.spring.batch.compare;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.support.AbstractItemStreamItemWriter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.redis.spring.batch.DataStructure;
import com.redis.spring.batch.compare.KeyComparison.Status;
import com.redis.spring.batch.reader.ValueReader;
import com.redis.spring.batch.support.Utils;

public class KeyComparisonItemWriter extends AbstractItemStreamItemWriter<DataStructure<String>> {

	private static final Logger log = LoggerFactory.getLogger(KeyComparisonItemWriter.class);

	private final KeyComparisonResults results = new KeyComparisonResults();
	private final ValueReader<String, DataStructure<String>> valueReader;
	private final long ttlTolerance;
	private List<KeyComparisonListener> listeners = new ArrayList<>();

	public KeyComparisonItemWriter(ValueReader<String, DataStructure<String>> valueReader, Duration ttlTolerance) {
		setName(ClassUtils.getShortName(getClass()));
		Assert.notNull(valueReader, "A value reader is required");
		Utils.assertPositive(ttlTolerance, "TTL tolerance");
		this.valueReader = valueReader;
		this.ttlTolerance = ttlTolerance.toMillis();
	}

	public void addListener(KeyComparisonListener listener) {
		this.listeners.add(listener);
	}

	public void setListeners(List<KeyComparisonListener> listeners) {
		this.listeners = listeners;
	}

	@Override
	public synchronized void open(ExecutionContext executionContext) {
		if (valueReader instanceof ItemStream) {
			((ItemStream) valueReader).open(executionContext);
		}
		super.open(executionContext);
	}

	@Override
	public void update(ExecutionContext executionContext) {
		if (valueReader instanceof ItemStream) {
			((ItemStream) valueReader).update(executionContext);
		}
		super.update(executionContext);
	}

	@Override
	public void close() {
		super.close();
		if (valueReader instanceof ItemStream) {
			((ItemStream) valueReader).close();
		}
	}

	@Override
	public void write(List<? extends DataStructure<String>> sourceItems) throws Exception {
		List<DataStructure<String>> targetItems = valueReader
				.read(sourceItems.stream().map(DataStructure::getKey).collect(Collectors.toList()));
		if (targetItems == null || targetItems.size() != sourceItems.size()) {
			log.warn("Missing values in value reader response");
			return;
		}
		results.addAndGetSource(sourceItems.size());
		for (int index = 0; index < sourceItems.size(); index++) {
			DataStructure<String> source = sourceItems.get(index);
			DataStructure<String> target = targetItems.get(index);
			Status status = compare(source, target);
			increment(status);
			KeyComparison comparison = new KeyComparison(source, target, status);
			listeners.forEach(c -> c.keyComparison(comparison));
		}
	}

	private long increment(Status status) {
		switch (status) {
		case OK:
			return results.incrementOK();
		case MISSING:
			return results.incrementMissing();
		case TTL:
			return results.incrementTTL();
		case TYPE:
			return results.incrementType();
		case VALUE:
			return results.incrementValue();
		}
		throw new IllegalArgumentException("Unknown status: " + status);
	}

	private Status compare(DataStructure<String> source, DataStructure<String> target) {
		if (source.getValue() == null) {
			if (target.getValue() == null) {
				return Status.OK;
			}
			return Status.VALUE;
		}
		if (target.getValue() == null) {
			return Status.MISSING;
		}
		if (source.getType() != target.getType()) {
			return Status.TYPE;
		}
		if (Objects.deepEquals(source.getValue(), target.getValue())) {
			if (source.hasTTL()) {
				if (target.hasTTL() && Math.abs(source.getAbsoluteTTL() - target.getAbsoluteTTL()) <= ttlTolerance) {
					return Status.OK;
				}
				return Status.TTL;
			}
			if (target.hasTTL()) {
				return Status.TTL;
			}
			return Status.OK;
		}
		return Status.VALUE;
	}

	public KeyComparisonResults getResults() {
		return results;
	}

	public static KeyComparisonItemWriterBuilder valueReader(ValueReader<String, DataStructure<String>> valueReader) {
		return new KeyComparisonItemWriterBuilder(valueReader);
	}

	public static class KeyComparisonItemWriterBuilder {

		private static final Duration DEFAULT_TTL_TOLERANCE = Duration.ofMillis(100);

		private final ValueReader<String, DataStructure<String>> valueReader;
		private Duration ttlTolerance = DEFAULT_TTL_TOLERANCE;

		public KeyComparisonItemWriterBuilder(ValueReader<String, DataStructure<String>> valueReader) {
			this.valueReader = valueReader;
		}

		public KeyComparisonItemWriterBuilder tolerance(Duration ttlTolerance) {
			this.ttlTolerance = ttlTolerance;
			return this;
		}

		public KeyComparisonItemWriter build() {
			return new KeyComparisonItemWriter(valueReader, ttlTolerance);
		}
	}

}