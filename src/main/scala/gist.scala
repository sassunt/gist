package gist

import dispatch._
import java.io.{ InputStream, OutputStream }
import java.util.Scanner

class Gist extends Authorize
  with Credentials
  with Gists {

  protected def http = Http

  protected def api = :/("api.github.com").secure

  case class Options(name: Option[String] = None, content: Option[String] = None, public: Boolean = true)

  def parseExtras(extras: Iterable[String]) = {
    val it = extras.iterator
    (Options() /: it)({
      (a, e) => e match {
        case "-p" => a.copy(public = false)
        case "-c" => if (it.hasNext) a.copy(content = Some(it.next)) else a
        case "-n" => if (it.hasNext) a.copy(name = Some(it.next)) else a
      }
    })
  }

  def run(args: Array[String]): Int = {
    val AuthCredentials = """^(.+):(.+)""".r
    shutdownAfter(args.toList match { // toList for pattern matching bug in 2.9.2
      case List("auth", AuthCredentials(user, pass)) =>
        auth(user, pass)().fold(err, {
          _ match {
            case Right(access) => 
              ok("stored access")
            case Left(ae) =>
              err("error authenticating: %s" format ae)
          }
        })        
      case List("user", name) =>
        user(name)().fold(err, { gs =>
          ok(gs.map(show).mkString("\n"))
        })
      case List("cat", sha) =>
        id(sha)().fold(err, { gs =>
          ok(gs.map(cat).mkString("\n"))
        })
      case List("show", sha) =>
        id(sha)().fold(err, { gs =>
          ok(gs.map(show).mkString("\n"))
        })
      case List("push", extras @ _*) =>
        val opts = parseExtras(extras)
        opts.content match {
          case Some(content) =>
            mk(Seq(File(opts.name.getOrElse("gistfile.txt"), content)),
               public = false)().fold(err, {
              gs =>
                ok(gs.map(show).mkString("\n"))
            })
          case _ =>
            err("content required")
        }
      case List("--", extras @ _*) =>
        piped(System.in) { content =>
          mk(Seq(File("", content)),
             public = false)().fold(err, {
            gs =>
              ok(gs.map(show).mkString("\n"))
          })
        }
      case List("help") =>
        ok("usage: [auth|cat|help|post|user|show] ...")
      case _ =>
        all().fold(err, { gs =>
          ok(gs.map(show).mkString("\n"))
        })
    })
  }

  private def shutdown = http.shutdown()

  private def shutdownAfter[T](f: => T): T =
    try { f }
    finally { shutdown }

  private def bold(txt: String) = 
    Console.BOLD + txt + Console.RESET

  private def show(ref: GistRef) =
    "%s %s %s" format(bold(ref.id), ref.htmlUrl,
                      ref.files.map(f => "\n - %s" format f.name).mkString("\n"))

  private def cat(ref: GistRef) =
    ref.files.map(f => "%s\n\n%s".format(bold("+ " + f.name), f.content)).mkString("\n\n")

  private def err(msg: Throwable): Int =
    err(msg.getMessage())

  private def err(msg: String): Int = {
    System.err.println("error: %s" format msg)
    1
  }

  private def ok(msg: String) = {
    println(msg)
    0
  }

  private def piped(in: InputStream)(f: String => Int): Int = {
    @annotation.tailrec
    def check: Int = System.in.available match {
      case n if(n > 0) =>
        @annotation.tailrec
        def consume(scan: Scanner, buf: StringBuffer): Int = {
          if (scan.hasNextLine()) {
            buf.append(scan.nextLine)
            consume(scan, buf)
          } else f(buf.toString)
        }
        consume(new Scanner(in), new StringBuffer())
      case _ =>
        Thread.sleep(100)
        check
    }
    check
  }
}

object Main {
  def main(args: Array[String]) {
    System.exit(new Gist().run(args))
  }
}

class Script extends xsbti.AppMain {
  def run(conf: xsbti.AppConfiguration) =
    new Exit(new Gist().run(conf.arguments))
}

class Exit(val code: Int) extends xsbti.Exit