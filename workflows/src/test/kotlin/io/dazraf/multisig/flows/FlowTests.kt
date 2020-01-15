package io.dazraf.multisig.flows

import io.dazraf.multisig.contracts.MultiSigContract
import net.corda.core.internal.packageName
import net.corda.core.utilities.contextLogger
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp.Companion.findCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowTests {
  companion object {
    private val log = contextLogger()
  }

  private val network = MockNetwork(
    MockNetworkParameters(
      cordappsForAllNodes = listOf(
        findCordapp(MultiSigContract::class.packageName),
        findCordapp(MultiSigCreateFlow::class.packageName),
        findCordapp(MultiSigCreateResponderFlow::class.packageName)
      )
    )
  )
  private val a = network.createNode()
  private val b = network.createNode()

  init {
    listOf(a, b).forEach {
      it.registerInitiatedFlow(MultiSigCreateResponderFlow::class.java)
    }
  }

  @Before
  fun setup() = network.runNetwork()

  @After
  fun tearDown() = network.stopNodes()

  @Test
  fun `create multi sig`() {
    val flow = a.startFlow(
      MultiSigCreateFlow(network.defaultNotaryIdentity, b.info.singleIdentity())
    )
    network.runNetwork()
    val stx = flow.toCompletableFuture().get()
    log.info("flow returned $stx")
  }
}