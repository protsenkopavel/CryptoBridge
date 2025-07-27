package net.protsenko.spotfetchprice.service.provider;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.MEXCApiProperties;
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

import static net.protsenko.spotfetchprice.util.MEXCApiSignUtils.hmacSHA256Hex;
import static net.protsenko.spotfetchprice.util.NetworkNormalizer.normalize;

@Slf4j
@Component
public class MEXCTradingInfoProvider implements TradingInfoProvider {

    private final MEXCApiProperties apiProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebClient webClient;

    public MEXCTradingInfoProvider(MEXCApiProperties apiProperties, RedisTemplate<String, String> redisTemplate) {
        this.apiProperties = apiProperties;
        this.redisTemplate = redisTemplate;
        this.webClient = WebClient.builder()
                .baseUrl(apiProperties.getBaseUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(apiProperties.getMaxInMemorySize())
                        )
                        .build())
                .clientConnector(apiProperties.createConnector())
                .build();
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();

        String allCoinsJson = getAllCoinsFromCache();
        if (allCoinsJson == null) {
            allCoinsJson = fetchAndCacheAllCoinsJson(exchange);
            if (allCoinsJson == null) return stub();
        }

        return parseTradingInfoForCoin(allCoinsJson, coin);
    }

    private String getAllCoinsFromCache() {
        return redisTemplate.opsForValue().get(apiProperties.getRedisKeyAll());
    }

    private String fetchAndCacheAllCoinsJson(ExchangeType exchange) {
        try {
            String json = fetchAllCoinsFromApi(exchange);
            if (json != null && !json.isEmpty()) {
                redisTemplate.opsForValue().set(apiProperties.getRedisKeyAll(), json, 24, TimeUnit.HOURS);
                return json;
            }
        } catch (Exception ex) {
            log.error("Ошибка получения данных MEXC: ", ex);
        }
        return null;
    }

    private String fetchAllCoinsFromApi(ExchangeType exchange) {
        long timestamp = System.currentTimeMillis();
        int recvWindow = 5000;
        String params = "timestamp=" + timestamp + "&recvWindow=" + recvWindow;
        String signature = hmacSHA256Hex(apiProperties.getSecret(), params);
        String url = apiProperties.getSpotConfigPath() + "?" + params + "&signature=" + signature;

        try {
            return webClient.get()
                    .uri(url)
                    .header("X-MEXC-APIKEY", apiProperties.getKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(apiProperties.getResponseTimeoutSeconds()))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name(), e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();
        } catch (Exception e) {
            log.error("Ошибка при запросе к MEXC API: ", e);
            return null;
        }
    }

    private TradingInfoDTO parseTradingInfoForCoin(String json, String coin) {
        try {
            JSONArray arr = new JSONArray(json);
            JSONObject coinObj = findCoinObject(arr, coin);
            if (coinObj == null) return stub();

            return buildTradingInfoFromJson(coinObj);
        } catch (Exception ex) {
            log.error("Ошибка парсинга MEXC: ", ex);
            return stub();
        }
    }

    private JSONObject findCoinObject(JSONArray arr, String coin) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (coin.equalsIgnoreCase(o.optString("coin"))) {
                return o;
            }
        }
        return null;
    }

    private TradingInfoDTO buildTradingInfoFromJson(JSONObject coinObj) {
        JSONArray networkList = coinObj.optJSONArray("networkList");
        if (networkList == null || networkList.isEmpty()) return stub();

        List<TradingNetworkInfoDTO> networks = new ArrayList<>();
        for (int i = 0; i < networkList.length(); i++) {
            JSONObject net = networkList.getJSONObject(i);
            networks.add(parseNetwork(net));
        }
        return new TradingInfoDTO(networks);
    }

    private TradingNetworkInfoDTO parseNetwork(JSONObject net) {
        String networkRaw = net.optString("network", net.optString("netWork"));
        String network = normalize(networkRaw);
        boolean depositEnable = net.optBoolean("depositEnable", false);
        boolean withdrawEnable = net.optBoolean("withdrawEnable", false);
        double withdrawFee = -1;
        if (net.has("withdrawFee")) {
            try {
                withdrawFee = Double.parseDouble(net.optString("withdrawFee", "-1"));
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
