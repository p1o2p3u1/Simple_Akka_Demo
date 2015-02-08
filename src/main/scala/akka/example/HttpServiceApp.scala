package akka.example

import java.net.InetSocketAddress

import akka.actor._
import akka.io._
import spray.can.Http
import spray.http._

class HttpServiceActor extends Actor with ActorLogging{
  def receive = {
    case Http.Connected(remote, _) =>
      log.info("Remote address {} connected.",remote)
      sender ! Http.Register(
        context.actorOf(Props(new HttpConnectionHandler(remote, sender()))))
  }
}

class HttpConnectionHandler(remote: InetSocketAddress, connection: ActorRef) extends Actor with ActorLogging{
  context.watch(connection)
  def receive: Receive = {
    case HttpRequest(get, uri, _, _, _) =>
      sender ! HttpResponse(entity = uri.path.toString())
    case _: Tcp.ConnectionClosed =>
      log.debug("Stopping, because connection for remote address {} closed", remote)
      context.stop(self)
    case Terminated(`connection`) =>
      log.debug("Stopping, because connection for remote address {} died", remote)
      context.stop(self)
  }
}

object HttpServiceApp extends App{
  implicit val system = ActorSystem("http-service-app")

  val listener = system.actorOf(Props[HttpServiceActor], "http-service")

  IO(Http) ! Http.Bind(listener, interface = "localhost", port = 1111)
}
