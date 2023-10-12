package dapex

import cats.effect._
import dapex.rabbitmq.consumer.DapexMQConsumer
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object MainApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    Resource
      .eval(Slf4jLogger.create[IO])
      .use { implicit logger: Logger[IO] =>
        AppServer
          .createServer[IO]()
          //.use(service => AppServer.processRMQMessages(service))
          .use(service =>
            DapexMQConsumer
              .consumeRMQ(service.rmqClient, service.rmqHandler.toList, service.channel)
          )
          .as(ExitCode.Success)
      }
}
