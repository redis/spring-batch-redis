package com.redis.spring.batch.writer;

import java.util.function.Predicate;

import org.springframework.core.convert.converter.Converter;
import org.springframework.util.Assert;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.BaseRedisAsyncCommands;

public interface RedisOperation<K, V, T> {

	RedisFuture<?> execute(BaseRedisAsyncCommands<K, V> commands, T item);

	interface RedisOperationBuilder<K, V, T> {

		RedisOperation<K, V, T> build();

	}

	class NullValuePredicate<T> implements Predicate<T> {

		private final Converter<T, ?> value;

		public NullValuePredicate(Converter<T, ?> value) {
			Assert.notNull(value, "A value converter is required");
			this.value = value;
		}

		@Override
		public boolean test(T t) {
			return value.convert(t) == null;
		}

	}

	abstract class DelBuilder<K, V, T, B extends DelBuilder<K, V, T, B>> implements RedisOperationBuilder<K, V, T> {

		protected Predicate<T> del;

		protected DelBuilder(Converter<T, ?> value) {
			this.del = new NullValuePredicate<>(value);
		}

		@SuppressWarnings("unchecked")
		public B del(Predicate<T> del) {
			this.del = del;
			return (B) this;
		}

	}

	abstract class RemoveBuilder<K, V, T, B extends RemoveBuilder<K, V, T, B>> extends DelBuilder<K, V, T, B> {

		protected Predicate<T> remove = t -> false;

		protected RemoveBuilder(Converter<T, ?> value) {
			super(value);
		}

		@SuppressWarnings("unchecked")
		public B remove(Predicate<T> remove) {
			this.remove = remove;
			return (B) this;
		}

	}

}