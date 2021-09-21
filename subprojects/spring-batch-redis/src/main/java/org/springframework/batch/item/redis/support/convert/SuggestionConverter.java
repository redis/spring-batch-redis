package org.springframework.batch.item.redis.support.convert;

import com.redis.lettucemod.api.search.Suggestion;
import org.springframework.core.convert.converter.Converter;

public class SuggestionConverter<V, T> implements Converter<T, Suggestion<V>> {

    private Converter<T, V> string;
    private Converter<T, Double> score;
    private Converter<T, V> payload;

    public SuggestionConverter(Converter<T, V> string, Converter<T, Double> score, Converter<T, V> payload) {
        this.string = string;
        this.score = score;
        this.payload = payload;
    }

    @Override
    public Suggestion<V> convert(T source) {
        Suggestion<V> suggestion = new Suggestion<>();
        suggestion.setString(string.convert(source));
        suggestion.setScore(score.convert(source));
        suggestion.setPayload(payload.convert(source));
        return suggestion;
    }
}