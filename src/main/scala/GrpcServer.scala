import java.util.Date

import io.grpc.stub.StreamObserver
import io.grpc.{Server, ServerBuilder}
import yukkuri.echo.grpc.messages.{EchoRequest, EchoResponse}
import yukkuri.echo.grpc.service.EchoServiceGrpc

import scala.concurrent.{ExecutionContext, Future}

import scala.concurrent.ExecutionContext.Implicits.global

object GrpcServer extends App {
  val server = new GrpcServer(ExecutionContext.global)
  server.start()
  server.blockUntilShutdown()
}

class GrpcServer(executionContext: ExecutionContext) {
  self =>

  private[this] var server: Server = null

  private def start(): Unit = {
    server = ServerBuilder
      .forPort(50051)
      .addService(
        EchoServiceGrpc.bindService(new EchoServiceGrpcImpl, executionContext))
      .build
      .start
    sys.addShutdownHook {
      self.stop()
      println("* server shut down *")
    }
  }

  private def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

//  import scala.concurrent._
//  import java.util.concurrent.Executors
//  val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1000))

  private class EchoServiceGrpcImpl extends EchoServiceGrpc.EchoService {

    override def unary(request: EchoRequest): Future[EchoResponse] = {

      // FIXME delayの大きいリクエストを同時にたくさん受けるとつまる？

      Future {
        Thread.sleep(request.delaySec * 1000)
        EchoResponse(s"${request.message}")
      }

//      val receiveTime = new Date()
//      val responseTimeMs = receiveTime.getTime + (request.delaySec * 1000)
//
//      Future{
//        val runTime = new Date()
//        val sleepTimeMs = responseTimeMs - runTime.getTime
//        if (sleepTimeMs <= 0) Thread.sleep(sleepTimeMs)
//        EchoResponse(s"${request.message} # receive: $receiveTime, run: $runTime")
//      }(ec)

    }

    override def clientStreaming(responseObserver: StreamObserver[EchoResponse])
      : StreamObserver[EchoRequest] = {
      new StreamObserver[EchoRequest] {
        override def onError(t: Throwable) = {
          println("clientStreaming error")
          responseObserver.onError(t)
        }

        override def onCompleted() = {
          // TODO この辺でスリープする？
          responseObserver.onNext(EchoResponse("clientStreaming completed"))
          responseObserver.onCompleted()
          println("clientStreaming completed")
        }

        override def onNext(req: EchoRequest) = {
          println(s"clientStreaming arrived ${req.message}")
        }
      }

    }

    override def serverStreaming(
        request: EchoRequest,
        responseObserver: StreamObserver[EchoResponse]): Unit = {

      for (_ <- 1 to Math.max(request.repeat, 1)) {
        responseObserver.onNext(EchoResponse(request.message))
      }

      responseObserver.onCompleted()

    }

    override def bidirectionalStreaming(
        responseObserver: StreamObserver[EchoResponse])
      : StreamObserver[EchoRequest] = {
      new StreamObserver[EchoRequest] {
        override def onError(t: Throwable) = {
          println("bidirectionalStreaming error")
          responseObserver.onError(t)
        }

        override def onCompleted() = {
          // TODO この辺でスリープする？
          responseObserver.onNext(
            EchoResponse("bidirectionalStreaming completed"))
          responseObserver.onCompleted()
          println("bidirectionalStreaming completed")
        }

        override def onNext(req: EchoRequest) = {

          println(s"bidirectionalStreaming arrived ${req.message}")

          for (_ <- 1 to Math.max(req.repeat, 1)) {
            responseObserver.onNext(EchoResponse(req.message))
          }

        }
      }

    }

  }

}
