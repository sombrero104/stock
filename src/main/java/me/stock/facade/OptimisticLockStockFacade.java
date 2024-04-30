package me.stock.facade;

import me.stock.service.OptimisticLockStockService;
import org.springframework.stereotype.Component;

@Component
public class OptimisticLockStockFacade {

    private final OptimisticLockStockService optimisticLockStockService;

    public OptimisticLockStockFacade(OptimisticLockStockService optimisticLockStockService) {
        this.optimisticLockStockService = optimisticLockStockService;
    }

    public void decrease(Long id, Long quantity) throws InterruptedException {
        while (true) {
            try {
                optimisticLockStockService.decrease(id, quantity);

                break; // 위에서 정상적으로 종료 시 break로 while문을 빠져나가도록 함.
            } catch (Exception e) {
                Thread.sleep(50); // 에러 발생 시 50 밀리세컨드 후 재시도.
            }
        }
    }

}
