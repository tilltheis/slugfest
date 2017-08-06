package de

package object tilltheis {
  case class Point(x: Double, y: Double) {
    def clipTo(dimensions: Dimensions): Point =
      Point(
        ((x % dimensions.width) + dimensions.width) % dimensions.width,
        ((y % dimensions.height) + dimensions.height) % dimensions.height)
  }

  case class Dimensions(width: Int, height: Int)

  sealed trait Direction
  object Direction {
    case object Left extends Direction
    case object Right extends Direction
  }

}
