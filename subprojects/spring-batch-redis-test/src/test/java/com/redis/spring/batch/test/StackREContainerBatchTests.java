package com.redis.spring.batch.test;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.redis.enterprise.testcontainers.RedisEnterpriseContainer;
import com.redis.testcontainers.RedisStackContainer;

@EnabledOnOs(OS.LINUX)
class StackREContainerBatchTests extends BatchTests {

	private static final RedisStackContainer source = RedisContainerFactory.stack();

	private static final RedisEnterpriseContainer target = RedisContainerFactory.enterprise();

	@Override
	protected RedisStackContainer getRedisServer() {
		return source;
	}

	@Override
	protected RedisEnterpriseContainer getTargetRedisServer() {
		return target;
	}

}
