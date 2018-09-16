import java.util.concurrent.TimeUnit

import io.grpc.stub.StreamObserver
import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}
import yukkuri.echo.grpc.messages.{EchoRequest, EchoResponse}
import yukkuri.echo.grpc.service.EchoServiceGrpc
import yukkuri.echo.grpc.service.EchoServiceGrpc.{EchoServiceBlockingStub, EchoServiceStub}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise

object GrpcClient extends App {

  val channel =
    ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext.build
  val blockingStub = EchoServiceGrpc.blockingStub(channel)
  val nonBlockingStub = EchoServiceGrpc.stub(channel)
  val client = new GrpcClient(channel, blockingStub, nonBlockingStub)

  try {
    client.unary()
    client.clientStreaming()
    client.serverStreaming()
    client.bidirectionalStreaming()
  } finally {
    client.shutdown()
  }
}

class GrpcClient private (
                           private val channel: ManagedChannel,
                           private val blockingStub: EchoServiceBlockingStub,
                           private val nonBlockingStub: EchoServiceStub
                         ) {

  def shutdown() = {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  def unary() = {
    val request =
      EchoRequest("unary request")
    try {
      val response = blockingStub.unary(request)
      println("unary response message: " + response.message)
    } catch {
      case e: StatusRuntimeException => println("RPC status: " + e.getStatus)
    }
  }

  def clientStreaming() = {

    val resObs = new StreamObserver[EchoResponse] {
      override def onError(t: Throwable) = println("clientStreaming failed")
      override def onCompleted() = println("clientStreaming complete")
      override def onNext(value: EchoResponse) = println(s"clientStreaming response: ${value.message}")
    }

    val reqObs: StreamObserver[EchoRequest] = nonBlockingStub.clientStreaming(resObs)


    reqObs.onNext(EchoRequest("clientStreaming request 1"))
    Thread.sleep(2000)
    reqObs.onNext(EchoRequest("clientStreaming request 2"))
    Thread.sleep(2000)
    reqObs.onNext(EchoRequest("clientStreaming request 3"))

    //resObs.onCompleted()
    reqObs.onCompleted()

  }

  def serverStreaming() = {

    val promise = Promise[String]()

    val resObs = new StreamObserver[EchoResponse] {
      override def onError(t: Throwable) = println("serverStreaming failed")
      override def onCompleted() = {
        promise.success("serverStreaming complete")
      }
      override def onNext(value: EchoResponse) = println(s"serverStreaming response: ${value.message}")
    }

    nonBlockingStub.serverStreaming(EchoRequest(message = "serverStreaming request", repeat = 3), resObs)

    val res = promise.future
    res.onComplete(println)

  }

  def bidirectionalStreaming() = {

    val promise = Promise[String]()

    val resObs = new StreamObserver[EchoResponse] {
      override def onError(t: Throwable) = println("bidirectionalStreaming failed")
      override def onCompleted() = {
        promise.success("bidirectionalStreaming complete")
      }
      override def onNext(value: EchoResponse) = println(s"bidirectionalStreaming response: ${value.message}")
    }

    val reqObs: StreamObserver[EchoRequest] = nonBlockingStub.bidirectionalStreaming(resObs)

    reqObs.onNext(EchoRequest("bidirectionalStreaming request 1"))
    Thread.sleep(2000)
    reqObs.onNext(EchoRequest("bidirectionalStreaming request 2"))
    Thread.sleep(2000)
    reqObs.onNext(EchoRequest("bidirectionalStreaming request 3"))

    reqObs.onCompleted()


  }

}
