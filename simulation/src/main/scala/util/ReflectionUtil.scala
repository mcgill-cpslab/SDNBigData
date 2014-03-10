package scalasem.util

@deprecated("TBD", "1.0.0")
object ReflectionUtil {

  /**
   * TODO: change to Scala 2.10.x API
   * @param className, the full path of the class
   * @return the instance of the class
   */
  def newInstance(className: String, parameters: Array[Any],
                  parameterTypes: Class[_]*): Any = {
    val clz = Class.forName(className)
    val clzCtor = clz.getConstructor(parameterTypes(0))
    clzCtor.newInstance(parameters)
  }
}
