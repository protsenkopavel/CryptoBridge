package net.protsenko.spotfetchprice.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.MEXCApiProperties;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MEXCTradingInfoProvider implements TradingInfoProvider {

    private final MEXCApiProperties apiProperties;

    @Qualifier("tradingInfoRedisTemplate")
    private final RedisTemplate<String, TradingInfoDTO> redisTemplate;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.mexc.com")
            .build();

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("N/A", -1.0, false, false)
        ));
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();
        String redisKey = "tradingInfo:mexc:" + coin;
        ValueOperations<String, TradingInfoDTO> ops = redisTemplate.opsForValue();
        TradingInfoDTO cached = ops.get(redisKey);
        if (cached != null) return cached;

        try {
            String url = "/api/v3/capital/config/getall";

            String response = webClient.get()
                    .uri(url)
                    .header("X-MEXC-APIKEY", apiProperties.getKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name() , e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();

            if (response == null || response.isEmpty()) return stub();

            JSONArray arr = new JSONArray(response);
            JSONObject coinObj = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (coin.equalsIgnoreCase(o.optString("coin"))) {
                    coinObj = o;
                    break;
                }
            }
            if (coinObj == null) return stub();

            JSONArray networkList = coinObj.optJSONArray("networkList");
            if (networkList == null || networkList.isEmpty()) return stub();

            List<TradingNetworkInfoDTO> networks = new ArrayList<>();
            for (int i = 0; i < networkList.length(); i++) {
                JSONObject net = networkList.getJSONObject(i);
                String network = net.optString("network");
                boolean depositEnable = net.optBoolean("depositEnable", false);
                boolean withdrawEnable = net.optBoolean("withdrawEnable", false);
                double withdrawFee = -1;
                if (net.has("withdrawFee")) {
                    try {
                        withdrawFee = Double.parseDouble(net.optString("withdrawFee", "-1"));
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