package in.ac.caluniv.cse.ubrca.scheduler.policy;

/**
 * Migration behavior supported by the simulator.
 */
public enum MigrationMode {
    /** The policy never migrates a running container after placement. */
    NONE,

    /** The policy migrates only after a VM is already low on credits or exhausted. */
    REACTIVE,

    /** The policy predicts future credit risk and migrates before exhaustion. */
    PROACTIVE
}
