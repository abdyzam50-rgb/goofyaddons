package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.ExternalOrderEvidence;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Predicate;

/** Durable review evidence must precede releasing externally handled ownership. */
final class ProductionExternalReconciliation {
    private ProductionExternalReconciliation() {}

    static ProductionJobs.Job archiveAndRelease(ProductionJobs jobs,ExternalOrderEvidence evidence,
            Predicate<ExternalOrderEvidence> release)throws IOException {
        var parent=jobs.find(evidence.parent()).orElseThrow(()->new IllegalArgumentException("Original craft parent is missing; order retained"));
        if(!parent.account().equals(evidence.account()) || parent.state()!=ProductionJobs.State.REVIEW)
            throw new IllegalArgumentException("Original craft parent must belong to this account and remain in REVIEW");
        var archived=review(evidence);
        var previous=jobs.find(archived.id()).orElse(null);
        if(previous!=null && (!previous.recipeKey().equals(archived.recipeKey()) || !previous.account().equals(archived.account())
                || !Objects.equals(previous.costBasis(),archived.costBasis()) || previous.submittedAt()!=archived.submittedAt()
                || !Objects.toString(previous.reason(),"").contains(originalEvidence(evidence))))
            throw new IllegalArgumentException("Existing child review conflicts with the original order; evidence retained");
        // A retry uses the same child ID, so interrupted handoffs cannot create duplicate evidence.
        jobs.put(archived);
        if(!release.test(evidence))throw new IOException("Original order could not be released; saved review and order retained");
        return archived;
    }

    private static ProductionJobs.Job review(ExternalOrderEvidence evidence) {
        return new ProductionJobs.Job(evidence.child(),"reconciled:external:bazaar:"+evidence.product(),evidence.account(),
            ProductionJobs.State.REVIEW,1,-1,evidence.placedAt(),evidence.observedAt(),evidence.cost(),null,null,
            "Player declared external claim/movement/sale of an ingredient. Fresh loaded orders show no owned matching order, and accessible inventory contains none of this product. "
                +originalEvidence(evidence)+" Checked at "+evidence.observedAt()+", container "+evidence.container()
                +", server observation "+evidence.serverObservation()+". Recorded cost is the original order commitment, not proof of acquired inventory. "
                +"Refund, sale proceeds and profit remain unconfirmed; no acquisition or settlement was recorded.");
    }
    private static String originalEvidence(ExternalOrderEvidence evidence) {
        return "Original child "+evidence.child()+"; parent "+evidence.parent()+"; account scope "+evidence.accountScope()
            +"; product "+evidence.product()+" ("+evidence.name()+"); units "+evidence.units()+"; unit cost "+evidence.unitCost()
            +"; committed cost "+evidence.cost()+"; placed at "+evidence.placedAt()+"; saved cancellation refund "
            +Objects.toString(evidence.confirmedCancelRefund(),"unconfirmed")+".";
    }
}
