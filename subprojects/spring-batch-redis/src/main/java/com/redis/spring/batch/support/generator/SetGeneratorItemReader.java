package com.redis.spring.batch.support.generator;

import java.util.HashSet;
import java.util.Set;

import com.redis.spring.batch.support.DataStructure.Type;

public class SetGeneratorItemReader extends CollectionGeneratorItemReader<Set<String>> {

	public SetGeneratorItemReader() {
		super(Type.SET);
	}

	@Override
	protected Set<String> value() {
		return new HashSet<>(members());
	}

}