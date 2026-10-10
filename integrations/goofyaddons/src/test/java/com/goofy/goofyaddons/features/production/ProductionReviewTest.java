package com.goofy.goofyaddons.features.production;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProductionReviewTest {
    @TempDir Path dir;
    ProductionJobs.Job job(String id,String account,ProductionJobs.State state) {
        return new ProductionJobs.Job(id,"run:craft:OUTPUT",account,state,1,-1,0,0,1314.8,null,null,"Unknown balance",0);
    }
    @Test void acknowledgementRetainsEvidenceAndPersistsWithoutAssumingProfit()throws Exception {
        Path path=dir.resolve("jobs.json");var jobs=new ProductionJobs(path);
        jobs.put(job("429960e1-full","owner",ProductionJobs.State.REVIEW));
        var result=jobs.acknowledgeReview("429960e1","owner");
        assertEquals(ProductionJobs.State.CANCELLED,result.state());assertEquals(1314.8,result.costBasis());
        assertTrue(result.reason().contains("no sale or profit assumed"));assertTrue(result.reason().contains("Unknown balance"));
        assertEquals(result,new ProductionJobs(path).find(result.id()).orElseThrow());
    }
    @Test void foreignActiveAmbiguousAndShortJobReferencesCannotBeAcknowledged()throws Exception {
        var jobs=new ProductionJobs(dir.resolve("jobs.json"));
        jobs.put(job("11111111-a","owner",ProductionJobs.State.REVIEW));
        jobs.put(job("11111111-b","owner",ProductionJobs.State.REVIEW));
        jobs.put(job("22222222-a","other",ProductionJobs.State.REVIEW));
        jobs.put(job("33333333-a","owner",ProductionJobs.State.BUYING));
        for(String id:new String[]{"11111111","22222222","33333333","1111"})
            assertThrows(IllegalArgumentException.class,()->jobs.acknowledgeReview(id,"owner"));
        assertEquals(3,jobs.all().stream().filter(j->j.state()==ProductionJobs.State.REVIEW).count());
        assertEquals(ProductionJobs.State.BUYING,jobs.find("33333333-a").orElseThrow().state());
    }
}
