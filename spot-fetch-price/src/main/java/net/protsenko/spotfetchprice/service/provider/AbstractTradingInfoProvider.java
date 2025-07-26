package net.protsenko.spotfetchprice.service.provider;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractTradingInfoProvider implements TradingInfoProvider {

    protected final RedisTemplate<String, TradingInfoDTO> redisTemplate;
    protected final WebClient webClient;

    protected AbstractTradingInfoProvider(RedisTemplate<String, TradingInfoDTO> redisTemplate, WebClient webClient) {
        this.redisTemplate = redisTemplate;
        this.webClient = webClient;
    }

    protected abstract String getRedisKey(CurrencyPair pair);
    protected abstract Mono<String> buildRequest(ExchangeType exchange, CurrencyPair pair);
    protected abstract TradingInfoDTO parseResponse(String response, CurrencyPair pair);

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String redisKey = getRedisKey(pair);
        ValueOperations<String, TradingInfoDTO> ops = redisTemplate.opsForValue();
        TradingInfoDTO cached = ops.get(redisKey);
        if (cached != null) return cached;

        try {
            String response = buildRequest(exchange, pair)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name(), e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();

            TradingInfoDTO dto = (response == null || response.isEmpty())
                    ? stub()
                    : parseResponse(response, pair);

            ops.set(redisKey, dto, 24, TimeUnit.HOURS);
            return dto;
        } catch (Exception ex) {
            log.error("Ошибка получения данных {}: ", exchange, ex);
            return stub();
        }
    }

    protected TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("N/A", -1.0, false, false)
        ));
    }
}