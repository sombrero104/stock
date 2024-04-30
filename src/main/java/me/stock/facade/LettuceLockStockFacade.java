package me.stock.facade;

import me.stock.repository.RedisLockRepository;
import me.stock.service.StockService;
import org.springframework.stereotype.Component;

@Component
public class LettuceLockStockFacade {

    private RedisLockRepository redisLockRepository;
    private StockService stockService;

    public LettuceLockStockFacade(RedisLockRepository redisLockRepository, StockService stockService) {
        this.redisLockRepository = redisLockRepository;
        this.stockService = stockService;
    }

    public void decrease(Long id, Long quantity) throws InterruptedException {
        while (!redisLockRepository.lock(id)) {
            Thread.sleep(100); // 락을 획득하지 못하면 100밀리세컨드 대기한 후 다시 재요청한다. (성능상 이점)
        }

        try {
            stockService.decrease(id, quantity); // 비즈니스 로직 실행.
        } finally {
            redisLockRepository.unlock(id); // 비즈니스 로직 실행이 끝나면 락을 해제한다.
        }
    }

}
