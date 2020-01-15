package io.dazraf.multisig.flows

import co.paralleluniverse.fibers.Suspendable
import io.dazraf.multisig.contracts.MultiSigContract
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.CompositeKey
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.unwrap

@InitiatedBy(MultiSigCreateFlow::class)
class MultiSigCreateResponderFlow(private val session: FlowSession): FlowLogic<Unit>() {
  companion object {
    private val log = contextLogger()
  }

  @Suspendable
  override fun call() {
    log.info("session started")
    log.info("waiting for state")
    val state = session.receive<MultiSigContract.MultiSigAsset>().unwrap { it }
    log.info("sending our public key")
    val stx = session.sendAndReceive<SignedTransaction>(ourIdentity.owningKey).unwrap { istx ->
      log.info("received partially signed transaction. verifying ...")
      requireThat {
        "there must be a Create command" using istx.tx.commands.any { command -> command.value == MultiSigContract.Command.Create }
        val command = istx.tx.commands.first { command -> command.value == MultiSigContract.Command.Create }
        "there is one signing composite key" using (command.signers.size == 1)
        val key = command.signers.first()
        "key must be composite" using (key is CompositeKey)
        val compKey = key as CompositeKey
        "composite key must contain our key" using (compKey.leafKeys.contains(
          ourIdentity.owningKey
        ))
      }
      istx
    }
    log.info("creating a signature for the transaction")
    val sig = serviceHub.createSignature(stx, ourIdentity.owningKey)
    log.info("sending back transaction signature")
    session.send(sig)
    log.info("waiting for ledger commit")
    waitForLedgerCommit(stx.id)
    log.info("done")
  }
}