package net.protsenko.spotfetchprice.repo;

import net.protsenko.spotfetchprice.entity.CoinBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoinBlacklistRepository extends JpaRepository<CoinBlacklist, Long> {
}