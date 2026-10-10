package com.goofy.goofyaddons.features.production;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Durable intent and ownership. A saved click intent is uncertain until live evidence verifies it. */
public final class ProductionJobs {
    public enum State {PLANNED,BUYING,PROCESSING,SUBMITTING,WAITING,CLAIMING,OUTPUT_READY,LISTING,SELLING,DONE,CANCELLED,REVIEW}
    public record Job(String id,String recipeKey,String account,State state,int batches,int workstationSlot,
            long submittedAt,long readyAt,Double costBasis,String petUuid,String auctionUuid,String reason,int completedBatches) {
        public Job(String id,String recipeKey,String account,State state,int batches,int workstationSlot,
                long submittedAt,long readyAt,Double costBasis,String petUuid,String auctionUuid,String reason) {
            this(id,recipeKey,account,state,batches,workstationSlot,submittedAt,readyAt,costBasis,petUuid,auctionUuid,reason,0);
        }
        public Job {
            if(id==null || id.isBlank() || recipeKey==null || recipeKey.isBlank() || account==null || account.isBlank()
                    || state==null || batches<1 || batches>4096 || workstationSlot< -1 || workstationSlot>54
                    || completedBatches<0 || completedBatches>batches || submittedAt<0 || readyAt<0 || costBasis!=null && (!Double.isFinite(costBasis) || costBasis<0))
                throw new IllegalArgumentException("Invalid production job");
        }
        public Job withCost(double cost,String reason){return new Job(id,recipeKey,account,state,batches,workstationSlot,submittedAt,readyAt,cost,petUuid,auctionUuid,reason,completedBatches);}
        public Job withState(State next,String reason){return new Job(id,recipeKey,account,next,batches,workstationSlot,submittedAt,readyAt,costBasis,petUuid,auctionUuid,reason,completedBatches);}
        public Job submitted(int slot,long now,long ready,Double cost){return new Job(id,recipeKey,account,State.WAITING,batches,slot,now,ready,cost,petUuid,auctionUuid,null,completedBatches);}
        public Job completedBatch(){
            int completed=completedBatches+1;
            return new Job(id,recipeKey,account,completed==batches?State.OUTPUT_READY:state,batches,workstationSlot,
                submittedAt,readyAt,costBasis,petUuid,auctionUuid,"Verified output batches; no sale or profit assumed",completed);
        }
    }
    private final Path path;
    private final Map<String,Job> jobs=new LinkedHashMap<>();
    public ProductionJobs(Path path)throws java.io.IOException {
        this.path=path;
        if(Files.exists(path)) {
            try {
                var root=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if(root.get("schema").getAsInt()!=1 || root.getAsJsonArray("jobs").size()>4096)throw new IllegalArgumentException("Invalid job journal");
                for(var row:root.getAsJsonArray("jobs")) {
                    Job j=new Gson().fromJson(row,Job.class);
                    // Constructor validation cannot be bypassed by deserialization.
                    j=new Job(j.id,j.recipeKey,j.account,j.state,j.batches,j.workstationSlot,j.submittedAt,j.readyAt,j.costBasis,j.petUuid,j.auctionUuid,j.reason,j.completedBatches);
                    if(jobs.putIfAbsent(j.id,j)!=null)throw new IllegalArgumentException("Duplicate job ID");
                }
            }catch(RuntimeException invalid){throw new java.io.IOException("Production journal unreadable; original file preserved",invalid);}
        }
    }
    public List<Job> all(){return List.copyOf(jobs.values());}
    public Optional<Job> find(String id){return Optional.ofNullable(jobs.get(id));}
    public void put(Job job)throws java.io.IOException {
        if(!jobs.containsKey(job.id) && jobs.size()>=4096)throw new java.io.IOException("Production journal limit reached; no action performed");
        var previous=jobs.put(job.id,job);
        try {save();}catch(java.io.IOException failed){if(previous==null)jobs.remove(job.id);else jobs.put(job.id,previous);throw failed;}
    }
    /** Explicit player acknowledgement after inspecting an uncertain job; retains its evidence. */
    public Job acknowledgeReview(String prefix,String account)throws java.io.IOException {
        if(prefix==null || prefix.length()<8)throw new IllegalArgumentException("Use at least eight characters of the job ID");
        var matches=all().stream().filter(j->j.account().equals(account) && j.id().startsWith(prefix)).toList();
        if(matches.size()!=1 || matches.getFirst().state()!=State.REVIEW)
            throw new IllegalArgumentException("Name exactly one REVIEW job belonging to this account");
        var job=matches.getFirst();
        var acknowledged=job.withState(State.CANCELLED,"Player acknowledged manual inventory/workstation/listing review; no sale or profit assumed. Previous: "+job.reason());
        put(acknowledged);return acknowledged;
    }
    public boolean occupies(ProductionRecipe.Kind kind,int slot,RecipeCatalog catalog,String account) {
        return jobs.values().stream().anyMatch(j->j.account.equals(account) && j.workstationSlot==slot && j.state!=State.DONE && j.state!=State.CANCELLED
                && catalog.byKey(j.recipeKey).map(r->r.kind()==kind).orElse(false));
    }
    /** Crash recovery never assumes a pending purchase/submission/claim/listing succeeded or failed. */
    public void recoverUncertain(String account)throws java.io.IOException {
        var original=new LinkedHashMap<>(jobs);boolean changed=false;
        for(var j:List.copyOf(jobs.values()))if(j.account.equals(account) && Set.of(State.BUYING,State.PROCESSING,State.SUBMITTING,State.CLAIMING,State.LISTING).contains(j.state)) {
            jobs.put(j.id,j.withState(State.REVIEW,"Interrupted operation; verify live inventory, workstation and listings"));changed=true;
        }
        if(changed)try{save();}catch(java.io.IOException failed){jobs.clear();jobs.putAll(original);throw failed;}
    }
    private void save()throws java.io.IOException {
        if(path.getParent()!=null)Files.createDirectories(path.getParent());
        var root=new JsonObject();root.addProperty("schema",1);root.add("jobs",new Gson().toJsonTree(jobs.values()));
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(root));
        Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}
