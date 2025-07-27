package net.protsenko.spotfetchprice.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.BingXApiProperties;
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
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static net.protsenko.spotfetchprice.util.BingXApiSignUtils.generateHmac256;
import static net.protsenko.spotfetchprice.util.BingXApiSignUtils.getMessageToDigest;
import static net.protsenko.spotfetchprice.util.NetworkNormalizer.normalize;

@Slf4j
@Component
public class BingxTradingInfoProvider implements TradingInfoProvider {

    private final BingXApiProperties bingxApiProperties;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebClient webClient;

    public BingxTradingInfoProvider(BingXApiProperties bingxApiProperties, RedisTemplate<String, String> redisTemplate) {
        this.bingxApiProperties = bingxApiProperties;
        this.redisTemplate = redisTemplate;
        this.webClient = WebClient.builder()
                .baseUrl(bingxApiProperties.getBaseUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(bingxApiProperties.getMaxInMemorySize())
                        )
                        .build())
                .clientConnector(bingxApiProperties.createConnector())
                .build();
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();

        String cachedJson = getCachedJson();
        if (cachedJson == null) {
            cachedJson = fetchAndCacheJson(exchange);
            if (cachedJson == null) return stub();
        }

        return parseCoinInfoFromJson(cachedJson, coin);
    }

    private String getCachedJson() {
        return redisTemplate.opsForValue().get(bingxApiProperties.getRedis_key());
    }

    private String fetchAndCacheJson(ExchangeType exchange) {
        try {
            String response = fetchAllCoinsFromApi(exchange);
            if (response != null && !response.isEmpty()) {
                redisTemplate.opsForValue().set(
                        bingxApiProperties.getRedis_key(), response, 24, TimeUnit.HOURS
                );
                return response;
            }
        } catch (Exception ex) {
            log.error("Ошибка получения данных BingX: ", ex);
        }
        return null;
    }

    private String fetchAllCoinsFromApi(ExchangeType exchange) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        TreeMap<String, String> parameters = new TreeMap<>();
        parameters.put("timestamp", timestamp);

        String valueToDigest = getMessageToDigest(parameters);
        String messageDigest = generateHmac256(valueToDigest, bingxApiProperties.getSecret());
        String parametersString = valueToDigest + "&signature=" + messageDigest;

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(bingxApiProperties.getSpotConfigPath())
                            .query(parametersString)
                            .build())
                    .header("X-BX-APIKEY", bingxApiProperties.getKey())
                    .header("User-Agent", bingxApiProperties.getUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name(), e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();
        } catch (Exception e) {
            log.error("Ошибка при запросе к BingX API: ", e);
            return null;
        }
    }

    private TradingInfoDTO parseCoinInfoFromJson(String json, String coin) {
        try {
            JSONObject root = new JSONObject(json);
            if (!"0".equals(root.optString("code"))) {
                log.warn("BingX error: {}", root.optString("msg"));
                return stub();
            }
            JSONArray dataArr = root.optJSONArray("data");
            if (dataArr == null || dataArr.isEmpty()) return stub();

            JSONObject coinObj = findCoinObject(dataArr, coin);
            if (coinObj == null) return stub();

            return buildTradingInfoFromJson(coinObj);
        } catch (Exception e) {
            log.error("Ошибка парсинга JSON BingX: ", e);
            return stub();
        }
    }

    private JSONObject findCoinObject(JSONArray dataArr, String coin) {
        for (int i = 0; i < dataArr.length(); i++) {
            JSONObject obj = dataArr.getJSONObject(i);
            if (coin.equalsIgnoreCase(obj.optString("coin"))) {
                return obj;
            }
        }
        return null;
    }

    private TradingInfoDTO buildTradingInfoFromJson(JSONObject coinObj) {
        JSONArray chains = coinObj.optJSONArray("networkList");
        if (chains == null || chains.isEmpty()) return stub();

        List<TradingNetworkInfoDTO> networks = new ArrayList<>();
        for (int i = 0; i < chains.length(); i++) {
            JSONObject chain = chains.getJSONObject(i);
            String networkRaw = chain.optString("network", "");
            String network = normalize(networkRaw);
            boolean depositEnable = chain.optBoolean("depositEnable", false);
            boolean withdrawEnable = chain.optBoolean("withdrawEnable", false);
            double withdrawFee = -1;
            if (chain.has("withdrawFee")) {
                try {
                    withdrawFee = Double.parseDouble(chain.optString("withdrawFee", "-1"));
                } catch (Exception ignore) {}
            }
            networks.add(new TradingNetworkInfoDTO(network, withdrawFee, depositEnable, withdrawEnable));
        }
        return new TradingInfoDTO(networks);
    }

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("N/A", -1.0, false, false)
        ));
    }
}
