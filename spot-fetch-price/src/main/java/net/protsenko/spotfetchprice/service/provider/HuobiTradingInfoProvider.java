package net.protsenko.spotfetchprice.service.provider;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.HuobiApiProperties;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.protsenko.spotfetchprice.util.NetworkNormalizer.normalize;

@Slf4j
@Component
public class HuobiTradingInfoProvider implements TradingInfoProvider {

    private final HuobiApiProperties huobiApiProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebClient webClient;

    public HuobiTradingInfoProvider(HuobiApiProperties huobiApiProperties, RedisTemplate<String, String> redisTemplate) {
        this.huobiApiProperties = huobiApiProperties;
        this.redisTemplate = redisTemplate;
        this.webClient = WebClient.builder()
                .baseUrl(huobiApiProperties.getBaseUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(huobiApiProperties.getMaxInMemorySize()))
                        .build())
                .clientConnector(huobiApiProperties.createConnector())
                .build();
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toLowerCase();

        String json = getCachedJson();
        if (json == null) {
            json = fetchAndCacheJson(exchange);
            if (json == null) return stub();
        }

        return parseCoinInfoFromJson(json, coin);
    }

    private String getCachedJson() {
        return redisTemplate.opsForValue().get(huobiApiProperties.getRedisKey());
    }

    private String fetchAndCacheJson(ExchangeType exchange) {
        try {
            String response = fetchAllCoinsFromApi(exchange);
            if (response != null && !response.isEmpty()) {
                redisTemplate.opsForValue().set(
                        huobiApiProperties.getRedisKey(), response, 24, TimeUnit.HOURS
                );
                return response;
            }
        } catch (Exception ex) {
            log.error("Ошибка получения данных Huobi: ", ex);
        }
        return null;
    }

    private String fetchAllCoinsFromApi(ExchangeType exchange) {
        try {
            return webClient.get()
                    .uri(huobiApiProperties.getSpotConfigPath())
                    .header("User-Agent", huobiApiProperties.getUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(huobiApiProperties.getResponseTimeoutSeconds()))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name(), e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();
        } catch (Exception e) {
            log.error("Ошибка при запросе к Huobi API: ", e);
            return null;
        }
    }

    private TradingInfoDTO parseCoinInfoFromJson(String json, String coin) {
        try {
            JSONObject root = new JSONObject(json);
            if (!"200".equals(root.optString("code")) && !"0".equals(root.optString("code"))) {
                log.warn("Huobi error: {}", root.optString("message"));
                return stub();
            }
            JSONArray dataArr = root.optJSONArray("data");
            if (dataArr == null || dataArr.isEmpty()) return stub();

            JSONObject coinObj = findCoinObject(dataArr, coin);
            if (coinObj == null) return stub();

            return buildTradingInfoFromJson(coinObj);
        } catch (Exception e) {
            log.error("Ошибка парсинга JSON Huobi: ", e);
            return stub();
        }
    }

    private JSONObject findCoinObject(JSONArray dataArr, String coin) {
        for (int i = 0; i < dataArr.length(); i++) {
            JSONObject obj = dataArr.getJSONObject(i);
            if (coin.equalsIgnoreCase(obj.optString("currency"))) {
                return obj;
            }
        }
        return null;
    }

    private TradingInfoDTO buildTradingInfoFromJson(JSONObject coinObj) {
        JSONArray chains = coinObj.optJSONArray("chains");
        if (chains == null || chains.isEmpty()) return stub();

        List<TradingNetworkInfoDTO> networks = new ArrayList<>();
        for (int i = 0; i < chains.length(); i++) {
            JSONObject chain = chains.getJSONObject(i);
            String networkRaw = chain.optString("chain", "");
            String network = normalize(networkRaw);
            boolean depositEnable = "allowed".equalsIgnoreCase(chain.optString("depositStatus", "prohibited"));
            boolean withdrawEnable = "allowed".equalsIgnoreCase(chain.optString("withdrawStatus", "prohibited"));
            double withdrawFee = -1;
            if (chain.has("transactFeeWithdraw")) {
                try {
                    withdrawFee = Double.parseDouble(chain.optString("transactFeeWithdraw", "-1"));
                } catch (Exception ignore) {
                }
            }
            networks.add(new TradingNetworkInfoDTO(network, withdrawFee, depositEnable, withdrawEnable));
        }
        return new TradingInfoDTO(networks);
    }

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("", -1.0, false, false)
        ));
    }

}
