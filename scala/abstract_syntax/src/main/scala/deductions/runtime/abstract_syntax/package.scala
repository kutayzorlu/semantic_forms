package deductions.runtime

import com.typesafe.scalalogging.Logger
import deductions.runtime.utils.LogUtils

package object abstract_syntax extends LogUtils {
//  val logger = LogManager.getLogger("abstract_syntax")
  val logger = Logger("abstract_syntax")

//  def time[T](mess: String, sourceCode: => T): T = {
//    super.time(mess, sourceCode, activate = logger.isDebugEnabled())
//  }

}