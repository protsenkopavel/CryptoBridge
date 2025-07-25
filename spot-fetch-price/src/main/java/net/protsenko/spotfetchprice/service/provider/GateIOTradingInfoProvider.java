package net.protsenko.spotfetchprice.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.GateIOApiProperties;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GateIOTradingInfoProvider implements TradingInfoProvider {

    private final GateIOApiProperties apiProperties;

    @Qualifier("tradingInfoRedisTemplate")
    private final RedisTemplate<String, TradingInfoDTO> redisTemplate;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.gateio.ws/api/v4")
            .build();

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("N/A", -1.0, false, false)
        ));
    }

    private static String sign(String payload, String secret) throws Exception {
        Mac sha512Hmac = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        sha512Hmac.init(secretKey);
        byte[] hash = sha512Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();
        String redisKey = "tradingInfo:gateio:" + coin;
        ValueOperations<String, TradingInfoDTO> ops = redisTemplate.opsForValue();
        TradingInfoDTO cached = ops.get(redisKey);
        if (cached != null) return cached;

        try {
            String path = "/wallet/currency_chains";
            String query = "currency=" + coin;
            String method = "GET";
            String body = "";
            long ts = System.currentTimeMillis() / 1000L;

            String payload = ts + "\n" + method + "\n" + path + "\n" + query + "\n" + body + "\n";
            String sign = sign(payload, apiProperties.getSecret());

            String url = path + "?" + query;

            String response = webClient.get()
                    .uri(url)
                    .header("KEY", apiProperties.getKey())
                    .header("Timestamp", String.valueOf(ts))
                    .header("SIGN", sign)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name() , e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();

            if (response == null || response.isEmpty()) return stub();

            JSONArray arr = new JSONArray(response);
            if (arr.isEmpty()) return stub();

            List<TradingNetworkInfoDTO> networks = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String network = obj.optString("chain", "");
                boolean depositEnable = obj.optInt("is_deposit_disabled", 0) == 0;
                boolean withdrawEnable = obj.optInt("is_withdraw_disabled", 0) == 0;
                double withdrawFee = -1;
                if (obj.has("withdraw_fee")) {
                    try {
                        withdrawFee = Double.parseDouble(obj.optString("withdraw_fee", "-1"));
                    } catch (Exception ignore) {
                    }
                }
                networks.add(new TradingNetworkInfoDTO(network, withdrawFee, depositEnable, withdrawEnable));
            }

            TradingInfoDTO dto = new TradingInfoDTO(networks);
            ops.set(redisKey, dto, 10, TimeUnit.MINUTES);
            return dto;
        } catch (Exception ex) {
            ex.printStackTrace();
            return stub();
        }
    }
}
