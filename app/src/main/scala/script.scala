package gist

import xsbti.{ AppMain, AppConfiguration }
import java.io.{ InputStream, OutputStream }
import dispatch.Http
import java.util.Scanner

object Script {
  case class Options(name: Option[String] = None,
                     content: Option[String] = None,
                     public: Boolean = true)

  val http = Http
  def parseOptions(options: Iterable[String]) = {
    val it = options.iterator
    (Options() /: it)({
      (a, e) => e match {
        case "-p" => a.copy(public = false)
        case "-c" => if (it.hasNext) a.copy(content = Some(it.next)) else a
        case "-n" => if (it.hasNext) a.copy(name = Some(it.next)) else a
      }
    })
  }

  def apply(args: Array[String]): Int = {
    val gist = new Gist(http)
    val AuthCredentials = """^(.+):(.+)""".r
    shutdownAfter(args.toList match { // toList for pattern matching bug in 2.9.2
      case List("auth", AuthCredentials(user, pass)) =>
        gist.auth(user, pass)().fold(err, {
          _ match {
            case Right(access) => 
              ok("authorized %s" format user)
            case Left(ae) =>
              err("error authenticating: %s" format ae)
          }
        })
      case List("auth", "-d") =>
        gist.deauth match {
          case Some(login) => ok("deleted authorization for %s" format login)
          case _ => err("failed to deauthenticate")
        }
      case List("whoami") =>
        ok(gist.authorized.getOrElse("annonymous user"))
      case List("user", name) =>
        gist.user(name)().fold(err, { gs =>
          ok(gs.map(show).mkString("\n"))
        })
      case List("cat", sha) =>
        gist.id(sha)().fold(err, { gs =>
          ok(gs.map(cat).mkString("\n"))
        })
      case List("show", sha) =>
        gist.id(sha)().fold(err, { gs =>
          ok(gs.map(show).mkString("\n"))
        })
      case List("push", extras @ _*) =>
        val opts = parseOptions(extras)
        opts.content match {
          case Some(content) =>
            gist.mk(Seq(File(opts.name.getOrElse(""), content)),
               public = opts.public)().fold(err, {
              gs =>
                ok(gs.map(show).mkString("\n"))
            })
          case _ =>
            err("content required")
        }
      case List("--", extras @ _*) =>
        val opts = parseOptions(extras)
        piped(System.in) { content =>
          gist.mk(Seq(File(opts.name.getOrElse(""), content)),
             public = opts.public)().fold(err, {
            gs =>
              ok(gs.map(show).mkString("\n"))
          })
        }
      case List("rm", sha) =>
        gist.rm(sha)().fold(err, {
          _ => ok("deleted %s" format sha)
        })
      case List("+", sha) =>
        gist.visibility(sha, true)().fold(err, {
          _ => ok("anyone can now see %s" format sha)
        })
      case List("-", sha) =>
        gist.visibility(sha, false)().fold(err, {
          _ => ok("only you can see %s" format sha)
        })
      case List("star", sha, extras @ _*) =>
        val set = !extras.contains("-d")
        gist.star(sha, set)().fold(err, {
          _ => ok("%s %s" format(if (set) "starred" else "unstarred", sha))
        })
      case List("help") =>
        ok("usage: [auth|cat|help|push|user|show|star|+|-] ...")
      case _ =>
        gist.all().fold(err, { gs =>
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
    "%s %s %s %s %s" format(if (ref.public) "+" else "-",
                         bold(ref.id),
                         ref.htmlUrl,
                         ref.desc,
                         ref.files.map(f => "\n * %s (%s)" format(f.name, f.size)).mkString("\n"))

  private def cat(ref: GistRef) =
    ref.files.map(f => "%s\n\n%s".format(bold("* " + f.name), f.content)).mkString("\n\n")

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
            buf.append("\n")
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
    System.exit(Script(args))
  }
}

class Script extends AppMain {
  def run(conf: AppConfiguration) =
    new Exit(Script(conf.arguments))
}

class Exit(val code: Int) extends xsbti.Exit
