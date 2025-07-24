package net.protsenko.spotfetchprice.service.provider;


import lombok.RequiredArgsConstructor;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.BitgetApiProperties;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class BitgetTradingInfoProvider implements TradingInfoProvider {

    private final BitgetApiProperties apiProperties;

    @Qualifier("tradingInfoRedisTemplate")
    private final RedisTemplate<String, TradingInfoDTO> redisTemplate;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.bitget.com")
            .build();

    private static TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("", -1.0, true, true)
        ));
    }

    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        String coin = pair.getBase().getCurrencyCode().toUpperCase();
        String redisKey = "tradingInfo:bitget:" + coin;
        ValueOperations<String, TradingInfoDTO> ops = redisTemplate.opsForValue();
        TradingInfoDTO cached = ops.get(redisKey);
        if (cached != null) return cached;

        try {
            String url = "/api/spot/v1/public/coinInfo";

            String response = webClient.get()
                    .uri(url)
                    .header("ACCESS-KEY", apiProperties.getKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorReturn("")
                    .block();

            if (response == null || response.isEmpty()) return stub();

            JSONObject root = new JSONObject(response);
            if (!"00000".equals(root.optString("code"))) {
                System.err.println("Bitget error: " + root.optString("msg"));
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

            JSONArray chains = coinObj.optJSONArray("chains");
            if (chains == null || chains.isEmpty()) return stub();

            List<TradingNetworkInfoDTO> networks = new ArrayList<>();
            for (int i = 0; i < chains.length(); i++) {
                JSONObject chain = chains.getJSONObject(i);
                String network = chain.optString("chain", "");
                boolean depositEnable = chain.optInt("isDepositable", 1) == 1;
                boolean withdrawEnable = chain.optInt("isWithdrawable", 1) == 1;
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
            ops.set(redisKey, dto, 10, TimeUnit.MINUTES);
            return dto;
        } catch (Exception ex) {
            ex.printStackTrace();
            return stub();
        }
    }
}
