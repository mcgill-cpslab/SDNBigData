package scalasem.network.forwarding.controlplane.openflow

class OFCounter (private [openflow] val name : String) {
}

//Per table counters

class OFTableCount extends OFCounter("table_counter") {
  private [controlplane] var packetlookup : Long = 0
  private [controlplane] var packetmatches : Long = 0
  private [forwarding] var referencecount : Int = 0
  private [forwarding] var flowbytes : Int = 0

  def increaseTableCounters(pl : Long, pm : Long) {
    packetlookup += pl
    packetmatches += pm
  }
}

//Per port counters
class OFPortCount (private [openflow] val port_num : Int) extends OFCounter("port_counter") {
  private [forwarding] var receivedpacket : Long = 0
  private [forwarding] var transmittedpacket : Long = 0
  private [forwarding] var receivedbytes : Long = 0
  private [forwarding] var transmittedbytes : Long = 0
  private [forwarding] var receivedrops : Long = 0
  private [forwarding] var transmitdrops : Long = 0
  private [forwarding] var receivederror : Long = 0
  private [forwarding] var transmiterror : Long = 0
  private [forwarding] var receiveframe_align_error : Long = 0
  private [forwarding] var receive_overrun_error : Long = 0
  private [forwarding] var receive_crc_error : Long = 0
  private [forwarding] var collisions : Long = 0


  /**
   *
   * @param receivedpkt
   * @param transmittedpkt
   * @param rcvedbytes
   * @param trsttedbytes
   * @param rcveddrops
   * @param receiveddrops
   * @param trsmittteddrops
   * @param rcvederrors
   * @param trsmittederrors
   * @param rcvedframe_aligh_error
   * @param rcved_overrun_error
   * @param rcved_crc_error
   * @param collisns
   */
  def increasePortCounters(receivedpkt : Long, transmittedpkt : Long, rcvedbytes : Long,
                        trsttedbytes : Long, rcveddrops : Long, receiveddrops : Long,
                        trsmittteddrops : Long, rcvederrors: Long, trsmittederrors : Long,
                        rcvedframe_aligh_error : Long, rcved_overrun_error: Long, rcved_crc_error : Long,
                        collisns : Long) {
    receivedpacket += receivedpkt
    transmittedpacket += transmittedpkt
    receivedbytes += rcvedbytes
    transmittedbytes += trsttedbytes
    receivedrops += rcveddrops
    transmitdrops += trsmittteddrops
    receivederror += rcvederrors
    transmiterror += trsmittederrors
    receiveframe_align_error += rcvedframe_aligh_error
    receive_overrun_error += rcved_overrun_error
    receive_crc_error += rcved_crc_error
    collisions += collisns
  }
}

class OFFlowCount extends OFCounter("flow_counter") {
  private [forwarding] var receivedpacket : Long = 0
  private [forwarding] var receivedbytes : Long = 0
  private [forwarding] var durationSeconds : Int = 0
  private [forwarding] var durationNanoSeconds : Int = 0

  def increaseFlowCounters(rp : Long, rb : Long, ds : Int, dns : Int) {
    receivedpacket += rp
    receivedbytes += rb
    durationSeconds += ds
    durationNanoSeconds += dns
  }
}

