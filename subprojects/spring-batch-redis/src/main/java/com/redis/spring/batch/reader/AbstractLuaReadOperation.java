package com.redis.spring.batch.reader;

import java.util.List;
import java.util.concurrent.Future;

import com.redis.spring.batch.common.Operation;
import com.redis.spring.batch.common.Utils;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.async.BaseRedisAsyncCommands;
import io.lettuce.core.api.async.RedisScriptingAsyncCommands;

public abstract class AbstractLuaReadOperation<K, V, T> implements Operation<K, V, K, T> {

	private final String digest;

	protected AbstractLuaReadOperation(AbstractRedisClient client, String filename) {
		this.digest = Utils.loadScript(Utils.connectionSupplier(client), filename);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Future<T> execute(BaseRedisAsyncCommands<K, V> commands, K key) {
		RedisFuture<List<Object>> result = ((RedisScriptingAsyncCommands<K, V>) commands).evalsha(digest,
				ScriptOutputType.MULTI, key);
		return result.thenApply(this::convert).toCompletableFuture();
	}

	protected abstract T convert(List<Object> list);

}
