package com.goofy.goofyaddons.menu;

/** Item identity and selected device facts; display names are never enough. */
public record ItemMetadata(String uuid,String petType,String petTier,Double petExperience,
        String petHeldItem,String petSkin,Integer petCandyUsed,String vanillaId,CompactorData compactor) {
    public ItemMetadata(String uuid,String petType,String petTier,Double petExperience,String petHeldItem,String petSkin,Integer petCandyUsed,String vanillaId) {
        this(uuid,petType,petTier,petExperience,petHeldItem,petSkin,petCandyUsed,vanillaId,null);
    }
    public static final ItemMetadata EMPTY=new ItemMetadata(null,null,null,null,null,null,null,null);
    public String petVariant() {
        if(petType==null || petTier==null)return null;
        int tier=java.util.List.of("COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC","DIVINE").indexOf(petTier);
        return tier<0?null:petType+";"+tier;
    }
    public boolean samePet(ItemMetadata other) {
        return other!=null && uuid!=null && uuid.equals(other.uuid) && petVariant()!=null
                && petVariant().equals(other.petVariant()) && java.util.Objects.equals(petExperience,other.petExperience)
                && java.util.Objects.equals(petHeldItem,other.petHeldItem) && java.util.Objects.equals(petSkin,other.petSkin)
                && java.util.Objects.equals(petCandyUsed,other.petCandyUsed);
    }
}
