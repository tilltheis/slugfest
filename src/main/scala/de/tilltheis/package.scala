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

  // https://stackoverflow.com/a/12034334
  def escapeHtml(value: String): String = {
    val entityMap = Map(
      '&' -> "&amp;",
      '<' -> "&lt;",
      '>' -> "&gt;",
      '"' -> "&quot;",
      '\'' -> "&#39;",
      '/' -> "&#x2F;",
      '`' -> "&#x60;",
      '=' -> "&#x3D")
    value.flatMap(c => entityMap.getOrElse(c, c.toString))
  }
}
