package deductions.runtime.jena

import org.w3.banana.jena.Jena
import org.apache.log4j.Logger
import org.apache.jena.riot.RDFDataMgr
import org.w3.banana.RDFOpsModule
import org.w3.banana.SparqlOps
import java.net.URL
import org.w3.banana.jena.JenaModule
import org.w3.banana.RDFStore
import org.w3.banana.diesel._
import org.w3.banana.RDFStore
import org.w3.banana.RDFOpsModule
import org.w3.banana.RDFOps
import scala.util.Try
import org.w3.banana.RDF
import org.w3.banana.jena.io.JenaRDFReader
import scala.util.Success
import scala.util.Failure

/**
 * Helpers for RDF Store
 *  In Banana 0.7 :
 *  - JenaStore is not existing anymore
 *  - generic API for transactions
 *
 * TODO rename RDFStoreHelpers
 * TODO remove dependency to Jena after anyRDFReader is introduced in Banana
 */

trait JenaHelpers extends JenaModule {
  import ops._
  import rdfStore.transactorSyntax._
  import rdfStore.graphStoreSyntax._

  type Store = RDFStoreObject.DATASET

  /**
   * store URI in a named graph,
   * transactional,
   * using Jena's RDFDataMgr
   * with Jena Riot for smart reading of any format,
   * (use content-type or else file extension)
   * cf https://github.com/w3c/banana-rdf/issues/105
   */
  def storeURI(uri: Rdf#URI, graphUri: Rdf#URI, dataset: Store): Rdf#Graph = {
    val r = dataset.rw({
      storeURINoTransaction(uri, graphUri, dataset)
    })
    r match {
      case Success(g) => g
      case Failure(e) =>
        Logger.getRootLogger().error("ERROR: " + e)
        throw e
    }
  }

  /**
   * read from uri no matter what the syntax is;
   *  probably can also load an URI with the # part
   */
  def storeURINoTransaction(uri: Rdf#URI, graphUri: Rdf#URI, dataset: Store): Rdf#Graph = {
    Logger.getRootLogger().info(s"Before storeURI uri $uri graphUri $graphUri")
    val gForStore = dataset.getGraph(graphUri)
    Logger.getRootLogger().info(s"Before loadModel uri $uri")
    val graph = RDFDataMgr.loadModel(uri.toString()).getGraph
    // val graph = anyRDFReader.readAnyRDFSyntax(uri.toString()) . get // TODO after proposing anyRDFReader to Banana
    dataset.appendToGraph(uri, graph)
    Logger.getRootLogger().info(s"storeURI uri $uri : stored")
    graph
  }

}
