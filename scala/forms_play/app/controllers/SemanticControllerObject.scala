package controllers

import deductions.runtime.jena.ImplementationSettings
import deductions.runtime.services.html.Form2HTMLObject
import deductions.runtime.services.{CentralSemanticController, TypicalSFDependencies}
import deductions.runtime.user.RegisterPage
import deductions.runtime.utils.DefaultConfiguration
import play.api.mvc.{Action, Request}
import deductions.runtime.mobion.GeoController
import deductions.runtime.mobion.PerVehicleView
import deductions.apps.ContactsFrontPage
import deductions.apps.ContactsDashboard
import deductions.runtime.utils.FormModuleBanana

/** RDF based HTTP Controller/router;
 *  this allows to create new pages or services ("plugins")
 *  without relying on routes file of Play! framework,
 *  or rather using just one entry for /page.
 *
 * To create a new service, just implement interface in trait SemanticController,
 * and add this to val actions below. */
object SemanticControllerObject
extends play.api.mvc.Results with
    ImplementationSettings.RDFCache
    with CentralSemanticController[ImplementationSettings.Rdf, ImplementationSettings.DATASET]
    with LanguageManagement
    with RegisterPage[ImplementationSettings.Rdf, ImplementationSettings.DATASET]
    with FormModuleBanana[ImplementationSettings.Rdf] {

  import ops._

  private class DependenciesNoTextQuery extends TypicalSFDependencies {
    override implicit val config = new DefaultConfiguration {
      override val useTextQuery = false
    }
  }

  //////////////////////////////////////////////////////////////
  /** add/remove plugin pages */
  val actions: Seq[deductions.runtime.core.SemanticController] =
    Seq(
      new DependenciesNoTextQuery with GeoController[Rdf, DATASET] with FormModuleBanana[Rdf] {},
      new DependenciesNoTextQuery with PerVehicleView[Rdf, DATASET] with FormModuleBanana[Rdf] {},
      new DependenciesNoTextQuery with ContactsFrontPage[Rdf, DATASET] with FormModuleBanana[Rdf]  {},
      new DependenciesNoTextQuery with ContactsDashboard[Rdf, DATASET]
        with FormModuleBanana[Rdf] {}
      )
  //////////////////////////////////////////////////////////////

  override implicit val config = new DefaultConfiguration {}
  override lazy val htmlGenerator =
    Form2HTMLObject.makeDefaultForm2HTML(config)(ops)

  def page() =
    Action { implicit request: Request[_] =>
      val requestCopy = getRequestCopy()
      val userid = requestCopy.userId()
      val title = "SemanticController view"
      val lang = chooseLanguage(request)
      val userInfo = displayUser(userid, requestCopy)
      // outputMainPage()
      Ok( result(requestCopy) ) .as("text/html; charset=utf-8")
    }
}