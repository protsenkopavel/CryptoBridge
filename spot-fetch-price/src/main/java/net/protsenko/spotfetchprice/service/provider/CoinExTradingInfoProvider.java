package net.protsenko.spotfetchprice.service.provider;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.CoinEXApiProperties;
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
public class CoinExTradingInfoProvider implements TradingInfoProvider {

    private final CoinEXApiProperties apiProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebClient webClient;

    public CoinExTradingInfoProvider(CoinEXApiProperties apiProperties, RedisTemplate<String, String> redisTemplate) {
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
            log.error("Ошибка получения данных CoinEx: ", ex);
        }
        return null;
    }

    private String fetchAllCoinsFromApi(ExchangeType exchange) {
        try {
            return webClient.get()
                    .uri(apiProperties.getSpotConfigPath())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(apiProperties.getResponseTimeoutSeconds()))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name(), e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();
        } catch (Exception e) {
            log.error("Ошибка при запросе к CoinEx API: ", e);
            return null;
        }
    }

    private TradingInfoDTO parseTradingInfoForCoin(String json, String coin) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("code") != 0) {
                log.warn("CoinEx error: {}", root.optString("message"));
                return stub();
            }
            JSONArray dataArr = root.optJSONArray("data");
            if (dataArr == null || dataArr.isEmpty()) return stub();

            JSONObject coinObj = findCoinObject(dataArr, coin);
            if (coinObj == null) return stub();

            return buildTradingInfoFromJson(coinObj);
        } catch (Exception ex) {
            log.error("Ошибка парсинга CoinEx: ", ex);
            return stub();
        }
    }

    private JSONObject findCoinObject(JSONArray dataArr, String coin) {
        for (int i = 0; i < dataArr.length(); i++) {
            JSONObject obj = dataArr.getJSONObject(i);
            JSONObject asset = obj.optJSONObject("asset");
            if (asset != null && coin.equalsIgnoreCase(asset.optString("ccy"))) {
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
            boolean depositEnable = chain.optBoolean("deposit_enabled", false);
            boolean withdrawEnable = chain.optBoolean("withdraw_enabled", false);
            double withdrawFee = -1;
            if (chain.has("withdrawal_fee")) {
                try {
                    withdrawFee = Double.parseDouble(chain.optString("withdrawal_fee", "-1"));
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