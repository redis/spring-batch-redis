package org.springframework.batch.item.redis.support.operation;

import java.util.function.Predicate;

import org.springframework.core.convert.converter.Converter;
import org.springframework.util.Assert;

import com.redis.lettucemod.api.async.RedisModulesAsyncCommands;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.RestoreArgs;
import io.lettuce.core.api.async.RedisKeyAsyncCommands;

public class Restore<K, V, T> extends AbstractKeyOperation<K, V, T> {

    public static final long TTL_KEY_DOES_NOT_EXIST = -2;

    private final Converter<T, byte[]> value;
    private final Converter<T, Long> absoluteTTL;

    public Restore(Converter<T, K> key, Converter<T, byte[]> value, Converter<T, Long> absoluteTTL) {
        super(key, new InexistentKeyPredicate<>(absoluteTTL));
        Assert.notNull(value, "A value converter is required");
        Assert.notNull(absoluteTTL, "A TTL converter is required");
        this.value = value;
        this.absoluteTTL = absoluteTTL;
    }

    private static class InexistentKeyPredicate<T> implements Predicate<T> {

        private final Converter<T, Long> absoluteTTL;

        private InexistentKeyPredicate(Converter<T, Long> absoluteTTL) {
            this.absoluteTTL = absoluteTTL;
        }

        @Override
        public boolean test(T t) {
            Long ttl = absoluteTTL.convert(t);
            if (ttl == null) {
                return false;
            }
            return ttl == TTL_KEY_DOES_NOT_EXIST;
        }

    }

    @Override
    protected RedisFuture<?> doExecute(RedisModulesAsyncCommands<K, V> commands, T item, K key) {
        return commands.restore(key, value.convert(item), args(item));
    }

    protected RestoreArgs args(T item) {
        Long ttl = this.absoluteTTL.convert(item);
        RestoreArgs args = new RestoreArgs();
        if (ttl != null && ttl > 0) {
            args.absttl();
            args.ttl(ttl);
        }
        return args;
    }

}
