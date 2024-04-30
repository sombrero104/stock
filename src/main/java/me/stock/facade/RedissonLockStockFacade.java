package me.stock.facade;

import me.stock.service.StockService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedissonLockStockFacade {

    private RedissonClient redissonClient;
    private StockService stockService;

    public RedissonLockStockFacade(RedissonClient redissonClient, StockService stockService) {
        this.redissonClient = redissonClient;
        this.stockService = stockService;
    }

    public void decrease(Long id, Long quantity) {
        RLock lock = redissonClient.getLock(id.toString());

        try {
            boolean available = lock.tryLock(15, 1, TimeUnit.SECONDS);
            // 몇 초 동안 획득을 시도할 것인지, 몇 초 동안 락을 점유할 것인지 설정한다.
            // (테스트 실행 시 실패하는 경우 waitTime을 좀 더 늘려준다.)

            if(!available) {
                System.out.println("lock 획득 실패.");
                return;
            }

            // 성공적으로 락을 획득하였다면, 재고를 감소하는 비즈니스 로직 실행.
            stockService.decrease(id, quantity);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

}
