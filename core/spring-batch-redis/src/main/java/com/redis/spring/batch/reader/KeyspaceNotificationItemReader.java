package com.redis.spring.batch.reader;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.support.AbstractItemStreamItemReader;
import org.springframework.util.ClassUtils;

import com.redis.spring.batch.RedisItemReader;
import com.redis.spring.batch.common.DataType;
import com.redis.spring.batch.common.SetBlockingQueue;
import com.redis.spring.batch.util.CodecUtils;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.RedisCodec;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

public class KeyspaceNotificationItemReader<K> extends AbstractItemStreamItemReader<K>
		implements KeyItemReader<K>, PollableItemReader<K> {

	public enum OrderingStrategy {
		FIFO, PRIORITY
	}

	public static final String MATCH_ALL = "*";

	public static final String PUBSUB_PATTERN_FORMAT = "__keyspace@%s__:%s";

	public static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMillis(100);

	public static final OrderingStrategy DEFAULT_ORDERING = OrderingStrategy.PRIORITY;

	public static final String QUEUE_METER = "redis.batch.notification.queue.size";

	public static final String QUEUE_MISS_COUNTER = "redis.batch.notification.queue.misses";

	private static final KeyspaceNotificationComparator NOTIFICATION_COMPARATOR = new KeyspaceNotificationComparator();

	private final Log log = LogFactory.getLog(getClass());

	private final AbstractRedisClient client;

	private final Function<String, K> stringKeyEncoder;

	private int database;

	private String keyPattern;

	private OrderingStrategy orderingStrategy = DEFAULT_ORDERING;

	private DataType keyType;

	private int queueCapacity = RedisItemReader.DEFAULT_NOTIFICATION_QUEUE_CAPACITY;

	private Duration pollTimeout = DEFAULT_POLL_TIMEOUT;

	private BlockingQueue<KeyspaceNotification> queue;

	private Counter queueMissCounter;

	private KeyspaceNotificationPublisher notificationPublisher;

	private String name;

	public KeyspaceNotificationItemReader(AbstractRedisClient client, RedisCodec<K, ?> codec) {
		setName(ClassUtils.getShortName(getClass()));
		this.client = client;
		this.stringKeyEncoder = CodecUtils.stringKeyFunction(codec);
	}

	@Override
	public void setName(String name) {
		super.setName(name);
		this.name = name;
	}

	public BlockingQueue<KeyspaceNotification> getQueue() {
		return queue;
	}

	public void setDatabase(int database) {
		this.database = database;
	}

	public void setKeyPattern(String keyPattern) {
		this.keyPattern = keyPattern;
	}

	public void setPollTimeout(Duration pollTimeout) {
		this.pollTimeout = pollTimeout;
	}

	public void setQueueCapacity(int queueCapacity) {
		this.queueCapacity = queueCapacity;
	}

	public void setKeyType(DataType keyType) {
		this.keyType = keyType;
	}

	public void setOrderingStrategy(OrderingStrategy orderingStrategy) {
		this.orderingStrategy = orderingStrategy;
	}

	@Override
	public synchronized void open(ExecutionContext executionContext) throws ItemStreamException {
		if (notificationPublisher == null) {
			log.debug(String.format("Opening %s", name));
			queue = new SetBlockingQueue<>(notificationQueue(), queueCapacity);
			Metrics.globalRegistry.gaugeCollectionSize(QUEUE_METER, Collections.emptyList(), queue);
			queueMissCounter = Metrics.globalRegistry.counter(QUEUE_MISS_COUNTER);
			notificationPublisher = publisher();
			log.debug(String.format("Opened %s", name));
		}
	}

	private KeyspaceNotificationPublisher publisher() {
		String pattern = String.format(PUBSUB_PATTERN_FORMAT, database, keyPattern());
		KeyspaceNotificationPublisher publisher = publisher(pattern);
		publisher.addConsumer(this::acceptKeyspaceNotification);
		publisher.open();
		return publisher;
	}

	private void acceptKeyspaceNotification(KeyspaceNotification notification) {
		if ((keyType == null || notification.getEvent().getType() == keyType) && queue.remainingCapacity() > 0) {
			boolean added = queue.offer(notification);
			if (!added) {
				queueMissCounter.increment();
			}
		}
	}

	private AbstractKeyspaceNotificationPublisher publisher(String pattern) {
		if (client instanceof RedisClusterClient) {
			return new RedisClusterKeyspaceNotificationPublisher((RedisClusterClient) client, pattern);
		}
		return new RedisKeyspaceNotificationPublisher((RedisClient) client, pattern);
	}

	private String keyPattern() {
		if (keyPattern == null) {
			return MATCH_ALL;
		}
		return keyPattern;
	}

	private BlockingQueue<KeyspaceNotification> notificationQueue() {
		if (orderingStrategy == OrderingStrategy.PRIORITY) {
			return new PriorityBlockingQueue<>(queueCapacity, NOTIFICATION_COMPARATOR);
		}
		return new LinkedBlockingQueue<>(queueCapacity);
	}

	@Override
	public boolean isOpen() {
		return notificationPublisher != null;
	}

	@Override
	public synchronized void close() {
		if (notificationPublisher != null) {
			log.debug(String.format("Closing %s", name));
			notificationPublisher.close();
			notificationPublisher = null;
			log.debug(String.format("Closed %s", name));
		}
	}

	@Override
	public K read() throws InterruptedException {
		return poll(pollTimeout.toMillis(), TimeUnit.MILLISECONDS);
	}

	@Override
	public K poll(long timeout, TimeUnit unit) throws InterruptedException {
		KeyspaceNotification notification = queue.poll(timeout, unit);
		if (notification == null) {
			return null;
		}
		return stringKeyEncoder.apply(notification.getKey());
	}

}
