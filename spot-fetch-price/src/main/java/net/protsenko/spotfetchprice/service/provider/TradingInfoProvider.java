package net.protsenko.spotfetchprice.service.provider;

import net.protsenko.spotfetchprice.dto.TradingInfoDTO;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.currency.CurrencyPair;

public interface TradingInfoProvider {
    TradingInfoDTO getTradingInfo(ExchangeType exchange, CurrencyPair pair);
}
