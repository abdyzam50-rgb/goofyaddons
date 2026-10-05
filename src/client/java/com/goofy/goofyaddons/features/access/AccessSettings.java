package com.goofy.goofyaddons.features.access;
public final class AccessSettings {
    public String bazaarMode="AUTO";
    public boolean checkSkills=true;
    public int navigationTimeoutSeconds=90;
    public void validate() {
        if(!java.util.Set.of("AUTO","COMMAND","NPC").contains(bazaarMode==null?"":bazaarMode)
                || navigationTimeoutSeconds<15 || navigationTimeoutSeconds>180)
            throw new IllegalArgumentException("Access requires bazaarMode AUTO/COMMAND/NPC and a 15-180 second timeout");
    }
}
