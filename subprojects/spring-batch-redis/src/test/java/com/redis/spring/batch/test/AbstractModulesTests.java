package com.redis.spring.batch.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.batch.item.support.IteratorItemReader;
import org.springframework.batch.item.support.ListItemReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.lettucemod.Beers;
import com.redis.lettucemod.search.IndexInfo;
import com.redis.lettucemod.search.Suggestion;
import com.redis.lettucemod.timeseries.AddOptions;
import com.redis.lettucemod.timeseries.DuplicatePolicy;
import com.redis.lettucemod.timeseries.RangeOptions;
import com.redis.lettucemod.timeseries.Sample;
import com.redis.lettucemod.timeseries.TimeRange;
import com.redis.lettucemod.util.RedisModulesUtils;
import com.redis.spring.batch.common.DataType;
import com.redis.spring.batch.common.KeyComparison.Status;
import com.redis.spring.batch.common.KeyValue;
import com.redis.spring.batch.common.ToSampleFunction;
import com.redis.spring.batch.common.ToSuggestionFunction;
import com.redis.spring.batch.common.ValueReader;
import com.redis.spring.batch.gen.GeneratorItemReader;
import com.redis.spring.batch.gen.TimeSeriesOptions;
import com.redis.spring.batch.util.CodecUtils;
import com.redis.spring.batch.writer.OperationItemWriter;
import com.redis.spring.batch.writer.operation.JsonDel;
import com.redis.spring.batch.writer.operation.JsonSet;
import com.redis.spring.batch.writer.operation.Sugadd;
import com.redis.spring.batch.writer.operation.TsAdd;
import com.redis.spring.batch.writer.operation.TsAddAll;

import io.lettuce.core.codec.ByteArrayCodec;

abstract class AbstractModulesTests extends AbstractLiveTests {

	private static final String JSON_BEER_1 = "[{\"id\":\"1\",\"brewery_id\":\"812\",\"name\":\"Hocus Pocus\",\"abv\":\"4.5\",\"ibu\":\"0\",\"srm\":\"0\",\"upc\":\"0\",\"filepath\":\"\",\"descript\":\"Our take on a classic summer ale.  A toast to weeds, rays, and summer haze.  A light, crisp ale for mowing lawns, hitting lazy fly balls, and communing with nature, Hocus Pocus is offered up as a summer sacrifice to clodless days.\\n\\nIts malty sweetness finishes tart and crisp and is best apprediated with a wedge of orange.\",\"add_user\":\"0\",\"last_mod\":\"2010-07-22 20:00:20 UTC\",\"style_name\":\"Light American Wheat Ale or Lager\",\"cat_name\":\"Other Style\"}]";

	private static final int BEER_COUNT = 1019;

	@Test
	void beerIndex() throws Exception {
		Beers.populateIndex(connection);
		IndexInfo indexInfo = RedisModulesUtils.indexInfo(commands.ftInfo(Beers.INDEX));
		Assertions.assertEquals(BEER_COUNT, indexInfo.getNumDocs());
	}

	@Test
	void compareTimeseries(TestInfo info) throws Exception {
		int count = 123;
		for (int index = 0; index < count; index++) {
			commands.tsAdd("ts:" + index, Sample.of(123));
		}
		KeyspaceComparison comparisons = compare(info);
		Assertions.assertEquals(count, comparisons.get(Status.MISSING).size());
	}

	@Test
	void readTimeseries() throws Exception {
		String key = "myts";
		Sample[] samples = { Sample.of(System.currentTimeMillis(), 1.1),
				Sample.of(System.currentTimeMillis() + 10, 2.2) };
		for (Sample sample : samples) {
			commands.tsAdd(key, sample);
		}
		ValueReader<String, String, String, KeyValue<String>> executor = structValueReader();
		KeyValue<String> ds = executor.execute(key);
		Assertions.assertEquals(key, ds.getKey());
		Assertions.assertEquals(DataType.TIMESERIES, ds.getType());
		Assertions.assertEquals(Arrays.asList(samples), ds.getValue());
		executor.close();
	}

	@Test
	void readTimeseriesByteArray() throws Exception {
		String key = "myts";
		Sample[] samples = { Sample.of(System.currentTimeMillis(), 1.1),
				Sample.of(System.currentTimeMillis() + 10, 2.2) };
		for (Sample sample : samples) {
			commands.tsAdd(key, sample);
		}
		ValueReader<byte[], byte[], byte[], KeyValue<byte[]>> executor = structValueReader(ByteArrayCodec.INSTANCE);
		Function<String, byte[]> toByteArrayKeyFunction = CodecUtils.toByteArrayKeyFunction(CodecUtils.STRING_CODEC);
		KeyValue<byte[]> ds = executor.execute(toByteArrayKeyFunction.apply(key));
		Assertions.assertArrayEquals(toByteArrayKeyFunction.apply(key), ds.getKey());
		Assertions.assertEquals(DataType.TIMESERIES, ds.getType());
		Assertions.assertEquals(Arrays.asList(samples), ds.getValue());
		executor.close();
	}

	@Test
	void writeSug(TestInfo info) throws Exception {
		String key = "sugadd";
		List<Suggestion<String>> values = new ArrayList<>();
		for (int index = 0; index < 100; index++) {
			values.add(Suggestion.string("word" + index).score(index + 1).payload("payload" + index).build());
		}
		ListItemReader<Suggestion<String>> reader = new ListItemReader<>(values);
		Sugadd<String, String, Suggestion<String>> sugadd = new Sugadd<>(keyFunction(key),
				new ToSuggestionFunction<>(Suggestion::getString, Suggestion::getScore, Suggestion::getPayload));
		OperationItemWriter<String, String, Suggestion<String>> writer = writer(sugadd);
		run(info, reader, writer);
		assertEquals(1, commands.dbsize());
		assertEquals(values.size(), commands.ftSuglen(key));
	}

	@Test
	void writeSugIncr(TestInfo info) throws Exception {
		String key = "sugaddIncr";
		List<Suggestion<String>> values = new ArrayList<>();
		for (int index = 0; index < 100; index++) {
			values.add(Suggestion.string("word" + index).score(index + 1).payload("payload" + index).build());
		}
		ListItemReader<Suggestion<String>> reader = new ListItemReader<>(values);
		ToSuggestionFunction<String, Suggestion<String>> converter = new ToSuggestionFunction<>(Suggestion::getString,
				Suggestion::getScore, Suggestion::getPayload);
		Sugadd<String, String, Suggestion<String>> sugadd = new Sugadd<>(keyFunction(key), converter);
		sugadd.setIncr(true);
		OperationItemWriter<String, String, Suggestion<String>> writer = writer(sugadd);
		run(info, reader, writer);
		assertEquals(1, commands.dbsize());
		assertEquals(values.size(), commands.ftSuglen(key));
	}

	@Test
	void writeTimeseries(TestInfo info) throws Exception {
		String key = "ts";
		Map<Long, Double> samples = new HashMap<>();
		for (int index = 0; index < 100; index++) {
			samples.put(Instant.now().toEpochMilli() + index, (double) index);
		}
		TsAdd<String, String, Entry<Long, Double>> tsAdd = new TsAdd<>(keyFunction(key),
				new ToSampleFunction<>(e -> e.getKey(), e -> e.getValue()));
		ListItemReader<Entry<Long, Double>> reader = new ListItemReader<>(new ArrayList<>(samples.entrySet()));
		OperationItemWriter<String, String, Entry<Long, Double>> writer = writer(tsAdd);
		run(info, reader, writer);
		assertEquals(1, commands.dbsize());
	}

	@Test
	void writeJsonSet(TestInfo info) throws Exception {
		JsonSet<String, String, JsonNode> jsonSet = new JsonSet<>(n -> "beer:" + n.get("id").asText(),
				JsonNode::toString);
		jsonSet.setPath(".");
		OperationItemWriter<String, String, JsonNode> writer = writer(jsonSet);
		IteratorItemReader<JsonNode> reader = new IteratorItemReader<>(Beers.jsonNodeIterator());
		run(info, reader, writer);
		Assertions.assertEquals(BEER_COUNT, keyCount("beer:*"));
		Assertions.assertEquals(new ObjectMapper().readTree(JSON_BEER_1),
				new ObjectMapper().readTree(commands.jsonGet("beer:1", "$")));
	}

	@Test
	void writeJsonDel(TestInfo info) throws Exception {
		GeneratorItemReader gen = generator(73, DataType.JSON);
		generate(info, gen);
		JsonDel<String, String, KeyValue<String>> jsonDel = new JsonDel<>(KeyValue::getKey);
		OperationItemWriter<String, String, KeyValue<String>> writer = writer(jsonDel);
		run(info, gen, writer);
		Assertions.assertEquals(0, commands.dbsize());
	}

	@Test
	void writeTsAdd(TestInfo info) throws Exception {
		String key = "ts:1";
		Random random = new Random();
		int count = 100;
		List<Sample> samples = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			long timestamp = System.currentTimeMillis() - count + (index % (count / 2));
			samples.add(Sample.of(timestamp, random.nextDouble()));
		}
		ListItemReader<Sample> reader = new ListItemReader<>(samples);
		AddOptions<String, String> addOptions = AddOptions.<String, String>builder().policy(DuplicatePolicy.LAST)
				.build();
		TsAdd<String, String, Sample> tsadd = new TsAdd<>(keyFunction(key), Function.identity());
		tsadd.setOptions(addOptions);
		OperationItemWriter<String, String, Sample> writer = writer(tsadd);
		run(info, reader, writer);
		Assertions.assertEquals(count / 2,
				commands.tsRange(key, TimeRange.unbounded(), RangeOptions.builder().build()).size(), 2);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	void writeTsAddAll(TestInfo info) throws Exception {
		int count = 10;
		GeneratorItemReader reader = generator(count, DataType.TIMESERIES);
		AddOptions<String, String> addOptions = AddOptions.<String, String>builder().policy(DuplicatePolicy.LAST)
				.build();
		TsAddAll<String, String, KeyValue<String>> tsadd = new TsAddAll<>();
		tsadd.setKeyFunction(KeyValue::getKey);
		tsadd.setSamplesFunction(t -> (Collection<Sample>) t.getValue());
		tsadd.setOptions(addOptions);
		OperationItemWriter<String, String, Sample> writer = new OperationItemWriter(client, CodecUtils.STRING_CODEC,
				tsadd);
		run(info, reader, writer);
		for (int index = 1; index <= count; index++) {
			Assertions.assertEquals(TimeSeriesOptions.DEFAULT_SAMPLE_COUNT.getMin(),
					commands.tsRange(reader.key(index), TimeRange.unbounded(), RangeOptions.builder().build()).size(),
					2);
		}
	}

}