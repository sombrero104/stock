package me.stock.service;

import me.stock.domain.Stock;
import me.stock.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * NamedLock 사용 시,
     * 부모의 트랜잭션과 별도의 트랜잭션으로 실행해야 하기 때문에
     * propagation 설정을 'REQUIRES_NEW'로 설정해준다.
     *
     * (강의 질문 설명)
     * 부모의 트랜잭션과 동일한 범위로 묶인다면 Synchronized 와 같은 문제가 발생합니다.
     * Database 에 commit 되기전에 락이 풀리는 현상이 발생합니다.
     * 그렇기때문에 별도의 트랜잭션으로 분리를 해주어 Database 에 정상적으로 commit 이 된 이후에 락을 해제하는것을 의도하였습니다.
     * 핵심은 lock 을 해제하기전에 Database 에 commit 이 되도록 하는것입니다.
     *
     * https://www.inflearn.com/questions/1195339
     *
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // NamedLock 사용 시
    // @Transactional
    public void decrease(Long id, Long quantity) {
    // public synchronized void decrease(Long id, Long quantity) {
        // Stock 조회
        // 재고를 감소시킨 뒤
        // 갱신된 값을 저장.

        Stock stock = stockRepository.findById(id).orElseThrow();
        stock.decrease(quantity);
        stockRepository.saveAndFlush(stock);
    }

}
