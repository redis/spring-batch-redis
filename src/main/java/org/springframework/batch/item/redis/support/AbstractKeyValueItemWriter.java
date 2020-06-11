package org.springframework.batch.item.redis.support;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.async.BaseRedisAsyncCommands;
import io.lettuce.core.api.async.RedisKeyAsyncCommands;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

public abstract class AbstractKeyValueItemWriter<K, V, T extends AbstractKeyValue<K, ?>> extends AbstractRedisItemWriter<K, V, T> {

    protected AbstractKeyValueItemWriter(GenericObjectPool<? extends StatefulConnection<K, V>> pool, Function<StatefulConnection<K, V>, BaseRedisAsyncCommands<K, V>> commands, Duration commandTimeout) {
        super(pool, commands, commandTimeout);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void write(BaseRedisAsyncCommands<K, V> commands, List<RedisFuture<?>> futures, T item) {
        if (item.getValue() == null || item.getTtl() == AbstractKeyValue.TTL_NOT_EXISTS) {
            futures.add(((RedisKeyAsyncCommands<K, V>) commands).del(item.getKey()));
        } else {
            if (item.getTtl() >= 0) {
                doWrite(commands, futures, item, item.getTtl());
            } else {
                doWrite(commands, futures, item);
            }
        }
    }

    protected abstract void doWrite(BaseRedisAsyncCommands<K, V> commands, List<RedisFuture<?>> futures, T item);

    protected abstract void doWrite(BaseRedisAsyncCommands<K, V> commands, List<RedisFuture<?>> futures, T item, long ttl);

}
