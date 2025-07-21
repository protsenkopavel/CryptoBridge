package net.protsenko.cryptobridge.cryptobridge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.cryptobridge.cryptobridge.dto.ExchangeTickersDTO;
import net.protsenko.cryptobridge.cryptobridge.dto.PriceSpreadResult;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceSpreadService {

    private final ExchangeService exchangeService;

    public Optional<PriceSpreadResult> findMaxPriceSpreadForPair(CurrencyPair pair, List<ExchangeType> exchanges) {
        List<ExchangeTickersDTO> tickersByExchange = exchangeService.getAllMarketDataForAllExchanges(
                exchanges,
                List.of(pair)
        );

        Map<String, Double> pricesByExchange = new HashMap<>();

        for (ExchangeTickersDTO dto : tickersByExchange) {
            if (dto == null || dto.tickers() == null) continue;

            dto.tickers().stream()
                    .filter(tickerDTO -> pair.equals(new CurrencyPair(tickerDTO.baseCurrency(), tickerDTO.counterCurrency())))
                    .findFirst()
                    .ifPresent(tickerDTO -> pricesByExchange.put(dto.exchangeName(), tickerDTO.last()));
        }

        if (pricesByExchange.size() < 2) {
            return Optional.empty();
        }

        PriceSpreadResult maxSpread = getPriceSpreadResult(pair, pricesByExchange);

        return Optional.ofNullable(maxSpread);
    }

    private PriceSpreadResult getPriceSpreadResult(CurrencyPair pair, Map<String, Double> pricesByExchange) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(pricesByExchange.entrySet());

        PriceSpreadResult maxSpread = null;
        double maxDiff = 0.0;

        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                var e1 = entries.get(i);
                var e2 = entries.get(j);

                double diff = Math.abs(e1.getValue() - e2.getValue());
                if (diff > maxDiff) {
                    maxDiff = diff;
                    maxSpread = new PriceSpreadResult(
                            pair,
                            e1.getKey(),
                            e1.getValue(),
                            e2.getKey(),
                            e2.getValue(),
                            diff
                    );
                }
            }
        }

        return maxSpread;
    }

}