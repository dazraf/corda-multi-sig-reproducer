package io.dazraf.multisig.flows

import co.paralleluniverse.fibers.Suspendable
import io.dazraf.multisig.contracts.MultiSigContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.unwrap
import java.security.PublicKey

@StartableByRPC
@StartableByService
@InitiatingFlow
class MultiSigCreateFlow(private val notary: Party, private val otherParty: Party) :
  FlowLogic<SignedTransaction>() {

  companion object {
    private val log = contextLogger()
  }

  @Suspendable
  override fun call(): SignedTransaction {
    log.info("started")
    val output = MultiSigContract.MultiSigAsset(this.ourIdentity)
    val session = this.initiateFlow(otherParty)
    log.info("sending output")
    val otherKey = session.sendAndReceive<PublicKey>(output).unwrap {
      log.info("verifying key $it")
      requireThat {
        "that the key is not a composite key" using (it !is CompositeKey)
      }
      it
    }
    log.info("verifying key")
    val compositeKey = CompositeKey.Builder().apply {
      addKey(ourIdentity.owningKey, 1)
      addKey(otherKey, 1)
    }.build(1)

    log.info("creating transaction")
    val itx = TransactionBuilder(notary = notary).apply {
      addCommand(Command(MultiSigContract.Command.Create, compositeKey))
      addOutputState(
        output,
        MultiSigContract.CONTRACT_ID
      )
      val queryResult = serviceHub.attachments.queryAttachments(AttachmentQueryCriteria.AttachmentsQueryCriteria())
      verify(serviceHub)
    }.let {
      log.info("signging initial transaction")
      serviceHub.signInitialTransaction(it)
    }
    log.info("sending intial transaction to other party")
    val transactionSig = session.sendAndReceive<Any>(itx).unwrap {
      log.info("received back $it")
      when (it) {
        is TransactionSignature -> it
        else -> error("party refused to sign transaction")
      }
    }
    log.info("adding signature $transactionSig")
    val stx = itx.withAdditionalSignature(transactionSig)
    log.info("invoking finality flow")
    val txHash = subFlow(
      FinalityFlow(stx, listOf(session))
    ).id
    log.info("waiting for ledger commit")
    waitForLedgerCommit(txHash)
    log.info("done")
    return stx
  }
}