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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.protsenko.spotfetchprice.util.KucoinApiSignUtils.sign;
import static net.protsenko.spotfetchprice.util.NetworkNormalizer.normalize;

@Slf4j
@Component
@RequiredArgsConstructor
public class KucoinTradingInfoProvider implements TradingInfoProvider {

    private final KucoinApiProperties kucoinApiProperties;
    @Qualifier("tradingInfoRedisTemplate")
    private final RedisTemplate<String, TradingInfoDTO> redisTemplate;
    private final WebClient webClient = WebClient.builder()
            .baseUrl(KucoinApiProperties.baseUrl)
            .build();

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
            log.error("KucoinTradingInfoProvider exception: ", ex);
        }
        return stub();
    }

    private String buildRedisKey(String coin) {
        return "tradingInfo:kucoin:" + coin;
    }

    private TradingInfoDTO getFromCache(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    private void putToCache(String key, TradingInfoDTO dto) {
        redisTemplate.opsForValue().set(key, dto, 24, TimeUnit.HOURS);
    }

    private String fetchCoinInfoFromApi(ExchangeType exchange, String coin) throws Exception {
        long timestamp = System.currentTimeMillis();
        String url = KucoinApiProperties.baseUrl + coin;
        String strToSign = timestamp + "GET" + "/api/v3/currencies/" + coin;
        String signature = sign(strToSign, kucoinApiProperties.getSecret());

        return webClient.get()
                .uri(url)
                .header("KC-API-KEY", kucoinApiProperties.getKey())
                .header("KC-API-SIGN", signature)
                .header("KC-API-TIMESTAMP", String.valueOf(timestamp))
                .header("KC-API-PASSPHRASE", kucoinApiProperties.getPassphrase())
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
        if (!"200000".equals(root.optString("code"))) {
            log.warn("Kucoin error: {}", root.optString("msg"));
            return null;
        }

        JSONObject data = root.optJSONObject("data");
        JSONArray chains = data != null ? data.optJSONArray("chains") : null;
        if (chains == null || chains.isEmpty()) {
            log.warn("Kucoin: chains empty");
            return null;
        }

        List<TradingNetworkInfoDTO> networks = new ArrayList<>();
        for (int i = 0; i < chains.length(); i++) {
            networks.add(parseChain(chains.getJSONObject(i)));
        }
        return new TradingInfoDTO(networks);
    }

    private TradingNetworkInfoDTO parseChain(JSONObject chain) {
        String networkRaw = chain.optString("chainName", "");
        String network = normalize(networkRaw);
        boolean depositEnabled = chain.optBoolean("isDepositEnabled", false);
        boolean withdrawEnabled = chain.optBoolean("isWithdrawEnabled", false);
        double withdrawFee = 0;
        try {
            withdrawFee = Double.parseDouble(chain.optString("withdrawalMinFee", "0"));
        } catch (Exception ignore) {
        }
        return new TradingNetworkInfoDTO(network, withdrawFee, depositEnabled, withdrawEnabled);
    }

    private TradingInfoDTO stub() {
        return new TradingInfoDTO(List.of(
                new TradingNetworkInfoDTO("", -1.0, false, false)
        ));
    }

}