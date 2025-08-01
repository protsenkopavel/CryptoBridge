package net.protsenko.spotfetchprice.service;

import lombok.Data;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Data
public class ArbitrageScannerConfig {

    private List<CurrencyPair> pairsToScan = null;

    private List<ExchangeType> exchangesToScan = null;

    private double minVolume = 0.0;

    private double minProfitPercent = 0.0;

    private double maxProfitPercent = 20.0;

    private List<String> whitelist = null;

    private List<String> blacklist = null;

}
