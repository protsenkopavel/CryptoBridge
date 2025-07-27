package net.protsenko.spotfetchprice.service.provider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TradingInfoProviderFactory {
    private final BybitTradingInfoProvider bybitProvider;
    private final KucoinTradingInfoProvider kucoinProvider;
    private final GateIOTradingInfoProvider gateIOTradingInfoProvider;
    private final MEXCTradingInfoProvider mexcTradingInfoProvider;
    private final BitgetTradingInfoProvider bitgetTradingInfoProvider;
    private final BingXTradingInfoProvider bingxTradingInfoProvider;
    private final OKXTradingInfoProvider okxTradingInfoProvider;
    private final CoinExTradingInfoProvider coinExTradingInfoProvider;

    private final Map<ExchangeType, TradingInfoProvider> providers = new EnumMap<>(ExchangeType.class);

    @PostConstruct
    public void init() {
        providers.put(ExchangeType.KUCOIN, kucoinProvider);
        providers.put(ExchangeType.BYBIT, bybitProvider);
        providers.put(ExchangeType.GATEIO, gateIOTradingInfoProvider);
        providers.put(ExchangeType.MEXC, mexcTradingInfoProvider);
        providers.put(ExchangeType.BITGET, bitgetTradingInfoProvider);
        providers.put(ExchangeType.BINGX, bingxTradingInfoProvider);
        providers.put(ExchangeType.OKX, okxTradingInfoProvider);
        providers.put(ExchangeType.COINEX, coinExTradingInfoProvider);


        for (ExchangeType type : ExchangeType.values()) {
            providers.putIfAbsent(type, new StubTradingInfoProvider());
        }
    }

    public TradingInfoProvider getProvider(ExchangeType type) {
        return providers.getOrDefault(type, new StubTradingInfoProvider());
    }
}