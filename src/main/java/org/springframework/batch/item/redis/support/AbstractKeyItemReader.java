package org.springframework.batch.item.redis.support;

import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.sync.BaseRedisCommands;
import io.lettuce.core.api.sync.RedisKeyCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.Iterator;
import java.util.function.Function;

@Slf4j
public abstract class AbstractKeyItemReader<K, V, C extends StatefulConnection<K, V>> extends AbstractItemCountingItemStreamItemReader<K> {

    private final C connection;
    private final Function<C, BaseRedisCommands<K, V>> commands;
    private final ScanArgs scanArgs;

    private Iterator<K> keyIterator;
    private KeyScanCursor<K> cursor;

    protected AbstractKeyItemReader(C connection, Function<C, BaseRedisCommands<K, V>> commands, ScanArgs scanArgs) {
        setName(ClassUtils.getShortName(getClass()));
        Assert.notNull(connection, "A connection is required.");
        Assert.notNull(commands, "A commands supplier is required.");
        this.connection = connection;
        this.commands = commands;
        this.scanArgs = scanArgs == null ? new ScanArgs() : scanArgs;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected synchronized void doOpen() {
        if (cursor != null) {
            return;
        }
        cursor = ((RedisKeyCommands<K, V>) commands.apply(connection)).scan(scanArgs);
        keyIterator = cursor.getKeys().iterator();
    }

    @Override
    protected synchronized void doClose() {
        if (cursor == null) {
            return;
        }
        cursor = null;
        keyIterator = null;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected synchronized K doRead() throws Exception {
        while (!(keyIterator.hasNext() || cursor.isFinished())) {
            cursor = ((RedisKeyCommands<K, V>) commands.apply(connection)).scan(cursor, scanArgs);
            keyIterator = cursor.getKeys().iterator();
        }
        if (keyIterator.hasNext()) {
            return keyIterator.next();
        }
        if (cursor.isFinished()) {
            return null;
        }
        throw new IllegalStateException("No more keys but cursor not finished");
    }

}
