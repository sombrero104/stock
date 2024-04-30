<br/>

# 재고시스템으로 알아보는 동시성이슈 해결방법
<br/>

https://www.inflearn.com/course/%EB%8F%99%EC%8B%9C%EC%84%B1%EC%9D%B4%EC%8A%88-%EC%9E%AC%EA%B3%A0%EC%8B%9C%EC%8A%A4%ED%85%9C#
<br/><br/>
https://github.com/sangyongchoi/stock-example
<br/><br/><br/>

## 작업 환경 세팅

##### [Docker 설치] 
(docker 사이트에서 데스크탑 버전으로 설치해도 됨.) <br/>
~~~
brew install docker
brew link docker
docker version 
~~~

##### [MySQL 설치 및 실행]
~~~
docker pull mysql
docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=1234 -name mysql mysql
docker ps 
~~~

##### [MySQL 데이터베이스 생성]
~~~
docker exec -it mysql bash
mysql -u root -p
create database stock_example;
use stock_example;
~~~
<br/><br/>

## 레이스 컨디션(Race Condition) 이란?
- 둘 이상의 스레드가 공유 데이터에 액세스할 수 있고, 동시에 변경하려고 할 때 발생하는 문제.
    - 둘 이상의 스레드: 요청.
    - 공유 데이터: 재고 데이터.
    - 동시에 변경하려고 할 때: 업데이트 할 때 
    - 발생하는 문제: 값이 정상적으로 바뀌지 않는 문제.
- 해결방법: 하나의 스레드만 데이터에 액세스 할 수 있도록 한다.
<br/><br/>

## 1. synchronized 이용해보기 
한 번에 한 개의 스레드만 접근 가능하게 된다. <br/>
하지만 '동시에_100개의_요청()' 테스트 코드는 실패한다. <br/>
100을 스레드 100개로 1씩 감소하면 결과는 0이어야 하는데, 결과가 50으로 나온다. <br/>
이유는 StockService 의 decrease()가  @Transactional 을 사용하는데 <br/>
@Transactional 은 트랜잭션을 위하여 아래와 같은 기능을 하는 새로운 프록시 클래스를 만들게 된다. <br/>
~~~
public void decrease(Long id, Long quantity) {
    startTransaction();
    
    stockService.decrease(id, quantity);
    
    /**
      * 이 사이에서 문제가 발생한다.
      * 한 스레드가 decrease()를 실행한 후,
      * endTransaction()을 실행하여 commit하기 전에 
      * 다른 스레드가 decrease() 메소드를 호출할 수 있다. 
      * 그렇게 되면 이전 스레드의 값이 갱신되기 이전에 
      * 다른 스레드가 조회를 하기 때문에 
      * synchronized를 사용하기 전과 같은 문제가 발생한다. 
    **/ 
    
    endTransaction(); 
}
~~~

### synchronized를 사용했을 때 문제점 
- 여기서 StockService의 @Transactional을 없애면 테스트는 성공하지만, @Transactional을 사용할 수 없게 된다. <br/>
- 자바의 synchronized는 하나의 프로세스 안에서만 보장이 된다. <br/>
  - 서버가 여러대일 경우에는 여러 곳에서 데이터에 접근하게 되어 역시나 레이스 컨디션이 발생하게 된다. <br/>
  - 실제 운영 중인 서비스는 대부분 여러 대의 서버를 사용하기 때문에 synchronized는 거의 사용하지 않는다. <br/>
<br/><br/>

## 2. Database 에서 지원하는 방법 이용해보기

### Mysql 을 활용한 다양한 방법

### 2-1. Pessimistic Lock (비관적인 락) 
실제로 데이터에 Lock 을 걸어서 정합성을 맞추는 방법입니다. <br/>
exclusive lock 을 걸게되며 다른 트랜잭션에서는 lock 이 해제되기전에 데이터를 가져갈 수 없게됩니다. <br/>
충돌이 빈번할 경우에는 Optimistic Lock(낙관적인 락) 보다 성능이 좋을 수 있습니다. <br/>
하지만 데드락이 걸릴 수 있기때문에 주의하여 사용하여야 합니다. <br/> 
~~~
public interface StockRepository extends JpaRepository<Stock, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Stock s where s.id = :id")
  Stock findByIdWithPessimisticLock(Long id);
  
}
~~~

### 2-2. Optimistic Lock (낙관적인 락) 
실제로 Lock 을 이용하지 않고 버전을 이용함으로써 정합성을 맞추는 방법입니다. <br/>
먼저 데이터를 읽은 후에 update 를 수행할 때 현재 내가 읽은 버전이 맞는지 확인하며 업데이트 합니다. <br/>
내가 읽은 버전에서 수정사항이 생겼을 경우에는 application에서 다시 읽은후에 작업을 수행해야 합니다. <br/>
~~~
update set version = version + 1, quantity = 2
from stock
where id = 1 and version = 1 
~~~
Stock 엔티티에 @Version 애노테이션이 붙은 version 필드를 추가한다. <br/>
~~~
@Entity
public class Stock {

  @Version
  private Long version;
  
}
~~~
아래와 같이 업데이트 실패 시 재시도를 하는 OptimisticLockStockFacade 클래스를 만들어 준다. <br/>
~~~
public class OptimisticLockStockFacade {
  ... 
  public void decrease(Long id, Long quantity) throws InterruptedException {
        while (true) { // 계속 재시도. 
            try {
                optimisticLockStockService.decrease(id, quantity);

                break; // 위에서 정상적으로 종료 시 break로 while문을 빠져나가도록 함.
            } catch (Exception e) {
                Thread.sleep(50); // 에러 발생 시 50 밀리세컨드 후 재시도.
            }
        }
    }
}
~~~
Optimistic Lock(낙관적인 락)은 락을 사용하지 않으므로, Pessimistic Lock(비관적인 락)보다 성능상 이점이 있다. <br/>
단점으로는 업데이트 실패 시 재시도 로직을 개발자가 직접 작성해줘야 하는 번거로움이 있다. <br/>
충돌이 빈번한 경우에는 Pessimistic Lock(비관적인 락), 빈번하지 않은 경우에는 Optimistic Lock(낙관적인 락)을 추천한다. <br/>

### 2-3. Named Lock (이름을 가진 락) 
이름을 가진 metadata locking 입니다. <br/>
이름을 가진 lock 을 획득한 후 해제할때까지 다른 세션은 이 lock 을 획득할 수 없도록 합니다. <br/>
주의할점으로는 transaction 이 종료될 때 lock 이 자동으로 해제되지 않습니다. <br/>
별도의 명령어로 해제를 수행해주거나 선점시간이 끝나야 해제됩니다. <br/>
<br/>
Pessimistic Lock(비관적인 락)과 비슷한데 차이점은 <br/>
Pessimistic Lock(비관적인 락)은 로우나 테이블 단위로 락을 걸지만 <br/>
Named Lock(이름을 가진 락)은 로우나 테이블 단위가 아닌 **_메타데이터(metadata)_** 에 락을 건다. <br/>
<br/>
Pessimistic Lock(비관적인 락)에서는 Stock 테이블에 락을 걸었었지만, <br/>
Named Lock (이름을 가진 락)은 Stock에 락을 걸지 않고, 별도의 공간에 락을 건다. <br/>
<br/>
이 예제에서는 편의성을 위해 JPA의 Native Query 기능을 활용해서 구현하고, 동일한 DataSource를 사용한다. <br/>
실제로 사용할 때에는 DataSource를 분리해서 사용하는 것을 권장한다. <br/>
같은 DataSource를 사용하면 커넥션풀이 부족해져서 다른 서비스에도 영향을 끼칠 수 있기 때문이다. <br/>
<br/>
https://dev.mysql.com/doc/refman/8.0/en/ <br/>
https://dev.mysql.com/doc/refman/8.0/en/locking-functions.html <br/>
https://dev.mysql.com/doc/refman/8.0/en/metadata-locking.html <br/>
<br/>
MySQL에서는 get_lock()으로 락을 획득할 수 있고, release_lock()으로 락을 해제할 수 있다. <br/>
~~~
public interface NamedLockRepository extends JpaRepository<Stock, Long> {

    @Query(value = "select get_lock(:key, 3000)", nativeQuery = true)
    void getLock(String key);

    @Query(value = "select release_lock(:key)", nativeQuery = true)
    void releaseLock(String key);

}
~~~
<br/>

~~~
                                           -----------------------
                                           |                     |
[세션 1] -> [select get_lock('1', 1000)] -> |----------           |
                                           ||  Lock  |           |
[세션 2] -> [select get_lcok('1', 1000)] -> |----------           |
                                           |         ----------  |
                                           |         |  Stock  | |
                                           |         ----------- |
                                           -----------------------
~~~

[세션 1]이 '1'이라는 이름으로 락을 획득하면, <br/>
[세션 2]는 [세션 1]에서 사용 중인 락 '1'이 해제될 때까지 기다려야 락을 획득할 수 있다. <br/>
<br/><br/>

#### * 커넥션 풀 사이즈 문제 
> 예제에서는 하나의 데이터소스를 사용하지만, <br/>
실제로 사용할 때에는 데이터소스를 분리해서 사용하는 것을 권장한다. <br/>
같은 데이터소스를 사용하면 커넥션 풀이 부족해지는 현상으로 인해 다른 서비스에도 영향일 끼칠 수 있기 때문이다. <br/>

> (이 예제에서는 같은 데이터소스를 사용하기 때문에 <br/>
application.yml 에서 커넥션 풀 사이즈를 아래와 같이 넉넉하게 설정해 준다.) <br/>

##### [application.yml]
~~~
    hikari:
      maximum-pool-size: 40
~~~
<br/>

그리고 아래와 같이 실제 수량 감소를 실행하는 서비스 로직을 실행하기 전에 실행되는 퍼사드를 하나 만들어준다. <br/>

##### [NamedLockStockFacade.java]
~~~
@Component
public class NamedLockStockFacade {
  .....
  
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
~~~
<br/>

NamedLock은 주로 분산락을 사용할 때 사용한다. 타임아웃도 설정 가능하다. <br/>
하지만 트랜잭션 종료 시 락 해제를 해줘야 하기 때문에 주의해서 사용해야 한다. <br/>
실제로 사용할 때에는 구현 방법이 복잡해질 수 있다. <br/>
<br/><br/>

## 3. Redis 이용해보기 

### 분산 락을 구현할 때 사용하는 대표적인 라이브러리
- Lettuce
  - setnx 명령어를 활용하여 분산락 구현.
    - setnx: set if not exist의 줄임말. 
    - key와 value를 set할 때 기존에 값이 없을 때에만 set하는 명령어. 
  - setnx 명령어를 사용하는 방식은 spin lock 방식이므로, 개발자가 retry 로직을 작성해줘야 한다. 
    - spin lock: 락을 획득하려는 스레드가 락을 사용할 수 있는지 반복적으로 확인하면서 락 획득을 시도하는 방식. 
  - 장점: 구현이 간단하다.
    - (spring data redis를 사용하면 Lettuce가 기본이기 때문에 별도의 라이브러리를 사용하지 않아도 된다.)
  - 단점: spin lock 방식이므로 Redis에 부하를 줄 수 있다. 
    - (그렇기 때문에 아래 구현 예제에서는 획득 재시도 요청 시 thread.sleep()으로 텀을 주었다.) 
- Redisson
  - pub-sub 기반으로 Lock 구현 제공. 
    - pub-sub: 채널을 하나 만들고 락을 점유 중인 스레드가 대기 중인 락에게 해제를 알려주면 이 알림을 받은 스레드가 락 획득을 시도하는 방식. 
    - Lettuce 와는 다르게 별도의 retry 로직을 작성하지 않아도 됨.
  - Redisson은 자신이 점유하고 있는 락을 해제할 때, 채널에 메시지를 보내줌으로써, <br/>
    락을 획득해야 하는 스레드들에게 락을 획득하라고 전달해준다. <br/>
    그러면 대기 중인 스레드들이 메시지를 받고 락 획득을 시도하게 된다. <br/>
  - Lettuce는 계속해서 락 획득을 시도하는 반면에, <br/>
    Redisson은 락 해제가 되었을 때, 한 번 혹은 몇번만 시도를 하기 때문에 Redis에 부하를 줄여주게 된다. <br/>
  - 장점: pub-sub 방식이기 때문에 Redis의 부하를 줄여준다.
  - 단점: 구현이 복잡하고 별도의 라이브러리를 사용해야 하는 부담감이 있다. (라이브러리 사용법을 공부해야 한다.)
- 실무에서는?
  - 재시도가 필요하지 않은 lock은 Lettuce 활용.
  - 재시도가 필요한 경우에는 Redisson 활용. 
<br/>

## 3-1. Lettuce 구현하기 

### Redis 설치
~~~
docker pull redis 
docker run --name myredis -d -p 6379:6379 redis 
docker ps 
~~~
<br/>

### Redis 의존성 추가 
~~~
dependencies {
	.....
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
~~~ 
<br/>

### setnx 명령어 테스트
setnx 명령어를 사용하여 구현하기 때문에 먼저 setnx가 잘 실행되는지 확인한다. <br/>
프롬프트에서 'docker ps' 명령을 실행한 후 Redis 컨테이너의 아이디를 복사한다. <br/>
~~~
docker ps
~~~ 
그리고 아래 명령으로 위에서 복사한 Redis 컨테이너 아이디로 Redis-CLI를 실행한다. <br/>
~~~
docker exec -it 7f09ec61727e redis-cli 
~~~
key가 '1'인 데이터를 setnx 해본다. <br/>
key는 '1', value는 'lock'이라는 문자열로 지정해준다. <br/>
~~~
setnx 1 lock 
~~~
처음에는 key가 '1'인 데이터가 없기 때문에 성공한다. <br/>
하지만 다시 key가 '1'인 데이터를 setnx하면 이미 값이 있기 때문에 실패하는 것을 확인할 수 있다. <br/>
~~~
127.0.0.1:6379> setnx 1 lock
(integer) 1
127.0.0.1:6379> setnx 1 lock
(integer) 0
~~~
key를 삭제한 후에 다시 setnx를 하면 성공하는 것을 확인할 수 있다. <br/>
~~~
127.0.0.1:6379> del 1
(integer) 1
127.0.0.1:6379> setnx 1 lock
(integer) 1
~~~
Lettuce를 사용하는 방식은 MySQL의 NamedLock을 사용하는 것과 비슷하다. <br/>
다른점은 Redis를 사용한다는 점과 세션 관리에 신경을 안써도 된다는 점이다. <br/>
<br/>

### Redis Repository 생성 
~~~
@Component
public class RedisLockRepository {
    ..... 
    public Boolean lock(Long key) {
        return redisTemplate
                .opsForValue()
                .setIfAbsent(generateKey(key), "lock", Duration.ofMillis(3_000));
    }

    public Boolean unlock(Long key) {
        return redisTemplate.delete(generateKey(key));
    }
    .....
}
~~~
<br/>

### 퍼사드 클래스 추가
비즈니스 로직 실행 전에 락 획득을 요청하고, 비즈니스 로직 실행 후에 락을 해제 요청하는 작업을 담당하는 퍼사드 클래스를 추가해 준다. <br/>
~~~
@Component
public class LettuceLockStockFacade {
  .....
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
  .....
}
~~~
<br/>

## 3-2. Redisson 구현하기

### Redisson 의존성 추가 
https://mvnrepository.com/artifact/org.redisson/redisson-spring-boot-starter
~~~
implementation group: 'org.redisson', name: 'redisson-spring-boot-starter', version: '3.23.2'
~~~
<br/>

### Redisson에서 사용하는 pub-sub 테스트
~~~
docker ps
docker exec -it 7f09ec61727e redis-cli 
~~~ 
프롬프트 창을 두 개 띄워서 아래와 같이 메시지를 주고 받는 테스트를 해본다. <br/>

[프롬프트 1]
~~~
127.0.0.1:6379> subscribe ch1
1) "subscribe"
2) "ch1"
3) (integer) 1
~~~
[프롬프트 2]
~~~
127.0.0.1:6379> publish ch1 hello
(integer) 1
~~~
[프롬프트 1]
~~~
127.0.0.1:6379> subscribe ch1
1) "subscribe"
2) "ch1"
3) (integer) 1
1) "message"
2) "ch1"
3) "hello"
~~~
Redisson은 자신이 점유하고 있는 락을 해제할 때, 채널에 메시지를 보내줌으로써, <br/>
락을 획득해야 하는 스레드들에게 락을 획득하라고 전달해준다. <br/>
그러면 대기 중인 스레드들이 메시지를 받고 락 획득을 시도하게 된다. <br/>
<br/>
Lettuce는 계속해서 락 획득을 시도하는 반면에, <br/>
Redisson은 락 해제가 되었을 때, 한 번 혹은 몇번만 시도를 하기 때문에 Redis에 부하를 줄여주게 된다. <br/>
<br/>

### 퍼사드 클래스 생성
Redisson 같은 경우에는 락 관련 클래스들을 라이브러리에서 제공해 주기 때문에 따로 Repository를 만들지 않아도 된다. <br/>
하지만 비즈니스 로직 요청 전/후에 락 획득 및 해제를 해야 하므로 퍼사드 클래스를 만들어준다. <br/>
~~~
@Component
public class RedissonLockStockFacade {

    private RedissonClient redissonClient;
    ..... 

    public void decrease(Long id, Long quantity) {
        RLock lock = redissonClient.getLock(id.toString());

        try {
            boolean available = lock.tryLock(10, 1, TimeUnit.SECONDS);
            // 몇 초 동안 획득을 시도할 것인지, 몇 초 동안 락을 점유할 것인지 설정한다.

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
~~~
<br/><br/>

## 정리 

### MySQL과 Redis 비교하기
- MySQL
  - 이미 MySQL을 사용하고 있다면 별도의 비용없이 사용 가능하다.
  - 어느 정도의 트래픽까지는 문제없이 활용 가능하다.
  - **_Redis보다는 성능이 좋지 않다._** 
- Redis
  - 활용 중인 Redis가 없다면 별도의 **_구축 비용과 인프라 관리 비용_** 이 발생한다.
  - MySQL 보다 성능이 좋다.

<br/><br/><br/><br/>


