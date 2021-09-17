package org.springframework.batch.item.redis.support;

import com.redis.lettucemod.api.sync.RedisModulesCommands;
import io.lettuce.core.KeyScanArgs;
import io.lettuce.core.ScanIterator;
import io.lettuce.core.api.StatefulConnection;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.support.AbstractItemStreamItemReader;

import java.util.function.Function;
import java.util.function.Supplier;

public class ScanKeyItemReader extends AbstractItemStreamItemReader<String> {

    private final Supplier<StatefulConnection<String, String>> connectionSupplier;
    private final Function<StatefulConnection<String, String>, RedisModulesCommands<String, String>> sync;
    private final String match;
    private final long count;
    private final String type;

    private StatefulConnection<String, String> connection;
    private ScanIterator<String> scanIterator;

    public ScanKeyItemReader(Supplier<StatefulConnection<String, String>> connectionSupplier, Function<StatefulConnection<String, String>, RedisModulesCommands<String, String>> sync, String match, long count, String type) {
        this.connectionSupplier = connectionSupplier;
        this.sync = sync;
        this.match = match;
        this.count = count;
        this.type = type;
    }

    @Override
    public synchronized void open(ExecutionContext executionContext) {
        super.open(executionContext);
        if (connection == null) {
            connection = connectionSupplier.get();
        }
        if (scanIterator == null) {
            KeyScanArgs args = KeyScanArgs.Builder.limit(count);
            if (match != null) {
                args.match(match);
            }
            if (type != null) {
                args.type(type);
            }
            scanIterator = ScanIterator.scan(sync.apply(connection), args);
        }
    }

    @Override
    public synchronized String read() {
        if (scanIterator.hasNext()) {
            return scanIterator.next();
        }
        return null;
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            connection.close();
        }
        super.close();
    }

}
