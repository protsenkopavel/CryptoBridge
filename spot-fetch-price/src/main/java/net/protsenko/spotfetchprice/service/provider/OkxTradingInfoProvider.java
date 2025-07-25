package net.protsenko.spotfetchprice.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.OkxApiProperties;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OkxTradingInfoProvider implements TradingInfoProvider {

    private final OkxApiProperties okxApiProperties;

    @Qualifier("tradingInfoRedisTemplate")
    private final RedisTemplate<String, TradingInfoDTO> redisTemplate;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://www.okx.com")
            .build();

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("N/A", -1.0, false, false)
        ));
    }

    private String sign(String preHash, String secret) throws Exception {
        Mac sha256Mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Mac.init(secretKey);
        byte[] hash = sha256Mac.doFinal(preHash.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();
        String redisKey = "tradingInfo:okx:" + coin;
        ValueOperations<String, TradingInfoDTO> ops = redisTemplate.opsForValue();
        TradingInfoDTO cached = ops.get(redisKey);
        if (cached != null) return cached;

        try {
            String method = "GET";
            String endpointPath = "/api/v5/asset/currencies";
            String query = "?ccy=" + coin;
            String requestPathWithParams = endpointPath + query;
            String body = "";
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);
            String timestamp = fmt.format(Instant.now());

            String preHash = timestamp + method + requestPathWithParams + body;
            String sign = sign(preHash, okxApiProperties.getSecret());

            String response = webClient.get()
                    .uri(requestPathWithParams)
                    .header("OK-ACCESS-KEY", okxApiProperties.getKey())
                    .header("OK-ACCESS-SIGN", sign)
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

            if (response == null || response.isEmpty()) return stub();

            JSONObject root = new JSONObject(response);
            if (!"0".equals(root.optString("code"))) {
                System.err.println("OKX error: " + root.optString("msg"));
                return stub();
            }

            JSONArray dataArr = root.optJSONArray("data");
            if (dataArr == null || dataArr.isEmpty()) return stub();

            List<TradingNetworkInfoDTO> networks = new ArrayList<>();
            for (int i = 0; i < dataArr.length(); i++) {
                JSONObject obj = dataArr.getJSONObject(i);

                String chain = obj.optString("chain", "");
                boolean depositEnable = obj.optBoolean("canDep", false);
                boolean withdrawEnable = obj.optBoolean("canWd", false);
                double withdrawFee = -1;
                if (obj.has("fee")) {
                    try {
                        withdrawFee = Double.parseDouble(obj.optString("fee", "-1"));
                    } catch (Exception ignore) {
                    }
                }
                networks.add(new TradingNetworkInfoDTO(
                        chain,
                        withdrawFee,
                        depositEnable,
                        withdrawEnable
                ));
            }

            TradingInfoDTO dto = new TradingInfoDTO(networks);
            ops.set(redisKey, dto, 10, TimeUnit.MINUTES);
            return dto;
        } catch (Exception ex) {
            log.error("Ошибка получения данных OKX: ", ex);
            return stub();
        }
    }
}