package net.protsenko.spotfetchprice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.ExchangeTickersDTO;
import net.protsenko.spotfetchprice.dto.PriceSpreadResult;
import net.protsenko.spotfetchprice.dto.TickerData;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceSpreadService {

    private final net.protsenko.spotfetchprice.service.ExchangeService exchangeService;

    public Optional<PriceSpreadResult> findMaxArbitrageSpreadForPair(
            CurrencyPair pair,
            List<ExchangeType> exchanges,
            double minVolume,
            double minProfitPercent
    ) {
        List<ExchangeTickersDTO> tickersByExchange = exchangeService.getAllMarketDataForAllExchanges(
                exchanges,
                List.of(pair)
        );

        Map<String, TickerData> tickerDataMap = new HashMap<>();

        for (ExchangeTickersDTO dto : tickersByExchange) {
            if (dto == null || dto.tickers() == null) continue;

            dto.tickers().stream()
                    .filter(tickerDTO -> pair.equals(new CurrencyPair(tickerDTO.baseCurrency(), tickerDTO.counterCurrency())))
                    .findFirst()
                    .ifPresent(ticker -> {
                        if (ticker.bid() > 0 && ticker.ask() > 0 && ticker.volume() >= minVolume) {
                            tickerDataMap.put(dto.exchangeName(), new TickerData(ticker.bid(), ticker.ask(), ticker.volume()));
                        }
                    });
        }

        if (tickerDataMap.size() < 2) {
            return Optional.empty();
        }

        return findMaxSpread(pair, tickerDataMap, minProfitPercent);
    }

    private Optional<PriceSpreadResult> findMaxSpread(
            CurrencyPair pair,
            Map<String, TickerData> tickerDataMap,
            double minProfitPercent
    ) {
        var entries = new ArrayList<>(tickerDataMap.entrySet());

        return entries.stream()
                .flatMap(eSell -> entries.stream()
                        .filter(eBuy -> !eBuy.getKey().equals(eSell.getKey()))
                        .map(eBuy -> new PriceSpreadCandidate(
                                eBuy.getKey(), eBuy.getValue().ask(),
                                eSell.getKey(), eSell.getValue().bid()
                        ))
                )
                .filter(c -> c.spread() > 0)
                .filter(c -> {
                    double profitPercent = (c.priceB - c.priceA) / c.priceA * 100.0;
                    return profitPercent >= minProfitPercent;
                })
                .max(Comparator.comparingDouble(PriceSpreadCandidate::spread))
                .map(c -> new PriceSpreadResult(
                        pair,
                        c.exchangeA, c.priceA,
                        c.exchangeB, c.priceB,
                        c.spread()
                ));
    }

    private record PriceSpreadCandidate(String exchangeA, double priceA, String exchangeB, double priceB) {
        double spread() {
            return priceB - priceA;
        }
    }
}