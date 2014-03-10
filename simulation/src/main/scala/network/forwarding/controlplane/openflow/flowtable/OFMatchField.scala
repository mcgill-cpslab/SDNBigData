package scalasem.network.forwarding.controlplane.openflow

import java.util
import java.util.Arrays

import org.openflow.protocol.OFMatch
import org.openflow.util.HexString

import utils.IPAddressConvertor

class OFMatchField() extends OFMatch {

  override def hashCode = {
    val prime: Int = 131
    var result: Int = 1
    if ((wildcards & OFMatch.OFPFW_DL_DST) == 0)
      result = prime * result + Arrays.hashCode(dataLayerDestination)
    if ((wildcards & OFMatch.OFPFW_DL_SRC) == 0)
      result = prime * result + Arrays.hashCode(dataLayerSource)
    if ((wildcards & OFMatch.OFPFW_DL_TYPE) == 0)
      result = prime * result + dataLayerType
    if ((wildcards & OFMatch.OFPFW_DL_VLAN) == 0)
      result = prime * result + dataLayerVirtualLan
    if ((wildcards & OFMatch.OFPFW_DL_VLAN_PCP) == 0)
      result = prime * result + dataLayerVirtualLanPriorityCodePoint
    if ((wildcards & OFMatch.OFPFW_IN_PORT) == 0)
      result = prime * result + inputPort
    val dstmasklen = Math.min(getNetworkDestinationMaskLen, 32)
    val srcmasklen = Math.min(getNetworkSourceMaskLen, 32)
    val dstmask = ~((1 << (32 - dstmasklen)) - 1)
    val srcmask = ~((1 << (32 - srcmasklen)) - 1)
    val dst =  networkDestination & dstmask
    val src = networkSource & srcmask
    result = prime * result + src
    result = prime * result + dst
    if ((wildcards & OFMatch.OFPFW_NW_PROTO) == 0)
      result = prime * result + networkProtocol
    if ((wildcards & OFMatch.OFPFW_NW_TOS) == 0)
      result = prime * result + networkTypeOfService
    if ((wildcards & OFMatch.OFPFW_TP_DST) == 0)
      result = prime * result + transportDestination
    if ((wildcards & OFMatch.OFPFW_TP_SRC) == 0)
      result = prime * result + transportSource
    result
  }

  def toCompleteString() : String = {
    val sb = StringBuilder.newBuilder
    sb.append("inport :" + this.inputPort + ", ")
    sb.append("dl src:" + HexString.toHexString(this.dataLayerSource) + ", ")
    sb.append("dl dst:" + HexString.toHexString(this.dataLayerDestination) + ", ")
    sb.append("nw src:" + IPAddressConvertor.IntToDecimalString(networkSource) + ", ")
    sb.append("nw dst:" + IPAddressConvertor.IntToDecimalString(networkDestination) + ", ")
    sb.append("vlan id:" + this.dataLayerVirtualLan + ", ")
    sb.append("wildcards:" + this.wildcards)
    sb.toString()
  }

  def matching(toCompare : OFMatchField) : Boolean = {
    if ((wildcards & OFMatch.OFPFW_IN_PORT) == 0 &&
      this.inputPort != toCompare.getInputPort) {
      return false
    }
    if ((wildcards & OFMatch.OFPFW_DL_DST) == 0 &&
      !util.Arrays.equals(this.dataLayerDestination, toCompare.getDataLayerDestination)) {
      return false
    }
    if ((wildcards & OFMatch.OFPFW_DL_SRC) == 0 &&
      !util.Arrays.equals(this.dataLayerSource, toCompare.getDataLayerSource)) {
      return false
    }
    if ((wildcards & OFMatch.OFPFW_DL_TYPE) == 0
      && this.dataLayerType != toCompare.getDataLayerType) {
      return false
    }
    if ((wildcards & OFMatch.OFPFW_DL_VLAN) == 0 &&
      this.dataLayerVirtualLan != toCompare.getDataLayerVirtualLan) {
      return false
    }
    if ((wildcards & OFMatch.OFPFW_DL_VLAN_PCP) == 0 &&
      this.dataLayerVirtualLanPriorityCodePoint != toCompare.getDataLayerVirtualLanPriorityCodePoint) {
      return false
    }
    if ((wildcards & OFMatch.OFPFW_NW_PROTO) == 0 &&
      this.networkProtocol != toCompare.getNetworkProtocol) {
      return false
    }
    if ((wildcards & OFMatch.OFPFW_NW_TOS) == 0 &&
      this.networkTypeOfService != toCompare.getNetworkTypeOfService) {
      return false
    }
    //compare network layer src/dst
    val dstmasklen = getNetworkDestinationMaskLen
    val srcmasklen = getNetworkSourceMaskLen
    if (dstmasklen > 32 && networkDestination != toCompare.getNetworkDestination) {
      return false
    }
    if (srcmasklen > 32 && networkSource != toCompare.getNetworkSource) {
      return false
    }
    val dstmask = ~((1 << (32 - dstmasklen)) - 1)
    val srcmask = ~((1 << (32 - srcmasklen)) - 1)
    if (dstmasklen <= 32 &&
      (networkDestination & dstmask) != (toCompare.getNetworkDestination & dstmask)) {
      return false
    }
    if (srcmasklen <= 32 &&
      (networkSource & srcmask) != (toCompare.getNetworkSource & srcmask)) {
      return false
    }
    //layer - 4
    if ((wildcards & OFMatch.OFPFW_TP_DST) == 0 &&
      this.transportDestination != toCompare.getTransportDestination) {
      return false
    }
    if ((wildcards & OFMatch.OFPFW_TP_SRC) == 0 &&
      this.transportSource != toCompare.getTransportSource) {
      return false
    }
    true
  }

  override def equals(obj : Any) : Boolean = {
    if (!obj.isInstanceOf[OFMatchField])
      return false
    matching(obj.asInstanceOf[OFMatchField])
  }
}
