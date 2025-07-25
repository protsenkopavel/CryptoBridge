package net.protsenko.spotfetchprice.service;

import lombok.RequiredArgsConstructor;
import net.protsenko.spotfetchprice.entity.CoinBlacklist;
import net.protsenko.spotfetchprice.entity.CoinWhitelist;
import net.protsenko.spotfetchprice.repo.CoinBlacklistRepository;
import net.protsenko.spotfetchprice.repo.CoinWhitelistRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArbitrageScannerConfigService {

    private final CoinWhitelistRepository whitelistRepository;

    private final CoinBlacklistRepository blacklistRepository;

    public List<String> getWhitelist() {
        return whitelistRepository.findAll().stream().map(CoinWhitelist::getSymbol).toList();
    }
    public List<String> getBlacklist() {
        return blacklistRepository.findAll().stream().map(CoinBlacklist::getSymbol).toList();
    }
}