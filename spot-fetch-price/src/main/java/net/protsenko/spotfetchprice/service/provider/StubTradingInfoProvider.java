package net.protsenko.spotfetchprice.service.provider;

import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.currency.CurrencyPair;

import java.util.List;

public class StubTradingInfoProvider implements TradingInfoProvider {
    @Override
    public TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair) {
        return new TradingInfoDTO(List.of());
    }
}
