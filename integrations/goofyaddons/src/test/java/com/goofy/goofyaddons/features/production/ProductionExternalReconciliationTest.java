package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.generalflipper.ProductionBazaarOrders.ExternalOrderEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ProductionExternalReconciliationTest {
    @TempDir Path temp;
    private ExternalOrderEvidence evidence(){
        return new ExternalOrderEvidence("verification","curedmc","curedmc/Papaya","6acfc294-child","e5a941cd-parent",
            "ENCHANTED_MYCELIUM_CUBE","Enchanted Mycelium Cube",1,133475.5,133475.5,1000,null,2000,23,4);
    }
    private ProductionJobs jobs()throws Exception {
        var jobs=new ProductionJobs(temp.resolve("jobs.json"));
        jobs.put(new ProductionJobs.Job(evidence().parent(),"crafting:AMALGAMATED_CRIMSONITE","curedmc",ProductionJobs.State.REVIEW,
            1,-1,0,0,133475.5,null,null,"Interrupted ingredient procurement"));
        return jobs;
    }
    @Test void persistsOriginalReviewBeforeReleaseAndNeverRecordsCompletion()throws Exception {
        var jobs=jobs();
        var archived=ProductionExternalReconciliation.archiveAndRelease(jobs,evidence(),observed->{
            try {
                var durable=new ProductionJobs(temp.resolve("jobs.json")).find(observed.child()).orElseThrow();
                assertEquals(ProductionJobs.State.REVIEW,durable.state());
                assertEquals(133475.5,durable.costBasis());assertEquals(0,durable.completedBatches());
                assertTrue(durable.reason().contains("Refund, sale proceeds and profit remain unconfirmed"));
                assertTrue(durable.reason().contains("parent e5a941cd-parent"));
            }catch(Exception failure){throw new AssertionError(failure);}
            return true;
        });
        assertEquals(evidence().child(),archived.id());assertEquals(2,jobs.all().size());
        assertEquals(ProductionJobs.State.REVIEW,jobs.find(evidence().parent()).orElseThrow().state());
    }
    @Test void failedReleaseKeepsReviewAndRetryDoesNotDuplicateIt()throws Exception {
        var jobs=jobs();var calls=new AtomicInteger();
        assertThrows(java.io.IOException.class,()->ProductionExternalReconciliation.archiveAndRelease(jobs,evidence(),observed->{calls.incrementAndGet();return false;}));
        assertEquals(ProductionJobs.State.REVIEW,new ProductionJobs(temp.resolve("jobs.json")).find(evidence().child()).orElseThrow().state());
        ProductionExternalReconciliation.archiveAndRelease(jobs,evidence(),observed->{calls.incrementAndGet();return true;});
        assertEquals(2,calls.get());assertEquals(2,jobs.all().size());
    }
    @Test void failedDurableWriteNeverInvokesRelease()throws Exception {
        var jobs=jobs();var calls=new AtomicInteger();
        Files.createDirectory(temp.resolve("jobs.json.tmp"));
        assertThrows(java.io.IOException.class,()->ProductionExternalReconciliation.archiveAndRelease(jobs,evidence(),observed->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get());assertTrue(jobs.find(evidence().child()).isEmpty());
        assertTrue(new ProductionJobs(temp.resolve("jobs.json")).find(evidence().child()).isEmpty());
    }
    @Test void wrongAccountOrUnreviewedParentNeverReleasesChild()throws Exception {
        var jobs=jobs();var calls=new AtomicInteger();
        var parent=jobs.find(evidence().parent()).orElseThrow();
        jobs.put(parent.withState(ProductionJobs.State.BUYING,"Active procurement"));
        assertThrows(IllegalArgumentException.class,()->ProductionExternalReconciliation.archiveAndRelease(jobs,evidence(),observed->{calls.incrementAndGet();return true;}));
        jobs.put(new ProductionJobs.Job(parent.id(),parent.recipeKey(),"another-account",ProductionJobs.State.REVIEW,1,-1,0,0,133475.5,null,null,"Other account"));
        assertThrows(IllegalArgumentException.class,()->ProductionExternalReconciliation.archiveAndRelease(jobs,evidence(),observed->{calls.incrementAndGet();return true;}));
        assertEquals(0,calls.get());assertTrue(jobs.find(evidence().child()).isEmpty());
    }
    @Test void conflictingExistingChildEvidenceIsPreserved()throws Exception {
        var jobs=jobs();
        var previous=new ProductionJobs.Job(evidence().child(),"recovered:bazaar:ENCHANTED_MYCELIUM_CUBE","curedmc",ProductionJobs.State.REVIEW,1,-1,0,0,0.0,null,null,"Verified refund evidence");
        jobs.put(previous);
        assertThrows(IllegalArgumentException.class,()->ProductionExternalReconciliation.archiveAndRelease(jobs,evidence(),observed->fail("Must not release conflicting ownership")));
        assertEquals(previous,jobs.find(evidence().child()).orElseThrow());
    }
}
