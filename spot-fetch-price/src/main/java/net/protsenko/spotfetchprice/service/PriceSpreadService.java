package net.protsenko.spotfetchprice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.*;
import net.protsenko.spotfetchprice.service.provider.TradingInfoProviderFactory;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceSpreadService {

    private final ExchangeService exchangeService;
    private final TradingInfoProviderFactory tradingInfoProviderFactory;

    public List<PriceSpreadResult> findMaxArbitrageSpreadsForPairs(SpreadsRq spreadsRq) {
        List<ExchangeType> exchangeTypes = parseExchangeTypes(spreadsRq.exchanges());

        List<CurrencyPair> currencyPairs;
        if (spreadsRq.pairs() == null || spreadsRq.pairs().isEmpty()) {
            currencyPairs = exchangeService.getAvailableCurrencyPairs(exchangeTypes);
        } else {
            currencyPairs = parseCurrencyPairs(spreadsRq.pairs());
        }

        currencyPairs = filterCurrencyPairs(currencyPairs, spreadsRq.whitelist(), spreadsRq.blacklist());

        Map<ExchangeType, Map<CurrencyPair, TickerDTO>> allTickersByExchange =
                exchangeService.getAllMarketDataForAllExchanges(exchangeTypes, currencyPairs);

        Map<CurrencyPair, Map<ExchangeType, TickerDTO>> tickersByPair = new HashMap<>();
        for (var exchangeEntry : allTickersByExchange.entrySet()) {
            ExchangeType exchangeType = exchangeEntry.getKey();
            Map<CurrencyPair, TickerDTO> pairMap = exchangeEntry.getValue();
            for (var pairEntry : pairMap.entrySet()) {
                CurrencyPair pair = pairEntry.getKey();
                TickerDTO ticker = pairEntry.getValue();
                if (ticker.bid() > 0 && ticker.ask() > 0 && ticker.volume() >= spreadsRq.minVolume()) {
                    tickersByPair.computeIfAbsent(pair, k -> new HashMap<>())
                            .put(exchangeType, ticker);
                }
            }
        }

        return tickersByPair.entrySet().parallelStream()
                .map(entry -> {
                    CurrencyPair pair = entry.getKey();
                    Map<ExchangeType, TickerDTO> tickerMap = entry.getValue();
                    Map<String, TickerData> dataMap = tickerMap.entrySet().stream()
                            .collect(Collectors.toMap(
                                    e -> e.getKey().name(),
                                    e -> new TickerData(e.getValue().bid(), e.getValue().ask(), e.getValue().volume())
                            ));
                    return findMaxSpread(pair, dataMap, spreadsRq.minProfitPercent(), spreadsRq.maxProfitPercent());
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private List<CurrencyPair> filterCurrencyPairs(List<CurrencyPair> pairs, List<String> whitelist, List<String> blacklist) {
        return pairs.stream()
                .filter(pair -> {
                    String counter = pair.getCounter().toString();

                    boolean allowed = (whitelist == null || whitelist.isEmpty()) || whitelist.contains(counter);

                    boolean forbidden = (blacklist != null && blacklist.contains(counter));

                    return allowed && !forbidden;
                })
                .collect(Collectors.toList());
    }

    public Optional<PriceSpreadResult> findMaxArbitrageSpreadForPair(
            CurrencyPair pair,
            List<ExchangeType> exchanges,
            double minVolume,
            double minProfitPercent,
            double maxProfitPercent
    ) {
        Map<ExchangeType, Map<CurrencyPair, TickerDTO>> tickersByExchange =
                exchangeService.getAllMarketDataForAllExchanges(exchanges, List.of(pair));

        Map<String, TickerData> tickerDataMap = new HashMap<>();

        for (Map.Entry<ExchangeType, Map<CurrencyPair, TickerDTO>> entry : tickersByExchange.entrySet()) {
            ExchangeType exchangeType = entry.getKey();
            Map<CurrencyPair, TickerDTO> pairMap = entry.getValue();
            TickerDTO ticker = pairMap.get(pair);
            if (ticker != null && ticker.bid() > 0 && ticker.ask() > 0 && ticker.volume() >= minVolume) {
                tickerDataMap.put(exchangeType.name(), new TickerData(ticker.bid(), ticker.ask(), ticker.volume()));
            }
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
        if (tickerDataMap.size() < 2) {
            return Optional.empty();
        }

        Map.Entry<String, TickerData> minAskEntry = null;
        Map.Entry<String, TickerData> secondMinAskEntry = null;

        Map.Entry<String, TickerData> maxBidEntry = null;
        Map.Entry<String, TickerData> secondMaxBidEntry = null;

        for (Map.Entry<String, TickerData> entry : tickerDataMap.entrySet()) {
            if (minAskEntry == null || entry.getValue().ask() < minAskEntry.getValue().ask()) {
                secondMinAskEntry = minAskEntry;
                minAskEntry = entry;
            } else if (secondMinAskEntry == null || entry.getValue().ask() < secondMinAskEntry.getValue().ask()) {
                secondMinAskEntry = entry;
            }

            if (maxBidEntry == null || entry.getValue().bid() > maxBidEntry.getValue().bid()) {
                secondMaxBidEntry = maxBidEntry;
                maxBidEntry = entry;
            } else if (secondMaxBidEntry == null || entry.getValue().bid() > secondMaxBidEntry.getValue().bid()) {
                secondMaxBidEntry = entry;
            }
        }

        if (minAskEntry == null) {
            return Optional.empty();
        }

        PriceSpreadCandidate bestCandidate;

        if (!minAskEntry.getKey().equals(maxBidEntry.getKey())) {
            bestCandidate = new PriceSpreadCandidate(
                    minAskEntry.getKey(), minAskEntry.getValue().ask(), minAskEntry.getValue().volume(),
                    maxBidEntry.getKey(), maxBidEntry.getValue().bid(), maxBidEntry.getValue().volume()
            );
        } else {
            PriceSpreadCandidate candidate1 = null;
            if (secondMinAskEntry != null) {
                candidate1 = new PriceSpreadCandidate(
                        secondMinAskEntry.getKey(), secondMinAskEntry.getValue().ask(), secondMinAskEntry.getValue().volume(),
                        maxBidEntry.getKey(), maxBidEntry.getValue().bid(), maxBidEntry.getValue().volume()
                );
            }

            PriceSpreadCandidate candidate2 = null;
            if (secondMaxBidEntry != null) {
                candidate2 = new PriceSpreadCandidate(
                        minAskEntry.getKey(), minAskEntry.getValue().ask(), minAskEntry.getValue().volume(),
                        secondMaxBidEntry.getKey(), secondMaxBidEntry.getValue().bid(), secondMaxBidEntry.getValue().volume()
                );
            }

            if (candidate1 != null && candidate2 != null) {
                bestCandidate = candidate1.spread() > candidate2.spread() ? candidate1 : candidate2;
            } else if (candidate1 != null) {
                bestCandidate = candidate1;
            } else if (candidate2 != null) {
                bestCandidate = candidate2;
            } else {
                return Optional.empty();
            }
        }

        if (bestCandidate.spread() <= 0) {
            return Optional.empty();
        }

        double profitPercent = (bestCandidate.sellPrice() - bestCandidate.buyPrice()) / bestCandidate.buyPrice() * 100.0;
        if (profitPercent >= minProfitPercent && profitPercent <= maxProfitPercent) {
            ExchangeType buyType = ExchangeType.valueOf(bestCandidate.buyExchange());
            ExchangeType sellType = ExchangeType.valueOf(bestCandidate.sellExchange());
            TradingInfoDTO buyTradingInfo = tradingInfoProviderFactory.getProvider(buyType)
                    .getTradingInfo(buyType, pair);
            TradingInfoDTO sellTradingInfo = tradingInfoProviderFactory.getProvider(sellType)
                    .getTradingInfo(sellType, pair);

            return Optional.of(new PriceSpreadResult(
                    pair,
                    bestCandidate.buyExchange(), bestCandidate.buyPrice(), bestCandidate.buyVolume(), buyTradingInfo,
                    bestCandidate.sellExchange(), bestCandidate.sellPrice(), bestCandidate.sellVolume(), sellTradingInfo,
                    bestCandidate.spread(),
                    profitPercent
            ));
        }

        return Optional.empty();
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
        String cleanPairStr = pairStr
                .replaceAll("^[^A-Z0-9]+", "")
                .replaceAll("[^A-Z0-9/_]", "");
        String normalized = cleanPairStr.replace('/', '_');
        String[] parts = normalized.split("_");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid currency pair format: " + pairStr);
        }
        return new CurrencyPair(parts[0], parts[1]);
    }

}