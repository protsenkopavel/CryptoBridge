package net.protsenko.spotfetchprice.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.dto.TradingNetworkInfoDTO;
import net.protsenko.spotfetchprice.props.BybitApiProperties;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.protsenko.spotfetchprice.util.BybitApiSignUtils.sign;
import static net.protsenko.spotfetchprice.util.NetworkNormalizer.normalize;

@Slf4j
@Component
@RequiredArgsConstructor
public class BybitTradingInfoProvider implements TradingInfoProvider {

    private final BybitApiProperties bybitApiProperties;
    @Qualifier("tradingInfoRedisTemplate")
    private final RedisTemplate<String, TradingInfoDTO> redisTemplate;

    private final WebClient webClient = WebClient.builder().build();

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
            log.error("Ошибка BybitTradingInfoProvider: ", ex);
        }
        return stub();
    }

    private String buildRedisKey(String coin) {
        return "tradingInfo:bybit:" + coin;
    }

    private TradingInfoDTO getFromCache(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    private void putToCache(String key, TradingInfoDTO dto) {
        redisTemplate.opsForValue().set(key, dto, 24, TimeUnit.HOURS);
    }

    private String fetchCoinInfoFromApi(ExchangeType exchange, String coin) {
        long timestamp = System.currentTimeMillis();
        String query = "coin=" + coin;
        String preSign = timestamp + bybitApiProperties.getKey() + BybitApiProperties.RECV_WINDOW + query;
        String signStr = sign(preSign, bybitApiProperties.getSecret());
        String url = BybitApiProperties.API_URL + "?" + query;

        try {
            return webClient.get()
                    .uri(url)
                    .header("X-BAPI-API-KEY", bybitApiProperties.getKey())
                    .header("X-BAPI-TIMESTAMP", String.valueOf(timestamp))
                    .header("X-BAPI-RECV-WINDOW", BybitApiProperties.RECV_WINDOW)
                    .header("X-BAPI-SIGN", signStr)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .onErrorResume(e -> {
                        log.error("Ошибка при вызове {} API: {}", exchange.name(), e.getMessage(), e);
                        return Mono.just("");
                    })
                    .block();
        } catch (Exception ex) {
            log.error("Ошибка запроса к Bybit API: ", ex);
            return null;
        }
    }

    private TradingInfoDTO parseTradingInfo(String response) {
        if (response == null || response.isEmpty()) return null;

        JSONObject obj = new JSONObject(response);
        if (obj.optInt("retCode") != 0) return null;

        JSONObject result = obj.optJSONObject("result");
        if (result == null) return null;

        JSONArray rows = result.optJSONArray("rows");
        if (rows == null || rows.isEmpty()) return null;

        JSONObject coinObj = rows.getJSONObject(0);
        JSONArray chains = coinObj.optJSONArray("chains");
        if (chains == null || chains.isEmpty()) return null;

        List<TradingNetworkInfoDTO> networks = new ArrayList<>();
        for (int i = 0; i < chains.length(); i++) {
            networks.add(parseChain(chains.getJSONObject(i)));
        }

        return new TradingInfoDTO(networks);
    }

    private TradingNetworkInfoDTO parseChain(JSONObject chain) {
        String networkRaw = chain.optString("chainType", chain.optString("chain", ""));
        String network = normalize(networkRaw);
        boolean depositEnable = chain.optInt("depositEnable", chain.optInt("chainDeposit", 0)) == 1;
        boolean withdrawEnable = chain.optInt("withdrawEnable", chain.optInt("chainWithdraw", 0)) == 1;
        double withdrawFee = 0;
        try {
            withdrawFee = Double.parseDouble(chain.optString("withdrawFee", "0"));
        } catch (Exception ignore) {
        }
        return new TradingNetworkInfoDTO(network, withdrawFee, depositEnable, withdrawEnable);
    }

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("N/A", -1.0, false, false)
        ));
    }

}
