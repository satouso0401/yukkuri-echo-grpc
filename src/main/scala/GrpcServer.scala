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
      .addService(EchoServiceGrpc.bindService(new EchoServiceGrpcImpl, executionContext))
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

  private class EchoServiceGrpcImpl extends EchoServiceGrpc.EchoService {

    override def unary(request: EchoRequest): Future[EchoResponse] = {

      // delayの大きいリクエストを同時にたくさん受けるとつまる？
      Future {
        Thread.sleep(request.delaySec * 1000)
        EchoResponse(s"${request.message}")
      }
    }

    override def clientStreaming(responseObserver: StreamObserver[EchoResponse]): StreamObserver[EchoRequest] = {
      new StreamObserver[EchoRequest] {
        override def onError(t: Throwable) = {
          println("clientStreaming error")
          responseObserver.onError(t)
        }

        override def onCompleted() = {
          responseObserver.onNext(EchoResponse("clientStreaming completed"))
          responseObserver.onCompleted()
          println("clientStreaming completed")
        }

        override def onNext(req: EchoRequest) = {
          println(s"clientStreaming arrived ${req.message}")
        }
      }

    }

    override def serverStreaming(request: EchoRequest, responseObserver: StreamObserver[EchoResponse]): Unit = {

      for (_ <- 1 to Math.max(request.repeat, 1)) {
        Thread.sleep(request.delaySec * 1000)
        responseObserver.onNext(EchoResponse(request.message))
      }

      responseObserver.onCompleted()

    }

    override def bidirectionalStreaming(responseObserver: StreamObserver[EchoResponse]): StreamObserver[EchoRequest] = {
      new StreamObserver[EchoRequest] {
        override def onError(t: Throwable) = {
          println("bidirectionalStreaming error")
          t.printStackTrace()
          responseObserver.onError(t)
        }

        override def onCompleted() = {
          responseObserver.onCompleted()
          println("bidirectionalStreaming client completed")
        }

        override def onNext(req: EchoRequest) = {

          println(s"bidirectionalStreaming arrived ${req.message}")

          if (req.delimit) {
            responseObserver.onNext(EchoResponse(req.message))
            responseObserver.onCompleted()
          } else {
            Future {
              for (_ <- 1 to Math.max(req.repeat, 1)) {
                Thread.sleep(req.delaySec * 1000)
                responseObserver.onNext(EchoResponse(req.message))
              }
            }
          }

        }
      }

    }

  }

}
