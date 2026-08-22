package in.ac.caluniv.cse.ubrca.scheduler;

import in.ac.caluniv.cse.ubrca.scheduler.policy.BranchAndBoundProxyPolicy;
import in.ac.caluniv.cse.ubrca.scheduler.policy.ChanceConstrainedPolicy;
import in.ac.caluniv.cse.ubrca.scheduler.policy.CreditAwareReactivePolicy;
import in.ac.caluniv.cse.ubrca.scheduler.policy.EnergyAwareVmConsolidationPolicy;
import in.ac.caluniv.cse.ubrca.scheduler.policy.HeftPolicy;
import in.ac.caluniv.cse.ubrca.scheduler.policy.KubernetesResourcePackingPolicy;
import in.ac.caluniv.cse.ubrca.scheduler.policy.SchedulingPolicy;
import in.ac.caluniv.cse.ubrca.scheduler.policy.UbrCaNoBayesPolicy;
import in.ac.caluniv.cse.ubrca.scheduler.policy.UbrCaNoMigrationPolicy;
import in.ac.caluniv.cse.ubrca.scheduler.policy.UbrCaPolicy;

/**
 * Stable identifiers for the scheduling algorithms used in experiments.
 *
 * <p>The algorithmic concepts are implemented in separate classes under
 * {@code in.ac.caluniv.cse.ubrca.scheduler.policy}. This enum is deliberately
 * kept as a lightweight identifier so result tables, CSV files, and plots keep
 * stable ordering and labels.</p>
 */
public enum SchedulerPolicy {
    HEFT("HEFT"),
    KRP("KRP"),
    EAVC("EA-VC"),
    CCS("CCS"),
    CARS("CARS"),
    BNB("Strict-feasibility diagnostic"),
    UBR_CA_NO_MIGRATION("UBR-CA no migration"),
    UBR_CA_NO_BAYES("UBR-CA no Bayesian"),
    UBR_CA("UBR-CA");

    private final String label;

    SchedulerPolicy(String label) {
        this.label = label;
    }

    public String label() { return label; }

    /** Create the documented strategy class implementing this policy. */
    public SchedulingPolicy createPolicy() {
        return switch (this) {
            case HEFT -> new HeftPolicy();
            case KRP -> new KubernetesResourcePackingPolicy();
            case EAVC -> new EnergyAwareVmConsolidationPolicy();
            case CCS -> new ChanceConstrainedPolicy();
            case CARS -> new CreditAwareReactivePolicy();
            case BNB -> new BranchAndBoundProxyPolicy();
            case UBR_CA_NO_MIGRATION -> new UbrCaNoMigrationPolicy();
            case UBR_CA_NO_BAYES -> new UbrCaNoBayesPolicy();
            case UBR_CA -> new UbrCaPolicy();
        };
    }
}
