package scalasem.network.topology

class Link (val end_from : Node,
             val end_to : Node,
            private[network] val bandwidth : Double) {

  override def toString = "link-" + end_from.ip_addr(0) + "-" + end_to.ip_addr(0)
}

object Link {
  def otherEnd (link : Link, node : Node) : Node = {
    assert(link != null)
    if (link.end_to == node) link.end_from
    else link.end_to
  }
}
