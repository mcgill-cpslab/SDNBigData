package scalasem.network.traffic


abstract class FlowStatus

case object NewStartFlow extends FlowStatus {override def toString = "NewStartFlow"}
case object RunningFlow extends FlowStatus {override def toString = "RunningFlow"}
case object ChangingRateFlow extends FlowStatus {override def toString = "ChangingFlow"}
case object CompletedFlow extends FlowStatus  {override def toString = "CompletedFlow"}