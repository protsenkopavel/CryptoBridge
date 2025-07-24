package net.protsenko.spotfetchprice.service.provider;

import lombok.RequiredArgsConstructor;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.BybitApiProperties;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.protsenko.spotfetchprice.props.BybitApiProperties.API_URL;
import static net.protsenko.spotfetchprice.props.BybitApiProperties.RECV_WINDOW;

@Component
@RequiredArgsConstructor
public class BybitTradingInfoProvider implements TradingInfoProvider {

    private final BybitApiProperties bybitApiProperties;

    @Qualifier("tradingInfoRedisTemplate")
    private final RedisTemplate<String, TradingInfoDTO> redisTemplate;

    private final WebClient webClient = WebClient.builder().build();

    private static TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("", -1, false, false)
        ));
    }

    public static String sign(String message, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();
        String redisKey = "tradingInfo:bybit:" + coin;
        ValueOperations<String, TradingInfoDTO> ops = redisTemplate.opsForValue();
        TradingInfoDTO cached = ops.get(redisKey);
        if (cached != null) return cached;

        try {
            long timestamp = System.currentTimeMillis();
            String query = "coin=" + coin;
            String preSign = timestamp + bybitApiProperties.getKey() + RECV_WINDOW + query;
            String sign = sign(preSign, bybitApiProperties.getSecret());
            String url = API_URL + "?" + query;

            String response = webClient.get()
                    .uri(url)
                    .header("X-BAPI-API-KEY", bybitApiProperties.getKey())
                    .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                    .header("X-BAPI-RECV-WINDOW", RECV_WINDOW)
                    .header("X-BAPI-SIGN", sign)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorReturn("")
                    .block();

            if (response == null || response.isEmpty()) {
                return stub();
            }

            JSONObject obj = new JSONObject(response);
            if (obj.getInt("retCode") != 0) {
                System.err.println("Bybit error: " + obj.optString("retMsg") + ", coin=" + coin);
                return stub();
            }

            JSONObject result = obj.getJSONObject("result");
            JSONArray rows = result.getJSONArray("rows");
            if (rows.isEmpty()) return stub();

            JSONObject coinObj = rows.getJSONObject(0);
            JSONArray chains = coinObj.getJSONArray("chains");

            List<TradingNetworkInfoDTO> networks = new ArrayList<>();
            for (int i = 0; i < chains.length(); i++) {
                JSONObject chain = chains.getJSONObject(i);
                String chainType = chain.optString("chainType", chain.optString("chain", ""));
                boolean depositEnable = chain.optInt("depositEnable", chain.optInt("chainDeposit", 0)) == 1;
                boolean withdrawEnable = chain.optInt("withdrawEnable", chain.optInt("chainWithdraw", 0)) == 1;
                double withdrawFee = 0;
                try {
                    withdrawFee = Double.parseDouble(chain.optString("withdrawFee", "0"));
                } catch (Exception ignore) {
                }

                networks.add(new TradingNetworkInfoDTO(chainType, withdrawFee, depositEnable, withdrawEnable));
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
