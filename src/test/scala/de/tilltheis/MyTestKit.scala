package de.tilltheis

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestKitBase, TestProbe}

import scala.concurrent.duration._

// The real TestKitBase ignores the "akka.test.single-expect-default" config setting because it probably doesn't load
// the config correctly.
trait MyTestKitBase extends TestKitBase {
  private val timeout = 500.millis

  override def remainingOrDefault: FiniteDuration = {
    import akka.testkit.TestDuration
    remainingOr(timeout.dilated)
  }
}

class MyTestKit(_system: => ActorSystem) extends TestKit(_system) with MyTestKitBase

object MyTestProbe {
  def apply()(implicit system: ActorSystem) = new TestProbe(system) with MyTestKitBase
  def apply(name: String)(implicit system: ActorSystem) = new TestProbe(system, name) with MyTestKitBase
}
