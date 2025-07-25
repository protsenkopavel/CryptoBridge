package net.protsenko.spotfetchprice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.protsenko.spotfetchprice.dto.ExchangeTickersDTO;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, ExchangeTickersDTO> exchangeTickersRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, ExchangeTickersDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        Jackson2JsonRedisSerializer<ExchangeTickersDTO> serializer = new Jackson2JsonRedisSerializer<>(ExchangeTickersDTO.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        serializer.setObjectMapper(mapper);

        template.setDefaultSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, TradingInfoDTO> tradingInfoRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, TradingInfoDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(TradingInfoDTO.class));
        return template;
    }

}
