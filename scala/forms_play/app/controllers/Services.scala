package controllers

import java.net.URI
import java.net.URLDecoder
import java.io.File

import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results._

import scalaz.Scalaz._

import scala.util.Success
import scala.util.Failure
import scala.concurrent.Future

import deductions.runtime.services.RDFContentNegociation
import deductions.runtime.jena.ImplementationSettings
import deductions.runtime.services.RecoverUtilities
import deductions.runtime.services.CORS
import deductions.runtime.jena.RDFStoreLocalJenaProvider
import deductions.runtime.sparql_cache.RDFCacheAlgo


class ServicesApp extends  {
    override implicit val config = new PlayDefaultConfiguration
  }
  with Services
  with RDFStoreLocalJenaProvider


/** controller for non-SPARQL Services (or SPARQL related but not in the W3C recommendations)
 *  including LDP */
trait Services extends
RDFCacheAlgo[ImplementationSettings.Rdf, ImplementationSettings.DATASET]
with RDFContentNegociation
with LanguageManagement
with RecoverUtilities
with HTTPoutputFromThrowable
with CORS {

  def httpOptions(path: String) = {
	  Action { implicit request =>
      logger.info("\nLDP: OPTIONS: " + request)
//      Ok("OPTIONS: " + request)
      Ok("")
        .as("text/html; charset=utf-8")
        .withHeaders(corsHeaders.toList:_*)
    }
  }


  import scala.concurrent.ExecutionContext.Implicits.global

  /** load URI into TDB; HTTP param publisher (provenance) for adding triple with dct:publisher */
  def loadURI(uriString: String): Action[AnyContent] = {
    recoverFromOutOfMemoryErrorGeneric[Action[AnyContent]](
      {
        Action { implicit request: Request[_] =>
//         val resultFuture = Future {
          val uri = ops.URI(URLDecoder.decode(uriString, "UTF-8"))
          val httpRequest = copyRequest(request)
//          println( s">>>> loadURI: httpRequest $httpRequest")
          val tryGraph = retrieveURIBody(
            uri, dataset, httpRequest, transactionsInside = true)
          val result:String = tryGraph match {
            case Success(gr)           =>
              val rr = wrapInTransaction {
                // add triple <uri> dct:publisher <dct:publisher> to graph <uri> */
                httpRequest.getHTTPparameterValue("publisher")  match {
                  case Some(publisherURI) =>
                  val triple = ops.Triple( uri, dct("publisher"),
                    ops.URI(publisherURI) )
//                  println( s">>>> loadURI: publisher URI <$publisherURI> , $triple")
                  rdfStore.appendToGraph(dataset, uri, ops.makeGraph(
                    Seq( triple ) ) )
                  case None => println( s">>>> loadURI: <$uri> no publisher URI")
                }
              s"Success loading <$uriString>, size: ${ gr.size() }"
              }
              rr . toString()
            case scala.util.Failure(f) => f.getLocalizedMessage
          }
          logger.info("Task ended: " +result)
//         }
//         Ok("Task started: " + resultFuture.toString())
         Ok("Task result: " + result)
        }
      },
      (t: Throwable) =>
        errorActionFromThrowable(t, "in importing URI"))
  }

  def loadURIpost(): Action[AnyContent] = {
    recoverFromOutOfMemoryErrorGeneric[Action[AnyContent]](
      {
        Action { implicit request: Request[AnyContent] =>
          def loadURI(uri: String): String = {
            val tryGraph = retrieveURIBody(
              ops.URI(uri), dataset, copyRequest(request), transactionsInside = true)
            val result = tryGraph match {
              case Success(gr)           => s"Success loading <$uri>, size: ${gr.size()}"
              case Failure(f) => f.getLocalizedMessage
            }
            result
          }

          val content = getContent(request)
          logger.info("loadURIpost " + content)
          val finalResults = content match {
            case None =>
              Ok("ERROR")
            case Some(urisString) =>
              val uris = urisString.split("\n")
              val results = for (uri <- uris) yield {
                loadURI(uri)
              }
              Ok(results.mkString("\n"))
          }
          finalResults
        }
      },
      (t: Throwable) =>
        errorActionFromThrowable(t, "in POST /load-uri"))
  }

  def wellKnownVoid(): Action[AnyContent] = {
    Action { implicit request: Request[AnyContent] =>
    val file = "void.ttl"
    val void =
    if (new File(file).exists()) {
      scala.io.Source.fromFile( file, "UTF-8").getLines().mkString("\n")
    } else
      "# VOID file void.ttl not provided"

      Ok(void).as("text/turtle; charset=utf-8")
    }
  }

}
