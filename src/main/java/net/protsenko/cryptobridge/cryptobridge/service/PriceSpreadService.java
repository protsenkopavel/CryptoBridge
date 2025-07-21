package net.protsenko.cryptobridge.cryptobridge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.cryptobridge.cryptobridge.dto.ExchangeTickersDTO;
import net.protsenko.cryptobridge.cryptobridge.dto.PriceSpreadResult;
import net.protsenko.cryptobridge.cryptobridge.dto.TickerData;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceSpreadService {

    private final ExchangeService exchangeService;

    public Optional<PriceSpreadResult> findMaxArbitrageSpreadForPair(
            CurrencyPair pair,
            List<ExchangeType> exchanges,
            double minVolume
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

        return findMaxSpread(pair, tickerDataMap);
    }

    private Optional<PriceSpreadResult> findMaxSpread(CurrencyPair pair, Map<String, TickerData> tickerDataMap) {
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