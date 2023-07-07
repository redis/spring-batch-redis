package com.redis.spring.batch.reader;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import org.springframework.batch.core.step.builder.SimpleStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.CompositeItemWriter;

import com.redis.spring.batch.RedisItemReader.BaseBuilder;
import com.redis.spring.batch.common.KeyValue;
import com.redis.spring.batch.common.ValueType;
import com.redis.spring.batch.step.FlushingStepBuilder;
import com.redis.spring.batch.step.FlushingStepOptions;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

public class LiveRedisItemReader<K, V> extends AbstractRedisItemReader<K, V> {

	private final KeyspaceNotificationItemReader<K, V> keyReader;
	private KeyspaceNotificationOptions keyspaceNotificationOptions = KeyspaceNotificationOptions.builder().build();
	private FlushingStepOptions flushingOptions = FlushingStepOptions.builder().build();

	public LiveRedisItemReader(AbstractRedisClient client, RedisCodec<K, V> codec, ValueType valueType) {
		super(client, codec, valueType);
		this.keyReader = new KeyspaceNotificationItemReader<>(client, codec);
	}

	public KeyspaceNotificationOptions getKeyspaceNotificationOptions() {
		return keyspaceNotificationOptions;
	}

	public void setKeyspaceNotificationOptions(KeyspaceNotificationOptions keyspaceNotificationOptions) {
		this.keyspaceNotificationOptions = keyspaceNotificationOptions;
	}

	public FlushingStepOptions getFlushingOptions() {
		return flushingOptions;
	}

	public void setFlushingOptions(FlushingStepOptions options) {
		this.flushingOptions = options;
	}

	public KeyspaceNotificationItemReader<K, V> getKeyReader() {
		return keyReader;
	}

	@Override
	protected KeyspaceNotificationItemReader<K, V> keyReader() {
		keyReader.setOptions(keyspaceNotificationOptions);
		return keyReader;
	}

	@Override
	protected SimpleStepBuilder<K, K> step(StepBuilder stepBuilder) {
		SimpleStepBuilder<K, K> step = super.step(stepBuilder);
		return new FlushingStepBuilder<>(step).options(flushingOptions);
	}

	@Override
	protected ItemWriter<KeyValue<K>> queueWriter(BlockingQueue<KeyValue<K>> queue) {
		ItemWriter<KeyValue<K>> queueWriter = super.queueWriter(queue);
		CompositeItemWriter<KeyValue<K>> compositeWriter = new CompositeItemWriter<>();
		compositeWriter.setDelegates(Arrays.asList(queueWriter, new BigKeyItemWriter()));
		return compositeWriter;
	}

	private class BigKeyItemWriter implements ItemWriter<KeyValue<K>> {

		private final long memLimit = options.getMemoryUsageOptions().getLimit().toBytes();

		@Override
		public void write(List<? extends KeyValue<K>> items) throws Exception {
			List<String> bigKeys = items.stream().filter(v -> v.getMemoryUsage() > memLimit).map(KeyValue::getKey)
					.map(this::toString).collect(Collectors.toList());
			keyReader.blockKeys(bigKeys);
		}

		private String toString(K key) {
			return StringCodec.UTF8.decodeKey(codec.encodeKey(key));
		}

	}

	public static Builder<String, String> client(AbstractRedisClient client) {
		return new Builder<>(client, StringCodec.UTF8);
	}

	public static <K, V> Builder<K, V> client(AbstractRedisClient client, RedisCodec<K, V> codec) {
		return new Builder<>(client, codec);
	}

	public static class Builder<K, V> extends BaseBuilder<K, V, Builder<K, V>> {

		private KeyspaceNotificationOptions keyspaceNotificationOptions = KeyspaceNotificationOptions.builder().build();
		private FlushingStepOptions flushingOptions = FlushingStepOptions.builder().build();

		public Builder(AbstractRedisClient client, RedisCodec<K, V> codec) {
			super(client, codec);
		}

		public Builder<K, V> keyspaceNotificationOptions(KeyspaceNotificationOptions options) {
			this.keyspaceNotificationOptions = options;
			return this;
		}

		public Builder<K, V> flushingOptions(FlushingStepOptions options) {
			this.flushingOptions = options;
			return this;
		}

		public LiveRedisItemReader<K, V> dump() {
			return build(ValueType.DUMP);
		}

		public LiveRedisItemReader<K, V> struct() {
			return build(ValueType.STRUCT);
		}

		public LiveRedisItemReader<K, V> build(ValueType valueType) {
			LiveRedisItemReader<K, V> reader = new LiveRedisItemReader<>(client, codec, valueType);
			configure(reader);
			reader.setFlushingOptions(flushingOptions);
			reader.setKeyspaceNotificationOptions(keyspaceNotificationOptions);
			return reader;
		}

	}

}
