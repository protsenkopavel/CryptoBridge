package net.protsenko.spotfetchprice.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.KucoinApiProperties;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.protsenko.spotfetchprice.props.KucoinApiProperties.API_URL;

@Slf4j
@Component
@RequiredArgsConstructor
public class KucoinTradingInfoProvider implements TradingInfoProvider {

    private final KucoinApiProperties kucoinApiProperties;

    @Qualifier("tradingInfoRedisTemplate")
    private final RedisTemplate<String, TradingInfoDTO> redisTemplate;

    private final WebClient webClient = WebClient.builder()
            .baseUrl(API_URL)
            .build();

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("N/A", -1.0, false, false)
        ));
    }

    private static String sign(String message, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : sig) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();
        String redisKey = "tradingInfo:kucoin:" + coin;
        ValueOperations<String, TradingInfoDTO> ops = redisTemplate.opsForValue();

        TradingInfoDTO cached = ops.get(redisKey);
        if (cached != null) {
            return cached;
        }

        try {
            long timestamp = System.currentTimeMillis();
            String url = KucoinApiProperties.API_URL + coin;

            String strToSign = timestamp + "GET" + "/api/v3/currencies/" + coin;
            String signature = sign(strToSign, kucoinApiProperties.getSecret());

            String response = webClient.get()
                    .uri(url)
                    .header("KC-API-KEY", kucoinApiProperties.getKey())
                    .header("KC-API-SIGN", signature)
                    .header("KC-API-TIMESTAMP", String.valueOf(timestamp))
                    .header("KC-API-PASSPHRASE", kucoinApiProperties.getPassphrase())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name() , e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();

            if (response == null || response.isEmpty()) {
                return stub();
            }

            JSONObject root = new JSONObject(response);
            if (!"200000".equals(root.optString("code"))) {
                System.err.println("Kucoin error fetching " + coin + ": " + root.optString("msg"));
                return stub();
            }

            JSONObject data = root.getJSONObject("data");
            JSONArray chains = data.optJSONArray("chains");
            if (chains == null || chains.isEmpty()) {
                System.err.println("Kucoin: chains empty for " + coin);
                return stub();
            }

            List<TradingNetworkInfoDTO> networks = new ArrayList<>();

            for (int i = 0; i < chains.length(); i++) {
                JSONObject chain = chains.getJSONObject(i);
                String chainName = chain.optString("chainName", "");
                boolean depositEnabled = chain.optBoolean("isDepositEnabled", false);
                boolean withdrawEnabled = chain.optBoolean("isWithdrawEnabled", false);
                double withdrawFee = 0;
                try {
                    withdrawFee = Double.parseDouble(chain.optString("withdrawalMinFee", "0"));
                } catch (Exception ignore) {
                }

                networks.add(new TradingNetworkInfoDTO(chainName, withdrawFee, depositEnabled, withdrawEnabled));
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