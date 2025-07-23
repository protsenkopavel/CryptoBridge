package net.protsenko.spotfetchprice.service.exchange;

import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.kucoin.KucoinMarketDataServiceRaw;
import org.knowm.xchange.kucoin.dto.response.AllTickersResponse;
import org.knowm.xchange.kucoin.dto.response.AllTickersTickerResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static net.protsenko.spotfetchprice.dto.TickerDTO.parseKucoinSymbol;

public class KucoinClient extends BaseXChangeClient {

    public KucoinClient(Exchange exchange) {
        super(exchange);
    }

    @Override
    public List<TickerDTO> getTickers(List<CurrencyPair> instruments) throws IOException {
        KucoinMarketDataServiceRaw rawService = (KucoinMarketDataServiceRaw) exchange.getMarketDataService();
        AllTickersResponse allTickersResponse = rawService.getKucoinTickers();

        AllTickersTickerResponse[] tickersArray = allTickersResponse.getTicker();

        return Arrays.stream(tickersArray)
                .filter(ticker -> instruments == null || instruments.isEmpty() ||
                        instruments.contains(parseKucoinSymbol(ticker.getSymbol())))
                .map(TickerDTO::fromKucoinTicker)
                .toList();
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.KUCOIN;
    }
}