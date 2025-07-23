package net.protsenko.spotfetchprice.service.exchange;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.mapper.TickerMapper;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.bitfinex.service.BitfinexMarketDataServiceRaw;
import org.knowm.xchange.bitfinex.v2.dto.marketdata.BitfinexTicker;
import org.knowm.xchange.currency.CurrencyPair;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
public class BitfinexClient extends BaseXChangeClient {

    public BitfinexClient(Exchange exchange) {
        super(exchange);
    }

    @Override
    public List<TickerDTO> getTickers(List<CurrencyPair> instruments) {
        BitfinexExchange bitfinexExchange = (BitfinexExchange) exchange;
        @SuppressWarnings("UnstableApiUsage")
        BitfinexMarketDataServiceRaw rawService = new BitfinexMarketDataServiceRaw(
                bitfinexExchange, bitfinexExchange.getResilienceRegistries()
        );

        List<CurrencyPair> pairsToQuery = instruments == null ?
                getCurrencyPairs() :
                instruments;

        try {
            BitfinexTicker[] bitfinexTickers = rawService.getBitfinexTickers(pairsToQuery);
            return Arrays.stream(bitfinexTickers)
                    .filter(ticker -> !ticker.getSymbol().startsWith("f"))
                    .map(TickerMapper::fromBitfinexV2Ticker)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            log.error("Error fetching tickers from Bitfinex: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BITFINEX;
    }
}