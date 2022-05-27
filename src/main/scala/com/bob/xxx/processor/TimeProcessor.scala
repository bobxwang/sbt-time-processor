package com.bob.xxx.processor

import com.bob.xxx.annotation.TimeAnnotation

import java.util
import com.sun.source.util.Trees
import com.sun.tools.javac.code.{Flags, TypeTag}
import com.sun.tools.javac.model.JavacElements
import com.sun.tools.javac.processing.JavacProcessingEnvironment
import com.sun.tools.javac.tree.JCTree._
import com.sun.tools.javac.tree.{JCTree, TreeMaker, TreeTranslator}
import com.sun.tools.javac.util.{List, Name}

import javax.annotation.processing._
import javax.lang.model.SourceVersion
import javax.lang.model.element.{ElementKind, ExecutableElement, TypeElement}
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import scala.collection.JavaConverters._

@SupportedAnnotationTypes(value = Array {
  "com.geely.cloud.server.annotation.TimeAnnotation"
})
class TimeProcessor extends AbstractProcessor with Process {

  private var filer: Filer = _

  private var messager: Messager = _

  private var elementUtils: JavacElements = _

  private var typeUtils: Types = _

  private var trees: Trees = _

  private var treeMaker: TreeMaker = _

  private var env: JavacProcessingEnvironment = _

  override def process(annotations: util.Set[_ <: TypeElement], roundEnv: RoundEnvironment): Boolean = {

    if (!roundEnv.processingOver()) {

      messager.printMessage(Diagnostic.Kind.NOTE, "begin to handler timeProcessor")

      annotations.asScala.foreach(x => {

        val annotatedElements = roundEnv.getElementsAnnotatedWith(x).asScala
        annotatedElements.foreach(y => {

          if (y.getKind == ElementKind.METHOD) {

            /* 开始： 添加一个私有方法，方法名为原先的方法名加上 Copy */
            val originMethod = elementUtils.getTree(y).asInstanceOf[JCTree.JCMethodDecl]
            var copyStatements = List.nil[JCStatement]()
            originMethod.getBody.getStatements.asScala.foreach(xxx => {
              copyStatements = copyStatements.append(xxx)
            })
            val block = treeMaker.Block(0, copyStatements)
            val newMethod = treeMaker.MethodDef(treeMaker.Modifiers(Flags.PRIVATE),
              elementUtils.getName(s"${originMethod.name}Copy"),
              originMethod.restype,
              originMethod.typarams,
              originMethod.recvparam,
              originMethod.params,
              originMethod.thrown,
              block,
              originMethod.defaultValue
            )
            val cjt = trees.getTree(y.getEnclosingElement).asInstanceOf[JCTree]
            cjt.accept(new TreeTranslator() {
              override def visitClassDef(jcClassDecl: JCClassDecl): Unit = {
                jcClassDecl.defs = jcClassDecl.defs.prepend(newMethod)
                super.visitClassDef(jcClassDecl)
              }
            })
            /* 结束： 添加一个私有方法，方法名为原先的方法名加上 Copy */

            val packageName = elementUtils.getPackageOf(y).getQualifiedName.toString
            val className = ((y.getEnclosingElement).asInstanceOf[TypeElement]).getQualifiedName.toString.replace(packageName + ".", "")
            val methodName = y.getSimpleName.toString
            val ey = y.asInstanceOf[ExecutableElement]
            val returnType = ey.getReturnType
            val inputType = ey.getParameters
            messager.printMessage(Diagnostic.Kind.NOTE, packageName + " ---> " + className + " ---> " + methodName + " ---> " + returnType + " ---> " + inputType.asScala.mkString(" -- "))

            var abcdStatements = List.nil[JCStatement]()
            val timeStartField = makeTimeStartField(treeMaker, elementUtils)
            val loggerField = makeLoggerField(treeMaker, elementUtils, className)
            abcdStatements = abcdStatements.append(timeStartField)
            abcdStatements = abcdStatements.append(loggerField)

            var newStatements = List.nil[JCStatement]()

            val timeAnnotation = y.getAnnotation(classOf[TimeAnnotation])
            if (inputType.size() > 0 && timeAnnotation.printInput()) { // 打印入参
              inputType.asScala.map(ii => {
                val stmt = printArgs(loggerField.getName, ii.getSimpleName.toString, s"${ii.getSimpleName}")
                newStatements = newStatements.append(stmt)
              })
            }

            // 将现有方法体改写成调用Copy方法的调用
            val a: JCExpression = treeMaker.Ident(elementUtils.getName(s"${originMethod.name}Copy"))
            val jcm: JCTree.JCMethodInvocation = if (inputType.size() > 0) {
              var ip = List.nil[JCTree.JCExpression]()
              inputType.asScala.foreach(xi => {
                val c: JCExpression = treeMaker.Ident(elementUtils.getName(xi.getSimpleName.toString))
                ip = ip.append(c)
              })
              treeMaker.Apply(List.nil[JCTree.JCExpression], a, ip)
            } else {
              treeMaker.Apply(List.nil[JCTree.JCExpression], a, List.nil[JCTree.JCExpression]())
            }

            if (hasRetrunValue(returnType)) {

              val rsName = "rs_field_" + (Math.random * 10000).toInt

              var rsto = returnType.toString
              val iiiii = rsto.indexOf("<")
              if (iiiii > 0) {
                // 防止返回值类型是泛型的情况
                rsto = rsto.substring(0, iiiii)
              }
              val pc = rsto.split("""\.""")
              // 获得返回值类型并赋值给 ft
              val ft = if (pc.size > 0) {
                var a: JCExpression = treeMaker.Ident(elementUtils.getName(pc(0)))
                pc.drop(1).foreach(pcc => {
                  a = treeMaker.Select(a, elementUtils.getName(pcc))
                })
                a
              } else {
                treeMaker.Ident(elementUtils.getName(rsto))
              }

              // 定义一个私有变量名字为,类型为 ft,用于接收返回值
              val lll = treeMaker.VarDef(treeMaker.Modifiers(Flags.FINAL), elementUtils.getName(rsName), ft, jcm)
              newStatements = newStatements.append(lll)

              if (timeAnnotation.printOutput()) {
                val stmt = printArgs(loggerField.getName, rsName, s"${methodName} result")
                newStatements = newStatements.append(stmt)
              }
              val returnStatement = treeMaker.Return(treeMaker.Ident(elementUtils.getName(rsName)))
              newStatements = newStatements.append(returnStatement)
            } else {
              val lc = treeMaker.Exec(jcm)
              newStatements = newStatements.append(lc)
            }

            val finalizer = makePrintBlock(treeMaker, elementUtils, timeStartField, loggerField)
            val stat = treeMaker.Try(treeMaker.Block(0, newStatements), List.nil[JCTree.JCCatch](), finalizer)
            var abtryBlock = List.nil[JCStatement]()
            abtryBlock = abtryBlock.appendList(abcdStatements)
            abtryBlock = abtryBlock.append(stat)
            originMethod.body.stats = abtryBlock
          }
        })
      })

      messager.printMessage(Diagnostic.Kind.NOTE, "end to handler timeProcessor")
    } else {
      messager.printMessage(Diagnostic.Kind.NOTE, "timeProcessor assertions inlined")
    }

    true
  }

  protected def makePrintBlock(maker: TreeMaker, utils: JavacElements, `var`: JCTree.JCVariableDecl, loggerField: JCTree.JCVariableDecl): JCTree.JCBlock = {

    var printlnExpression: JCExpression = maker.Ident(loggerField.getName)
    printlnExpression = maker.Select(printlnExpression, utils.getName("info"))

    val currentTime: JCExpression = makeCurrentTime(maker, utils)
    val elapsedTime: JCExpression = maker.Binary(Tag.MINUS, currentTime, maker.Ident(`var`.name))
    var formatExpression: JCExpression = maker.Ident(utils.getName("String"))
    formatExpression = maker.Select(formatExpression, utils.getName("format"))
    var formatArgs = List.nil[JCExpression]()
    formatArgs = formatArgs.append(maker.Literal("Elapsed %s"))
    formatArgs = formatArgs.append(elapsedTime)
    val format = maker.Apply(List.nil[JCExpression](), formatExpression, formatArgs)
    var printlnArgs = List.nil[JCExpression]()
    printlnArgs = printlnArgs.append(format)

    val print = maker.Apply(List.nil[JCTree.JCExpression], printlnExpression, printlnArgs)

    val stmt = maker.Exec(print)
    var stmts = List.nil[JCStatement]()
    stmts = stmts.append(stmt)
    maker.Block(0, stmts)
  }

  protected def makeTimeStartField(maker: TreeMaker, utils: JavacElements): JCTree.JCVariableDecl = {
    val currentTime = makeCurrentTime(maker, utils)
    val fieldName = "time_start_" + (Math.random * 10000).toInt
    // 定义一个变量并赋初始值
    maker.VarDef(maker.Modifiers(Flags.FINAL), utils.getName(fieldName), maker.TypeIdent(TypeTag.LONG), currentTime)
  }

  private def makeCurrentTime(maker: TreeMaker, utils: JavacElements) = {
    var exp: JCExpression = maker.Ident(utils.getName("System"))
    exp = maker.Select(exp, utils.getName("currentTimeMillis"))
    // 定义一个方法调用
    maker.Apply(List.nil[JCTree.JCExpression], exp, List.nil[JCTree.JCExpression])
  }

  override def getSupportedSourceVersion: SourceVersion = SourceVersion.latestSupported

  override def init(processingEnv: ProcessingEnvironment): Unit = {
    super.init(processingEnv)

    env = processingEnv.asInstanceOf[JavacProcessingEnvironment]
    filer = processingEnv.getFiler
    messager = processingEnv.getMessager
    elementUtils = processingEnv.getElementUtils.asInstanceOf[JavacElements]
    typeUtils = processingEnv.getTypeUtils
    trees = Trees.instance(processingEnv)
    treeMaker = TreeMaker.instance(env.getContext)
  }

  /**
    *
    * @param name    自定义的Logger类型私有变量名
    * @param argName 入参或出参参数名称
    * @param inputName
    * @return
    */
  private def printArgs(name: Name, argName: String, inputName: String) = {
    var printlnExpression: JCExpression = treeMaker.Ident(name)
    printlnExpression = treeMaker.Select(printlnExpression, elementUtils.getName("info"))

    var formatExpression: JCExpression = treeMaker.Ident(elementUtils.getName("String"))
    formatExpression = treeMaker.Select(formatExpression, elementUtils.getName("format"))

    var aformatArgs = List.nil[JCExpression]()
    aformatArgs = aformatArgs.append(treeMaker.Ident(elementUtils.getName(argName)))
    var c: JCExpression = treeMaker.Ident(elementUtils.getName("String"))
    c = treeMaker.Select(c, elementUtils.getName("valueOf"))
    val cmc = treeMaker.Apply(List.nil[JCTree.JCExpression], c, aformatArgs)

    var formatArgs = List.nil[JCExpression]()
    formatArgs = formatArgs.append(treeMaker.Literal(s"${inputName} is -> %s"))
    formatArgs = formatArgs.append(cmc)
    val format = treeMaker.Apply(List.nil[JCExpression](), formatExpression, formatArgs)

    var printlnArgs = List.nil[JCExpression]()
    printlnArgs = printlnArgs.append(format)
    val print = treeMaker.Apply(List.nil[JCTree.JCExpression], printlnExpression, printlnArgs)
    val stmt = treeMaker.Exec(print)
    stmt
  }
}