package gist

import scala.Console._

class Color private (txt: String) {
  def :# (color: String) = Color.invokeColorMethod(color).map(_ + txt + RESET).getOrElse(txt) }

object Color {
  implicit def stringToColor(txt: String) = new Color(txt)

  def apply(txt: String) = new Color(txt)

  private def invokeColorMethod(colorName :String) = {
    try {
      val clazz = Class.forName("scala.Console$")
      val obj = clazz.getField("MODULE$").get(null)
      val method = clazz.getMethod(colorName)
      Some(method.invoke(obj).asInstanceOf[String])
    } catch {
      case e => None
    }
  }
}
