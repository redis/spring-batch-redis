package com.redis.spring.batch.support.operation.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.api.async.BaseRedisAsyncCommands;

public class WaitForReplicationOperationExecutor<K, V, T> implements OperationExecutor<K, V, T> {

	private final OperationExecutor<K, V, T> delegate;
	private final int replicas;
	private final long timeout;

	public WaitForReplicationOperationExecutor(OperationExecutor<K, V, T> delegate, int replicas, long timeout) {
		this.delegate = delegate;
		this.replicas = replicas;
		this.timeout = timeout;
	}

	@Override
	public List<Future<?>> execute(BaseRedisAsyncCommands<K, V> commands, List<? extends T> items) {
		List<Future<?>> futures = new ArrayList<>();
		futures.addAll(delegate.execute(commands, items));
		futures.add(commands.waitForReplication(replicas, this.timeout).toCompletableFuture().thenAccept(r -> {
			if (r < replicas) {
				throw new RedisCommandExecutionException(
						String.format("Insufficient replication level - expected: %s, actual: %s", replicas, r));
			}
		}));
		return futures;
	}

}