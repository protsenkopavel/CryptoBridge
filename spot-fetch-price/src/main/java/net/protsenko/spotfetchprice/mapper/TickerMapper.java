package net.protsenko.spotfetchprice.mapper;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.kucoin.dto.response.AllTickersTickerResponse;

import java.math.BigDecimal;

@Slf4j
@UtilityClass
public class TickerMapper {

    public TickerDTO fromTicker(Ticker ticker) {
        return new TickerDTO(
                ticker.getInstrument().getBase().getCurrencyCode(),
                ticker.getInstrument().getCounter().getCurrencyCode(),
                safeDouble(ticker.getLast()),
                safeDouble(ticker.getBid()),
                safeDouble(ticker.getAsk()),
                safeDouble(ticker.getVolume()),
                ticker.getTimestamp() != null ? ticker.getTimestamp().getTime() : 0L
        );
    }

    public TickerDTO fromKucoinTicker(AllTickersTickerResponse ticker) {
        CurrencyPair pair = parseKucoinSymbol(ticker.getSymbol());

        return new TickerDTO(
                pair.getBase().getCurrencyCode(),
                pair.getCounter().getCurrencyCode(),
                ticker.getLast() != null ? ticker.getLast().doubleValue() : 0,
                ticker.getBuy() != null ? ticker.getBuy().doubleValue() : 0,
                ticker.getSell() != null ? ticker.getSell().doubleValue() : 0,
                ticker.getVol() != null ? ticker.getVol().doubleValue() : 0,
                0L
        );
    }

    public TickerDTO fromBitfinexV2Ticker(org.knowm.xchange.bitfinex.v2.dto.marketdata.BitfinexTicker ticker) {
        if (ticker == null) {
            return null;
        }

        CurrencyPair pair = parseBitfinexSymbol(ticker.getSymbol());

        double bid = safeDouble(ticker.getBid());
        double ask = safeDouble(ticker.getAsk());
        double last = safeDouble(ticker.getLastPrice());
        double volume = safeDouble(ticker.getVolume());

        return new TickerDTO(
                pair.getBase().getCurrencyCode(),
                pair.getCounter().getCurrencyCode(),
                last,
                bid,
                ask,
                volume,
                0L
        );
    }

    private double safeDouble(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0.0;
    }

    private CurrencyPair parseBitfinexSymbol(String symbol) {
        log.debug("Parsing Bitfinex symbol: {}", symbol);
        if (symbol == null || !symbol.startsWith("t")) {
            throw new IllegalArgumentException("Invalid Bitfinex symbol: " + symbol);
        }
        String coreSymbol = symbol.substring(1);

        if (coreSymbol.length() < 6) {
            throw new IllegalArgumentException("Symbol too short for pair: " + coreSymbol);
        }

        String base = coreSymbol.substring(0, 3);
        String counter = coreSymbol.substring(3);

        return new CurrencyPair(base, counter);
    }

    public CurrencyPair parseKucoinSymbol(String symbol) {
        String[] parts = symbol.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid Kucoin symbol format: " + symbol);
        }
        return new CurrencyPair(parts[0], parts[1]);
    }

}