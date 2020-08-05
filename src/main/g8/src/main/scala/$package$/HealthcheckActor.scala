package $package$ 

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

// TODO Provide a real healthcheck
object HealthcheckActor {

  // Protocol
  sealed trait Command
  final case class Ping(replyTo: ActorRef[Pong]) extends Command
  final case class Pong(msg: String)             extends Command

  def apply(): Behavior[Ping] = Behaviors.receive { (ctx, msg) =>
    ctx.log.info("Ping")
    msg.replyTo ! Pong("pong")
    Behaviors.same
  }
}
