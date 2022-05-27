package com.bob.xxx.processor

import com.sun.tools.javac.code.Flags
import com.sun.tools.javac.code.Type.JCVoidType
import com.sun.tools.javac.model.JavacElements
import com.sun.tools.javac.tree.JCTree.JCExpression
import com.sun.tools.javac.tree.{JCTree, TreeMaker}
import com.sun.tools.javac.util.List
import javax.lang.model.`type`.TypeMirror

trait Process {

  protected def makeLoggerField(maker: TreeMaker, utils: JavacElements, className: String): JCTree.JCVariableDecl = {
    var a: JCExpression = maker.Ident(utils.getName("org"))
    a = maker.Select(a, utils.getName("slf4j"))
    a = maker.Select(a, utils.getName("LoggerFactory"))
    a = maker.Select(a, utils.getName("getLogger"))

    val loggingType = selfType(className = className, maker = maker, utils = utils)

    val lp = maker.Apply(List.nil[JCTree.JCExpression], a, List.of(loggingType))

    val fieldName = "logger_field_" + (Math.random * 10000).toInt
    var t: JCExpression = maker.Ident(utils.getName("org"))
    t = maker.Select(t, utils.getName("slf4j"))
    t = maker.Select(t, utils.getName("Logger"))

    maker.VarDef(maker.Modifiers(Flags.FINAL), utils.getName(fieldName), t, lp)
  }

  private def selfType(maker: TreeMaker, utils: JavacElements, className: String) = {
    val c = maker.Ident(utils.getName(className))
    val m = utils.getName("class")
    maker.Select(c, m)
  }

  protected def hasRetrunValue(returnType: TypeMirror) = !classOf[JCVoidType].isAssignableFrom(returnType.getClass)
}