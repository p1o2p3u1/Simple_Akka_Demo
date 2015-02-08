package akka.example
/*
  TCP Server
 */
import java.net.InetSocketAddress
import akka.actor._
import akka.io.{Tcp, IO}
import akka.util.ByteString


/*
  The new Akka I/O implementation abstracts over transport protocols via so called
  “drivers” and introduces the concept of an “I/O manager” as the API for a
  particular driver.
 */
class EchoService(endpoint: InetSocketAddress) extends Actor with ActorLogging{

  def receive: Receive = {
    /*
      Listening to TCP connections means handling messages of type Tcp.Connected
     */
    case Tcp.Connected(remote, _) =>
      log.info("Remote address {} connected.", remote)
      //we pass the sender, i.e. the connection, to the EchoConnectionHandler.
      sender ! Tcp.Register(
        context.actorOf(Props(new EchoConnectedHandler(remote, sender()))))
  }
}


class EchoConnectedHandler(remote: InetSocketAddress, connection: ActorRef) extends Actor with ActorLogging{
  // We need to know when the connection dies without sending a `Tcp.ConnectionClosed`
  context.watch(connection)

  def receive: Receive = {
    case Tcp.Connected =>
      sender ! Tcp.Write(ByteString(s"Welcome to the echo service. Your address is $remote"))
    case Tcp.Received(data) =>
      val text = data.utf8String.trim
      log.info("Received '{}' from remote address {}", text, remote)
      text match{
        case "close" =>
          sender ! Tcp.Write(ByteString("Close connection."))
          context.stop(self)
        case _ =>
          // Simply write it back
          sender ! Tcp.Write(ByteString(s"Received: $text\n"))
      }
    case _: Tcp.ConnectionClosed =>
      log.info("Stopping, because connection for remote address {} closed.", remote)
      context.stop(self)
    case Terminated(`connection`) =>
      log.info("Stopping, because connection for remote address {} died.", remote)
      context.stop(self)
  }

}

object EchoServiceApp extends App{
  // create an actor system and an endpoint on localhost port 11111
  implicit val system = ActorSystem("echo-service-system")
  val endpoint = new InetSocketAddress("localhost", 11111)
  val listener = system.actorOf(Props(new EchoService(endpoint)), "echo-service")
  IO(Tcp) ! Tcp.Bind(listener, endpoint)
  readLine(s"Hit enter to exit. ${System.getProperty("line.separator")}")
  system.shutdown()
}
