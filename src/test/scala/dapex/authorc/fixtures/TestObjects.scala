package dapex.authorc.fixtures

import cats.effect.IO
import cats.syntax.all._
import cats.{Applicative, Monad}
import com.hazelcast.aggregation.Aggregator
import com.hazelcast.config.IndexConfig
import com.hazelcast.core.EntryView
import com.hazelcast.map._
import com.hazelcast.map.listener.{MapListener, MapPartitionLostListener}
import com.hazelcast.projection.Projection
import com.hazelcast.query.Predicate
import dapex.authorc.domain.caching.{AuthenticationCachingServiceAlgebra, CachingMapsAlgebra}
import dapex.authorc.domain.security.{HashingServiceAlgebra, SecurityTokenServiceAlgebra}
import dapex.config.ServerConfiguration
import dapex.messaging._
import dapex.rabbitmq.RabbitQueue
import dapex.rabbitmq.publisher.DapexMQPublisherAlgebra
import io.circe.config.parser

import java.time.Instant
import java.util
import java.util.concurrent.{CompletionStage, ConcurrentHashMap, ConcurrentMap, TimeUnit}
import java.util.{Map, UUID}

object TestObjects {

  val authRequest = DapexMessage(
    endpoint = Endpoint(resource = "service.auth", method = "select"),
    client = Client(
      clientId = "app-1",
      requestId = "app-req-1",
      sourceEndpoint = "auth",
      authorisation = ""
    ),
    originator = Originator(
      clientId = "app-1",
      requestId = "app-req-1",
      sourceEndpoint = "auth"
    ),
    criteria = Vector(
      Criterion("username", "test@test.com", "EQ"),
      Criterion("password", "password1234", "EQ")
    ),
    update = Vector(),
    insert = Vector(),
    process = Vector(),
    response = None
  )

  val serviceRequestId = s"${authRequest.client.clientId}-${authRequest.client.requestId}"

  val dbResponse = authRequest.copy(
    endpoint = Endpoint("service.auth", "response"),
    client = Client(
      clientId = "service.dbread",
      requestId = serviceRequestId,
      sourceEndpoint = "service.dbread",
      authorisation = ""
    ),
    response = Some(
      Response(
        status = "ok",
        message = "",
        data = Vector(
          FieldValuePair("customerId", "1"),
          FieldValuePair("firstname", "Tester"),
          FieldValuePair("surname", "Test"),
          FieldValuePair("email", "test@test.com"),
          FieldValuePair("password", "hash")
        )
      )
    )
  )

  val config = parser
    .decodePath[ServerConfiguration]("server")
    .toOption
    .getOrElse(throw new Exception("Issue with configuration!"))

  class CachingMaps(cache: ConcurrentMap[String, DapexMessage]) extends CachingMapsAlgebra[IO] {

    override def saveByToken(token: String, dapexMessage: DapexMessage): IO[Unit] =
      cache.put(token, dapexMessage).pure[IO] *>
        IO.unit

    override def getByToken(token: String): IO[Option[DapexMessage]] =
      IO {
        if (cache.containsKey(token))
          Some(cache.get(token))
        else
          None
      }

    override def saveByRequestId(requestId: String, dapexMessage: DapexMessage): IO[Unit] =
      saveByToken(requestId, dapexMessage)

    override def getByRequestId(requestId: String): IO[Option[DapexMessage]] = getByToken(requestId)
  }

  case class CachingService[F[_]: Monad](cache: util.Map[String, DapexMessage])
      extends AuthenticationCachingServiceAlgebra[F] {

    override def saveByToken(token: String, dapexMessage: DapexMessage): F[Unit] =
      saveByRequestId(token, dapexMessage)

    override def getByToken(token: String): F[Option[DapexMessage]] = getByRequestId(token)

    override def saveByRequestId(requestId: String, dapexMessage: DapexMessage): F[Unit] = {
      cache.put(requestId, dapexMessage)
      ().pure[F]
    }

    override def getByRequestId(requestId: String): F[Option[DapexMessage]] =
      if (cache.containsKey(requestId)) {
        (Some((cache.get(requestId))): Option[DapexMessage]).pure[F]
      } else
        (None: Option[DapexMessage]).pure[F]
  }

  class RMPPublisher[F[_]: Applicative](queue: util.Queue[DapexMessage])
      extends DapexMQPublisherAlgebra[F] {
    override def publishMessageToQueue(message: DapexMessage, q: RabbitQueue): F[Unit] = {
      queue.add(message)
      ().pure[F]
    }
  }

  val hashingService = new HashingServiceAlgebra {

    override def getHash(value: String, salt: String): String = "hash"
  }

  val securityTokenService = new SecurityTokenServiceAlgebra {

    override def generateTokenFor(username: String, instant: Instant): String = "jwt-token"
  }

  class TestImap() extends IMap[String, String] {

    // We are using mutable map for testing only
    private val cache = new ConcurrentHashMap[String, String]()

    override def putAll(m: util.Map[_ <: String, _ <: String]): Unit = ???

    override def containsKey(key: Any): Boolean = cache.containsKey(key)

    override def containsValue(value: Any): Boolean = ???

    override def get(key: Any): String = cache.get(key)

    override def put(key: String, value: String): String = cache.put(key, value)

    override def remove(key: Any): String = ???

    override def removeAll(predicate: Predicate[String, String]): Unit = ???

    override def delete(key: Any): Unit = ???

    override def flush(): Unit = ???

    override def getAll(keys: util.Set[String]): util.Map[String, String] = ???

    override def loadAll(replaceExistingValues: Boolean): Unit = ???

    override def loadAll(keys: util.Set[String], replaceExistingValues: Boolean): Unit = ???

    override def clear(): Unit = cache.clear()

    override def getAsync(key: String): CompletionStage[String] = ???

    override def putAsync(key: String, value: String): CompletionStage[String] = ???

    override def putAsync(
        key: String,
        value: String,
        ttl: Long,
        ttlUnit: TimeUnit
    ): CompletionStage[String] = ???

    override def putAsync(
        key: String,
        value: String,
        ttl: Long,
        ttlUnit: TimeUnit,
        maxIdle: Long,
        maxIdleUnit: TimeUnit
    ): CompletionStage[String] = ???

    override def putAllAsync(map: util.Map[_ <: String, _ <: String]): CompletionStage[Void] = ???

    override def setAsync(key: String, value: String): CompletionStage[Void] = ???

    override def setAsync(
        key: String,
        value: String,
        ttl: Long,
        ttlUnit: TimeUnit
    ): CompletionStage[Void] = ???

    override def setAsync(
        key: String,
        value: String,
        ttl: Long,
        ttlUnit: TimeUnit,
        maxIdle: Long,
        maxIdleUnit: TimeUnit
    ): CompletionStage[Void] = ???

    override def removeAsync(key: String): CompletionStage[String] = ???

    override def tryRemove(key: String, timeout: Long, timeunit: TimeUnit): Boolean = ???

    override def tryPut(key: String, value: String, timeout: Long, timeunit: TimeUnit): Boolean =
      ???

    override def put(key: String, value: String, ttl: Long, ttlUnit: TimeUnit): String =
      put(key, value)

    override def put(
        key: String,
        value: String,
        ttl: Long,
        ttlUnit: TimeUnit,
        maxIdle: Long,
        maxIdleUnit: TimeUnit
    ): String = ???

    override def putTransient(key: String, value: String, ttl: Long, ttlUnit: TimeUnit): Unit = ???

    override def putTransient(
        key: String,
        value: String,
        ttl: Long,
        ttlUnit: TimeUnit,
        maxIdle: Long,
        maxIdleUnit: TimeUnit
    ): Unit = ???

    override def putIfAbsent(key: String, value: String, ttl: Long, ttlUnit: TimeUnit): String = ???

    override def putIfAbsent(
        key: String,
        value: String,
        ttl: Long,
        ttlUnit: TimeUnit,
        maxIdle: Long,
        maxIdleUnit: TimeUnit
    ): String = ???

    override def set(key: String, value: String): Unit = ???

    override def set(key: String, value: String, ttl: Long, ttlUnit: TimeUnit): Unit = ???

    override def set(
        key: String,
        value: String,
        ttl: Long,
        ttlUnit: TimeUnit,
        maxIdle: Long,
        maxIdleUnit: TimeUnit
    ): Unit = ???

    override def setAll(map: util.Map[_ <: String, _ <: String]): Unit = ???

    override def setAllAsync(map: util.Map[_ <: String, _ <: String]): CompletionStage[Void] = ???

    override def lock(key: String): Unit = ???

    override def lock(key: String, leaseTime: Long, timeUnit: TimeUnit): Unit = ???

    override def isLocked(key: String): Boolean = ???

    override def tryLock(key: String): Boolean = ???

    override def tryLock(key: String, time: Long, timeunit: TimeUnit): Boolean = ???

    override def tryLock(
        key: String,
        time: Long,
        timeunit: TimeUnit,
        leaseTime: Long,
        leaseTimeunit: TimeUnit
    ): Boolean = ???

    override def unlock(key: String): Unit = ???

    override def forceUnlock(key: String): Unit = ???

    override def addLocalEntryListener(listener: MapListener): UUID = ???

    override def addLocalEntryListener(
        listener: MapListener,
        predicate: Predicate[String, String],
        includeValue: Boolean
    ): UUID = ???

    override def addLocalEntryListener(
        listener: MapListener,
        predicate: Predicate[String, String],
        key: String,
        includeValue: Boolean
    ): UUID = ???

    override def addInterceptor(interceptor: MapInterceptor): String = ???

    override def removeInterceptor(id: String): Boolean = ???

    override def addEntryListener(listener: MapListener, includeValue: Boolean): UUID = ???

    override def removeEntryListener(id: UUID): Boolean = ???

    override def addPartitionLostListener(listener: MapPartitionLostListener): UUID = ???

    override def removePartitionLostListener(id: UUID): Boolean = ???

    override def addEntryListener(listener: MapListener, key: String, includeValue: Boolean): UUID =
      ???

    override def addEntryListener(
        listener: MapListener,
        predicate: Predicate[String, String],
        includeValue: Boolean
    ): UUID = ???

    override def addEntryListener(
        listener: MapListener,
        predicate: Predicate[String, String],
        key: String,
        includeValue: Boolean
    ): UUID = ???

    override def getEntryView(key: String): EntryView[String, String] = ???

    override def evict(key: String): Boolean = ???

    override def evictAll(): Unit = ???

    override def keySet(): util.Set[String] = ???

    override def values(): util.Collection[String] = ???

    override def entrySet(): util.Set[Map.Entry[String, String]] = ???

    override def keySet(predicate: Predicate[String, String]): util.Set[String] = ???

    override def entrySet(
        predicate: Predicate[String, String]
    ): util.Set[Map.Entry[String, String]] =
      ???

    override def values(predicate: Predicate[String, String]): util.Collection[String] = ???

    override def localKeySet(): util.Set[String] = ???

    override def localKeySet(predicate: Predicate[String, String]): util.Set[String] = ???

    override def addIndex(indexConfig: IndexConfig): Unit = ???

    override def getLocalMapStats: LocalMapStats = ???

    override def executeOnKey[R](
        key: String,
        entryProcessor: EntryProcessor[String, String, R]
    ): R =
      ???

    override def executeOnKeys[R](
        keys: util.Set[String],
        entryProcessor: EntryProcessor[String, String, R]
    ): util.Map[String, R] = ???

    override def submitToKeys[R](
        keys: util.Set[String],
        entryProcessor: EntryProcessor[String, String, R]
    ): CompletionStage[util.Map[String, R]] = ???

    override def submitToKey[R](
        key: String,
        entryProcessor: EntryProcessor[String, String, R]
    ): CompletionStage[R] = ???

    override def executeOnEntries[R](
        entryProcessor: EntryProcessor[String, String, R]
    ): util.Map[String, R] = ???

    override def executeOnEntries[R](
        entryProcessor: EntryProcessor[String, String, R],
        predicate: Predicate[String, String]
    ): util.Map[String, R] = ???

    override def aggregate[R](aggregator: Aggregator[_ >: Map.Entry[String, String], R]): R = ???

    override def aggregate[R](
        aggregator: Aggregator[_ >: Map.Entry[String, String], R],
        predicate: Predicate[String, String]
    ): R = ???

    override def project[R](
        projection: Projection[_ >: Map.Entry[String, String], R]
    ): util.Collection[R] = ???

    override def project[R](
        projection: Projection[_ >: Map.Entry[String, String], R],
        predicate: Predicate[String, String]
    ): util.Collection[R] = ???

    override def getQueryCache(name: String): QueryCache[String, String] = ???

    override def getQueryCache(
        name: String,
        predicate: Predicate[String, String],
        includeValue: Boolean
    ): QueryCache[String, String] = ???

    override def getQueryCache(
        name: String,
        listener: MapListener,
        predicate: Predicate[String, String],
        includeValue: Boolean
    ): QueryCache[String, String] = ???

    override def setTtl(key: String, ttl: Long, timeunit: TimeUnit): Boolean = ???

    override def iterator(): util.Iterator[Map.Entry[String, String]] = ???

    override def iterator(fetchSize: Int): util.Iterator[Map.Entry[String, String]] = ???

    override def isEmpty: Boolean = ???

    override def size(): Int = ???

    override def getPartitionKey: String = ???

    override def getName: String = "TestMap"

    override def getServiceName: String = ???

    override def destroy(): Unit = ???
  }

}
