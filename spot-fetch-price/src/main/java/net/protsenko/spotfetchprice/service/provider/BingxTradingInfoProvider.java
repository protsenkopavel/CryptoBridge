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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static net.protsenko.spotfetchprice.util.NetworkNormalizer.normalize;

@Slf4j
@Component
@RequiredArgsConstructor
public class BingxTradingInfoProvider implements TradingInfoProvider {

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private final BingXApiProperties bingxApiProperties;
    @Qualifier("tradingInfoRedisTemplate")
    private final RedisTemplate<String, TradingInfoDTO> redisTemplate;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://open-api.bingx.com")
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(20 * 1024 * 1024)
                    )
                    .build())
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(60))))
            .build();

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();
        String redisKey = "tradingInfo:bingx:" + coin;
        ValueOperations<String, TradingInfoDTO> ops = redisTemplate.opsForValue();
        TradingInfoDTO cached = ops.get(redisKey);
        if (cached != null) return cached;

        try {
            String path = "/openApi/wallets/v1/capital/config/getall";
            String timestamp = String.valueOf(System.currentTimeMillis());

            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put("timestamp", timestamp);
            String valueToDigest = getMessageToDigest(parameters);
            String messageDigest = generateHmac256(valueToDigest);
            String parametersString = valueToDigest + "&signature=" + messageDigest;

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .query(parametersString)
                            .build())
                    .header("X-BX-APIKEY", bingxApiProperties.getKey())
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name(), e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();

            if (response == null || response.isEmpty()) return stub();

            JSONObject root = new JSONObject(response);
            if (!"0".equals(root.optString("code"))) {
                System.err.println("BingX error: " + root.optString("msg"));
                return stub();
            }

            JSONArray dataArr = root.optJSONArray("data");
            if (dataArr == null || dataArr.isEmpty()) return stub();

            JSONObject coinObj = null;
            for (int i = 0; i < dataArr.length(); i++) {
                JSONObject obj = dataArr.getJSONObject(i);
                if (coin.equalsIgnoreCase(obj.optString("coin"))) {
                    coinObj = obj;
                    break;
                }
            }
            if (coinObj == null) return stub();

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
                    } catch (Exception ignore) {
                    }
                }
                networks.add(new TradingNetworkInfoDTO(network, withdrawFee, depositEnable, withdrawEnable));
            }

            TradingInfoDTO dto = new TradingInfoDTO(networks);
            ops.set(redisKey, dto, 24, TimeUnit.HOURS);
            return dto;
        } catch (Exception ex) {
            log.error("Ошибка получения данных BingX: ", ex);
            return stub();
        }
    }

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("N/A", -1.0, false, false)
        ));
    }

    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private byte[] hmac(String algorithm, byte[] key, byte[] message) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key, algorithm));
        return mac.doFinal(message);
    }

    private String generateHmac256(String message) {
        try {
            byte[] bytes = hmac("HmacSHA256", bingxApiProperties.getSecret().getBytes(StandardCharsets.UTF_8), message.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (Exception e) {
            log.error("generateHmac256 exception: {}", e.toString());
        }
        return "";
    }

    private String getMessageToDigest(TreeMap<String, String> parameters) {
        boolean first = true;
        StringBuilder valueToDigest = new StringBuilder();
        for (Map.Entry<String, String> e : parameters.entrySet()) {
            if (!first) {
                valueToDigest.append("&");
            }
            first = false;
            valueToDigest.append(e.getKey()).append("=").append(e.getValue());
        }
        return valueToDigest.toString();
    }

}
