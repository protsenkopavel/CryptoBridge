package net.protsenko.spotfetchprice.service.provider;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.OKXApiProperties;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.protsenko.spotfetchprice.util.NetworkNormalizer.normalize;
import static net.protsenko.spotfetchprice.util.OKXApiSignUtils.sign;

@Slf4j
@Component
public class OKXTradingInfoProvider implements TradingInfoProvider {

    private final OKXApiProperties okxApiProperties;
    @Qualifier("tradingInfoRedisTemplate")
    private final RedisTemplate<String, TradingInfoDTO> redisTemplate;
    private final WebClient webClient;

    public OKXTradingInfoProvider(OKXApiProperties okxApiProperties, RedisTemplate<String, TradingInfoDTO> redisTemplate) {
        this.okxApiProperties = okxApiProperties;
        this.redisTemplate = redisTemplate;
        this.webClient = WebClient.builder()
                .baseUrl(okxApiProperties.getBaseUrl())
                .build();
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();
        String redisKey = buildRedisKey(coin);

        TradingInfoDTO cached = getFromCache(redisKey);
        if (cached != null) return cached;

        try {
            String response = fetchCoinInfoFromApi(exchange, coin);
            TradingInfoDTO dto = parseTradingInfo(response);
            if (dto != null) {
                putToCache(redisKey, dto);
                return dto;
            }
        } catch (Exception ex) {
            log.error("Ошибка OKXTradingInfoProvider: ", ex);
        }
        return stub();
    }

    private String buildRedisKey(String coin) {
        return "tradingInfo:okx:" + coin;
    }

    private TradingInfoDTO getFromCache(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    private void putToCache(String key, TradingInfoDTO dto) {
        redisTemplate.opsForValue().set(key, dto, 24, TimeUnit.HOURS);
    }

    private String fetchCoinInfoFromApi(ExchangeType exchange, String coin) {
        String method = "GET";
        String query = "?ccy=" + coin;
        String requestPathWithParams = okxApiProperties.getStopConfigPath() + query;
        String body = "";

        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());

        String preHash = timestamp + method + requestPathWithParams + body;
        String signature = sign(preHash, okxApiProperties.getSecret());

        return webClient.get()
                .uri(requestPathWithParams)
                .header("OK-ACCESS-KEY", okxApiProperties.getKey())
                .header("OK-ACCESS-SIGN", signature)
                .header("OK-ACCESS-TIMESTAMP", timestamp)
                .header("OK-ACCESS-PASSPHRASE", okxApiProperties.getPassphrase())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    log.error("Ошибка при вызове {} API: {}", exchange.name(), e.getMessage(), e);
                    return Mono.just("");
                })
                .block();
    }

    private TradingInfoDTO parseTradingInfo(String response) {
        if (response == null || response.isEmpty()) return null;

        JSONObject root = new JSONObject(response);
        if (!"0".equals(root.optString("code"))) {
            log.warn("OKX error: {}", root.optString("msg"));
            return null;
        }

        JSONArray dataArr = root.optJSONArray("data");
        if (dataArr == null || dataArr.isEmpty()) return null;

        List<TradingNetworkInfoDTO> networks = new ArrayList<>();
        for (int i = 0; i < dataArr.length(); i++) {
            networks.add(parseNetwork(dataArr.getJSONObject(i)));
        }
        return new TradingInfoDTO(networks);
    }

    private TradingNetworkInfoDTO parseNetwork(JSONObject obj) {
        String networkRaw = obj.optString("chain", "");
        String network = normalize(networkRaw);

        boolean depositEnable = obj.optBoolean("canDep", false);
        boolean withdrawEnable = obj.optBoolean("canWd", false);
        double withdrawFee = -1;
        if (obj.has("fee")) {
            try {
                withdrawFee = Double.parseDouble(obj.optString("fee", "-1"));
            } catch (Exception ignore) {
            }
        }
        return new TradingNetworkInfoDTO(
                network,
                withdrawFee,
                depositEnable,
                withdrawEnable
        );
    }

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("N/A", -1.0, false, false)
        ));
    }
}
