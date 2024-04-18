package com.redis.spring.batch.gen;

import org.springframework.batch.item.ItemProcessor;

import com.redis.spring.batch.KeyValue;
import com.redis.spring.batch.KeyValue.Type;

public class GeneratorItemProcessor implements ItemProcessor<Item, KeyValue<String, Object>> {

	@Override
	public KeyValue<String, Object> process(Item item) throws Exception {
		KeyValue<String, Object> kv = new KeyValue<>();
		kv.setKey(item.getKey());
		kv.setTtl(item.getTtl());
		kv.setType(dataType(item));
		kv.setValue(item.getValue());
		return kv;
	}

	private Type dataType(Item item) {
		return Type.valueOf(item.getType().name());
	}

}
