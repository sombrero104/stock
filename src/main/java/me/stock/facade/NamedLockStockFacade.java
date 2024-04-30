package me.stock.facade;

import me.stock.repository.NamedLockRepository;
import me.stock.service.StockService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NamedLockStockFacade {

    private final NamedLockRepository namedLockRepository;

    private final StockService stockService;

    public NamedLockStockFacade(NamedLockRepository namedLockRepository, StockService stockService) {
        this.namedLockRepository = namedLockRepository;
        this.stockService = stockService;
    }

    @Transactional
    public void decrease(Long id, Long quantity) {
        try {
            namedLockRepository.getLock(id.toString()); // MySQL의 get_lock() 호출하게 됨.
            stockService.decrease(id, quantity); // 수량을 감소하는 로직 실행. => 따로 트랜잭션을 만들어서 커밋하므로 커밋이 보장됨.
        } finally {
            namedLockRepository.releaseLock(id.toString()); // MySQL의 release_lock() 호출하게 됨.
        }
    }

}
