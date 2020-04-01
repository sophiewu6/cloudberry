package actor

import java.net.URI

import akka.actor._
import akka.stream.Materializer
import org.eclipse.jetty.websocket.client.WebSocketClient
import play.api.{Configuration, Logger}
import play.api.libs.json.JsValue
import websocket.{TwitterMapServerToCloudBerrySocket, WebSocketFactory}

import scala.concurrent.ExecutionContext

/**
  * A routing actor that servers for rendering user request into cloudberry request
  *  and transfer cloudberry request/response through websocket connection.
  *
  * @param factory Factory of WebSocketClient
  * @param cloudberryWebsocketURL Websocket url of cloudberry
  * @param out ActorRef in akka flow representing frontend client
  * @param maxTextMessageSize Max size of text messages transmit in ws.
  * @param ec implicit execution context
  * @param materializer implicit materializer
  */
class TwitterMapPigeon (val factory: WebSocketFactory,
                        val cloudberryWebsocketURL: String,
                        val out: ActorRef,
                        val config: Configuration,
                        val maxTextMessageSize: Int)
                       (implicit ec: ExecutionContext, implicit val materializer: Materializer) extends Actor with ActorLogging {

  private val client: WebSocketClient = factory.newClient(maxTextMessageSize)
  private val socket: TwitterMapServerToCloudBerrySocket = factory.newSocket(out, config)
  private val clientLogger = Logger("client")

  override def preStart(): Unit = {
    super.preStart
    client.start()
    client.connect(socket, new URI(cloudberryWebsocketURL))
  }

  override def postStop(): Unit = {
    super.postStop
    client.stop()
  }

  /**
    * Handles Websocket sending from frontend to twitterMap Server
    */
  override def receive: Receive = {
    case frontEndRequest: JsValue =>
      clientLogger.info("request from frontend: " + frontEndRequest.toString)
      val cloudBerryRequest = renderRequest(frontEndRequest)
      clientLogger.info("request to cloudberry: " + cloudBerryRequest.toString)
      socket.sendMessage(cloudBerryRequest.toString)
    case e =>
      log.error("Unknown type of request " + e.toString)
  }

  //Logic of rendering cloudberry request goes here
  private def renderRequest(frontEndRequest: JsValue): JsValue = frontEndRequest
}

object TwitterMapPigeon {
  def props(factory: WebSocketFactory, cloudberryWebsocketURL: String, out: ActorRef, config: Configuration, maxTextMessageSize: Int)
           (implicit ec: ExecutionContext, materializer: Materializer) = Props(new TwitterMapPigeon(factory, cloudberryWebsocketURL, out, config, maxTextMessageSize))
}
