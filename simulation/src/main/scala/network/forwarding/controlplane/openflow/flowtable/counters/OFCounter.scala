package scalasem.network.controlplane.openflow.flowtable.counters

class OFCounter (private [openflow] val name : String) {
}

//Per table counters

class OFTableCount extends OFCounter("table_counter") {
  private [controlplane] var referencecount  : Int = 0
  private [controlplane] var packetlookup : Long = 0
  private [controlplane] var packetmatches : Long = 0
  private [controlplane] var flowbytes : Int = 0
}

//Per port counters
class OFPortCount (private [openflow] val port_num : Int) extends OFCounter("port_counter") {
  private [controlplane] var receivedpacket : Long = 0
  private [controlplane] var transmittedpacket : Long = 0
  private [controlplane] var receivedbytes : Long = 0
  private [controlplane] var transmittedbytes : Long = 0
  private [controlplane] var receivedrops : Long = 0
  private [controlplane] var transmitdrops : Long = 0
  private [controlplane] var receiveerror : Long = 0
  private [controlplane] var transmiterror : Long = 0
  private [controlplane] var receiveframe_align_error : Long = 0
  private [controlplane] var receive_overrun_error : Long = 0
  private [controlplane] var receive_crc_error : Long = 0
  private [controlplane] var collisions : Long = 0
}

class OFFlowCount () extends OFCounter("flow_counter") {
  private [controlplane] var receivedpacket : Long = 0
  private [controlplane] var receivedbytes : Long = 0
  private [controlplane] var durationSeconds : Int = 0
  private [controlplane] var durationNanoSeconds : Int = 0
}

