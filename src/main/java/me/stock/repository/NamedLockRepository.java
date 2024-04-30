package me.stock.repository;

import me.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NamedLockRepository extends JpaRepository<Stock, Long> {
    /**
     * 이 예제에서는 편의성을 위해 위처럼 Stock 엔티티를 사용하지만, 실무에서는 별도의 JDBC를 사용하는 것을 권장.
     * (이 예제에서는 하나의 데이터소스를 사용하지만,
     * 실제로 사용할 때에는 데이터소스를 분리해서 사용하는 것을 권장한다.
     * 같은 데이터소스를 사용하면 커넥션 풀이 부족해지는 현상으로 인해 다른 서비스에도 영향일 끼칠 수 있기 때문이다.)
     */

    @Query(value = "select get_lock(:key, 3000)", nativeQuery = true)
    void getLock(String key);

    @Query(value = "select release_lock(:key)", nativeQuery = true)
    void releaseLock(String key);

}
