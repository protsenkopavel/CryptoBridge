package net.protsenko.spotfetchprice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.*;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceSpreadService {

    private final ExchangeService exchangeService;

    public List<PriceSpreadResult> findMaxArbitrageSpreadsForPairs(SpreadsRq spreadsRq) {
        List<ExchangeType> exchangeTypes = parseExchangeTypes(spreadsRq.exchanges());

        List<CurrencyPair> currencyPairs;
        if (spreadsRq.pairs() == null || spreadsRq.pairs().isEmpty()) {
            currencyPairs = exchangeService.getAvailableCurrencyPairs(exchangeTypes);
        } else {
            currencyPairs = parseCurrencyPairs(spreadsRq.pairs());
        }

        List<ExchangeTickersDTO> allTickers = exchangeService.getAllMarketDataForAllExchanges(exchangeTypes, currencyPairs);

        Map<CurrencyPair, Map<String, TickerData>> tickersByPair = new HashMap<>();
        for (ExchangeTickersDTO exchangeTickers : allTickers) {
            if (exchangeTickers == null || exchangeTickers.tickers() == null) continue;
            for (TickerDTO ticker : exchangeTickers.tickers()) {
                if (ticker.bid() > 0 && ticker.ask() > 0 && ticker.volume() >= spreadsRq.minVolume()) {
                    CurrencyPair pair = new CurrencyPair(ticker.baseCurrency(), ticker.counterCurrency());
                    tickersByPair
                            .computeIfAbsent(pair, k -> new HashMap<>())
                            .put(exchangeTickers.exchangeName(), new TickerData(ticker.bid(), ticker.ask(), ticker.volume()));
                }
            }
        }

        return tickersByPair.entrySet().parallelStream()
                .map(entry -> findMaxSpread(entry.getKey(), entry.getValue(), spreadsRq.minProfitPercent(), spreadsRq.maxProfitPercent()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public Optional<PriceSpreadResult> findMaxArbitrageSpreadForPair(
            CurrencyPair pair,
            List<ExchangeType> exchanges,
            double minVolume,
            double minProfitPercent,
            double maxProfitPercent
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

        return findMaxSpread(pair, tickerDataMap, minProfitPercent, maxProfitPercent);
    }

    private Optional<PriceSpreadResult> findMaxSpread(
            CurrencyPair pair,
            Map<String, TickerData> tickerDataMap,
            double minProfitPercent,
            double maxProfitPercent
    ) {
        var entries = new ArrayList<>(tickerDataMap.entrySet());

        return entries.stream()
                .flatMap(eSell -> entries.stream()
                        .filter(eBuy -> !eBuy.getKey().equals(eSell.getKey()))
                        .map(eBuy -> new PriceSpreadCandidate(
                                eBuy.getKey(), eBuy.getValue().ask(), eBuy.getValue().volume(),
                                eSell.getKey(), eSell.getValue().bid(), eSell.getValue().volume()
                        ))
                )
                .filter(c -> c.spread() > 0)
                .filter(c -> {
                    double profitPercent = (c.sellPrice() - c.buyPrice()) / c.buyPrice() * 100.0;
                    return profitPercent >= minProfitPercent && profitPercent <= maxProfitPercent;
                })
                .max(Comparator.comparingDouble(PriceSpreadCandidate::spread))
                .map(c -> new PriceSpreadResult(
                        pair,
                        c.buyExchange(), c.buyPrice(), c.buyVolume(),
                        c.sellExchange(), c.sellPrice(), c.sellVolume(),
                        c.spread(),
                        (c.sellPrice() - c.buyPrice()) / c.buyPrice() * 100.0
                ));
    }

    public List<ExchangeType> parseExchangeTypes(List<String> exchanges) {
        if (exchanges == null || exchanges.isEmpty()) {
            return exchangeService.getAvailableExchanges();
        }
        return exchanges.stream()
                .map(ExchangeType::valueOf)
                .toList();
    }

    public List<CurrencyPair> parseCurrencyPairs(List<String> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            throw new IllegalArgumentException("Currency pairs list is empty or null");
        }
        return pairs.stream()
                .map(this::parseCurrencyPair)
                .toList();
    }

    private CurrencyPair parseCurrencyPair(String pairStr) {
        String normalized = pairStr.replace('/', '_');
        String[] parts = normalized.split("_");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid currency pair format: " + pairStr);
        }
        return new CurrencyPair(parts[0], parts[1]);
    }

}