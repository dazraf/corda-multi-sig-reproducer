package io.dazraf.multisig.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

class MultiSigContract : Contract {
  companion object {
    const val CONTRACT_ID: ContractClassName = "MultiSigContract"
  }

  override fun verify(tx: LedgerTransaction) {
    val inputs = tx.inputs
    val outputs = tx.outputs
    val commands = tx.commands
  }

  interface Command : CommandData {
    object Create : TypeOnlyCommandData(),
                    Command
    class Move(override val contract: Class<out Contract> = MultiSigContract::class.java)
      : MoveCommand,
        Command
  }

  data class MultiSigAsset(override val owner: AbstractParty) : OwnableState {
    override val participants: List<AbstractParty> get() = listOf(owner)
    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
      return CommandAndState(
        Command.Move(),
        copy(owner = newOwner)
      )
    }
  }
}

