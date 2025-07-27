package net.protsenko.spotfetchprice.service.provider;


import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.BitgetApiProperties;
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
public class BitgetTradingInfoProvider implements TradingInfoProvider {

    private final BitgetApiProperties bitgetApiProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebClient webClient;

    public BitgetTradingInfoProvider(BitgetApiProperties bitgetApiProperties, RedisTemplate<String, String> redisTemplate) {
        this.bitgetApiProperties = bitgetApiProperties;
        this.redisTemplate = redisTemplate;
        this.webClient = WebClient.builder()
                .baseUrl(bitgetApiProperties.getBaseUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(bitgetApiProperties.getMaxInMemorySize())
                        )
                        .build())
                .clientConnector(bitgetApiProperties.createConnector())
                .build();
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();

        String json = getAllCoinsJsonFromCache();
        if (json == null) {
            json = fetchAndCacheAllCoinsJson(exchange);
            if (json == null) return stub();
        }

        return buildTradingInfoForCoin(json, coin);
    }

    private String getAllCoinsJsonFromCache() {
        return redisTemplate.opsForValue().get(bitgetApiProperties.getRedisKeyAll());
    }

    private String fetchAndCacheAllCoinsJson(ExchangeType exchange) {
        try {
            String response = fetchAllCoinsFromApi(exchange);
            if (response != null && !response.isEmpty()) {
                redisTemplate.opsForValue().set(
                        bitgetApiProperties.getRedisKeyAll(),
                        response,
                        24, TimeUnit.HOURS
                );
                return response;
            }
        } catch (Exception ex) {
            log.error("Ошибка получения данных Bitget: ", ex);
        }
        return null;
    }

    private String fetchAllCoinsFromApi(ExchangeType exchange) {
        try {
            return webClient.get()
                    .uri(bitgetApiProperties.getSpotConfigUrl())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(bitgetApiProperties.getResponseTimeoutSeconds()))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name(), e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();
        } catch (Exception e) {
            log.error("Ошибка при запросе к Bitget API: ", e);
            return null;
        }
    }

    private TradingInfoDTO buildTradingInfoForCoin(String json, String coin) {
        try {
            JSONObject root = new JSONObject(json);
            if (!"00000".equals(root.optString("code"))) {
                log.warn("Bitget error: {}", root.optString("msg"));
                return stub();
            }

            JSONArray dataArr = root.optJSONArray("data");
            if (dataArr == null || dataArr.isEmpty()) return stub();

            JSONObject coinObj = findCoinObject(dataArr, coin);
            if (coinObj == null) return stub();

            return buildTradingInfoFromJson(coinObj);
        } catch (Exception ex) {
            log.error("Ошибка парсинга Bitget: ", ex);
            return stub();
        }
    }

    private JSONObject findCoinObject(JSONArray dataArr, String coin) {
        for (int i = 0; i < dataArr.length(); i++) {
            JSONObject obj = dataArr.getJSONObject(i);
            if (coin.equalsIgnoreCase(obj.optString("coinName"))) {
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
            networks.add(parseChain(chain));
        }
        return new TradingInfoDTO(networks);
    }

    private TradingNetworkInfoDTO parseChain(JSONObject chain) {
        String networkRaw = chain.optString("chain", "");
        String network = normalize(networkRaw);
        boolean depositEnable = "true".equalsIgnoreCase(chain.optString("rechargeable", "false"));
        boolean withdrawEnable = "true".equalsIgnoreCase(chain.optString("withdrawable", "false"));
        double withdrawFee = -1;
        if (chain.has("withdrawFee")) {
            try {
                withdrawFee = Double.parseDouble(chain.optString("withdrawFee", "-1"));
            } catch (Exception ignore) {
            }
        }
        return new TradingNetworkInfoDTO(network, withdrawFee, depositEnable, withdrawEnable);
    }

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("", -1.0, false, false)
        ));
    }

}