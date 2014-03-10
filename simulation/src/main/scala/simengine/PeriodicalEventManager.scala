package scalasem.simengine


object PeriodicalEventManager {

  var event : Event = null

  def run(startT: Double, endT: Double, step : Double) {
    if (event != null) {
      var t = startT
      while (t + step <= endT ) {
        event = event.repeatInFuture(t + step)
        if (event == null) return
        t = t + step
        SimulationEngine.addEvent(event)
      }
    }
  }
}
