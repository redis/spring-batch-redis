package org.springframework.batch.item.redis;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.batch.item.redis.support.AbstractRedisItemWriter;
import org.springframework.batch.item.redis.support.ClientBuilder;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.async.BaseRedisAsyncCommands;
import lombok.Setter;
import lombok.experimental.Accessors;

public class NoOpItemWriter<T> extends AbstractRedisItemWriter<T> {

	public NoOpItemWriter(AbstractRedisClient client,
			GenericObjectPoolConfig<StatefulConnection<String, String>> poolConfig) {
		super(client, poolConfig);
	}

	@Override
	protected RedisFuture<?> write(BaseRedisAsyncCommands<String, String> commands, T item) {
		return null;
	}

	public static <T> NoOpItemWriterBuilder<T> builder() {
		return new NoOpItemWriterBuilder<>();
	}

	@Setter
	@Accessors(fluent = true)
	public static class NoOpItemWriterBuilder<T> extends ClientBuilder<NoOpItemWriterBuilder<T>> {

		public NoOpItemWriter<T> build() {
			return new NoOpItemWriter<>(client, poolConfig);
		}

	}

}