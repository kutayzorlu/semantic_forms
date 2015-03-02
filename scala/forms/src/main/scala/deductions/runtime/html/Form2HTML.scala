package deductions.runtime.html

import scala.xml.Elem
import deductions.runtime.abstract_syntax.FormModule
import java.net.URLEncoder
import Form2HTML._
import scala.xml.NodeSeq
import deductions.runtime.abstract_syntax.DBPediaLookup

/**
 * different modes: display or edit;
 *  take in account datatype
 */
trait Form2HTML[NODE, URI <: NODE]
    // TODO test without extends FormModule:
    extends FormModule[NODE, URI] {
  type fm = FormModule[NODE, URI]

  val radioForIntervals = true // false // TODO the choice should be moved to FormSyntaxFactory
  /**
   * render the given Form Syntax as HTML;
   *  @param hrefPrefix URL prefix pre-pendended to created ID's for Hyperlink
   *  @param actionURI, actionURI2 HTML actions for the 2 submit buttons
   */
  def generateHTML(form: fm#FormSyntax[NODE, URI],
    hrefPrefix: String = "",
    editable: Boolean = false,
    actionURI: String = "/save", graphURI: String = "",
    actionURI2: String = "/save"): Elem = {

    val htmlForm =
      <div class="form">
        <input type="hidden" name="uri" value={ urlEncode(form.subject) }/>
        {
          for (field <- form.fields) yield {
            <div class="form-group">
              <div class="row">
                <label class="control-label" title={ field.comment }>{ field.label }</label>
                <div class="input">
                  { createHTMLField(field, editable, hrefPrefix) }
                </div>
              </div>
            </div>
          }
        }
      </div>

    if (editable)
      <form action={ actionURI } method="POST">
        <p class="text-right">
          <input value="SAVE" type="submit" class="btn btn-primary btn-lg"/>
        </p>
        <input type="hidden" name="url" value={ urlEncode(form.subject) }/>
        <input type="hidden" name="graphURI" value={ urlEncode(graphURI) }/>
        { htmlForm }
        <p class="text-right">
          <input value="SAVE" type="submit" formaction={ actionURI2 } class="btn btn-primary btn-lg pull-right"/>
        </p>
      </form>
    else
      htmlForm
  }

  private def createHTMLField(field: fm#Entry, editable: Boolean,
    hrefPrefix: String = ""): xml.NodeSeq = {
    field match {
      case l: LiteralEntry =>
        {
          if (editable) {
            createHTMLiteralEditableLField(l)
          } else {
            <div>{ l.value }</div>
          }
        }
      case r: ResourceEntry =>
        /* link to a known resource of the right type,
           * or (TODO) create a sub-form for a blank node of an ancillary type (like a street address),
           * or just create a new resource with its type, given by range, or derived
           * (like in N3Form in EulerGUI ) */
        {
          if (editable) {
            createHTMLResourceEditableLField(r)
          } else
            <a href={ Form2HTML.createHyperlinkString(hrefPrefix, r.value.toString) } title={ "Value of type " + r.type_.toString() }> {
              r.valueLabel
            }</a>
        }
      case r: BlankNodeEntry =>
        {
          if (editable) {
            <input class="form-control" value={ r.value.toString } name={ "BLA-" + urlEncode(r.property) } data-type={ r.type_.toString() }/>
            <input value={ r.value.toString } name={ "ORIG-BLA-" + urlEncode(r.property) } type="hidden"/>
          } else
            <a href={ Form2HTML.createHyperlinkString(hrefPrefix, r.value.toString, true) }>{
              r.getId
            }</a>
        }
      case _ => <p>Should not happen! createHTMLField({ field })</p>
    }
  }

  private def makeHTMLId(re: Entry) = "RES-" + urlEncode(re.property)

  /** create HTM Literal Editable Field, taking in account owl:DatatypeProperty's range */
  def createHTMLResourceEditableLField(r: ResourceEntry): NodeSeq = {
    <div>
      {
        if (r.openChoice)
          <input class="form-control" value={ r.value.toString } name={ makeHTMLId(r) } list={ makeHTMLIdForDatalist(r) } data-type={ r.type_.toString() } placeholder={ s"Enter or paste a resource URI: URL, IRI, etc of type ${r.type_.toString()}" }/>
      }
      {
        if (!r.possibleValues.isEmpty)
          <select value={ r.value.toString } name={ makeHTMLId(r) }>
            { formatPossibleValues(r) }
          </select>
        else Seq()
      }
      {
        Seq(
          addDBPediaLookup(r),
          // formatPossibleValues(field, inDatalist=true),
          if (r.alreadyInDatabase) {
            { println("r.alreadyInDatabase " + r) }
            <input value={ r.value.toString } name={ "ORIG-RES-" + urlEncode(r.property) } type="hidden"/>
          })
      }
    </div>
  }

  /** create HTM Literal Editable Field, taking in account owl:DatatypeProperty's range */
  def createHTMLiteralEditableLField(lit: LiteralEntry): NodeSeq = {
    val placeholder = s"Enter or paste a string of type ${lit.type_.toString()}"
    val elem = lit.type_.toString() match {

      // TODO in FormSyntaxFactory match graph pattern for interval datatype ; see issue #17
      case t if t == ("http://www.bizinnov.com/ontologies/quest.owl.ttl#interval-1-5") =>
        if (radioForIntervals)
          (for (n <- Range(0, 6)) yield (
            <input type="radio" name={ makeHTMLIdForLiteral(lit) } id={ makeHTMLIdForLiteral(lit) } checked={ if (n.toString.equals(lit.value)) "checked" else null } value={ n.toString }/>
            <label for={ makeHTMLIdForLiteral(lit) }>{ n }</label>
          )).flatten
        else {
          <select name={ makeHTMLIdForLiteral(lit) }>
            {
              //              val pv = for (n <- Range(0, 6)) yield {
              //                <option value={ n.toString() }>{ n }</option>
              //              }
              formatPossibleValues(lit)
            }
          </select>
        }

      case _ =>
        <input class="form-control" value={ lit.value } name={ makeHTMLIdForLiteral(lit) } type={ HTML5Types.xsd2html5TnputType(lit.type_.toString()) } placeholder={ placeholder }/>
    }
    elem ++
      <input value={ lit.value } name={ "ORIG-LIT-" + urlEncode(lit.property) } type="hidden"/>
  }

  private def makeHTMLIdForLiteral(lit: LiteralEntry) = "LIT-" + urlEncode(lit.property)

  private def makeHTMLIdForDatalist(re: fm#Entry) = {
    "possibleValues-" + (
      re match {
        case re: fm#ResourceEntry => (re.property + "--" + re.value).hashCode().toString()
        case lit: fm#LiteralEntry => (lit.property + "--" + lit.value).hashCode().toString()
        case bn: fm#BlankNodeEntry => (bn.property + "--" + bn.value).hashCode().toString()
      })
  }

  private def formatPossibleValues(field: fm#Entry, inDatalist: Boolean = false): NodeSeq = {
    field match {
      case re @ (_: fm#ResourceEntry | _: fm#LiteralEntry) =>
        //        val options = Seq(<option label="Choose a value or leave like it is." value=""></option>) ++
        val options = Seq(<option value=""></option>) ++
          (for (value <- re.possibleValues) yield <option value={ value._1.toString() }>{ value._2 }</option>)
        if (inDatalist)
          <datalist id={ makeHTMLIdForDatalist(re) }>
            { options }
          </datalist>
        else options
      case _ => <span></span>
    }
  }

  def addDBPediaLookup(r: ResourceEntry): NodeSeq = {
    // format: OFF    <-- for scalariform
    if (r.widgetType == DBPediaLookup) {
      formatPossibleValues(r, inDatalist = true) ++
      <script>
        installDbpediaComplete( '{ makeHTMLId(r) }' );
      </script>
    } else <div></div>
    // format: ON    <-- for scalariform
  }
}

object Form2HTML {
  def urlEncode(node: Any) = { URLEncoder.encode(node.toString, "utf-8") }

  def createHyperlinkString(hrefPrefix: String, uri: String, blanknode: Boolean = false): String = {
    if (hrefPrefix == "")
      uri
    else {
      val suffix = if (blanknode) "&blanknode=true" else ""
      hrefPrefix + urlEncode(uri) + suffix
    }
  }
}
